package de.escalon.xml.xjc;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import javax.xml.namespace.NamespaceContext;

import org.junit.Test;
import org.xml.sax.SAXException;

import com.sun.xml.xsom.SCD;
import com.sun.xml.xsom.XSComponent;
import com.sun.xml.xsom.XSSchemaSet;
import com.sun.xml.xsom.parser.XSOMParser;

public class SchemaComponentDesignatorTest {

    private NamespaceContext ns = new NamespaceContext() {

        @Override
        public Iterator getPrefixes(String namespaceURI) {
            if (namespaceURI.isEmpty()) {
                return Collections.emptyIterator();
            } else {
                return Arrays.asList("person")
                    .iterator();
            }
        }

        @Override
        public String getPrefix(String namespaceURI) {
            if (namespaceURI.isEmpty()) {
                return "";
            } else {
                return "person";
            }
        }

        @Override
        public String getNamespaceURI(String prefix) {
            if (prefix.isEmpty()) {
                return "";
            } else {
                return "http://example.com/person";
            }
        }
    };

    @Test
    public void testSCD() throws ParseException, IOException, SAXException {

        XSOMParser parser = new XSOMParser();

        String userDir = System.getProperty("user.dir");
        parser.parse(new File(userDir
                + "/src/it/person/src/main/wsdl/example/Person.xsd"));

        XSSchemaSet sset = parser.getResult();

        SCD scd = SCD.create("/type::person:Person/@friend", ns);

        XSComponent selectedComponent = scd.selectSingle(sset);
        assertEquals("friend attribute declaration", selectedComponent.toString());
    }

}
