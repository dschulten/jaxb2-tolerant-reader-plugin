package de.escalon.xml.xjc;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Iterator;

import javax.xml.namespace.NamespaceContext;

import org.junit.Test;

import com.sun.xml.xsom.SCD;

public class SchemaComponentDesignatorTest {

    @Test
    public void testSCD() throws ParseException {
        SCD.create("/type::vertrag-allg:CT_ZustellPerson//attribute::Adresse", new NamespaceContext() {
            
            @Override
            public Iterator getPrefixes(String namespaceURI) {
                return Arrays.asList("vertrag-allg").iterator();
            }
            
            @Override
            public String getPrefix(String namespaceURI) {
                return "vertrag-allg";
            }
            
            @Override
            public String getNamespaceURI(String prefix) {
                return "http://www.allianz.de/namespace/vertrag/V5";
            }
        });
    }
    
}
