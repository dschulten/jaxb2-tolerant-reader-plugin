package de.escalon.xml.xjc;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;

import org.junit.Test;

import com.example.person.Address;
import com.example.person.BaseAddress;
import com.example.person.Name;
import com.example.person.ObjectFactory;
import com.example.person.Person;

public class MarshalUnmarshalTest {

    @Test
    public void testToString() {
        
        ObjectFactory objectFactory = new ObjectFactory();
        Person person = createPerson(objectFactory);
        // check toString both for renamed properties and renamed classes
        assertThat(person.toString(), allOf(
                containsString("firstName=John"), containsString("givenName=Doe"),
                containsString("age=18"),
                containsString("shippingAddress=com.example.person.Address"),
                containsString("postCode=12121")));
    }
    
    @Test
    public void testEquals() {
        
        ObjectFactory objectFactory = new ObjectFactory();
        Person person1 = createPerson(objectFactory);
        Person person2 = createPerson(objectFactory);

        assertTrue("both persons must be equal", person1.equals(person2));
        assertTrue("both persons must be equal", person2.equals(person1));
    }
    
    @Test
    public void testMarshallingPerson() throws JAXBException {
        JAXBContext context = JAXBContext.newInstance("com.example.person");

        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

        ObjectFactory objectFactory = new ObjectFactory();

        Person person = createPerson(objectFactory);

        JAXBElement<Person> jaxbElement = new JAXBElement<Person>(new QName("uri", "local"), Person.class, person);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        marshaller.marshal(jaxbElement, out);

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

        Unmarshaller unmarshaller = context.createUnmarshaller();
        JAXBElement<Person> personJAXBElement = unmarshaller
            .unmarshal(new StreamSource(in), Person.class);
        assertNotNull("Person not unmarshalled", personJAXBElement.getValue());
        assertNotNull("Name not unmarshalled", personJAXBElement.getValue()
            .getName());
        assertEquals("John", personJAXBElement.getValue()
            .getName()
            .getFirstName());
        assertEquals("Doe", personJAXBElement.getValue()
            .getName()
            .getGivenName());
        assertEquals(18, personJAXBElement.getValue()
            .getAge()
            .intValue());
        BaseAddress shippingAddress = personJAXBElement.getValue()
            .getShippingAddress();
        assertNotNull("ShippingAddress not unmarshalled", shippingAddress);
        assertEquals("Carl Benz Str. 12", shippingAddress.getAddr1());
        assertEquals("Schwetzingen", shippingAddress.getCity());
        assertTrue(shippingAddress instanceof Address);
        assertEquals("12121", ((Address) shippingAddress).getPostCode());
        assertEquals("Germany", ((Address) shippingAddress).getCountry());
    }

    private Person createPerson(ObjectFactory objectFactory) {
        Person person = objectFactory.createPerson();

        Name name = new Name();
        name.setFirstName("John");
        name.setGivenName("Doe");
        person.setName(name);

        person.setAge(18);

        Address globalAddress = new Address();
        globalAddress.setCity("Schwetzingen");
        globalAddress.setAddr1("Carl Benz Str. 12");
        globalAddress.setCountry("Germany");
        globalAddress.setPostCode("12121");

        person.setShippingAddress(globalAddress);
        return person;
    }

}