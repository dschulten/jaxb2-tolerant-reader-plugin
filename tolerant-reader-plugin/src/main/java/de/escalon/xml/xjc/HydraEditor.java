package de.escalon.xml.xjc;

import com.sun.codemodel.JAnnotationUse;
import com.sun.tools.xjc.model.CClassInfo;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import de.escalon.hypermedia.hydra.mapping.Expose;
import de.escalon.hypermedia.hydra.mapping.Term;
import java.util.Collection;
import java.util.Map;
import javax.xml.namespace.QName;

/**
 * Allows to expose schema information as JSON-LD URIs for hydra-jsonld.
 */
public class HydraEditor {

  private static final boolean HYDRA_PRESENT =
      ClassHelper.isPresent("de.escalon.hypermedia.hydra.mapping.Expose");

  void applyExposeToAliasClasses(Outline outline, BeanInclusionHelper.BeanInclusions beanInclusions,
      Map<String, SchemaProcessor.ChangeSet> beansToChange) {
    Collection<SchemaProcessor.ChangeSet> values = beansToChange.values();
    for (SchemaProcessor.ChangeSet changeSet : values) {
      Annotatable target = Annotatable.from(changeSet.definedClass);
      applyPrefixTerm(target, beanInclusions, outline, changeSet.sourceClassOutline.target);
      applyExpose(null, target, beanInclusions, outline,
          changeSet.sourceClassOutline.target);
    }
  }

  void applyExpose(String property, Annotatable target,
      BeanInclusionHelper.BeanInclusions beanInclusions,
      Outline outline,
      CClassInfo classInfo) {
    if (HYDRA_PRESENT) {
      BeanInclusionHelper.BeanInclusion beanInclusion = beanInclusions.getBeanInclusion(classInfo);
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

  void applyExposeToClasses(Outline outline, BeanInclusionHelper.BeanInclusions beanInclusions,
      Collection<? extends ClassOutline> classOutlines,
      Map<String, SchemaProcessor.ChangeSet> beansToChange) {
    for (final ClassOutline classOutline : classOutlines) {
      Annotatable annotatable = Annotatable.from(classOutline.implClass);
      applyPrefixTerm(annotatable, beanInclusions, outline, classOutline.target);
      applyExpose(null, annotatable, beanInclusions, outline, classOutline.target);
    }
  }

  private void applyPrefixTerm(Annotatable target, BeanInclusionHelper.BeanInclusions beanInclusions, Outline outline,
      CClassInfo classInfo) {
    if (HYDRA_PRESENT) {
      BeanInclusionHelper.BeanInclusion beanInclusion = beanInclusions.getBeanInclusion(classInfo);
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
}
