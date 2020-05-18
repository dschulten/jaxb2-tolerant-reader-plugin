//
// Diese Datei wurde mit der JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.11 generiert 
// Siehe <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// �nderungen an dieser Datei gehen bei einer Neukompilierung des Quellschemas verloren. 
// Generiert: 2020.05.16 um 04:35:15 PM CEST 
//


package de.escalon.xml.xjc;

import de.escalon.hypermedia.hydra.mapping.Expose;
import de.escalon.hypermedia.hydra.mapping.Term;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;



/**
 * <p>Java-Klasse f�r BaseAddress complex type.
 * 
 * <p>Das folgende Schemafragment gibt den erwarteten Content an, der in dieser Klasse enthalten ist.
 * 
 * <pre>
 * &lt;complexType name="BaseAddress"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="Addr1" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="Addr2" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="City" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "BaseAddress")
@Term(define = "cust", as = "http://example.com/person#")
@Expose("cust:BaseAddress")
public class AddrBase implements Serializable
{

    private final static long serialVersionUID = -1L;
    @XmlElement(name = "Addr1", required = true)
    protected String addr1;
    @XmlElement(name = "City", required = true)
    protected String city;

    /**
     * Ruft den Wert der addr1-Eigenschaft ab.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Expose("cust:BaseAddress.addr1")
    public String getAddr1() {
        return addr1;
    }

    /**
     * Legt den Wert der addr1-Eigenschaft fest.
     * 
     */
    public void setAddr1(String addr1) {
        this.addr1 = addr1;
    }

    /**
     * Ruft den Wert der city-Eigenschaft ab.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Expose("cust:BaseAddress.city")
    public String getCity() {
        return city;
    }

    /**
     * Legt den Wert der city-Eigenschaft fest.
     * 
     */
    public void setCity(String city) {
        this.city = city;
    }

    public AddrBase withAddr1(String addr1) {
        setAddr1(addr1);
        return this;
    }

    public AddrBase withCity(String city) {
        setCity(city);
        return this;
    }
}
