package de.escalon.xml.xjc;

import com.sun.codemodel.JAnnotationArrayMember;
import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.model.CClassInfo;
import com.sun.tools.xjc.model.CCustomizations;
import com.sun.tools.xjc.model.CPropertyInfo;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import com.sun.xml.xsom.XSAttributeUse;
import com.sun.xml.xsom.XSComplexType;
import com.sun.xml.xsom.XSComponent;
import com.sun.xml.xsom.XSContentType;
import com.sun.xml.xsom.XSElementDecl;
import com.sun.xml.xsom.XSModelGroup;
import com.sun.xml.xsom.XSParticle;
import com.sun.xml.xsom.XSTerm;
import com.sun.xml.xsom.XSType;
import de.escalon.xml.xjc.SchemaProcessor.ChangeSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;
import org.apache.commons.lang3.StringUtils;
import org.xml.sax.Locator;

import static de.escalon.xml.xjc.SchemaInspector.isRequiredElementOrAttribute;

/**
 * Edits class info by moving, renaming, deleting and annotating classes and properties.
 * Uses {@link CodeModelEditor} to generate code if necessary.
 */
public class ClassInfoEditor {

  private static final Set<String> IGNORED_ANNOTATIONS = new HashSet<String>(
      Arrays.asList(XmlSeeAlso.class.getName(), XmlAccessorType.class.getName()));

  private final CodeModelEditor codeModelEditor = new CodeModelEditor();
  private final HydraEditor hydraEditor = new HydraEditor();

  void removeClass(Outline outline, CClassInfo classInfo) {
    String fullName = classInfo.getName();
    JPackage ownerPackage = classInfo.getOwnerPackage();
    JDefinedClass clazz = OutlineHelper.getJDefinedClassFromOutline(outline, fullName);
    if (clazz != null) { // no nested types
      ownerPackage.remove(clazz);
    }

    // Zap createXXX method from ObjectFactory
    String factoryName = ownerPackage.name() + ".ObjectFactory";
    JDefinedClass objFactory = OutlineHelper.getJDefinedClassFromOutline(outline, factoryName);
    if (objFactory != null) {
      Collection<JMethod> methods = objFactory.methods();
      Iterator<JMethod> methodIterator = methods.iterator();
      List<JMethod> methodsToRemove = new ArrayList<JMethod>();
      while (methodIterator.hasNext()) {
        JMethod method = methodIterator.next();
        String toRemoveCandidate = method.type()
            .fullName();
        if (toRemoveCandidate.equals(fullName)
            || toRemoveCandidate.equals("javax.xml.bind.JAXBElement<" + fullName + ">")
            || AnnotationHelper.hasXmlElementDeclScope(method, fullName)) {
          methodsToRemove.add(method);
        }
      }
      methods.removeAll(methodsToRemove);
      if (methods.isEmpty()) {
        ownerPackage.remove(objFactory);

        // delete the entire package, if empty
        if (!ownerPackage.classes()
            .hasNext()) {
          Iterator<JPackage> pkgs = ownerPackage.owner()
              .packages();
          while (pkgs.hasNext()) {
            JPackage jPackage = pkgs.next();
            if (jPackage.name()
                .equals(ownerPackage.name())) {
              pkgs.remove();
            }
          }
        }
      }
    }
  }

  private ChangeSet defineNewClassFrom(Outline outline, JPackage targetPackage,
      String newClassName,
      ClassOutline toReplace) {
    CClassInfo oldClassInfo = toReplace.target;
    Locator locator = oldClassInfo.getLocator();
    QName typeName = oldClassInfo.getTypeName();
    QName elementName = oldClassInfo.getElementName();
    XSComponent schemaSource = oldClassInfo.getSchemaComponent();
    CCustomizations customizations = oldClassInfo.getCustomizations();

    CClassInfo newClassInfo =
        new CClassInfo(oldClassInfo.model, targetPackage.owner(), targetPackage.name()
            .isEmpty() ? newClassName : targetPackage.name() + "." + newClassName, locator,
            typeName, elementName,
            schemaSource, customizations);
    // getClazz also adds classInfo to outline:
    ClassOutline newClassOutline = outline.getClazz(newClassInfo);

    Iterator<JClass> oldClassImplements = toReplace.implClass._implements();
    while (oldClassImplements.hasNext()) {
      JClass iface = oldClassImplements.next();
      newClassOutline.target._implements(iface);
      newClassOutline.implClass._implements(iface);
    }

    JDefinedClass newBean = newClassOutline.implClass;
    return new ChangeSet(toReplace, newClassOutline, newBean);
  }

  void createAliasBeans(Outline outline, BeanInclusionHelper.BeanInclusions beanInclusions,
      Collection<? extends ClassOutline> classOutlines,
      Map<String, ChangeSet> beansToChange) {
    for (ClassOutline classOutline : new ArrayList<ClassOutline>(classOutlines)) { // no
      // concurrent
      // mod
      CClassInfo classInfo = classOutline.target;
      JDefinedClass implClass = classOutline.implClass;

      String aliasBeanName = getBeanAliasName(classInfo, beanInclusions);
      if (!aliasBeanName.isEmpty()) {
        JPackage parent = implClass.getPackage();
        ChangeSet
            changeSet = replaceClass(outline, parent, aliasBeanName, classOutline);
        beansToChange.put(classOutline.target.fullName(), changeSet); // keep for later
      }
    }
  }

  private ChangeSet replaceClass(Outline outline, JPackage targetPackage,
      String newClassName,
      ClassOutline toReplace) {

    ChangeSet
        changeSet = defineNewClassFrom(outline, targetPackage, newClassName, toReplace);

    // add to ObjectFactory
    codeModelEditor.addToObjectFactory(outline, changeSet.definedClass);
    return changeSet;
  }

  void removeBeansWhichHaveAliases(Outline outline,
      Map<String, ChangeSet> beansToChange) {
    for (Map.Entry<String, ChangeSet> beanToChange : beansToChange.entrySet()) {
      removeClass(outline, beanToChange.getValue().sourceClassOutline.target);
    }
  }

  void addXmlSeeAlso(Outline outline, Map<String, Set<String>> classesToKeep,
      Map<String, ChangeSet> beansToChange, CClassInfo classInfo,
      JDefinedClass implClass) {
    Iterator<CClassInfo> subclasses = classInfo.listSubclasses();
    JAnnotationArrayMember arrayValue = null;
    while (subclasses.hasNext()) {
      CClassInfo subclass = subclasses.next();
      String subclassName = subclass.getName();
      if (classesToKeep.containsKey(subclassName)) {
        if (arrayValue == null) {
          JAnnotationUse annotateXmlSeeAlso = implClass.annotate(XmlSeeAlso.class);
          arrayValue = annotateXmlSeeAlso.paramArray("value");
        }
        ChangeSet changeSet = beansToChange.get(subclassName);

        if (changeSet != null) {
          subclassName = changeSet.getAliasBeanName();
        }
        JDefinedClass clazz = outline.getCodeModel()
            ._getClass(subclassName);
        if (clazz != null) { // not for restricted classes
          arrayValue.param(clazz);
        }
      }
    }
  }

  private String getBeanAliasName(CClassInfo classInfo,
      BeanInclusionHelper.BeanInclusions beanInclusions) {
    BeanInclusionHelper.BeanInclusion beanInclusionForClassInfo =
        beanInclusions.getBeanInclusion(classInfo);

    String aliasBeanName = "";
    if (beanInclusionForClassInfo != null) {
      aliasBeanName = beanInclusionForClassInfo.getBeanAlias();
    }
    return aliasBeanName;
  }

  void copyProperties(Outline outline, BeanInclusionHelper.BeanInclusions beanInclusions,
      Map<String, ChangeSet> beansToChange,
      CClassInfo sourceClassInfo, JDefinedClass sourceImplClass,
      ChangeSet changeSet,
      JDefinedClass aliasBean,
      Map<String, XSComponent> expectedProperties) throws ClassNotFoundException, IOException {

    // TODO review parameter list:ChangeSet vs sourceClassInfo/sourceImplClass

    Collection<JMethod> methods = sourceImplClass.methods();
    Map<String, JFieldVar> fields = sourceImplClass.fields();

    Set<String> expectedPropertyNames = expectedProperties.keySet();
    for (String expectedPropertyName : expectedPropertyNames) {
      String privatePropertyName = StringHelper.uncapitalize(expectedPropertyName);
      CPropertyInfo property = sourceClassInfo.getProperty(privatePropertyName);
      if (property == null) {
        throw new IllegalStateException("The bean "
            + aliasBean.fullName()
            + " has a schema restriction on the property "
            + privatePropertyName
            + " of its base type "
            + sourceClassInfo.fullName()
            + ", but the generated base bean has no such property. Add "
            + privatePropertyName
            + " to the properties list of the base <bean name=\""
            + sourceClassInfo.shortName
            + "\"/> element in your bindings.xjb.");
      }
    }

    JFieldVar serialVersionUidField = fields.get("serialVersionUID");
    // TODO how can we read the value of a field?
    if (serialVersionUidField != null) {
      aliasBean.field(serialVersionUidField.mods()
          .getValue(), serialVersionUidField.type(), serialVersionUidField.name(), JExpr.lit(-1L));
    }

    List<CPropertyInfo> properties = sourceClassInfo.getProperties();
    for (CPropertyInfo cPropertyInfo : properties) {
      if (!expectedProperties.isEmpty() && !expectedProperties.containsKey(
          cPropertyInfo.getName(true))) {
        continue;
      }
      String sourcePropertyName = cPropertyInfo.getName(false);

      BeanInclusionHelper.BeanInclusion beanInclusion =
          beanInclusions.getBeanInclusion(sourceClassInfo);
      String fieldName = sourcePropertyName;
      if (beanInclusion != null) {
        String aliasFieldName = beanInclusion.getPropertyAlias(sourcePropertyName);
        if (aliasFieldName != null) {
          fieldName = aliasFieldName;
        }
      }
      JFieldVar field = fields.get(fieldName);

      JType fieldType = field.type();
      JType aliasFieldType = getAliasFieldType(outline, beansToChange, fieldType);
      fieldType = aliasFieldType != null ? aliasFieldType : fieldType;

      // TODO: find property recursively in parent?
      CPropertyInfo sourceProperty = sourceClassInfo.getProperty(sourcePropertyName);
      changeSet.targetClassOutline.target.addProperty(sourceProperty);

      JFieldVar aliasBeanField = aliasBean.field(field.mods()
          .getValue(), fieldType, fieldName);
      // TODO apply restrictions on @XmlElement: required, value restrictions
      // TODO (although only for correctness, would have an effect on schemagen
      // TODO from our Jaxb bean).
      AnnotationHelper.applyAnnotations(outline, Annotatable.from(aliasBeanField),
          field.annotations());

      String publicName = fieldName.substring(0, 1)
          .toUpperCase() + fieldName.substring(1);
      Set<String> settersAndGetters = ClassHelper.getSettersAndGetters(publicName);

      for (JMethod method : methods) {
        if (!settersAndGetters.contains(method.name())) {
          continue;
        }
        List<JVar> params = method.params();
        if (params.isEmpty()) { // getter
          method.type(fieldType);
          aliasBean.methods()
              .add(method);
        } else { // setter
          codeModelEditor.addSetter(outline, aliasBean, aliasBeanField, method, fieldType);
        }
      }
    }
  }

  private JClass getAliasFieldType(Outline outline,
      Map<String, ChangeSet> beansToChange,
      JType fieldType) {
    JClass ret = null;
    if (fieldType instanceof JClass) {
      JClass fieldAsJClass = (JClass) fieldType;
      if (fieldAsJClass.isParameterized()) {
        List<JClass> typeParameters = fieldAsJClass.getTypeParameters();
        // getter must have one type parameter
        JClass typeParameter = typeParameters.get(0);
        ChangeSet changeSet = beansToChange.get(typeParameter.fullName());
        if (changeSet != null) {
          String genericTypeParameterAlias = changeSet.getAliasBeanName();
          JDefinedClass aliasOfGenericTypeParameter =
              OutlineHelper.getJDefinedClassFromOutline(outline,
                  genericTypeParameterAlias);
          JClass parseType = outline.getCodeModel()
              .ref(List.class);
          ret = parseType.narrow(aliasOfGenericTypeParameter);
        }
      } else {
        ChangeSet changeSet = beansToChange.get(fieldType.fullName());

        if (changeSet != null) {
          ret = OutlineHelper.getJDefinedClassFromOutline(outline, changeSet.getAliasBeanName());
        }
      }
    }
    return ret;
  }

  void applyBeanAliasesAndAdaptersToClassMembers(Outline outline,
      BeanInclusionHelper.BeanInclusions beanInclusions,
      Collection<? extends ClassOutline> classOutlines, Map<String, Set<String>> classesToKeep,
      Map<String, ChangeSet> beansToChange)
      throws ClassNotFoundException, IOException {

    for (ClassOutline classOutline : classOutlines) {
      CClassInfo classInfo = classOutline.target;
      JDefinedClass implClass = classOutline.implClass;
      codeModelEditor.applyAdaptersToFieldsAndAccessors(outline, beanInclusions, beansToChange,
          classInfo,
          implClass);
      applyBeanAliasesToFieldsAndAccessors(outline, beanInclusions, beansToChange, classInfo,
          implClass);

      JClass superClass = implClass._extends();
      ChangeSet changeSet = beansToChange.get(superClass.fullName());
      if (changeSet != null) {
        implClass._extends(changeSet.definedClass);
        classInfo.setBaseClass(changeSet.targetClassOutline.target);
      }
    }
  }

  private void applyBeanAliasesToFieldsAndAccessors(Outline outline,
      BeanInclusionHelper.BeanInclusions beanInclusions,
      Map<String, ChangeSet> beansToChange, CClassInfo classInfo,
      JDefinedClass implClass)
      throws ClassNotFoundException, IOException {
    Collection<JMethod> methods = implClass.methods();
    Map<String, JFieldVar> fields = implClass.fields();

    for (Map.Entry<String, JFieldVar> entry : new HashMap<String, JFieldVar>(
        fields).entrySet()) { // concurrent
      // mod
      JFieldVar field = entry.getValue();
      String fieldName = entry.getKey();
      JType fieldType = field.type();
      CPropertyInfo propertyInfo = classInfo.getProperty(fieldName);
      if (propertyInfo == null) {
        continue;
      }
      String publicName = propertyInfo.getName(true);
      JClass aliasFieldType = getAliasFieldType(outline, beansToChange, fieldType);

      if (aliasFieldType != null) {
        // field
        implClass.removeField(field);
        JFieldVar aliasTypeField = implClass.field(field.mods()
            .getValue(), aliasFieldType, fieldName);

        AnnotationHelper.applyAnnotations(outline, Annotatable.from(aliasTypeField),
            field.annotations());

        codeModelEditor.applyTypeToAccessors(outline, implClass, methods, field, fieldType,
            publicName,
            aliasFieldType);
      }
    }
  }

  void copyJavadocAndImplementsClause(CClassInfo sourceClassInfo, JDefinedClass aliasBean) {
    aliasBean.javadoc()
        .add(sourceClassInfo.javadoc);
    Iterator<JClass> impls = aliasBean._implements();
    while (impls.hasNext()) {
      JClass iface = impls.next();
      aliasBean._implements(iface);
    }
  }

  void applyXmlTypeToClasses(Collection<? extends ClassOutline> classOutlines,
      BeanInclusionHelper.BeanInclusions beanInclusions,
      Map<String, Set<String>> classesToKeep) {
    for (final ClassOutline classOutline : classOutlines) {
      CClassInfo classInfo = classOutline.target;
      String className = classInfo.getName();

      final Set<String> propertiesToKeep = classesToKeep.get(className);
      if (propertiesToKeep == null) {
        continue;
      }
      JDefinedClass implClass = classOutline.implClass;

      // add XmlType with name and propOrder
      QName typeName = classInfo.getTypeName();
      if (typeName != null) { // anonymous type
        JAnnotationUse annotateXmlType = implClass.annotate(XmlType.class);
        annotateXmlType.param("name", typeName.getLocalPart());
      }
    }
  }

  void addPropertiesToClasses(Outline outline, BeanInclusionHelper.BeanInclusions beanInclusions) {
    Collection<? extends ClassOutline> classOutlines = outline.getClasses();

    for (final ClassOutline classOutline : classOutlines) {
      BeanInclusionHelper.BeanInclusion beanInclusion =
          beanInclusions.getBeanInclusion(classOutline.target);
      codeModelEditor.addProperties(outline, beanInclusion, classOutline);
    }
  }

  void addPropertiesToAliases(Outline outline, BeanInclusionHelper.BeanInclusions beanInclusions,
      Map<String, ChangeSet> beansToChange) {
    for (ChangeSet changeSet : beansToChange.values()) {
      ClassOutline targetClassOutline = changeSet.targetClassOutline;
      ClassOutline sourceClassOutline = changeSet.sourceClassOutline;
      BeanInclusionHelper.BeanInclusion beanInclusion =
          beanInclusions.getBeanInclusion(sourceClassOutline.target);
      codeModelEditor.addProperties(outline, beanInclusion, targetClassOutline);
    }
  }

  void applyXmlSeeAlso(Outline outline, BeanInclusionHelper.BeanInclusions beanInclusions,
      Collection<? extends ClassOutline> classOutlines, Map<String, Set<String>> classesToKeep,
      Map<String, ChangeSet> beansToChange) {
    for (ClassOutline classOutline : classOutlines) {
      CClassInfo classInfo = classOutline.target;
      JDefinedClass implClass = classOutline.implClass;
      addXmlSeeAlso(outline, classesToKeep, beansToChange, classInfo, implClass);
    }
  }

  void applyXmlTypeToAliases(Collection<? extends ClassOutline> classOutlines,
      BeanInclusionHelper.BeanInclusions beanInclusions,
      Map<String, Set<String>> classesToKeep,
      Map<String, ChangeSet> beansToChange) {

    for (Map.Entry<String, ChangeSet> beanToRenameEntry : beansToChange.entrySet()) {
      ChangeSet changeSet = beanToRenameEntry.getValue();
      final ClassOutline classOutline = changeSet.sourceClassOutline;
      CClassInfo classInfo = classOutline.target;
      String className = classInfo.getName();

      final Set<String> propertiesToKeep = classesToKeep.get(className);
      if (propertiesToKeep == null) {
        continue;
      }
      JDefinedClass implClass = changeSet.definedClass;

      // add XmlType with name and propOrder
      JAnnotationUse annotateXmlType = implClass.annotate(XmlType.class);
      annotateXmlType.param("name", classInfo.getTypeName()
          .getLocalPart());
    }
  }

  void createRestrictedBeans(Outline outline, BeanInclusionHelper.BeanInclusions beanInclusions,
      Collection<? extends ClassOutline> classOutlines,
      Map<String, Set<String>> classesToKeep,
      Map<String, ChangeSet> beansToChange)
      throws ClassNotFoundException, IOException {

    for (ClassOutline sourceClassOutline : new ArrayList<ClassOutline>(classOutlines)) {
      CClassInfo sourceClassInfo = sourceClassOutline.target;
      JDefinedClass sourceImplClass = sourceClassOutline.implClass;

      if (!classesToKeep.containsKey(sourceClassInfo.getName())) {
        continue;
      }

      BeanInclusionHelper.BeanInclusion beanInclusion =
          beanInclusions.getBeanInclusion(sourceClassInfo);
      XSComponent schemaComponent = sourceClassInfo.getSchemaComponent();
      if (schemaComponent instanceof XSComplexType) {
        XSComplexType xsComplexType = (XSComplexType) schemaComponent;
        int derivationMethod = xsComplexType.getDerivationMethod();
        XSType baseType = xsComplexType.getBaseType();
        if (XSType.RESTRICTION == derivationMethod && !"anyType".equals(baseType.getName())) {
          XSContentType contentType = xsComplexType.getContentType();
          Collection<? extends XSAttributeUse> attributeUses = xsComplexType.getAttributeUses();
          // TODO this might be nested element restrictions, maybe visit instead
          Map<String, XSComponent> expectedProperties = new HashMap<String, XSComponent>();
          if (contentType instanceof XSParticle) {
            XSParticle contentTypeParticle = (XSParticle) contentType;
            XSTerm term = contentTypeParticle.getTerm();
            if (term instanceof XSModelGroup) {
              XSModelGroup modelGroup = (XSModelGroup) term;
              for (XSParticle xsParticle : modelGroup) {
                XSTerm elementDeclTerm = xsParticle.getTerm();
                if (elementDeclTerm.isElementDecl()) {
                  XSElementDecl elementDecl = elementDeclTerm.asElementDecl();
                  String name = elementDecl.getName();
                  if ((beanInclusion != null && beanInclusion.includesProperty(name))
                      || isRequiredElementOrAttribute(xsParticle)) {
                    expectedProperties.put(StringUtils.capitalize(name), xsParticle);
                  }
                }
              }
            }
          }
          for (XSAttributeUse attributeUse : attributeUses) {
            String name = attributeUse.getDecl().getName();
            if ((beanInclusion != null && beanInclusion.includesProperty(name)
                || isRequiredElementOrAttribute(attributeUse))) {
              expectedProperties.put(StringUtils.capitalize(name), attributeUse);
            }
          }

          JPackage parent = sourceImplClass.getPackage();
          parent.remove(sourceImplClass);
          ChangeSet
              changeSet = defineNewClassFrom(outline, parent, sourceImplClass.name(),
              sourceClassOutline);

          JDefinedClass aliasBean = changeSet.definedClass;

          ClassOutline restrictionBaseClassOutline = sourceClassOutline.getSuperClass();
          CClassInfo restrictionBaseClassInfo = restrictionBaseClassOutline.target;
          JDefinedClass restrictionBaseImplClass = restrictionBaseClassOutline.implClass;

          // do not extend
          copyJavadocAndImplementsClause(sourceClassInfo, aliasBean);

          // only copy properties matching restrictions
          copyProperties(outline, beanInclusions, beansToChange, restrictionBaseClassInfo,
              restrictionBaseImplClass, changeSet, aliasBean, expectedProperties);
        }
      }
    }
  }

  /**
   * Removes unused classes and properties and renames remaining aliased properties,
   * also removing XmlType and XmlSeeAlso annotations.
   *
   * @param outline        of classes
   * @param beanInclusions describing editing tasks
   * @param classOutlines  to use
   * @param classesToKeep  which should not be removed
   */
  void removeUnusedClassesAndFieldsAndRenameProperties(Outline outline,
      BeanInclusionHelper.BeanInclusions beanInclusions,
      Collection<? extends ClassOutline> classOutlines, Map<String, Set<String>> classesToKeep) {
    for (final ClassOutline classOutline : classOutlines) {
      CClassInfo classInfo = classOutline.target;
      String className = classInfo.getName();

      if (!classesToKeep.containsKey(className)) {
        removeClass(outline, classInfo);
      } else {
        // remove/rename fields, setters and getters
        JDefinedClass implClass = classOutline.implClass;
        Collection<JMethod> methods = implClass.methods();
        Map<String, JFieldVar> fields = implClass.fields();
        Collection<JMethod> methodsToRemove = new ArrayList<JMethod>();
        final Set<String> propertiesToKeep = classesToKeep.get(className);
        List<CPropertyInfo> properties = classInfo.getProperties();
        BeanInclusionHelper.BeanInclusion beanInclusion =
            beanInclusions.getBeanInclusion(classInfo);

        for (CPropertyInfo propertyInfo : new ArrayList<CPropertyInfo>(properties)) {
          String propertyPrivateName = propertyInfo.getName(false); // fooBar
          String propertyPublicName = propertyInfo.getName(true);
          if (!(propertiesToKeep.contains(propertyPrivateName))) {
            // remove unused field and accessor methods
            properties.remove(propertyInfo);
            JFieldVar fieldVar = fields.get(propertyPrivateName);
            implClass.removeField(fieldVar);
            Set<String> settersAndGetters = ClassHelper.getSettersAndGetters(propertyPublicName);
            for (JMethod method : methods) {
              if (settersAndGetters.contains(method.name())) { // FooBar
                methodsToRemove.add(method); // no concurrent
                // modification
              }
            }
            methods.removeAll(methodsToRemove);
          } else {
            // rename property alias fields and accessor methods
            if (beanInclusion != null) {
              String propertyAlias = beanInclusion.getPropertyAlias(propertyPrivateName);
              if (propertyAlias != null) {
                if (!propertyAlias.equals(propertyPrivateName)) {
                  // alias property: rename field and accessors and expose getter
                  String propertyAliasPublic = propertyAlias.substring(0, 1)
                      .toUpperCase() + propertyAlias.substring(1);

                  JFieldVar fieldVar = fields.get(propertyPrivateName);
                  fieldVar.name(propertyAlias);

                  Set<String> settersAndGetters =
                      ClassHelper.getSettersAndGetters(propertyPublicName);

                  for (JMethod method : methods) {
                    String methodName = method.name();
                    if (settersAndGetters.contains(methodName)) { // FooBar
                      method.name(methodName.replace(propertyPublicName, propertyAliasPublic));
                      if (methodName.startsWith("get") || methodName.startsWith("is")) {
                        // expose getter before renaming the property
                        hydraEditor.applyExpose(propertyInfo.getName(false),
                            Annotatable.from(method),
                            beanInclusions, outline, classInfo);
                      }
                    }
                  }
                  propertyInfo.setName(true, StringHelper.capitalize(propertyAlias));
                  propertyInfo.setName(false, propertyAlias);
                }
              } else {
                // no alias property: just expose
                hydraEditor.applyExpose(propertyInfo.getName(false),
                    Annotatable.from(ClassHelper.findGetterInClass(implClass,
                        propertyPublicName)),
                    beanInclusions, outline, classInfo);
              }
            }
          }
        }

        // remove XmlType and XmlSeeAlso
        Collection<JAnnotationUse> annotations = implClass.annotations();
        List<JAnnotationUse> annotationsToRemove = new ArrayList<JAnnotationUse>();
        for (JAnnotationUse annotation : annotations) {
          String annotationName = annotation.getAnnotationClass()
              .name();
          if (annotationName.equals("XmlType") || annotationName.equals("XmlSeeAlso")) {
            annotationsToRemove.add(annotation); // no concurrent
            // change
          }
        }
        for (JAnnotationUse annotationToRemove : annotationsToRemove) {
          implClass.removeAnnotation(annotationToRemove);
        }
      }
    }
  }

  void fillAliasBeanContent(Outline outline, Map<String, Set<String>> classesToKeep,
      BeanInclusionHelper.BeanInclusions beanInclusions,
      Map<String, ChangeSet> beansToChange)
      throws ClassNotFoundException, IOException {
    for (ChangeSet changeSet : beansToChange.values()) {
      ClassOutline sourceClassOutline = changeSet.sourceClassOutline;
      CClassInfo sourceClassInfo = sourceClassOutline.target;
      JDefinedClass sourceImplClass = sourceClassOutline.implClass;
      ClassOutline targetClassOutline = changeSet.targetClassOutline;
      JDefinedClass aliasBean = changeSet.definedClass;

      // copy class content

      // extend both JDefinedClass and ClassOutline of alias bean
      aliasBean._extends(sourceImplClass._extends());
      CClassInfo baseClass = sourceClassOutline.target.getBaseClass();
      targetClassOutline.target
          .setBaseClass(
              baseClass != null ? baseClass : sourceClassOutline.target.getRefBaseClass());
      // javadoc
      copyJavadocAndImplementsClause(sourceClassInfo, aliasBean);

      copyProperties(outline, beanInclusions, beansToChange, sourceClassInfo, sourceImplClass,
          changeSet,
          aliasBean, Collections.<String, XSComponent>emptyMap());

      Collection<JAnnotationUse> annotations = sourceImplClass.annotations();
      // XmlSeeAlso is handled by ourselves, hence ignore here:
      AnnotationHelper.applyAnnotations(outline, Annotatable.from(aliasBean), annotations,
          IGNORED_ANNOTATIONS);
    }
  }
}
