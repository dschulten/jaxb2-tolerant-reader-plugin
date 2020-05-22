package de.escalon.xml.xjc;

import de.escalon.xfinanz.Postanschrift;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SpringExpressionTest {

    @Test
    public void testStrassekomplett() {
        Postanschrift individuum = new Postanschrift();
        individuum.setStrassekomplett("Bubenhalde 10");
        assertEquals("Bubenhalde", individuum.getStrasse());
        assertEquals("10", individuum.getHausnummer());
        assertNull(individuum.getHausnummernzusatz());
    }

    @Test
    public void testStrassekomplettHouseNumberAppendix() {
        Postanschrift individuum = new Postanschrift();
        individuum.setStrassekomplett("Bubenhalde 10b");
        assertEquals("Bubenhalde", individuum.getStrasse());
        assertEquals("10", individuum.getHausnummer());
        assertEquals("b", individuum.getHausnummernzusatz());
    }

    @Test
    public void testStrassekomplettStreetNameContainsNumbers() {
        Postanschrift individuum = new Postanschrift();
        individuum.setStrassekomplett("Straße des 17. Juni 44");
        assertEquals("Straße des 17. Juni", individuum.getStrasse());
        assertEquals("44", individuum.getHausnummer());
        assertNull(individuum.getHausnummernzusatz());
    }

    @Test
    public void testStrassekomplettMannheimerQuadrate() {
        Postanschrift individuum = new Postanschrift();
        individuum.setStrassekomplett("Q7 4a");
        assertEquals("Q7", individuum.getStrasse());
        assertEquals("4", individuum.getHausnummer());
        assertEquals("a", individuum.getHausnummernzusatz());
    }
}
