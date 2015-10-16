package de.escalon.xml.xjc;

import com.captech.person.BaseAddress;
import com.captech.person.GlobalAddress;
import com.captech.person.Person;
import org.junit.Test;

import javax.xml.bind.*;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;
import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MarshalUnmarshalTest {

    @Test
    public void testMarshalling() throws JAXBException {
        JAXBContext context = JAXBContext.newInstance("com.captech.person");
        Marshaller marshaller = context.createMarshaller();
        Unmarshaller unmarshaller = context.createUnmarshaller();
        JAXBElement<Person> personJAXBElement = unmarshaller.unmarshal(
                new StreamSource(this.getClass().getResourceAsStream("/person.xml")), Person.class);
        assertNotNull("Person not unmarshalled", personJAXBElement.getValue());
        assertNotNull("Name not unmarshalled", personJAXBElement.getValue().getName());
        assertEquals("John", personJAXBElement.getValue().getName().getFirstName());
        assertEquals("Doe", personJAXBElement.getValue().getName().getLastName());
        assertEquals(18, personJAXBElement.getValue().getAge().intValue());
        BaseAddress shippingAddress = personJAXBElement.getValue().getShippingAddress();
        assertNotNull("ShippingAddress not unmarshalled", shippingAddress);
        assertEquals("Carl Benz Str. 12", shippingAddress.getAddr1());
        assertEquals("Schwetzingen", shippingAddress.getCity());
        assertTrue(shippingAddress instanceof GlobalAddress);
        assertEquals("Schwetzingen", ((GlobalAddress) shippingAddress).getPostalCode());
        assertEquals("Schwetzingen", ((GlobalAddress) shippingAddress).getCountry());


        // TODO: schema might require values, should the bean include them so they can be marshalled back - or should that
        // be up to the implementer
        // TODO: fail for missing properties and beans
        //System.out.println(person);
//        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

//        ObjectFactory objectFactory = new ObjectFactory();
//
//        Person person = objectFactory.createPerson();
//
//        Name name = new Name();
//        name.setFirstName("John");
//        name.setLastName("Doe");
//        name.setMiddleInitial("M");
//        person.setName(name);
//
//        person.setAge(18);
//
//        Extra extra = new Extra();
//        extra.setUselessField1(42);
//        person.setExtra(extra);
//
//        USAddress usAddress = new USAddress();
//        usAddress.setState("CA");
//        usAddress.setZip("70123");
//        usAddress.setAddr1("4 Grant Street");
//        usAddress.setCity("Pebble Beach");
//        person.setHomeAddress(usAddress);
//
//        GlobalAddress globalAddress = new GlobalAddress();
//        globalAddress.setCity("Schwetzingen");
//        globalAddress.setAddr1("Bürgermeister Schmidt Str. 12");
//        globalAddress.setCountry("Germany");
//        globalAddress.setPostalCode("12121");
//        person.setShippingAddress(globalAddress);
//
//        JAXBElement<Person> jaxbElement =
//                new JAXBElement<Person>(new QName("uri", "local"), Person.class, person);
//        marshaller.marshal(jaxbElement, new File("person.xml"));
    }

}