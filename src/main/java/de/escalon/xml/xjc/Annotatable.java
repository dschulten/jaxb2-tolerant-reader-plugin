package de.escalon.xml.xjc;

import java.lang.annotation.Annotation;

import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;

abstract class Annotatable {
    
    public static Annotatable from(JMethod target) {
        return new AnnotatableJMethod(target);
    }
    
    public static Annotatable from(JFieldVar target) {
        return new AnnotatableJFieldVar(target);
    }
    
    public static Annotatable from(JDefinedClass target) {
        return new AnnotatableJDefinedClass(target);
    }
    
    abstract JAnnotationUse annotate(JClass annotationJClass);

    abstract JAnnotationUse annotate(Class<? extends Annotation> clazz);
    abstract JAnnotationUse getAnnotation(JAnnotationUse ann);
}