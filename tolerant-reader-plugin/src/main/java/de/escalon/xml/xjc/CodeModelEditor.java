package de.escalon.xml.xjc;

import com.sun.codemodel.JArray;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JDocComment;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JForEach;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.model.CClassInfo;
import com.sun.tools.xjc.model.CPropertyInfo;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import de.escalon.xml.xjc.BeanInclusionHelper.BeanInclusions;
import de.escalon.xml.xjc.BeanInclusionHelper.ExpressionSpec;
import de.escalon.xml.xjc.SchemaProcessor.ChangeSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;

/**
 * Generates Java Code using com.sun.codemodel.
 */
public class CodeModelEditor {

  /**
   * Change field types and accessors so that they use adapted types. E.g. if there is a
   * <code>foo</code> field of type A, and type A is adapted to type B, this will make the
   * <code>foo</code> field into type B.
   *
   * @param outline        to use for code generation
   * @param beanInclusions to apply
   * @param beansToChange  change sets by fqcn
   * @param classInfo      class info
   * @param implClass      to edit
   */
  void applyAdaptersToFieldsAndAccessors(
      Outline outline, BeanInclusions beanInclusions,
      Map<String, ChangeSet> beansToChange, CClassInfo classInfo,
      JDefinedClass implClass)
      throws ClassNotFoundException, IOException {
    BeanInclusionHelper.BeanInclusion beanInclusion = beanInclusions.getBeanInclusion(classInfo);
    if (beanInclusion == null) {
      return;
    }

    Collection<JMethod> methods = implClass.methods();
    Map<String, JFieldVar> fields = implClass.fields();
    JCodeModel codeModel = outline.getCodeModel();
    for (Map.Entry<String, JFieldVar> entry : new HashMap<String, JFieldVar>(fields).entrySet()) {
      JFieldVar field = entry.getValue();
      String fieldName = entry.getKey();
      JType fieldType = field.type();
      CPropertyInfo propertyInfo = classInfo.getProperty(fieldName);
      if (propertyInfo == null) {
        continue;
      }
      String publicName = propertyInfo.getName(true);
      // the adapted field type is by definition not part of the Schema, must be available
      // at xjc compile time

      BeanInclusionHelper.AdapterSpec xmlAdapterSpec = beanInclusion.getXmlAdapter(fieldName);
      if (xmlAdapterSpec != null) {

        JType adaptedFieldType = codeModel.parseType(xmlAdapterSpec.adaptsToType);
        JClass adapterJClass = codeModel.directClass(xmlAdapterSpec.adapterClass);

        // TODO add setter to classInfo for adapted members of type List
        if (List.class.getName()
            .equals(fieldType.erasure()
                .fullName())) {

          JMethod setter = implClass.method(JMod.PUBLIC, adaptedFieldType, "set" + publicName);
          JVar param = setter.param(adaptedFieldType, fieldName);
          setter.body()
              .assign(JExpr._this()
                      .ref(field),
                  param);
        }

        // TODO code below is duplicate of applyBeanAliasesToFieldsAndAccessors
        implClass.removeField(field);

        JFieldVar adaptedField = implClass.field(field.mods()
            .getValue(), adaptedFieldType, fieldName);

        AnnotationHelper.applyAnnotations(outline, Annotatable.from(adaptedField),
            field.annotations());
        adaptedField.annotate(XmlJavaTypeAdapter.class)
            .param("value", adapterJClass);

        applyTypeToAccessors(outline, implClass, methods, field, fieldType, publicName,
            adaptedFieldType);
      }
    }
  }

  void applyTypeToAccessors(Outline outline, JDefinedClass implClass,
      Collection<JMethod> methods,
      JFieldVar field, JType fieldType, String publicName, JType adjustedFieldType)
      throws IOException,
      ClassNotFoundException {
    JMethod getter = ClassHelper.findGetterInClass(implClass, publicName);
    if (getter != null) {
      JMethod newMethod = implClass.method(getter.mods()
          .getValue(), adjustedFieldType, getter.name());

      JBlock body = newMethod.body();
      String fullName = adjustedFieldType.erasure()
          .fullName();
      if (List.class.getName()
          .equals(fullName) || ArrayList.class.getName()
          .equals(fullName)) {

        if (adjustedFieldType instanceof JClass) {
          JClass adjustedFieldTypeJClass = (JClass) adjustedFieldType;
          if (adjustedFieldTypeJClass.isParameterized()) {
            List<JClass> typeParameters = adjustedFieldTypeJClass.getTypeParameters();

            body._if(field.eq(JExpr._null()))
                ._then()
                .assign(JExpr._this()
                        .ref(field),
                    JExpr._new(outline.getCodeModel()
                        .ref(ArrayList.class)
                        .narrow(typeParameters)));
          }
        }
      }
      body._return(field);
      AnnotationHelper.applyAnnotations(outline, Annotatable.from(newMethod), getter.annotations());
      methods.remove(getter);
    }
    JMethod setter = ClassHelper.findSetterInClass(implClass, publicName, fieldType);
    if (setter != null) {
      addSetter(outline, implClass, field, setter, adjustedFieldType);
      methods.remove(setter);
    }
  }

  void addSetter(Outline outline, JDefinedClass bean, JFieldVar fieldVar,
      JMethod originalSetter,
      JType fieldTypeForNewSetter) {
    if (!List.class.getName()
        .equals(fieldTypeForNewSetter.erasure()
            .fullName())) {
      JMethod aliasedMethod = bean.method(originalSetter.mods()
          .getValue(), outline.getCodeModel().VOID, originalSetter.name());
      JDocComment originalJavadoc = originalSetter.javadoc();
      aliasedMethod.javadoc()
          .append(originalJavadoc);
      aliasedMethod.body()
          .assign(JExpr._this()
              .ref(fieldVar), aliasedMethod.param(fieldTypeForNewSetter, fieldVar.name()));
    }
  }

  /**
   * Applies expressions for computed getter.
   *
   * @param outline               of class which is being built
   * @param beanInclusions        from bindings file
   * @param classOutlines         all classes in schema
   * @param changeSetsByClassname fqcn of original class to change set
   */
  void addComputedGetter(Outline outline, BeanInclusions beanInclusions,
      Collection<? extends ClassOutline> classOutlines,
      Map<String, ChangeSet> changeSetsByClassname) {

    JCodeModel codeModel = outline.getCodeModel();

    for (ClassOutline classOutline : classOutlines) {
      BeanInclusionHelper.BeanInclusion beanInclusion =
          beanInclusions.getBeanInclusion(classOutline.target);
      if (beanInclusion == null) {
        continue;
      }
      Set<Map.Entry<String, ExpressionSpec>> expressionSpecEntries = beanInclusion.getExpressions()
          .entrySet();
      ChangeSet changeSet = changeSetsByClassname.get(classOutline.implClass.fullName());

      JDefinedClass implClass;
      if (changeSet == null) {
        implClass = classOutline.implClass;
      } else {
        implClass = changeSet.definedClass;
      }
      for (Map.Entry<String, ExpressionSpec> expressionSpecEntry : expressionSpecEntries) {
        ExpressionSpec expressionSpec = expressionSpecEntry.getValue();
        JMethod computedMethod = implClass.method(JMod.PUBLIC,
            OutlineHelper.getJClassFromOutline(outline,
                expressionSpec.computesToType),
            "get" + StringHelper.capitalize(expressionSpecEntry.getKey()));
        JBlock body = computedMethod.body();
        if (ClassHelper.isPresent("org.springframework.expression.ExpressionParser")) {
          // required Type references
          JType parserIface = codeModel._ref(org.springframework.expression.ExpressionParser.class);
          JType expressionIface = codeModel._ref(org.springframework.expression.Expression.class);
          JType contextIface =
              codeModel._ref(org.springframework.expression.EvaluationContext.class);
          JType parser = codeModel
              ._ref(org.springframework.expression.spel.standard.SpelExpressionParser.class);
          JType context = codeModel
              ._ref(org.springframework.expression.spel.support.StandardEvaluationContext.class);

          JVar parserVar = body.decl(parserIface, "parser", JExpr._new(parser));
          JVar contextVar = body.decl(contextIface, "context", JExpr._new(context)
              .arg(JExpr._this()));

          // assign #matcher as spel var if regex present
          if (expressionSpec.regex != null) {
            JType matcher = codeModel._ref(java.util.regex.Matcher.class);
            JType pattern = codeModel._ref(java.util.regex.Pattern.class);
            JVar patternVar = body.decl(pattern, "pattern",
                codeModel.ref(Pattern.class).staticInvoke("compile").arg(expressionSpec.regex));

            JVar regexPropertyValueVar =
                body.decl(codeModel._ref(String.class), "regexPropertyValue",
                    JExpr.cast(codeModel._ref(String.class),
                        JExpr.invoke(parserVar, "parseExpression")
                            .arg(JExpr.lit(expressionSpec.regexPropertyExpr))
                            .invoke("getValue").arg(contextVar)));

            JVar matcherVar = body.decl(matcher, "matcher",
                JExpr.invoke(patternVar, "matcher").arg(regexPropertyValueVar));
            body.invoke(matcherVar, "find");
            body.invoke(contextVar, "setVariable").arg("matcher").arg(matcherVar);
          }

          JVar expVar = body.decl(expressionIface, "exp", JExpr.invoke(parserVar, "parseExpression")
              .arg(JExpr.lit(expressionSpec.expression)));

          JVar ret = body.decl(codeModel._ref(java.lang.Object.class), "ret",
              JExpr.invoke(expVar, "getValue")
                  .arg(contextVar));
          body._return(
              JExpr.cast(codeModel.ref(expressionSpec.computesToType), ret));
        } else if (ClassHelper.isPresent("javax.el.ELProcessor")) {
          JType elp = codeModel._ref(javax.el.ELProcessor.class);
          JVar elpVar = body.decl(elp, "elp", JExpr._new(elp));
          JInvocation invokeDefineBean = body.invoke(elpVar, "defineBean");
          invokeDefineBean.arg("bean")
              .arg(JExpr._this());

          JVar ret =
              body.decl(codeModel.ref("java.lang.Object"), "ret", JExpr.invoke(elpVar, "eval")
                  .arg(JExpr.lit(expressionSpec.expression)));

          body._return(
              JExpr.cast(codeModel.ref(expressionSpec.computesToType), ret));
        } else {
          body._return(JExpr.direct(expressionSpec.expression));
        }
        computedMethod.annotate(XmlTransient.class);
      }
    }
  }

  /**
   * Applies expressions for synthetic setter.
   *
   * @param outline               of class which is being built
   * @param beanInclusions        from bindings file
   * @param classOutlines         all classes in schema
   * @param changeSetsByClassname fqcn of original class to change set
   */
  void addSyntheticSetter(Outline outline, BeanInclusions beanInclusions,
      Collection<? extends ClassOutline> classOutlines,
      Map<String, ChangeSet> changeSetsByClassname) {

    JCodeModel codeModel = outline.getCodeModel();

    for (ClassOutline classOutline : classOutlines) {
      BeanInclusionHelper.BeanInclusion beanInclusion =
          beanInclusions.getBeanInclusion(classOutline.target);
      if (beanInclusion == null) {
        continue;
      }
      Set<Map.Entry<String, BeanInclusionHelper.SetterSpec>> setterSpecEntries =
          beanInclusion.getSetters()
              .entrySet();
      ChangeSet changeSet = changeSetsByClassname.get(classOutline.implClass.fullName());

      JDefinedClass implClass;
      if (changeSet == null) {
        implClass = classOutline.implClass;
      } else {
        implClass = changeSet.definedClass;
      }
      for (Map.Entry<String, BeanInclusionHelper.SetterSpec> setterSpecEntry : setterSpecEntries) {
        JMethod setterMethod = implClass.method(JMod.PUBLIC,
            outline.getCodeModel().VOID,
            "set" + StringHelper.capitalize(setterSpecEntry.getKey()));

        BeanInclusionHelper.SetterSpec setterSpec = setterSpecEntry.getValue();
        setterMethod.param(OutlineHelper.getJClassFromOutline(outline, setterSpec.paramType),
            setterSpec.paramName);

        JBlock body = setterMethod.body();
        if (ClassHelper.isPresent("org.springframework.expression.ExpressionParser")) {
          // required Type references
          JType parserIface = codeModel._ref(org.springframework.expression.ExpressionParser.class);
          JType contextIface =
              codeModel._ref(org.springframework.expression.EvaluationContext.class);

          JClass stringList = codeModel.ref(List.class).narrow(String.class);
          JType parser = codeModel
              ._ref(org.springframework.expression.spel.standard.SpelExpressionParser.class);
          JType context = codeModel
              ._ref(org.springframework.expression.spel.support.StandardEvaluationContext.class);

          JVar parserVar = body.decl(parserIface, "parser", JExpr._new(parser));
          JVar contextVar = body.decl(contextIface, "context", JExpr._new(context)
              .arg(JExpr._this()));

          // assign #matcher as spel var if regex present
          if (setterSpec.regex != null) {
            JType matcher = codeModel._ref(java.util.regex.Matcher.class);
            JType pattern = codeModel._ref(java.util.regex.Pattern.class);
            JVar patternVar = body.decl(pattern, "pattern",
                codeModel.ref(Pattern.class).staticInvoke("compile").arg(setterSpec.regex));
            JVar matcherVar = body.decl(matcher, "matcher",
                JExpr.invoke(patternVar, "matcher").arg(setterMethod.params().get(0)));
            body.invoke(matcherVar, "find");
            body.invoke(contextVar, "setVariable").arg("matcher").arg(matcherVar);
          }
          body.invoke(contextVar, "setVariable")
              .arg(setterSpec.paramName)
              .arg(setterMethod.params().get(0));

          JArray assignmentArray = JExpr.newArray(codeModel._ref(String.class));
          List<String> assignments = setterSpecEntry.getValue().assignments;
          for (String assignment : assignments) {
            assignmentArray.add(JExpr.lit(assignment));
          }
          JInvocation asListInvocation =
              codeModel.ref(Arrays.class).staticInvoke("asList").arg(assignmentArray);
          JVar assignmentExpressions =
              body.decl(stringList, "assignmentExpressions", asListInvocation);

          JVar beanWrapperVar = body.decl(codeModel.ref(BeanWrapper.class), "beanWrapper",
              codeModel.ref(PropertyAccessorFactory.class)
                  .staticInvoke("forBeanPropertyAccess").arg(JExpr._this()));
          body.invoke(beanWrapperVar, "setAutoGrowNestedPaths").arg(JExpr.lit(true));
          JForEach forEach2 = body
              .forEach(codeModel.ref(String.class), "assignmentExpression", assignmentExpressions);

          JBlock forEachBody = forEach2.body();
          JVar assignmentPartsVar =
              forEachBody.decl(codeModel._ref(String[].class), "assignmentParts",
                  forEach2.var().invoke("trim")
                      .invoke("split").arg("\\s*=\\s*"));
          JVar expVar = forEachBody.decl(codeModel._ref(Object.class), "exprValue",
              JExpr.invoke(parserVar, "parseExpression")
                  .arg(assignmentPartsVar.component(JExpr.lit(1)))
                  .invoke("getValue").arg(contextVar));
          forEachBody.invoke(beanWrapperVar, "setPropertyValue")
              .arg(assignmentPartsVar.component(JExpr.lit(0)))
              .arg(expVar);
        } else if (ClassHelper.isPresent("javax.el.ELProcessor")) {
          throw new IllegalArgumentException("tr:set requires Spring EL");
        } else {
          throw new IllegalArgumentException("tr:set requires Spring EL");
        }
      }
    }
  }

  void addProperties(Outline outline, BeanInclusionHelper.BeanInclusion beanInclusion,
      ClassOutline classOutline) {
    if (beanInclusion == null) {
      return;
    }

    Map<String, String> propertiesToAdd = beanInclusion.getPropertiesToAdd();
    JDefinedClass implClass = classOutline.implClass;

    for (Map.Entry<String, String> propertyAndClass : propertiesToAdd.entrySet()) {
      String propertyToAdd = propertyAndClass.getKey();
      String classNameOfProperty = propertyAndClass.getValue();
      // can't add CPropertyInfo to classInfo as it requires a corresponding xml schema type
      JClass propertyType = OutlineHelper.getJClassFromOutline(outline, classNameOfProperty);
      JFieldVar field = implClass.field(JMod.PROTECTED,
          propertyType,
          propertyToAdd);
      field.annotate(XmlTransient.class);

      JMethod getter = implClass.method(JMod.PUBLIC, propertyType,
          "get" + StringHelper.capitalize(propertyToAdd));
      getter.body()
          ._return(JExpr._this()
              .ref(field));

      JMethod setter = implClass.method(JMod.PUBLIC, outline.getCodeModel().VOID,
          "set" + StringHelper.capitalize(propertyToAdd));
      setter.body()
          .assign(JExpr._this()
              .ref(field), setter.param(propertyType, field.name()));
    }
  }

  void addToObjectFactory(Outline outline, JDefinedClass newBean) {
    String factoryName = newBean._package()
        .name() + ".ObjectFactory";
    JDefinedClass objFactory = OutlineHelper.getJDefinedClassFromOutline(outline, factoryName);
    JMethod factoryMethod = objFactory.method(JMod.PUBLIC, newBean, "create" + newBean.name());
    factoryMethod.body()
        ._return(JExpr._new(newBean));
  }
}
