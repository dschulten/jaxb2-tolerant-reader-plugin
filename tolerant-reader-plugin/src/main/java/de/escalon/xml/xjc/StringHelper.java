package de.escalon.xml.xjc;

public class StringHelper {
    
    private StringHelper() {
        // prevent instantiation
    }
    
    public static String capitalize(String s) {
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }
    
    public static String uncapitalize(String s) {
        return s.substring(0,1).toLowerCase() + s.substring(1);
    }
    
    public static String chopFromLastDot(String s) {
        return s.substring(0, s.lastIndexOf('.'));
    }
    
    public static String removeStartAndEnd(String annotationArrayValue) {
        return annotationArrayValue.substring(1,
                annotationArrayValue.length() - 1);
    }
}
