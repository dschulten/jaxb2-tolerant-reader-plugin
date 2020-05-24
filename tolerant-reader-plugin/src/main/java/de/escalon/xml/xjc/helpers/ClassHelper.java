package de.escalon.xml.xjc.helpers;

import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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

    // TODO consider to replace by ClassHelper.findGetterInClass/findSetterInClass
    public static Set<String> getSettersAndGetters(String propertyPublicName) {
        return new HashSet<String>(Arrays.asList("set" + propertyPublicName, // FooBar
            "get" + propertyPublicName, "is" + propertyPublicName, "has" + propertyPublicName));
    }
    
}
