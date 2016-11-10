package de.escalon.xml.xjc;

import java.lang.annotation.Annotation;
import java.util.Collection;

import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JMethod;

class AnnotatableJMethod extends Annotatable {

    JMethod target;

    public AnnotatableJMethod(JMethod target) {
        super();
        this.target = target;
    }

    public JAnnotationUse annotate(JClass annotationJClass) {
        return target.annotate(annotationJClass);
    }

    public JAnnotationUse annotate(Class<? extends Annotation> annotation) {
        return target.annotate(annotation);
    }
    
    public JAnnotationUse getAnnotation(JAnnotationUse ann) {
        Collection<JAnnotationUse> annotations = target.annotations();
        for (JAnnotationUse jAnnotationUse : annotations) {
            if (jAnnotationUse.getAnnotationClass().fullName().equals(ann.getAnnotationClass().fullName())) {
                return jAnnotationUse;
            }
        }
        return null;
    }

}