package de.escalon.xml.xjc;

import com.sun.codemodel.JDefinedClass;
import com.sun.tools.xjc.outline.Outline;

public class OutlineHelper {

    public static JDefinedClass getJDefinedClassFromOutline(Outline outline, String fqcn) {
        JDefinedClass clazz = outline.getCodeModel()
            ._getClass(fqcn);
        return clazz;
    }
    
}
