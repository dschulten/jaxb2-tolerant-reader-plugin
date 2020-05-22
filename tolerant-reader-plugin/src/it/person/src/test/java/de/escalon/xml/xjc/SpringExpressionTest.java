package de.escalon.xml.xjc;

import static org.junit.Assert.assertEquals;

import com.example.person.ValueWrapper;
import org.junit.Test;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import com.example.person.Individuum;
import com.example.person.Name;

public class SpringExpressionTest {

    @Test
    public void testConcat() {
        Individuum individuum = new Individuum().withName(new Name().withFirstName("John")
            .withLastName("Doe"));
        ExpressionParser parser = new SpelExpressionParser();
        EvaluationContext context = new StandardEvaluationContext(individuum);
        Expression exp = parser.parseExpression("(name.FirstName + ' ' + name.LastName).trim()");
        Object ret = exp.getValue(context);
        assertEquals("John Doe", ret);
    }

    @Test
    public void testConcatNoName() {
        Individuum individuum = new Individuum();
        ExpressionParser parser = new SpelExpressionParser();
        EvaluationContext context = new StandardEvaluationContext(individuum);
        Expression exp = parser.parseExpression("((Name?.FirstName?:'') + ' ' + (Name?.LastName?:'')).trim()");
        Object ret = exp.getValue(context);
        assertEquals("", ret);
    }

    @Test
    public void testConcatOnlyLastName() {
        Individuum individuum = new Individuum().withName(new Name().withLastName("Doe"));
        ExpressionParser parser = new SpelExpressionParser();
        EvaluationContext context = new StandardEvaluationContext(individuum);
        Expression exp = parser.parseExpression("((Name?.FirstName?:'') + ' ' + (Name?.LastName?:'')).trim()");
        Object ret = exp.getValue(context);
        assertEquals("Doe", ret);
    }
    
    @Test
    public void testConcatOnlyFirstName() {
        Individuum individuum = new Individuum().withName(new Name().withFirstName("John"));
        ExpressionParser parser = new SpelExpressionParser();
        EvaluationContext context = new StandardEvaluationContext(individuum);
        Expression exp = parser.parseExpression("((Name?.FirstName?:'') + ' ' + (Name?.LastName?:'')).trim()");
        Object ret = exp.getValue(context);
        assertEquals("John", ret);
    }

    @Test
    public void testConcatFirstAndLastName() {
        Individuum individuum = new Individuum().withName(new Name().withLastName("Doe")
            .withFirstName("John"));
        ExpressionParser parser = new SpelExpressionParser();
        EvaluationContext context = new StandardEvaluationContext(individuum);
        Expression exp = parser.parseExpression("((Name?.FirstName?:'') + ' ' + (Name?.LastName?:'')).trim()");
        Object ret = exp.getValue(context);
        assertEquals("John Doe", ret);
    }

  @Test
  public void testRole() {
    Individuum individuum = new Individuum();
    individuum.setRoleWrapper(new ValueWrapper().withText("foo").withValue("001"));
    assertEquals("foo", individuum.getRoleText());
    assertEquals("001", individuum.getRoleValue());
  }

  @Test
  public void testEmployer() {
    Individuum individuum = new Individuum().withName(new Name().withFirstName("Joe"))
        .withEmployer(new Individuum().withName(new Name().withFirstName("Lisa")));
    individuum.setEmployerFirstName("Ben");
    assertEquals("Ben", individuum.getEmployerFirstName());
  }
}
