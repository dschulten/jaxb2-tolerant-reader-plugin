package de.escalon.xml.xjc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.namespace.QName;

import com.sun.codemodel.*;
import com.sun.tools.xjc.model.*;
import com.sun.tools.xjc.reader.Ring;
import de.escalon.xml.xjc.BeanInclusionHelper.SetterSpec;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
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

import de.escalon.hypermedia.hydra.mapping.Expose;
import de.escalon.hypermedia.hydra.mapping.Term;
import de.escalon.xml.xjc.BeanInclusionHelper.AdapterSpec;
import de.escalon.xml.xjc.BeanInclusionHelper.BeanInclusion;
import de.escalon.xml.xjc.BeanInclusionHelper.BeanInclusions;
import de.escalon.xml.xjc.BeanInclusionHelper.ExpressionSpec;

// TODO alias properties on parent classes loses withXXX method
// TODO serialVersionUID not copied in alias beans
// TODO person.function should be a String and the adapted type should only be written in xml
// TODO putting the adaption into the class requires both an xml transient property and the proper property
// TODO wrong list of aliases in error message about expected bean
// TODO use getSettersAndGetters when looking for accessors, from ClassHelper
// TODO allow multiline properties attribute list
// TODO improve error message when alias element is empty (string index -1)
// TODO duplicate tr:bean names should throw
// TODO do we include required properties from beans further up in the inheritance?
// TODO no serialVersionUID in alias class Address
// TODO even if a base bean does not have an element, a derived restricted 
// child bean might: Fullname.middleInitial - automatically include restricted properties 
// on base so we can copy them, then zap them after copy or - well - keep them
// TODO Expose for restricted classes: should it expose the restriction base instead of the restricted type?
// TODO expose implicitly included classes, too?
// TODO automatically keep required fields or attributes
// TODO automatically adjust getter and setter names for alias beans according to alias
// beans
// TODO let beanInclusions not maintain a map of simple name to list of beaninclusions matching 
// that simple name, but something more specific, maybe map of fullName to BeanInclusion? 
// TODO allow to expose properties under a different vocab
// TODO package-info prefix annotation, currently we expose with full url and use prefix
// TODO decouple structurally: support EL for property paths to aliases, or xpath or use
// https://blog.frankel.ch/customize-your-jaxb-bindings/ converter method. Reading a property as-is
// is simple, in order to 
// TODO create XSD Schema for tr extensions
// TODO use renamed namespace without version part for Expose (xjc renames packages, this
// TODO would require introducing a namespace-rename feature)
// TODO add getter Javadoc which allows to tell where property comes from, have XCD XPointer
// syntax there

/**
 * Reduces generated classes to only those classes and properties which a client really needs. For
 * decoupling structurally, use an {@link XmlAdapter} with {@link XmlJavaTypeAdapter} annotation,
 * annotated with jaxb2-annotate-plugin - your XmlAdapter can take an incoming class and convert it
 * to the structure you need.
 */
public class TolerantReaderPlugin extends Plugin {

    private static final Set<String> IGNORED_ANNOTATIONS = new HashSet<String>(
            Arrays.asList(XmlSeeAlso.class.getName(), XmlAccessorType.class.getName()));

    static final String NAMESPACE_URI = "http://jaxb2-commons.dev.java.net/tolerant-reader";

    /**
     * Name of Option to enable this plugin
     */
    private static final String OPTION_NAME = "Xtolerant-reader";

    private static final boolean HYDRA_PRESENT = ClassHelper.isPresent("de.escalon.hypermedia.hydra.mapping.Expose");

    private BeanInclusionHelper beanInclusionHelper;

    /**
     * Creates a new <code>TolerantReaderPlugin</code> instance.
     */
    public TolerantReaderPlugin() {
        beanInclusionHelper = new BeanInclusionHelper();
    }

    /**
     * TolerantReaderPlugin uses "-Xtolerant-reader" as the command-line argument
     */
    public String getOptionName() {
        return OPTION_NAME;
    }

    /**
     * Return usage information for plugin
     */
    public String getUsage() {
        return "  -" + OPTION_NAME + "    : restricts xjc compilation to classes and properties named in bindings file";
    }

    @Override
    public List<String> getCustomizationURIs() {
        return Arrays.asList(NAMESPACE_URI);
    }

    @Override
    public boolean isCustomizationTagName(String nsUri, String localName) {
        return NAMESPACE_URI.equals(nsUri) && ("include".equals(localName) || "alias".equals(localName)
                || "add".equals(localName)
                || "bean".equals(localName) || "adapter".equals(localName) || "compute".equals(localName)
                || "set".equals(localName) || "assign".equals(localName) || "regex".equals(localName)
        );
    }

    @Override
    public boolean run(Outline outline, Options opts, ErrorHandler errHandler) throws SAXException {
        processSchemaTags(outline, opts);
        return true;
    }

    private void processSchemaTags(Outline outline, Options opts) {
        CCustomizations customizations = outline.getModel()
                .getCustomizations();

        BeanInclusions beanInclusions = beanInclusionHelper.getBeanInclusions(customizations);

        Collection<? extends ClassOutline> classOutlines = outline.getClasses();

        // check that all included beans are present in classOutlines
        for (List<BeanInclusion> inclusionCandidates : beanInclusions) {
            for (BeanInclusion beanInclusion : inclusionCandidates) {
                ClassOutline found = findMatchingInclusionEntry(classOutlines, beanInclusion);
                if (found == null) {
                    throw new IllegalArgumentException("Tolerant reader expects bean " + inclusionCandidates.toString()
                            + ", but schema has no such bean");
                }
                List<CPropertyInfo> ownAndInheritedProperties = new ArrayList<CPropertyInfo>(
                        found.target.getProperties());
                CClassInfo currentClass = found.target;
                while (null != (currentClass = currentClass.getBaseClass())) {
                    if (currentClass != null) {
                        ownAndInheritedProperties.addAll(currentClass.getProperties());
                    }
                }
                List<String> propertyNames = new ArrayList<String>();
                for (CPropertyInfo cPropertyInfo : ownAndInheritedProperties) {
                    propertyNames.add(cPropertyInfo.getName(false));
                }
                if (!beanInclusion.isSatisfiedByProperties(ownAndInheritedProperties)) {
                    throw new IllegalArgumentException("Tolerant reader expects " + beanInclusion.toString()
                            + " but only found properties " + propertyNames + " in schema");
                }

            }
        }

        performEditing(outline, opts, beanInclusions);
    }

    class ChangeSet {
        final ClassOutline sourceClassOutline;
        final ClassOutline targetClassOutline;
        final JDefinedClass definedClass;

        public ChangeSet(ClassOutline sourceClassOutline, ClassOutline targetClassOutline, JDefinedClass definedClass) {
            super();
            this.sourceClassOutline = sourceClassOutline;
            this.targetClassOutline = targetClassOutline;
            this.definedClass = definedClass;
        }

        public String getAliasBeanName() {
            return definedClass.fullName();
        }
    }

    private void performEditing(Outline outline, Options opts, BeanInclusions beanInclusions) {
        // collect things to keep
        Collection<? extends ClassOutline> classOutlines = outline.getClasses();
        Map<String, Set<String>> classesToKeep = getClassesToKeep(beanInclusions, classOutlines);

        Ring ring = Ring.begin();
        Ring.add(outline.getModel());
        try {

            /** Map of FQCN of original class to change set */
            Map<String, ChangeSet> beansToChange = new HashMap<String, ChangeSet>();
            // edit properties of original classes, removes XmlType and XmlSeeAlso
            removeUnusedAndRenameProperties(outline, beanInclusions, classOutlines, classesToKeep);

            // create new beans, restricted and aliases
            createRestrictedBeans(outline, beanInclusions, classOutlines, classesToKeep, beansToChange);
            createAliasBeans(outline, beanInclusions, classOutlines, beansToChange);

            // apply alias bean names to fields and accessor methods of original classes
            applyBeanAliasesAndAdaptersToClassMembers(outline, beanInclusions, classOutlines, classesToKeep,
                    beansToChange);

            // apply computed expressions and synthetic setters to all included classes
            applyExpressions(outline, beanInclusions, classOutlines, beansToChange);
            applySyntheticSetters(outline, beanInclusions, classOutlines, beansToChange);

            // copy content of aliased beans to their alias bean counterparts
            fillAliasBeanContent(outline, classesToKeep, beanInclusions, beansToChange);

            applyXmlSeeAlso(outline, beanInclusions, classOutlines, classesToKeep, beansToChange);
            applyXmlTypeToClasses(classOutlines, beanInclusions, classesToKeep);
            applyXmlTypeToAliases(classOutlines, beanInclusions, classesToKeep, beansToChange);
            applyExposeToClasses(outline, beanInclusions, classOutlines, beansToChange);
            applyExposeToAliasClasses(outline, beanInclusions, beansToChange);

            addPropertiesToClasses(outline, beanInclusions);
            addPropertiesToAliases(outline, beanInclusions, beansToChange);

            removeBeansWhichHaveAliases(outline, beansToChange);
        } catch (Exception e) {
            throw new RuntimeException("failed to edit class", e);
        } finally {
            Ring.end(ring);
        }
    }

    private void addPropertiesToClasses(Outline outline, BeanInclusions beanInclusions) {
        Collection<? extends ClassOutline> classOutlines = outline.getClasses();

        for (final ClassOutline classOutline : classOutlines) {
            BeanInclusion beanInclusion = beanInclusions.getBeanInclusion(classOutline.target);
            addProperties(outline, beanInclusion, classOutline);
        }
    }

    private void addPropertiesToAliases(Outline outline, BeanInclusions beanInclusions,
            Map<String, ChangeSet> beansToChange) {
        for (ChangeSet changeSet : beansToChange.values()) {
            ClassOutline targetClassOutline = changeSet.targetClassOutline;
            ClassOutline sourceClassOutline = changeSet.sourceClassOutline;
            BeanInclusion beanInclusion = beanInclusions.getBeanInclusion(sourceClassOutline.target);
            addProperties(outline, beanInclusion, targetClassOutline);
        }
    }

    private void addProperties(Outline outline, BeanInclusion beanInclusion, ClassOutline classOutline) {
        if (beanInclusion == null) {
            return;
        }

        Map<String, String> propertiesToAdd = beanInclusion.getPropertiesToAdd();
        JDefinedClass implClass = classOutline.implClass;

        for (Entry<String, String> propertyAndClass : propertiesToAdd.entrySet()) {
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

    private void applyXmlSeeAlso(Outline outline, BeanInclusions beanInclusions,
            Collection<? extends ClassOutline> classOutlines, Map<String, Set<String>> classesToKeep,
            Map<String, ChangeSet> beansToChange) {
        for (ClassOutline classOutline : classOutlines) {
            CClassInfo classInfo = classOutline.target;
            JDefinedClass implClass = classOutline.implClass;
            addXmlSeeAlso(outline, classesToKeep, beansToChange, classInfo, implClass);
        }

    }

    /**
     * Applies expressions for computed getter.
     * @param outline of class which is being built
     * @param beanInclusions from bindings file
     * @param classOutlines all classes in schema
     * @param changeSetsByClassname  fqcn of original class to change set
     */
    private void applyExpressions(Outline outline, BeanInclusions beanInclusions,
            Collection<? extends ClassOutline> classOutlines, Map<String, ChangeSet> changeSetsByClassname) {

        JCodeModel codeModel = outline.getCodeModel();

        for (ClassOutline classOutline : classOutlines) {
            BeanInclusion beanInclusion = beanInclusions.getBeanInclusion(classOutline.target);
            if (beanInclusion == null) {
                continue;
            }
            Set<Entry<String, ExpressionSpec>> expressionSpecEntries = beanInclusion.getExpressions()
                    .entrySet();
            ChangeSet changeSet = changeSetsByClassname.get(classOutline.implClass.fullName());

            JDefinedClass implClass;
            if (changeSet == null) {
                implClass = classOutline.implClass;
            } else {
                implClass = changeSet.definedClass;
            }
            for (Entry<String, ExpressionSpec> expressionSpecEntry : expressionSpecEntries) {
                JMethod computedMethod = implClass.method(JMod.PUBLIC,
                        OutlineHelper.getJClassFromOutline(outline, expressionSpecEntry.getValue().computesToType),
                        "get" + StringHelper.capitalize(expressionSpecEntry.getKey()));
                JBlock body = computedMethod.body();
                if (ClassHelper.isPresent("org.springframework.expression.ExpressionParser")) {
                    // required Type references
                    JType parserIface = codeModel._ref(org.springframework.expression.ExpressionParser.class);
                    JType expressionIface = codeModel._ref(org.springframework.expression.Expression.class);
                    JType contextIface = codeModel._ref(org.springframework.expression.EvaluationContext.class);
                    JType parser = codeModel
                            ._ref(org.springframework.expression.spel.standard.SpelExpressionParser.class);
                    JType context = codeModel
                            ._ref(org.springframework.expression.spel.support.StandardEvaluationContext.class);

                    JVar parserVar = body.decl(parserIface, "parser", JExpr._new(parser));
                    JVar contextVar = body.decl(contextIface, "context", JExpr._new(context)
                            .arg(JExpr._this()));
                    JVar expVar = body.decl(expressionIface, "exp", JExpr.invoke(parserVar, "parseExpression")
                            .arg(JExpr.lit(expressionSpecEntry.getValue().expression)));

                    JVar ret = body.decl(codeModel._ref(java.lang.Object.class), "ret", JExpr.invoke(expVar, "getValue")
                            .arg(contextVar));
                    body._return(JExpr.cast(codeModel.ref(expressionSpecEntry.getValue().computesToType), ret));
                } else if (ClassHelper.isPresent("javax.el.ELProcessor")) {
                    JType elp = codeModel._ref(javax.el.ELProcessor.class);
                    JVar elpVar = body.decl(elp, "elp", JExpr._new(elp));
                    JInvocation invokeDefineBean = body.invoke(elpVar, "defineBean");
                    invokeDefineBean.arg("bean")
                            .arg(JExpr._this());

                    JVar ret = body.decl(codeModel.ref("java.lang.Object"), "ret", JExpr.invoke(elpVar, "eval")
                            .arg(JExpr.lit(expressionSpecEntry.getValue().expression)));

                    body._return(JExpr.cast(codeModel.ref(expressionSpecEntry.getValue().computesToType), ret));
                } else {
                    body._return(JExpr.direct(expressionSpecEntry.getValue().expression));
                }
                computedMethod.annotate(XmlTransient.class);
            }
        }
    }

    /**
     * Applies expressions for synthetic setter.
     * @param outline of class which is being built
     * @param beanInclusions from bindings file
     * @param classOutlines all classes in schema
     * @param changeSetsByClassname  fqcn of original class to change set
     */
    private void applySyntheticSetters(Outline outline, BeanInclusions beanInclusions,
            Collection<? extends ClassOutline> classOutlines, Map<String, ChangeSet> changeSetsByClassname) {

        JCodeModel codeModel = outline.getCodeModel();

        for (ClassOutline classOutline : classOutlines) {
            BeanInclusion beanInclusion = beanInclusions.getBeanInclusion(classOutline.target);
            if (beanInclusion == null) {
                continue;
            }
            Set<Entry<String, SetterSpec>> setterSpecEntries = beanInclusion.getSetters()
                    .entrySet();
            ChangeSet changeSet = changeSetsByClassname.get(classOutline.implClass.fullName());

            JDefinedClass implClass;
            if (changeSet == null) {
                implClass = classOutline.implClass;
            } else {
                implClass = changeSet.definedClass;
            }
            for (Entry<String, SetterSpec> setterSpecEntry : setterSpecEntries) {
                JMethod setterMethod = implClass.method(JMod.PUBLIC,
                        outline.getCodeModel().VOID,
                        "set" + StringHelper.capitalize(setterSpecEntry.getKey()));

                SetterSpec setterSpec = setterSpecEntry.getValue();
                setterMethod.param(OutlineHelper.getJClassFromOutline(outline, setterSpec.paramType), setterSpec.paramName);

                JBlock body = setterMethod.body();
                if (ClassHelper.isPresent("org.springframework.expression.ExpressionParser")) {
                    // required Type references
                    JType parserIface = codeModel._ref(org.springframework.expression.ExpressionParser.class);
                    JType contextIface = codeModel._ref(org.springframework.expression.EvaluationContext.class);
                    JType matcher = codeModel._ref(java.util.regex.Matcher.class);
                    JType pattern = codeModel._ref(java.util.regex.Pattern.class);
                    JClass stringList = codeModel.ref(List.class).narrow(String.class);

                    JType parser = codeModel
                            ._ref(org.springframework.expression.spel.standard.SpelExpressionParser.class);
                    JType context = codeModel
                            ._ref(org.springframework.expression.spel.support.StandardEvaluationContext.class);

                    JVar parserVar = body.decl(parserIface, "parser", JExpr._new(parser));
                    JVar contextVar = body.decl(contextIface, "context", JExpr._new(context)
                            .arg(JExpr._this()));

                    // assign #matcher if regex present, then set as spel var
                    if(setterSpec.regex != null) {
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
                    // TODO make input accessible as variable to spring context
                    JArray assignmentArray = JExpr.newArray(codeModel._ref(String.class));
                    List<String> assignments = setterSpecEntry.getValue().assignments;
                    for (String assignment : assignments) {
                        assignmentArray.add(JExpr.lit(assignment));
                    }
                    JInvocation asListInvocation = codeModel.ref(Arrays.class).staticInvoke("asList").arg(assignmentArray);
                    JVar assignmentExpressions = body.decl(stringList, "assignmentExpressions", asListInvocation);

                    JForEach forEach = body
                            .forEach(codeModel.ref(String.class), "assignmentExpression", assignmentExpressions);
                    JInvocation parseExpressionInvocation = forEach.body().invoke(parserVar, "parseExpression");
                    parseExpressionInvocation
                            .arg(forEach.var());
                    parseExpressionInvocation.invoke("getValue").arg(contextVar);
                } else if (ClassHelper.isPresent("javax.el.ELProcessor")) {
                    throw new IllegalArgumentException("tr:set requires Spring EL");
                } else {
                    throw new IllegalArgumentException("tr:set requires Spring EL");
                }
            }
        }
    }

    private void applyExposeToClasses(Outline outline, BeanInclusions beanInclusions,
            Collection<? extends ClassOutline> classOutlines, Map<String, ChangeSet> beansToChange) {
        for (final ClassOutline classOutline : classOutlines) {
            Annotatable annotatable = Annotatable.from(classOutline.implClass);
            applyPrefixTerm(annotatable, beanInclusions, outline, classOutline.target);
            applyExpose((String) null, annotatable, beanInclusions, outline, classOutline.target);
        }

    }

    private void applyPrefixTerm(Annotatable target, BeanInclusions beanInclusions, Outline outline,
            CClassInfo classInfo) {
        if (HYDRA_PRESENT) {
            BeanInclusion beanInclusion = beanInclusions.getBeanInclusion(classInfo);
            if (beanInclusion == null) {
                return;
            }
            String prefix = beanInclusion.getPrefix();
            if (!prefix.isEmpty()) {
                QName typeName = classInfo.getTypeName();
                if (typeName != null) { // anonymous type
                    JAnnotationUse annotateTerm = target.annotate(Term.class);
                    annotateTerm.param("define", prefix);
                    annotateTerm.param("as", typeName.getNamespaceURI() + "#");
                }
            }

        }

    }

    private void applyExposeToAliasClasses(Outline outline, BeanInclusions beanInclusions,
            Map<String, ChangeSet> beansToChange) {
        Collection<ChangeSet> values = beansToChange.values();
        for (ChangeSet changeSet : values) {
            Annotatable target = Annotatable.from(changeSet.definedClass);
            applyPrefixTerm(target, beanInclusions, outline, changeSet.sourceClassOutline.target);
            applyExpose((String) null, target, beanInclusions, outline, changeSet.sourceClassOutline.target);
        }
    }

    private void applyXmlTypeToAliases(Collection<? extends ClassOutline> classOutlines, BeanInclusions beanInclusions,
            Map<String, Set<String>> classesToKeep, Map<String, ChangeSet> beansToChange) {

        for (Entry<String, ChangeSet> beanToRenameEntry : beansToChange.entrySet()) {
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

    private void applyXmlTypeToClasses(Collection<? extends ClassOutline> classOutlines, BeanInclusions beanInclusions,
            Map<String, Set<String>> classesToKeep) throws IOException {
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

    private void removeBeansWhichHaveAliases(Outline outline, Map<String, ChangeSet> beansToChange) {
        for (Entry<String, ChangeSet> beanToChange : beansToChange.entrySet()) {
            removeClass(outline, beanToChange.getValue().sourceClassOutline.target);
        }
    }

    private void applyBeanAliasesAndAdaptersToClassMembers(Outline outline, BeanInclusions beanInclusions,
            Collection<? extends ClassOutline> classOutlines, Map<String, Set<String>> classesToKeep,
            Map<String, ChangeSet> beansToChange) throws ClassNotFoundException, IOException {

        for (ClassOutline classOutline : classOutlines) {
            CClassInfo classInfo = classOutline.target;
            JDefinedClass implClass = classOutline.implClass;
            applyAdaptersToFieldsAndAccessors(outline, beanInclusions, beansToChange, classInfo, implClass);
            applyBeanAliasesToFieldsAndAccessors(outline, beanInclusions, beansToChange, classInfo, implClass);

            JClass superClass = implClass._extends();
            ChangeSet changeSet = beansToChange.get(superClass.fullName());
            if (changeSet != null) {
                implClass._extends(changeSet.definedClass);
                classInfo.setBaseClass(changeSet.targetClassOutline.target);
            }
        }
    }

    /**
     * Change field types and accessors so that they use adapted types. E.g. if there is a
     * <code>foo</code> field of type A, and type A is adapted to type B, this will make the
     * <code>foo</code> field into type B.
     *
     * @param outline
     * @param beanInclusions
     * @param beansToChange
     * @param classInfo
     * @param implClass
     * @throws ClassNotFoundException
     * @throws IOException
     */
    private void applyAdaptersToFieldsAndAccessors(Outline outline, BeanInclusions beanInclusions,
            Map<String, ChangeSet> beansToChange, CClassInfo classInfo, JDefinedClass implClass)
            throws ClassNotFoundException, IOException {
        BeanInclusion beanInclusion = beanInclusions.getBeanInclusion(classInfo);
        if (beanInclusion == null) {
            return;
        }

        Collection<JMethod> methods = implClass.methods();
        Map<String, JFieldVar> fields = implClass.fields();
        JCodeModel codeModel = outline.getCodeModel();
        for (Entry<String, JFieldVar> entry : new HashMap<String, JFieldVar>(fields).entrySet()) {
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

            AdapterSpec xmlAdapterSpec = beanInclusion.getXmlAdapter(fieldName);
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

                AnnotationHelper.applyAnnotations(outline, Annotatable.from(adaptedField), field.annotations());
                adaptedField.annotate(XmlJavaTypeAdapter.class)
                        .param("value", adapterJClass);

                applyTypeToAccessors(outline, implClass, methods, field, fieldType, publicName, adaptedFieldType);

            }
        }

    }

    private JClass[] recursivelyConvertTypeArguments(JCodeModel codeModel, ParameterizedType parameterizedAdaptedType) {
        Type[] adaptedTypeArguments = parameterizedAdaptedType.getActualTypeArguments();
        JClass[] adaptedTypeArgumentsAsJClass = new JClass[adaptedTypeArguments.length];
        for (int i = 0; i < adaptedTypeArguments.length; i++) {
            Type adaptedTypeArgument = adaptedTypeArguments[i];
            if (adaptedTypeArgument instanceof Class) {
                adaptedTypeArgumentsAsJClass[i] = codeModel.ref((Class<?>) adaptedTypeArgument);
            } else if (adaptedTypeArgument instanceof ParameterizedType) {
                // e.g. List<Recipient<NaturalPerson>>
                ParameterizedType parameterizedAdaptedTypeArgument = (ParameterizedType) adaptedTypeArgument;
                JClass[] converted = recursivelyConvertTypeArguments(codeModel, parameterizedAdaptedTypeArgument);
                adaptedTypeArgumentsAsJClass[i] = codeModel
                        .ref((Class<?>) parameterizedAdaptedTypeArgument.getRawType())
                        .narrow(converted);
            }
        }
        return adaptedTypeArgumentsAsJClass;
    }

    private void applyBeanAliasesToFieldsAndAccessors(Outline outline, BeanInclusions beanInclusions,
            Map<String, ChangeSet> beansToChange, CClassInfo classInfo, JDefinedClass implClass)
            throws ClassNotFoundException, IOException {
        Collection<JMethod> methods = implClass.methods();
        Map<String, JFieldVar> fields = implClass.fields();

        for (Entry<String, JFieldVar> entry : new HashMap<String, JFieldVar>(fields).entrySet()) { // concurrent
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

                AnnotationHelper.applyAnnotations(outline, Annotatable.from(aliasTypeField), field.annotations());

                applyTypeToAccessors(outline, implClass, methods, field, fieldType, publicName, aliasFieldType);
            }
        }

    }

    private void applyTypeToAccessors(Outline outline, JDefinedClass implClass, Collection<JMethod> methods,
                                      JFieldVar field, JType fieldType, String publicName, JType adjustedFieldType) throws IOException,
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

    private void addXmlSeeAlso(Outline outline, Map<String, Set<String>> classesToKeep,
            Map<String, ChangeSet> beansToChange, CClassInfo classInfo, JDefinedClass implClass) {
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

    private void fillAliasBeanContent(Outline outline, Map<String, Set<String>> classesToKeep,
            BeanInclusions beanInclusions, Map<String, ChangeSet> beansToChange)
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
                    .setBaseClass(baseClass != null ? baseClass : sourceClassOutline.target.getRefBaseClass());
            // javadoc
            copyJavadocAndImplementsClause(sourceClassInfo, aliasBean);

            copyProperties(outline, beanInclusions, beansToChange, sourceClassInfo, sourceImplClass, changeSet,
                    aliasBean, Collections.<String, XSComponent>emptyMap());

            Collection<JAnnotationUse> annotations = sourceImplClass.annotations();
            // XmlSeeAlso is handled by ourselves, hence ignore here:
            AnnotationHelper.applyAnnotations(outline, Annotatable.from(aliasBean), annotations, IGNORED_ANNOTATIONS);

        }
    }

    private void copyJavadocAndImplementsClause(CClassInfo sourceClassInfo, JDefinedClass aliasBean) {
        aliasBean.javadoc()
                .add(sourceClassInfo.javadoc);
        Iterator<JClass> impls = aliasBean._implements();
        while (impls.hasNext()) {
            JClass iface = impls.next();
            aliasBean._implements(iface);
        }
    }

    private void createAliasBeans(Outline outline, BeanInclusions beanInclusions,
            Collection<? extends ClassOutline> classOutlines, Map<String, ChangeSet> beansToChange)
            throws JClassAlreadyExistsException {
        for (ClassOutline classOutline : new ArrayList<ClassOutline>(classOutlines)) { // no
            // concurrent
            // mod
            CClassInfo classInfo = classOutline.target;
            JDefinedClass implClass = classOutline.implClass;

            String aliasBeanName = getBeanAliasName(classInfo, beanInclusions);
            if (!aliasBeanName.isEmpty()) {
                JPackage parent = implClass.getPackage();
                ChangeSet changeSet = replaceClass(outline, parent, aliasBeanName, classOutline);
                beansToChange.put(classOutline.target.fullName(), changeSet); // keep for later
            }
        }
    }

    private void createRestrictedBeans(Outline outline, BeanInclusions beanInclusions,
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

            BeanInclusion beanInclusion = beanInclusions.getBeanInclusion(sourceClassInfo);
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
                                    if ((beanInclusion != null && beanInclusion.includesProperty(name)) || isRequiredElementOrAttribute(xsParticle)) {
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
                    ChangeSet changeSet = defineNewClassFrom(outline, parent, sourceImplClass.name(),
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

    private void copyProperties(Outline outline, BeanInclusions beanInclusions, Map<String, ChangeSet> beansToChange,
            CClassInfo sourceClassInfo, JDefinedClass sourceImplClass, ChangeSet changeSet, JDefinedClass aliasBean,
                                Map<String, XSComponent> expectedProperties) throws ClassNotFoundException, IOException {

        // TODO review parameter list:ChangeSet vs sourceClassInfo/sourceImplClass

        Collection<JMethod> methods = sourceImplClass.methods();
        Map<String, JFieldVar> fields = sourceImplClass.fields();

        Set<String> expectedPropertyNames = expectedProperties.keySet();
        for (String expectedPropertyName : expectedPropertyNames) {
            String privatePropertyName = StringHelper.uncapitalize(expectedPropertyName);
            CPropertyInfo property = sourceClassInfo.getProperty(privatePropertyName);
            if (property == null) {
                throw new IllegalStateException("The bean " + aliasBean.fullName()
                        + " has a schema restriction on the property " + privatePropertyName + " of its base type "
                        + sourceClassInfo.fullName() + ", but the generated base bean has no such property. Add "
                        + privatePropertyName + " to the properties list of the base <bean name=\""
                        + sourceClassInfo.shortName + "\"/> element in your bindings.xjb.");
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
            if (!expectedProperties.isEmpty() && !expectedProperties.containsKey(cPropertyInfo.getName(true))) {
                continue;
            }
            String sourcePropertyName = cPropertyInfo.getName(false);

            BeanInclusion beanInclusion = beanInclusions.getBeanInclusion(sourceClassInfo);
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
            AnnotationHelper.applyAnnotations(outline, Annotatable.from(aliasBeanField), field.annotations());

            String publicName = fieldName.substring(0, 1)
                    .toUpperCase() + fieldName.substring(1);
            Set<String> settersAndGetters = getSettersAndGetters(publicName);

            for (JMethod method : methods) {
                if (!settersAndGetters.contains(method.name())) {
                    continue;
                }
                JType typeOrAliasType = fieldType;
                List<JVar> params = method.params();
                if (params.isEmpty()) { // getter
                    method.type(typeOrAliasType);
                    aliasBean.methods()
                            .add(method);
                } else { // setter
                    addSetter(outline, aliasBean, aliasBeanField, method, typeOrAliasType);
                }
            }
        }
    }

    /**
     * Removes unused classes and renames properties.
     *
     * @param outline        of classes
     * @param beanInclusions describing editing tasks
     * @param classOutlines  to use
     * @param classesToKeep  which should not be removed
     */
    private void removeUnusedAndRenameProperties(Outline outline, BeanInclusions beanInclusions,
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
                BeanInclusion beanInclusion = beanInclusions.getBeanInclusion(classInfo);

                for (CPropertyInfo propertyInfo : new ArrayList<CPropertyInfo>(properties)) {
                    String propertyPrivateName = propertyInfo.getName(false); // fooBar
                    String propertyPublicName = propertyInfo.getName(true);
                    if (!(propertiesToKeep.contains(propertyPrivateName))) {
                        // remove unused field and accessor methods
                        properties.remove(propertyInfo);
                        JFieldVar fieldVar = fields.get(propertyPrivateName);
                        implClass.removeField(fieldVar);
                        Set<String> settersAndGetters = getSettersAndGetters(propertyPublicName);
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

                                    Set<String> settersAndGetters = getSettersAndGetters(propertyPublicName);

                                    for (JMethod method : methods) {
                                        String methodName = method.name();
                                        if (settersAndGetters.contains(methodName)) { // FooBar
                                            method.name(methodName.replace(propertyPublicName, propertyAliasPublic));
                                            if (methodName.startsWith("get") || methodName.startsWith("is")) {
                                                // expose getter before renaming the property
                                                applyExpose(propertyInfo.getName(false), Annotatable.from(method),
                                                        beanInclusions, outline, classInfo);
                                            }
                                        }
                                    }

                                    if (!(propertyPrivateName.equals(propertyAlias))) {
                                        propertyInfo.setName(true, StringHelper.capitalize(propertyAlias));
                                        propertyInfo.setName(false, propertyAlias);
                                    }
                                }
                            } else {
                                // no alias property: just expose
                                applyExpose(propertyInfo.getName(false),
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

    // TODO consider to replace by ClassHelper.findGetterInClass/findSetterInClass
    private Set<String> getSettersAndGetters(String propertyPublicName) {
        Set<String> settersAndGetters = new HashSet<String>(Arrays.asList("set" + propertyPublicName, // FooBar
                "get" + propertyPublicName, "is" + propertyPublicName, "has" + propertyPublicName));
        return settersAndGetters;
    }

    private void applyExpose(String property, Annotatable target, BeanInclusions beanInclusions, Outline outline,
            CClassInfo classInfo) {
        if (HYDRA_PRESENT) {
            BeanInclusion beanInclusion = beanInclusions.getBeanInclusion(classInfo);
            if (beanInclusion == null) {
                return;
            }
            QName typeName = classInfo.getTypeName();
            // type may be anonymous
            if (typeName != null) {
                JAnnotationUse annotateExpose = target.annotate(Expose.class);
                String prefix = beanInclusion.getPrefix();
                String typeUrl = prefix.isEmpty() ? typeName.getNamespaceURI() + "#" + classInfo.shortName
                        : prefix + ":" + classInfo.shortName;
                // must be an allowed XML ID *and* URI fragment
                String dotPropertyName = property == null ? "" : "." + property;

                annotateExpose.param("value", typeUrl + dotPropertyName);
            }
        }

    }

    private JClass getAliasFieldType(Outline outline, Map<String, ChangeSet> beansToChange, JType fieldType)
            throws ClassNotFoundException {
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
                    JDefinedClass aliasOfGenericTypeParameter = OutlineHelper.getJDefinedClassFromOutline(outline,
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

    private void addSetter(Outline outline, JDefinedClass bean, JFieldVar fieldVar, JMethod originalSetter,
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

    private String getBeanAliasName(CClassInfo classInfo, BeanInclusions beanInclusions) {
        BeanInclusion beanInclusionForClassInfo = beanInclusions.getBeanInclusion(classInfo);

        String aliasBeanName = "";
        if (beanInclusionForClassInfo != null) {
            aliasBeanName = beanInclusionForClassInfo.getBeanAlias();
        }
        return aliasBeanName;
    }

    private Map<String, Set<String>> getClassesToKeep(BeanInclusions beanInclusions,
            Collection<? extends ClassOutline> classOutlines) {
        Map<String, Set<String>> classesToKeep = new HashMap<String, Set<String>>();
        for (ClassOutline classOutline : classOutlines) {
            CClassInfo classInfo = classOutline.target;
            Set<String> includedPropertiesChecklist = new HashSet<String>();

            BeanInclusion beanInclusion = beanInclusions.getBeanInclusion(classInfo);
            if (beanInclusion == null) {
                continue;
            }
            // keep super classes recursively
            addClassesWithPropertiesToKeep(classesToKeep, classOutlines, classOutline, beanInclusions, beanInclusion,
                    includedPropertiesChecklist);
        }
        return classesToKeep;
    }

    private void addClassesWithPropertiesToKeep(Map<String, Set<String>> classesToKeep,
            Collection<? extends ClassOutline> classOutlines, ClassOutline classOutline, BeanInclusions beanInclusions,
            BeanInclusion beanInclusion, Set<String> includedPropertiesChecklist) {
        CClassInfo currentClassInfo = classOutline.target;

        // don't check BeanInclusion.includesClass here,
        // class hierarchy and property classes are automatically
        // included even if not included by any beanInclusion

        do { // keep class hierarchy
            String currentClassName = currentClassInfo.getName();
            if (!classesToKeep.containsKey(currentClassName)) {
                classesToKeep.put(currentClassName, new HashSet<String>());
            }

            List<CPropertyInfo> propertyInfos = currentClassInfo.getProperties();

            for (CPropertyInfo propertyInfo : propertyInfos) {
                String propertyInfoName = propertyInfo.getName(false); // fooBar

                XSComponent schemaComponent = propertyInfo.getSchemaComponent();
                boolean requiredElementOrAttribute = isRequiredElementOrAttribute(schemaComponent);

                if (beanInclusion.includesProperty(propertyInfoName) || requiredElementOrAttribute) {
                    Set<String> props = classesToKeep.get(currentClassName);
                    props.add(propertyInfoName);
                    includedPropertiesChecklist.add(propertyInfoName);

                    // include property types
                    String propertyTypeToKeep = findPropertyTypeToKeep(classOutline, propertyInfo);
                    if (propertyTypeToKeep != null) {
                        for (ClassOutline propertyClass : classOutlines) {
                            if (propertyTypeToKeep.equals(propertyClass.target.fullName())) {
                                // include property type class hierarchy
                                addClassesWithPropertiesToKeep(classesToKeep, classOutlines, propertyClass,
                                        beanInclusions, beanInclusion, includedPropertiesChecklist);
                                break;
                            }

                        }
                    }
                }
            }
        } while (null != (currentClassInfo = currentClassInfo.getBaseClass()));
    }

    private boolean isRequiredElementOrAttribute(XSComponent schemaComponent) {
        boolean requiredElementOrAttribute = false;
        if (schemaComponent instanceof XSParticle) {
            XSParticle particle = (XSParticle) schemaComponent;
            BigInteger minOccurs = particle.getMinOccurs();
            if (minOccurs == null || minOccurs.compareTo(BigInteger.ONE) > -1) {
                requiredElementOrAttribute = true;
            }
        } else if (schemaComponent instanceof XSAttributeUse) {
            XSAttributeUse attributeUse = (XSAttributeUse) schemaComponent;
            requiredElementOrAttribute = attributeUse.isRequired();
        }
        return requiredElementOrAttribute;
    }

    private String findPropertyTypeToKeep(ClassOutline classOutline, CPropertyInfo propertyInfo) {
        Collection<JMethod> methods = classOutline.implClass.methods();

        Set<String> settersAndGetters = getSettersAndGetters(propertyInfo.getName(true));

        for (JMethod jMethod : methods) {
            if (settersAndGetters.contains(jMethod.name())) {
                JType propertyType = jMethod.type();
                String propertyTypeToKeep = propertyType.fullName();
                if (propertyType instanceof JClass) {
                    JClass propertyJClass = (JClass) propertyType;
                    if (propertyJClass.isParameterized()) {
                        List<JClass> typeParams = propertyJClass.getTypeParameters();
                        for (JClass typeParam : typeParams) {
                            propertyTypeToKeep = typeParam.fullName();
                            break;
                        }
                    } else {
                        propertyTypeToKeep = propertyJClass.fullName();
                    }
                }
                return propertyTypeToKeep;
            }
        }
        return findPropertyTypeToKeep(classOutline.getSuperClass(), propertyInfo);
    }

    private ClassOutline findMatchingInclusionEntry(Collection<? extends ClassOutline> classOutlines,
            BeanInclusion inclusionEntry) {
        for (ClassOutline classOutline : classOutlines) {
            String className = classOutline.target.getName();
            if (inclusionEntry.includesClass(className)) {
                return classOutline;
            }
        }
        return null;
    }

    private void removeClass(Outline outline, CClassInfo classInfo) {
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
                        || hasXmlElementDeclScope(method, fullName)) {
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
                        JPackage jPackage = (JPackage) pkgs.next();
                        if (jPackage.name()
                                .equals(ownerPackage.name())) {
                            pkgs.remove();
                        }
                    }
                }
            }
        }

    }

    private ChangeSet replaceClass(Outline outline, JPackage targetPackage, String newClassName,
            ClassOutline toReplace) {

        ChangeSet changeSet = defineNewClassFrom(outline, targetPackage, newClassName, toReplace);

        // add to ObjectFactory
        addToObjectFactory(outline, changeSet.definedClass);
        return changeSet;
    }

    private ChangeSet defineNewClassFrom(Outline outline, JPackage targetPackage, String newClassName,
            ClassOutline toReplace) {
        CClassInfo oldClassInfo = toReplace.target;
        Locator locator = oldClassInfo.getLocator();
        QName typeName = oldClassInfo.getTypeName();
        QName elementName = oldClassInfo.getElementName();
        XSComponent schemaSource = oldClassInfo.getSchemaComponent();
        CCustomizations customizations = oldClassInfo.getCustomizations();

        CClassInfo newClassInfo = new CClassInfo(oldClassInfo.model, targetPackage.owner(), targetPackage.name()
                .isEmpty() ? newClassName : targetPackage.name() + "." + newClassName, locator, typeName, elementName,
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
        ChangeSet changeSet = new ChangeSet(toReplace, newClassOutline, newBean);
        return changeSet;
    }

    private void addToObjectFactory(Outline outline, JDefinedClass newBean) {
        String factoryName = newBean._package()
                .name() + ".ObjectFactory";
        JDefinedClass objFactory = OutlineHelper.getJDefinedClassFromOutline(outline, factoryName);
        JMethod factoryMethod = objFactory.method(JMod.PUBLIC, newBean, "create" + newBean.name());
        factoryMethod.body()
                ._return(JExpr._new(newBean));
    }

    private boolean hasXmlElementDeclScope(JMethod method, String removedClassName) {
        try {
            Collection<JAnnotationUse> annotations = method.annotations();
            for (JAnnotationUse ann : annotations) {
                if ("javax.xml.bind.annotation.XmlElementDecl".equals(ann.getAnnotationClass()
                        .fullName())) {
                    Map<String, JAnnotationValue> annotationMembers = ann.getAnnotationMembers();
                    JAnnotationValue scopeAnn = annotationMembers.get("scope");
                    if (scopeAnn != null) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        OutputStreamWriter writer = new OutputStreamWriter(out);
                        scopeAnn.generate(new JFormatter(writer));
                        writer.flush();
                        String annotationCode = new String(out.toByteArray(), "us-ascii");
                        if ((removedClassName + ".class").equals(annotationCode)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {
            throw new RuntimeException("failed to determine scope annotation value", e);
        }
    }

    @SuppressWarnings("unused")
    private void dump(CCustomizations cc) {
        for (int i = 0; i < cc.size(); i++) {
            Node n = cc.get(i).element;
            System.err.println("\t" + n.getNodeName() + " " + n.getNodeValue());
            NamedNodeMap attribs = n.getAttributes();
            if (attribs != null) {
                for (int j = 0; j < attribs.getLength(); j++) {
                    Node attrib = attribs.item(j);
                    System.err.println("\t\t" + attrib);
                }
            }
        }
    }

}