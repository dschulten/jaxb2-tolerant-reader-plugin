package de.escalon.xml.xjc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.namespace.QName;

import com.sun.codemodel.*;
import com.sun.tools.xjc.model.*;
import com.sun.tools.xjc.reader.Ring;
import de.escalon.xml.xjc.BeanInclusionHelper.SetterSpec;
import jdk.nashorn.internal.runtime.Property;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import com.sun.xml.xsom.XSAttributeUse;
import com.sun.xml.xsom.XSComplexType;
import com.sun.xml.xsom.XSComponent;
import com.sun.xml.xsom.XSContentType;
import com.sun.xml.xsom.XSElementDecl;
import com.sun.xml.xsom.XSModelGroup;
import com.sun.xml.xsom.XSParticle;
import com.sun.xml.xsom.XSTerm;
import com.sun.xml.xsom.XSType;

import de.escalon.hypermedia.hydra.mapping.Expose;
import de.escalon.hypermedia.hydra.mapping.Term;
import de.escalon.xml.xjc.BeanInclusionHelper.AdapterSpec;
import de.escalon.xml.xjc.BeanInclusionHelper.BeanInclusion;
import de.escalon.xml.xjc.BeanInclusionHelper.BeanInclusions;
import de.escalon.xml.xjc.BeanInclusionHelper.ExpressionSpec;

import static de.escalon.xml.xjc.SchemaInspector.isRequiredElementOrAttribute;

// TODO other plugins do not see adaptations from collection to single type
// TODO alias properties on parent classes loses withXXX method
// TODO serialVersionUID not copied in alias beans
// TODO putting the adaptation into the class requires both an xml transient property and the proper property
// TODO use getSettersAndGetters when looking for accessors, from ClassHelper
// TODO allow multiline properties attribute list
// TODO improve error message when alias element is empty (string index -1)
// TODO duplicate tr:bean names should throw
// TODO do we include required properties from beans further up in the inheritance?
// TODO no serialVersionUID in alias class Address
// TODO even if a base bean does not have an element, a derived restricted 
//   child bean might: Fullname.middleInitial - automatically include restricted properties
//   on base so we can copy them, then zap them after copy or - well - keep them
// TODO Expose for restricted classes: should it expose the restriction base instead of the restricted type?
// TODO expose implicitly included classes, too?
// TODO automatically keep required fields or attributes
// TODO automatically adjust getter and setter names for alias beans according to alias beans
// TODO let beanInclusions not maintain a map of simple name to list of beaninclusions matching 
//   that simple name, but something more specific, maybe map of fullName to BeanInclusion?
// TODO allow to expose properties under a different vocab
// TODO package-info prefix annotation, currently we expose with full url and use prefix
// TODO create XSD Schema for tr extensions
// TODO use renamed namespace without version part for Expose (xjc renames packages, this
//   would require introducing a namespace-rename feature)
// TODO add getter Javadoc which allows to tell where property comes from, have XCD XPointer
//   syntax there

/**
 * Reduces generated classes to only those classes and properties which a client really needs and
 * allows to rename and restructure.
 */
public class TolerantReaderPlugin extends Plugin {

  static final String NAMESPACE_URI = "http://jaxb2-commons.dev.java.net/tolerant-reader";

  /**
   * Name of Option to enable this plugin
   */
  private static final String OPTION_NAME = "Xtolerant-reader";

  private final BeanInclusionHelper beanInclusionHelper = new BeanInclusionHelper();
  private final SchemaProcessor schemaProcessor = new SchemaProcessor();

  /**
   * TolerantReaderPlugin uses "-Xtolerant-reader" as the command-line argument
   */
  public String getOptionName() {
    return OPTION_NAME;
  }

  /**
   * Return usage information for plugin
   */
  public String getUsage() {
    return "  -"
        + OPTION_NAME
        + "    : restricts xjc compilation to classes and properties named in bindings file";
  }

  @Override
  public List<String> getCustomizationURIs() {
    return Collections.singletonList(NAMESPACE_URI);
  }

  @Override
  public boolean isCustomizationTagName(String nsUri, String localName) {
    return NAMESPACE_URI.equals(nsUri) && ("include".equals(localName) || "alias".equals(localName)
        || "add".equals(localName)
        || "bean".equals(localName) || "adapter".equals(localName) || "compute".equals(localName)
        || "set".equals(localName) || "assign".equals(localName) || "regex".equals(localName)
    );
  }

  @Override
  public boolean run(Outline outline, Options opts, ErrorHandler errHandler) {
    CCustomizations customizations = outline.getModel()
        .getCustomizations();
    BeanInclusionHelper.BeanInclusions beanInclusions = beanInclusionHelper.getBeanInclusions(customizations);
    schemaProcessor.processSchemaTags(outline, opts, beanInclusions);
    return true;
  }

  @SuppressWarnings("unused")
  private void dump(CCustomizations cc) {
    for (CPluginCustomization cPluginCustomization : cc) {
      Node n = cPluginCustomization.element;
      System.err.println("\t" + n.getNodeName() + " " + n.getNodeValue());
      NamedNodeMap attribs = n.getAttributes();
      if (attribs != null) {
        for (int j = 0; j < attribs.getLength(); j++) {
          Node attrib = attribs.item(j);
          System.err.println("\t\t" + attrib);
        }
      }
    }
  }
}