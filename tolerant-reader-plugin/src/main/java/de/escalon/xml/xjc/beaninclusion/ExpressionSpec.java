package de.escalon.xml.xjc.beaninclusion;

/**
 * Represents tr:compute element.
 */
public class ExpressionSpec {

    public final String expression;
    public final String computesToType;
    public final String regex;
    public final String regexPropertyExpr;

    public ExpressionSpec(String expression, String computesToType, String regex,
        String regexPropertyExpr) {
        this.expression = expression;
        this.computesToType = computesToType;
        this.regex = regex;
        this.regexPropertyExpr = regexPropertyExpr;
    }

}
