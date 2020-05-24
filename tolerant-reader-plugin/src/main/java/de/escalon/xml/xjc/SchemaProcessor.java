package de.escalon.xml.xjc;

import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JType;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.model.CClassInfo;
import com.sun.tools.xjc.model.CPropertyInfo;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import com.sun.tools.xjc.reader.Ring;
import com.sun.xml.xsom.XSComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SchemaProcessor {

  private ClassInfoEditor classInfoEditor = new ClassInfoEditor();
  private CodeModelEditor codeModelEditor = new CodeModelEditor();
  private HydraHelper hydraHelper = new HydraHelper();

  void processSchemaTags(Outline outline, Options opts,
      BeanInclusionHelper.BeanInclusions beanInclusions) {
    Collection<? extends ClassOutline> classOutlines = outline.getClasses();

    // check that all included beans are present in classOutlines
    for (List<BeanInclusionHelper.BeanInclusion> inclusionCandidates : beanInclusions) {
      for (BeanInclusionHelper.BeanInclusion beanInclusion : inclusionCandidates) {
        ClassOutline found = findMatchingInclusionEntry(classOutlines, beanInclusion);
        if (found == null) {
          throw new IllegalArgumentException(
              "Tolerant reader expects bean " + inclusionCandidates.toString()
                  + ", but schema has no such bean");
        }
        List<CPropertyInfo> ownAndInheritedProperties = new ArrayList<CPropertyInfo>(
            found.target.getProperties());
        CClassInfo currentClass = found.target;
        while (null != (currentClass = currentClass.getBaseClass())) {
          ownAndInheritedProperties.addAll(currentClass.getProperties());
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

  private void performEditing(Outline outline, Options opts, BeanInclusionHelper.BeanInclusions beanInclusions) {
    // collect things to keep
    Collection<? extends ClassOutline> classOutlines = outline.getClasses();
    Map<String, Set<String>> classesToKeep = getClassesToKeep(beanInclusions, classOutlines);

    Ring ring = Ring.begin();
    Ring.add(outline.getModel());
    try {

      // Map of FQCN of original class to change set
      Map<String, ChangeSet> beansToChange = new HashMap<String, ChangeSet>();
      // edit properties of original classes, removes XmlType and XmlSeeAlso
      classInfoEditor.removeUnusedAndRenameProperties(outline, beanInclusions, classOutlines, classesToKeep);

      // create new beans, restricted and aliases
      classInfoEditor.createRestrictedBeans(outline, beanInclusions, classOutlines, classesToKeep, beansToChange);
      classInfoEditor.createAliasBeans(outline, beanInclusions, classOutlines, beansToChange);

      // apply alias bean names to fields and accessor methods of original classes
      classInfoEditor.applyBeanAliasesAndAdaptersToClassMembers(outline, beanInclusions, classOutlines,
          classesToKeep,
          beansToChange);

      // apply computed expressions and synthetic setters to all included classes
      codeModelEditor.applyExpressions(outline, beanInclusions, classOutlines, beansToChange);
      codeModelEditor.applySyntheticSetters(outline, beanInclusions, classOutlines, beansToChange);

      // copy content of aliased beans to their alias bean counterparts
      classInfoEditor.fillAliasBeanContent(outline, classesToKeep, beanInclusions, beansToChange);

      classInfoEditor.applyXmlSeeAlso(outline, beanInclusions, classOutlines, classesToKeep, beansToChange);
      classInfoEditor.applyXmlTypeToClasses(classOutlines, beanInclusions, classesToKeep);
      classInfoEditor.applyXmlTypeToAliases(classOutlines, beanInclusions, classesToKeep, beansToChange);
      hydraHelper.applyExposeToClasses(outline, beanInclusions, classOutlines, beansToChange);
      hydraHelper.applyExposeToAliasClasses(outline, beanInclusions, beansToChange);

      classInfoEditor.addPropertiesToClasses(outline, beanInclusions);
      classInfoEditor.addPropertiesToAliases(outline, beanInclusions, beansToChange);

      classInfoEditor.removeBeansWhichHaveAliases(outline, beansToChange);
    } catch (Exception e) {
      throw new RuntimeException("failed to edit class", e);
    } finally {
      Ring.end(ring);
    }
  }

  private Map<String, Set<String>> getClassesToKeep(
      BeanInclusionHelper.BeanInclusions beanInclusions,
      Collection<? extends ClassOutline> classOutlines) {
    Map<String, Set<String>> classesToKeep = new HashMap<String, Set<String>>();
    for (ClassOutline classOutline : classOutlines) {
      CClassInfo classInfo = classOutline.target;
      Set<String> includedPropertiesChecklist = new HashSet<String>();

      BeanInclusionHelper.BeanInclusion beanInclusion = beanInclusions.getBeanInclusion(classInfo);
      if (beanInclusion == null) {
        continue;
      }
      // keep super classes recursively
      addClassesWithPropertiesToKeep(classesToKeep, classOutlines, classOutline, beanInclusions,
          beanInclusion,
          includedPropertiesChecklist);
    }
    return classesToKeep;
  }

  private void addClassesWithPropertiesToKeep(Map<String, Set<String>> classesToKeep,
      Collection<? extends ClassOutline> classOutlines, ClassOutline classOutline,
      BeanInclusionHelper.BeanInclusions beanInclusions,
      BeanInclusionHelper.BeanInclusion beanInclusion, Set<String> includedPropertiesChecklist) {
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
        boolean requiredElementOrAttribute = SchemaInspector.isRequiredElementOrAttribute(schemaComponent);

        if (beanInclusion.includesProperty(propertyInfoName) || requiredElementOrAttribute) {
          Set<String> props = classesToKeep.get(currentClassName);
          props.add(propertyInfoName);
          includedPropertiesChecklist.add(propertyInfoName);

          // include property types
          String propertyTypeToKeep = findPropertyTypeToKeep(classOutline, propertyInfo);
          if (propertyTypeToKeep != null && !classesToKeep.containsKey(propertyTypeToKeep)) {
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

  private String findPropertyTypeToKeep(ClassOutline classOutline, CPropertyInfo propertyInfo) {
    Collection<JMethod> methods = classOutline.implClass.methods();

    Set<String> settersAndGetters = ClassHelper.getSettersAndGetters(propertyInfo.getName(true));

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
      BeanInclusionHelper.BeanInclusion inclusionEntry) {
    for (ClassOutline classOutline : classOutlines) {
      String className = classOutline.target.getName();
      if (inclusionEntry.includesClass(className)) {
        return classOutline;
      }
    }
    return null;
  }


  static class ChangeSet {
    final ClassOutline sourceClassOutline;
    final ClassOutline targetClassOutline;
    final JDefinedClass definedClass;

    public ChangeSet(ClassOutline sourceClassOutline, ClassOutline targetClassOutline,
        JDefinedClass definedClass) {
      super();
      this.sourceClassOutline = sourceClassOutline;
      this.targetClassOutline = targetClassOutline;
      this.definedClass = definedClass;
    }

    public String getAliasBeanName() {
      return definedClass.fullName();
    }
  }
}
