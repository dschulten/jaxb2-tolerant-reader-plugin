package de.escalon.xml.xjc.edit;

import com.sun.codemodel.JAnnotationUse;
import com.sun.tools.xjc.model.CClassInfo;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import de.escalon.hypermedia.hydra.mapping.Expose;
import de.escalon.hypermedia.hydra.mapping.Term;
import de.escalon.xml.xjc.ChangeSet;
import de.escalon.xml.xjc.annotate.Annotatable;
import de.escalon.xml.xjc.beaninclusion.BeanInclusion;
import de.escalon.xml.xjc.beaninclusion.BeanInclusions;
import de.escalon.xml.xjc.helpers.ClassHelper;
import java.util.Collection;
import java.util.Map;
import javax.xml.namespace.QName;

/**
 * Allows to expose schema information as JSON-LD URIs for hydra-jsonld.
 */
public class HydraEditor {

  private static final boolean HYDRA_PRESENT =
      ClassHelper.isPresent("de.escalon.hypermedia.hydra.mapping.Expose");

  public void applyExposeToAliasClasses(Outline outline, BeanInclusions beanInclusions,
      Map<String, ChangeSet> beansToChange) {
    Collection<ChangeSet> values = beansToChange.values();
    for (ChangeSet changeSet : values) {
      Annotatable target = Annotatable.from(changeSet.definedClass);
      applyPrefixTerm(target, beanInclusions, outline, changeSet.sourceClassOutline.target);
      applyExpose(null, target, beanInclusions, outline,
          changeSet.sourceClassOutline.target);
    }
  }

  void applyExpose(String property, Annotatable target,
      BeanInclusions beanInclusions,
      Outline outline,
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

  public void applyExposeToClasses(Outline outline, BeanInclusions beanInclusions,
      Collection<? extends ClassOutline> classOutlines,
      Map<String, ChangeSet> beansToChange) {
    for (final ClassOutline classOutline : classOutlines) {
      Annotatable annotatable = Annotatable.from(classOutline.implClass);
      applyPrefixTerm(annotatable, beanInclusions, outline, classOutline.target);
      applyExpose(null, annotatable, beanInclusions, outline, classOutline.target);
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
}
