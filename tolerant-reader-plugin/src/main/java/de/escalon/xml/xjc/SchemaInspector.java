package de.escalon.xml.xjc;

import com.sun.xml.xsom.XSAttributeUse;
import com.sun.xml.xsom.XSComponent;
import com.sun.xml.xsom.XSParticle;
import java.math.BigInteger;

public class SchemaInspector {

  public static boolean isRequiredElementOrAttribute(XSComponent schemaComponent) {
    boolean requiredElementOrAttribute = false;
    if (schemaComponent instanceof XSParticle) {
      XSParticle particle = (XSParticle) schemaComponent;
      BigInteger minOccurs = particle.getMinOccurs();
      if (minOccurs == null || minOccurs.compareTo(BigInteger.ONE) > -1) {
        requiredElementOrAttribute = true;
      }
    } else if (schemaComponent instanceof XSAttributeUse) {
      XSAttributeUse attributeUse = (XSAttributeUse) schemaComponent;
      requiredElementOrAttribute = attributeUse.isRequired();
    }
    return requiredElementOrAttribute;
  }
}
