package de.escalon.xml.xjc.beaninclusion;

import java.util.List;

/**
 * Represents tr:set element.
 */
public class SetterSpec {

    public final String paramType;
    public final String paramName;
    public final String regex;
    public final List<String> assignments;

    public SetterSpec(String paramType, String paramName, String regex, List<String> assignments) {
        this.paramType = paramType;
        this.paramName = paramName;
        this.regex = regex;
        this.assignments = assignments;
    }
}
