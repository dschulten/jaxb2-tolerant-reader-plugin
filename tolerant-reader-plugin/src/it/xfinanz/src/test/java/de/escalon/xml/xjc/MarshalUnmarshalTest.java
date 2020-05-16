package de.escalon.xml.xjc;

import de.escalon.xfinanz.ObjectFactory;
import de.escalon.xfinanz.SepaMandatNeu0602;
import org.eclipse.persistence.jaxb.JAXBContextProperties;
import org.eclipse.persistence.jaxb.MarshallerProperties;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MarshalUnmarshalTest {

  @Test
  public void testMarshallingSepaMandatNew() throws Exception {
    Map<String, Object> props = new HashMap<String, Object>();

    props.put(JAXBContextProperties.OXM_METADATA_SOURCE, "oxm-v1.xml");

    JAXBContext context =
        JAXBContext.newInstance(
            "de.escalon.xfinanz", Thread.currentThread().getContextClassLoader(), props);

    Marshaller marshaller = context.createMarshaller();
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

    marshaller.setProperty(MarshallerProperties.OBJECT_GRAPH, "v1");

    ObjectFactory xfn = new ObjectFactory();

    SepaMandatNeu0602 sepaMandatNeu0602 = createSepaMandatNeu0602(xfn);

    // TODO: see if we can override the namespace from the package-info.java
    JAXBElement<SepaMandatNeu0602> jaxbElement =
        new JAXBElement<SepaMandatNeu0602>(
            new QName("xFinanz280", "sepa.MandatNeu.0602"),
            SepaMandatNeu0602.class,
            sepaMandatNeu0602);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    marshaller.marshal(jaxbElement, out);

    byte[] outBytes = out.toByteArray();
    System.out.write(outBytes);

    ByteArrayInputStream in = new ByteArrayInputStream(outBytes);

    DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
    Document document = documentBuilder.parse(in);
    
    assertThat(document, Matchers.hasXPath("//postanschrift/strasse", Matchers.equalTo("Q7 1A")));

    // TODO: support writable, synthetic fields which can e.g. split incoming data and apply them to the JAXB bean

  }

  @Test
  public void testMarshallingSepaMandatNewCurrentVersion() throws Exception {
    Map<String, Object> props = new HashMap<String, Object>();

    JAXBContext context =
            JAXBContext.newInstance(
                    "de.escalon.xfinanz", Thread.currentThread().getContextClassLoader(), props);

    Marshaller marshaller = context.createMarshaller();
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

    ObjectFactory xfn = new ObjectFactory();

    SepaMandatNeu0602 sepaMandatNeu0602 = createSepaMandatNeu0602(xfn);

    // TODO: see if we can override the namespace from the package-info.java
    JAXBElement<SepaMandatNeu0602> jaxbElement =
            new JAXBElement<SepaMandatNeu0602>(
                    new QName("xFinanz310", "sepa.MandatNeu.0602"),
                    SepaMandatNeu0602.class,
                    sepaMandatNeu0602);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    marshaller.marshal(jaxbElement, out);

    byte[] outBytes = out.toByteArray();
    System.out.write(outBytes);

    ByteArrayInputStream in = new ByteArrayInputStream(outBytes);

    Unmarshaller unmarshaller = context.createUnmarshaller();
    JAXBElement<SepaMandatNeu0602> personJAXBElement =
            unmarshaller.unmarshal(new StreamSource(in), SepaMandatNeu0602.class);
    SepaMandatNeu0602 unmarshalledSepaMandatNeu = personJAXBElement.getValue();
    assertNotNull("SepaMandatNeu not unmarshalled", unmarshalledSepaMandatNeu);
    assertEquals(
            "001",
            unmarshalledSepaMandatNeu
                    .getSepaMandats()
                    .get(0)
                    .getSepaMandatStammdaten()
                    .getMandatTyp()
                    .getCode());
    assertEquals(
            "Q7 1A",
            unmarshalledSepaMandatNeu
                    .getPersonKomplett()
                    .getAdressStamms()
                    .get(0)
                    .getAnschrift()
                    .getPostanschrift()
                    .getStrassekomplett());
  }


  private SepaMandatNeu0602 createSepaMandatNeu0602(ObjectFactory xfn) {
    return xfn.createSepaMandatNeu0602()
        .withNachrichtenkopf(xfn.createNachrichtenkopf())
        .withPersonKomplett(
            xfn.createPersonKomplett()
                .withBankverbindungs(
                    xfn.createBankverbindung()
                        .withPersonenNummern(xfn.createPersonenNummern())
                        .withBankkonto(
                            xfn.createBankkonto()
                                .withIban("DE89 3704 0044 0532 0130 00")
                                .withInhaberNatuerlichePersons(
                                    xfn.createNatuerlichePerson()
                                        .withNameNatuerlichePerson(
                                            xfn.createNameNatuerlichePerson()
                                                .withVorname(
                                                    xfn.createAllgemeinerName().withName("Arndt"))
                                                .withFamilienname(
                                                    xfn.createAllgemeinerName()
                                                        .withName("von Bohlen und Halbach"))))
                                .withBankverbindungsNummern(xfn.createBankverbindungsNummern())))
                .withAdressStamms(
                    xfn.createAdressstamm()
                        .withAnschrift(
                            xfn.createAnschriftErweitert()
                                .withPostanschrift(
                                    xfn.createPostanschrift()
                                        .withStrasse("Q7")
                                        .withHausnummer("1")
                                        .withHausnummernzusatz("A"))))
                .withPersonNats(
                    xfn.createPersonNat()
                        .withNatuerlichePerson(
                            xfn.createNatuerlichePerson()
                                .withNameNatuerlichePerson(
                                    xfn.createNameNatuerlichePerson()
                                        .withVorname(xfn.createAllgemeinerName().withName("Arndt"))
                                        .withFamilienname(
                                            xfn.createAllgemeinerName()
                                                .withName("von Bohlen und Halbach"))))))
        .withSepaMandats(
            Arrays.asList(
                xfn.createSepaMandat()
                    .withMandatsreferenz("xxx")
                    .withGlaeubigerID("xxx")
                    .withSepaMandatStammdaten(
                        xfn.createSepaMandatStammdaten()
                            .withMandatTyp(
                                xfn.createCodeMandatTyp()
                                    .withCode("001") // Basislastschrift IV.1.113.2
                                ))));
  }
}
