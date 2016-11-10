package de.escalon.xml.xjc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.sun.codemodel.JAnnotationArrayMember;
import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JAnnotationValue;
import com.sun.codemodel.JFormatter;
import com.sun.tools.xjc.outline.Outline;

public class AnnotationHelper {

    public static void applyAnnotations(Outline outline, Annotatable annotationTarget,
            Collection<JAnnotationUse> annotations) throws IOException, ClassNotFoundException {
        AnnotationHelper.applyAnnotations(outline, annotationTarget, annotations, Collections
            .<String> emptySet());
    }

    public static void applyAnnotations(Outline outline, Annotatable annotationTarget,
            Collection<JAnnotationUse> annotations, Set<String> excludedSet) throws IOException,
            ClassNotFoundException {
        for (JAnnotationUse jAnnotationUse : annotations) {
            if (excludedSet.contains(jAnnotationUse.getAnnotationClass()
                .fullName())) {
                continue;
            }
            Map<String, JAnnotationValue> sourceAnnotationMembers = jAnnotationUse
                .getAnnotationMembers();
            for (Entry<String, JAnnotationValue> entry : sourceAnnotationMembers.entrySet()) {
                String annotationParamName = entry.getKey();
                JAnnotationValue annotationParamValue = entry.getValue();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                OutputStreamWriter writer = new OutputStreamWriter(bos);
                JFormatter f = new JFormatter(writer);
                annotationParamValue.generate(f);
                writer.flush();
                String valueString = new String(bos.toByteArray());

                JAnnotationUse annotation = annotationTarget.annotate(jAnnotationUse.getAnnotationClass());

                if (isClassLiteral(valueString)) {
                    annotation.param(annotationParamName,
                            OutlineHelper.getJDefinedClassFromOutline(outline, StringHelper.chopFromLastDot(
                                    valueString)));
                } else if (isEnumLiteral(valueString)) {
                    annotation.param(annotationParamName,
                            getEnumValue(valueString));
                } else if (isArray(valueString)) {
                    String[] arrayValues = StringHelper.removeStartAndEnd(valueString)
                        .trim()
                        .split(",", 0);
                    for (String annotationArrayValue : arrayValues) {
                        annotationArrayValue = annotationArrayValue.trim();
                        JAnnotationArrayMember paramArray = annotation.paramArray(annotationParamName);
                        if (isNonEmptyString(annotationArrayValue)) {
                            paramArray.param(StringHelper.removeStartAndEnd(annotationArrayValue));
                        } else if (isClassLiteral(annotationArrayValue)) {
                            paramArray.param(OutlineHelper.getJDefinedClassFromOutline(outline,
                                    StringHelper.chopFromLastDot(annotationArrayValue)));
                        } else if (isEnumLiteral(annotationArrayValue)) {
                            paramArray.param(getEnumValue(annotationArrayValue));
                        } else if (isBoolean(annotationArrayValue)) {
                            paramArray.param(createBooleanLiteral(annotationArrayValue));
                        } else if (isDouble(annotationArrayValue)) {
                            paramArray.param(createDoubleLiteral(annotationArrayValue));
                        } else if (isFloat(annotationArrayValue)) {
                            paramArray.param(createFloatLiteral(annotationArrayValue));
                        } else if (isLong(annotationArrayValue)) {
                            paramArray.param(createLongLiteral(annotationArrayValue));
                        } else if (!annotationArrayValue.isEmpty()) {
                            paramArray.param(createIntegerLiteral(annotationArrayValue));
                        }
                    }
                } else if (isNonEmptyString(valueString)) {
                    annotation.param(annotationParamName, StringHelper.removeStartAndEnd(valueString));
                } else if (isDouble(valueString)) {
                    annotation.param(annotationParamName, createDoubleLiteral(valueString));
                } else if (isFloat(valueString)) {
                    annotation.param(annotationParamName, createFloatLiteral(valueString));
                } else if (isLong(valueString)) {
                    annotation.param(annotationParamName, createLongLiteral(valueString));
                } else if (isBoolean(valueString)) {
                    annotation.param(annotationParamName, createBooleanLiteral(valueString));
                } else if (!valueString.isEmpty()) {
                    annotation.param(annotationParamName, createIntegerLiteral(valueString));
                }
            }
        }
    }

    private static int createIntegerLiteral(String valueString) {
        return Integer.parseInt(valueString);
    }

    private static double createDoubleLiteral(String valueString) {
        return Double.parseDouble(valueString);
    }

    private static double createFloatLiteral(String valueString) {
        return Float.parseFloat(valueString);
    }

    private static double createLongLiteral(String valueString) {
        return Long.parseLong(valueString);
    }

    private static boolean isDouble(String valueString) {
        return valueString.endsWith("D") || valueString.endsWith("d");
    }

    private static boolean isFloat(String valueString) {
        return valueString.endsWith("F") || valueString.endsWith("f");
    }

    private static boolean isLong(String valueString) {
        return valueString.endsWith("L") || valueString.endsWith("l");
    }

    private static boolean createBooleanLiteral(String valueString) {
        return Boolean.parseBoolean(valueString);
    }

    private static boolean isBoolean(String valueString) {
        return "true".equals(valueString) ||
                "false".equals(valueString);
    }

    private static Class<?> createClassLiteral(String valueString) throws ClassNotFoundException {
        return Class.forName(valueString);
    }

    private static <E extends Enum<E>> Enum<E> getEnumValue(String valueString) throws ClassNotFoundException {
        int lastDotPos = valueString.lastIndexOf('.');
        String enumType = valueString.substring(0, lastDotPos);
        String enumConstant = valueString.substring(lastDotPos + 1);
        @SuppressWarnings({ "rawtypes", "unchecked" })
        Enum<E> enumValue = Enum.valueOf((Class<Enum>) createClassLiteral(enumType), enumConstant);
        return enumValue;
    }

    private static boolean isEnumLiteral(String valueString) {
        int lastDotPos = valueString.lastIndexOf('.');
        return lastDotPos > 0
                && valueString.substring(lastDotPos + 1)
                    .matches("[A-Z_$][A-Z0-9_$].*");
    }

    private static boolean isArray(String valueString) {
        return valueString.startsWith("{") && valueString.endsWith("}");
    }

    private static boolean isClassLiteral(String valueString) {
        return valueString.endsWith(".class");
    }

    private static boolean isNonEmptyString(String annotationArrayValue) {
        return annotationArrayValue.startsWith("\"") && annotationArrayValue.endsWith("\"")
                && annotationArrayValue.length() > 2;
    }
}
