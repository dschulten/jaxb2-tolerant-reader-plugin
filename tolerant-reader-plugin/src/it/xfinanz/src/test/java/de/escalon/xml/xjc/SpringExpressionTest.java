package de.escalon.xml.xjc;

import de.escalon.xfinanz.Postanschrift;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SpringExpressionTest {

    @Test
    public void testStrassekomplett() {
        Postanschrift postanschrift = new Postanschrift();
        postanschrift.setStrassekomplett("Bubenhalde 10");
        assertEquals("Bubenhalde", postanschrift.getStrasse());
        assertEquals("10", postanschrift.getHausnummer());
        assertNull(postanschrift.getHausnummernzusatz());
    }

    @Test
    public void testStrassekomplettHouseNumberAppendix() {
        Postanschrift postanschrift = new Postanschrift();
        postanschrift.setStrassekomplett("Bubenhalde 10b");
        assertEquals("Bubenhalde", postanschrift.getStrasse());
        assertEquals("10", postanschrift.getHausnummer());
        assertEquals("b", postanschrift.getHausnummernzusatz());
    }

    @Test
    public void testStrassekomplettStreetNameContainsNumbers() {
        Postanschrift postanschrift = new Postanschrift();
        postanschrift.setStrassekomplett("Straße des 17. Juni 44");
        assertEquals("Straße des 17. Juni", postanschrift.getStrasse());
        assertEquals("44", postanschrift.getHausnummer());
        assertNull(postanschrift.getHausnummernzusatz());
    }

    @Test
    public void testStrassekomplettMannheimerQuadrate() {
        Postanschrift postanschrift = new Postanschrift();
        postanschrift.setStrassekomplett("Q7 4a");
        assertEquals("Q7", postanschrift.getStrasse());
        assertEquals("4", postanschrift.getHausnummer());
        assertEquals("a", postanschrift.getHausnummernzusatz());
    }
}
