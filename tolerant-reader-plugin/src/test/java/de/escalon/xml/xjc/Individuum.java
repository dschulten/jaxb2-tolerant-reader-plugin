//
// Diese Datei wurde mit der JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.11 generiert 
// Siehe <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// �nderungen an dieser Datei gehen bei einer Neukompilierung des Quellschemas verloren. 
// Generiert: 2020.05.16 um 04:35:15 PM CEST 
//

package de.escalon.xml.xjc;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import de.escalon.hypermedia.hydra.mapping.Expose;
import de.escalon.hypermedia.hydra.mapping.Term;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * <p>Java-Klasse f�r Person complex type.
 *
 * <p>Das folgende Schemafragment gibt den erwarteten Content an, der in dieser Klasse enthalten ist.
 *
 * <pre>
 * &lt;complexType name="Person"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="Name" type="{http://example.com/person}Name" minOccurs="0"/&gt;
 *         &lt;element name="age" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/&gt;
 *         &lt;element name="HomeAddress" type="{http://example.com/person}BaseAddress"/&gt;
 *         &lt;element name="ShippingAddress" type="{http://example.com/person}BaseAddress" minOccurs="0"/&gt;
 *         &lt;element name="OtherAddress" type="{http://example.com/person}BaseAddress" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="Extra" type="{http://example.com/person}Extra" minOccurs="0"/&gt;
 *         &lt;element name="Role" type="{http://example.com/person}ValueWrapper"/&gt;
 *         &lt;element name="Interest" type="{http://www.w3.org/2001/XMLSchema}int" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *       &lt;attribute name="personId" use="required" type="{http://www.w3.org/2001/XMLSchema}ID" /&gt;
 *       &lt;attribute name="friend" type="{http://www.w3.org/2001/XMLSchema}IDREF" /&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD) @XmlType(name = "Person") @Term(define = "cust", as = "http://example.com/person#") @Expose("cust:Person") public class Individuum
        implements Serializable {

    private final static long serialVersionUID = -1L;
    @XmlElement(name = "Name") protected Name name;
    protected Integer age;
    @XmlElement(name = "HomeAddress", required = true) protected AddrBase invoiceAddress;
    @XmlElement(name = "OtherAddress") protected List<AddrBase> otherAddress;
    @XmlElement(name = "Role", required = true) protected String role;
    @XmlAttribute(name = "personId", required = true) @XmlJavaTypeAdapter(CollapsedStringAdapter.class) @XmlID @XmlSchemaType(name = "ID") protected String personId;
    @XmlAttribute(name = "friend") @XmlIDREF @XmlSchemaType(name = "IDREF") protected Individuum buddy;
    @XmlTransient protected Integer myProperty;

    @XmlTransient public String getDisplayName() {
        ExpressionParser parser = new SpelExpressionParser();
        EvaluationContext context = new StandardEvaluationContext(this);
        Expression exp = parser.parseExpression(
                "T(org.apache.commons.lang3.StringUtils).trimToNull((name?.firstName?:'') + ' ' + (name?.lastName?:''))");
        Object ret = exp.getValue(context);
        return ((String) ret);
    }

    /**
     * Ruft den Wert der name-Eigenschaft ab.
     *
     * @return possible object is
     * {@link Name }
     */
    @Expose("cust:Person.name") public Name getName() {
        return name;
    }

    /**
     * Legt den Wert der name-Eigenschaft fest.
     */
    public void setName(Name name) {
        this.name = name;
    }

    /**
     * Ruft den Wert der age-Eigenschaft ab.
     *
     * @return possible object is
     * {@link Integer }
     */
    @Expose("cust:Person.age") public Integer getAge() {
        return age;
    }

    /**
     * Legt den Wert der age-Eigenschaft fest.
     */
    public void setAge(Integer age) {
        this.age = age;
    }

    @Expose("cust:Person.homeAddress") public AddrBase getInvoiceAddress() {
        return invoiceAddress;
    }

    /**
     * Legt den Wert der homeAddress-Eigenschaft fest.
     */
    public void setInvoiceAddress(AddrBase invoiceAddress) {
        this.invoiceAddress = invoiceAddress;
    }

    @Expose("cust:Person.otherAddress") public List<AddrBase> getOtherAddress() {
        if (otherAddress == null) {
            this.otherAddress = new ArrayList<AddrBase>();
        }
        return otherAddress;
    }

    public String getRole() {
        return role;
    }

    /**
     * Legt den Wert der role-Eigenschaft fest.
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * Ruft den Wert der personId-Eigenschaft ab.
     *
     * @return possible object is
     * {@link String }
     */
    @Expose("cust:Person.personId") public String getPersonId() {
        return personId;
    }

    /**
     * Legt den Wert der personId-Eigenschaft fest.
     */
    public void setPersonId(String personId) {
        this.personId = personId;
    }

    /**
     * Ruft den Wert der friend-Eigenschaft ab.
     *
     * @return possible object is
     * {@link Object }
     */
    @Expose("cust:Person.friend") public Individuum getBuddy() {
        return buddy;
    }

    /**
     * Legt den Wert der friend-Eigenschaft fest.
     */
    public void setBuddy(Individuum buddy) {
        this.buddy = buddy;
    }

    public Integer getMyProperty() {
        return this.myProperty;
    }

    public void setMyProperty(Integer myProperty) {
        this.myProperty = myProperty;
    }

    public Individuum withName(Name name) {
        setName(name);
        return this;
    }

    public Individuum withAge(Integer age) {
        setAge(age);
        return this;
    }

    public Individuum withInvoiceAddress(AddrBase invoiceAddress) {
        setInvoiceAddress(invoiceAddress);
        return this;
    }

    public Individuum withOtherAddress(AddrBase... values) {
        if (values != null) {
            for (AddrBase value : values) {
                getOtherAddress().add(value);
            }
        }
        return this;
    }

    public Individuum withOtherAddress(Collection<AddrBase> values) {
        if (values != null) {
            getOtherAddress().addAll(values);
        }
        return this;
    }

    public Individuum withRole(String role) {
        setRole(role);
        return this;
    }

    public Individuum withPersonId(String personId) {
        setPersonId(personId);
        return this;
    }

    public Individuum withBuddy(Individuum buddy) {
        setBuddy(buddy);
        return this;
    }

    public Individuum withMyProperty(Integer myProperty) {
        setMyProperty(myProperty);
        return this;
    }

}
