package de.escalon.xml.xjc;

import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JType;

public class ClassHelper {

    public static boolean isPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    public static JMethod findSetterInClass(JDefinedClass implClass, String publicName, JType fieldType) {
        return implClass.getMethod("set" + publicName, new JType[] { fieldType });
    }

    public static JMethod findGetterInClass(JDefinedClass implClass, String publicName) {
        JMethod getter;
        return (getter = implClass.getMethod("get" + publicName, new JType[0])) != null ? getter
                : implClass.getMethod("is" + publicName, new JType[0]);
    }

    
}
