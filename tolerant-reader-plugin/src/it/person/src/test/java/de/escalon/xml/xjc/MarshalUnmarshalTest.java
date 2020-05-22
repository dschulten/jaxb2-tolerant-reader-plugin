package de.escalon.xml.xjc;

import com.example.person.AddrBase;
import com.example.person.Address;
import com.example.person.Individuum;
import com.example.person.Name;
import com.example.person.ObjectFactory;
import com.example.person.USAddress;
import com.example.person.ValueWrapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.junit.Test;
import org.xml.sax.SAXException;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.isA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class MarshalUnmarshalTest {

    @Test
    public void testToString() {

        ObjectFactory objectFactory = new ObjectFactory();
        Individuum person = createPerson(objectFactory);
        // check toString both for renamed properties and renamed classes
        assertThat(person.toString(), allOf(
            containsString("firstName=John"), containsString("lastName=Doe"),
            containsString("age=18"),
            containsString("invoiceAddress=com.example.person.Address"),
            containsString("postCode=12121")));
    }

    @Test
    public void testEquals() {

        ObjectFactory objectFactory = new ObjectFactory();
        Individuum person1 = createPerson(objectFactory);
        Individuum person2 = createPerson(objectFactory);

        assertTrue("both persons must be equal", person1.equals(person2));
        assertTrue("both persons must be equal", person2.equals(person1));
    }

    @Test
    public void aliasBeansCanHaveCustomProperties() {
        Individuum individuum = new Individuum();
        individuum.setMyProperty(12);
        assertEquals(new Integer(12), individuum.getMyProperty());
    }

    @Test
    public void originalBeansCanHaveCustomProperties() {
        USAddress usAddress = new USAddress();
        usAddress.setGoVote(true);
        assertTrue(usAddress.getGoVote());
    }

    @Test
    public void testMarshallingPerson() throws JAXBException, IOException, SAXException {
        JAXBContext context = JAXBContext.newInstance("com.example.person");

        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = schemaFactory.newSchema(new StreamSource(new File(System.getProperty("user.dir")
            + "/src/main/wsdl/example", "Person.xsd")));
        marshaller.setSchema(schema);

        ObjectFactory objectFactory = new ObjectFactory();

        Individuum person = createPerson(objectFactory);

        JAXBElement<Individuum> jaxbElement = new JAXBElement<Individuum>(new QName("http://example.com/person",
            "Person"),
            Individuum.class, person);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        marshaller.marshal(jaxbElement, out);

        System.out.println(System.getProperty("user.dir"));

        byte[] outBytes = out.toByteArray();
        System.out.write(outBytes);

        ByteArrayInputStream in = new ByteArrayInputStream(outBytes);

        Unmarshaller unmarshaller = context.createUnmarshaller();
        JAXBElement<Individuum> personJAXBElement = unmarshaller
            .unmarshal(new StreamSource(in), Individuum.class);
        Individuum unmarshalledPerson = personJAXBElement.getValue();
        assertNotNull("Person not unmarshalled", unmarshalledPerson);
        assertEquals("John Doe", unmarshalledPerson.getDisplayName());
        assertNotNull("Name not unmarshalled", unmarshalledPerson.getName());
        assertEquals("John", unmarshalledPerson.getName()
            .getFirstName());
        assertEquals("Doe", unmarshalledPerson.getName()
            .getLastName());
        assertEquals(18, unmarshalledPerson.getAge()
            .intValue());
        assertEquals("Workhorse", unmarshalledPerson.getRole());
        AddrBase homeAddress = unmarshalledPerson.getInvoiceAddress();
        assertNotNull("HomeAddress not unmarshalled", homeAddress);
        assertEquals("Carl Benz Str. 12", homeAddress.getAddr1());
        assertEquals("Schwetzingen", homeAddress.getCity());
        assertTrue("unexpected address type", homeAddress instanceof Address);
        assertEquals("12121", ((Address) homeAddress).getPostCode());
        assertEquals("Germany", ((Address) homeAddress).getCountry());
    }

    @Test
    // @Ignore
    public void testUnmarshalPersonWithUnknowAddressSubtype() throws JAXBException, IOException, SAXException {
        JAXBContext context = JAXBContext.newInstance("com.example.person");

        InputStream in = this.getClass().getResourceAsStream("/person-address-subtype.xml");

        Unmarshaller unmarshaller = context.createUnmarshaller();
        JAXBElement<Individuum> personJAXBElement = unmarshaller
            .unmarshal(new StreamSource(in), Individuum.class);
        Individuum unmarshalledPerson = personJAXBElement.getValue();
        assertNotNull("Person not unmarshalled", unmarshalledPerson);
        assertEquals("John Doe", unmarshalledPerson.getDisplayName());
        assertNotNull("Name not unmarshalled", unmarshalledPerson.getName());
        assertEquals("John", unmarshalledPerson.getName()
            .getFirstName());
        assertEquals("Doe", unmarshalledPerson.getName()
            .getLastName());
        assertEquals(18, unmarshalledPerson.getAge()
            .intValue());
        assertEquals("Workhorse", unmarshalledPerson.getRole());
        AddrBase homeAddress = unmarshalledPerson.getInvoiceAddress();
        assertNotNull("HomeAddress not unmarshalled", homeAddress);
        assertEquals("Carl Benz Str. 12", homeAddress.getAddr1());
        assertEquals("Schwetzingen", homeAddress.getCity());
        assertThat(homeAddress, isA(AddrBase.class));
        List<AddrBase> otherAddresses = unmarshalledPerson.getOtherAddress();
        assertEquals(2, otherAddresses.size());
    }

    @Test
    public void testBuilderMethods() {
        ObjectFactory objectFactory = new ObjectFactory();
        // ensure builder methods are generated for alias bean
        Address globalAddress = objectFactory.createAddress()
            .withCity("Schwetzingen")
            .withAddr1("Carl Benz Str. 12")
            .withCountry("Germany")
            .withPostCode("12121");
        assertNotNull(globalAddress);
    }

    private Individuum createPerson(ObjectFactory objectFactory) {
        Individuum person = objectFactory.createIndividuum();
        person.setPersonId("ID_123");

        Name name = new Name();
        name.setFirstName("John");
        name.setLastName("Doe");
        person.setName(name);
        //person.getHobbies().add("playing Minecraft");

        person.setAge(18);
        person.setRole("Workhorse");

        Address globalAddress = new Address();
        globalAddress.setCity("Schwetzingen");
        globalAddress.setAddr1("Carl Benz Str. 12");
        globalAddress.setCountry("Germany");
        globalAddress.setPostCode("12121");

        List<AddrBase> otherAddress = person.getOtherAddress();
        otherAddress.add(globalAddress);
        otherAddress.add(globalAddress);

        person.setInvoiceAddress(globalAddress);
        person.setRoleWrapper(new ValueWrapper().withText("foo").withValue("001"));
        return person;
    }

}