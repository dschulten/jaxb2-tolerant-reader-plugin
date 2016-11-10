package de.escalon.xml.xjc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;

import org.junit.Test;

import com.captech.person.BaseAddress;
import com.captech.person.GlobalAddress;
import com.captech.person.Name;
import com.captech.person.ObjectFactory;
import com.captech.person.Person;

import de.allianz.namespace.absvertragsachprivatservice.v5.CTGetVertragRequest;
import de.allianz.namespace.absvertragsachprivatservice.v5.CTGetVertragResponse;
import de.allianz.namespace.basis.ProductComponent;
import de.allianz.namespace.person.NaturalPerson;
import de.allianz.namespace.vertragsachprivat.PrivatePropertyContract;
import de.allianz.namespace.vertragsachprivat.PrivatePropertyInsuranceContract;
import de.allianz.namespace.vertragsachprivat.PrivatePropertyInsuredPerson;
import de.allianz.namespace.vertragsachprivat.PrivatePropertyProductCharacteristic;

public class MarshalUnmarshalTest {

    @Test
    public void testMarshallingPerson() throws JAXBException {
        JAXBContext context = JAXBContext.newInstance("com.captech.person");

        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

        ObjectFactory objectFactory = new ObjectFactory();

        Person person = objectFactory.createPerson();

        Name name = new Name();
        name.setFirstName("John");
        name.setLastName("Doe");
        person.setName(name);

        person.setAge(18);

        GlobalAddress globalAddress = new GlobalAddress();
        globalAddress.setCity("Schwetzingen");
        globalAddress.setAddr1("Carl Benz Str. 12");
        globalAddress.setCountry("Germany");
        globalAddress.setPostalCode("12121");
        person.setShippingAddress(globalAddress);

        JAXBElement<Person> jaxbElement = new JAXBElement<Person>(new QName("uri", "local"), Person.class, person);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        marshaller.marshal(jaxbElement, out);

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

        Unmarshaller unmarshaller = context.createUnmarshaller();
        JAXBElement<Person> personJAXBElement = unmarshaller
                .unmarshal(new StreamSource(in), Person.class);
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
        assertEquals("12121", ((GlobalAddress) shippingAddress).getPostalCode());
        assertEquals("Germany", ((GlobalAddress) shippingAddress).getCountry());

    }



}