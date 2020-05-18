package de.escalon.xml.xjc;

import org.junit.Test;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SpringExpressionTest {

    @Test
    public void testAssignmentMannheimerQuadrate() {
        Postanschrift postanschrift = new Postanschrift();
        ExpressionParser parser = new SpelExpressionParser();
        EvaluationContext context = new StandardEvaluationContext(postanschrift);
        String streetHouseNumber = "Q7 1a";

        Pattern pattern = Pattern.compile("^([\\S\\s]+?)\\s+([\\d-\\s]*?)\\s*([a-zA-Z])?$");
        Matcher matcher = pattern.matcher(streetHouseNumber);
        matcher.find();

        context.setVariable("matcher", matcher);
        context.setVariable("value", streetHouseNumber);
        parser.parseExpression("strasse = #matcher.group(1) ").getValue(context);
        parser.parseExpression("hausnummer = #matcher.group(2) ").getValue(context);
        parser.parseExpression("hausnummernzusatz = #matcher.group(3) ").getValue(context);
        assertEquals("Q7", postanschrift.getStrasse());
        assertEquals("1", postanschrift.getHausnummer());
        assertEquals("a", postanschrift.getHausnummernzusatz());
    }

    @Test
    public void testAssignmentStreetnameWithNumbers() {
        Postanschrift postanschrift = new Postanschrift();
        ExpressionParser parser = new SpelExpressionParser();
        EvaluationContext context = new StandardEvaluationContext(postanschrift);
        String streetHouseNumber = "Straße des 17. Juni 14";

        Pattern pattern = Pattern.compile("^([\\S\\s]+?)\\s+([\\d-\\s]*?)\\s*([a-zA-Z])?$");
        Matcher matcher = pattern.matcher(streetHouseNumber);
        matcher.find();

        context.setVariable("matcher", matcher);
        context.setVariable("value", streetHouseNumber);
        parser.parseExpression("strasse = #matcher.group(1) ").getValue(context);
        parser.parseExpression("hausnummer = #matcher.group(2) ").getValue(context);
        parser.parseExpression("hausnummernzusatz = #matcher.group(3) ").getValue(context);
        assertEquals("Straße des 17. Juni", postanschrift.getStrasse());
        assertEquals("14", postanschrift.getHausnummer());
        assertNull("No hausnummernzusatz allowed here", postanschrift.getHausnummernzusatz());
    }

    @Test
    public void testAssignmentPlainStreetname() {
        Postanschrift postanschrift = new Postanschrift();
        ExpressionParser parser = new SpelExpressionParser();
        EvaluationContext context = new StandardEvaluationContext(postanschrift);
        String streetHouseNumber = "Bubenhalde 10";

        Pattern pattern = Pattern.compile("^([\\S\\s]+?)\\s+([\\d-\\s]*?)\\s*([a-zA-Z])?$");
        Matcher matcher = pattern.matcher(streetHouseNumber);
        matcher.find();

        context.setVariable("matcher", matcher);
        context.setVariable("value", streetHouseNumber);
        parser.parseExpression("strasse = #matcher.group(1) ").getValue(context);
        parser.parseExpression("hausnummer = #matcher.group(2) ").getValue(context);
        parser.parseExpression("hausnummernzusatz = #matcher.group(3) ").getValue(context);
        assertEquals("Bubenhalde", postanschrift.getStrasse());
        assertEquals("10", postanschrift.getHausnummer());
        assertNull("No hausnummernzusatz allowed here", postanschrift.getHausnummernzusatz());
    }

    class Postanschrift {
        protected String hausnummer;
        protected String hausnummernzusatz;
        protected String strasse;

        public String getHausnummer() {
            return hausnummer;
        }

        public void setHausnummer(String hausnummer) {
            this.hausnummer = hausnummer;
        }

        public String getHausnummernzusatz() {
            return hausnummernzusatz;
        }

        public void setHausnummernzusatz(String hausnummernzusatz) {
            this.hausnummernzusatz = hausnummernzusatz;
        }

        public String getStrasse() {
            return strasse;
        }

        public void setStrasse(String strasse) {
            this.strasse = strasse;
        }
    }

}
