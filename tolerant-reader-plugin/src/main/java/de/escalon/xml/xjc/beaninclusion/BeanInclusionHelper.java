package de.escalon.xml.xjc.beaninclusion;

import de.escalon.xml.xjc.TolerantReaderPlugin;
import java.util.*;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.sun.tools.xjc.model.CCustomizations;
import com.sun.tools.xjc.model.CPluginCustomization;

public class BeanInclusionHelper {

    public BeanInclusions getBeanInclusions(CCustomizations ccs) {
        Map<String, List<BeanInclusion>> includedClasses = new HashMap<String, List<BeanInclusion>>();
        for (CPluginCustomization pluginCustomization : findMyCustomizations(ccs)) {
            pluginCustomization.markAsAcknowledged();
            Element includeElement = pluginCustomization.element;
            String packageRoot = includeElement.getAttribute("packageRoot");
            String prefix = includeElement.getAttribute("prefix");
            String beanName = includeElement.getAttribute("bean");
            if (beanName == null || beanName.isEmpty()) {
                NodeList childNodes = includeElement.getChildNodes();
                for (int i = 0; i < childNodes.getLength(); i++) {
                    Node beanNode = childNodes.item(i);
                    if ("bean".equals(beanNode.getLocalName()) && beanNode instanceof Element) {
                        Element beanElement = (Element) beanNode;

                        beanName = beanElement.getAttribute("name");
                        BeanInclusion beanInclusion = evaluateBeanInclusion(
                            packageRoot, prefix, beanName, beanElement);

                        addBeanInclusion(includedClasses, beanInclusion);
                    }
                }
            } else { // plain tr:include bean="Foo" statement
                BeanInclusion beanInclusion = evaluateBeanInclusion(
                    packageRoot, prefix, beanName, includeElement);
                beanInclusion.setBeanAlias(includeElement.getAttribute("alias"));
                addBeanInclusion(includedClasses, beanInclusion);
            }

        }
        return new BeanInclusions(includedClasses);
    }

    private CCustomizations findMyCustomizations(CCustomizations cc) {
        CCustomizations list = new CCustomizations();
        for (CPluginCustomization cpc : cc) {
            Element e = cpc.element;
            if (TolerantReaderPlugin.NAMESPACE_URI.equals(e.getNamespaceURI()) && e.getLocalName()
                    .equals("include")) {
                list.add(cpc);
            }
        }

        return list;

    }

    private BeanInclusion evaluateBeanInclusion(String packageRoot, String prefix, String beanName,
            Element includeOrBeanElement) {

        HashMap<String, String> aliases = new HashMap<String, String>();
        HashSet<String> propertiesToInclude = new HashSet<String>();
        Map<String, AdapterSpec> xmlAdapterSpecs = new HashMap<String, AdapterSpec>();
        Map<String, ExpressionSpec> expressionSpecs = new HashMap<String, ExpressionSpec>();
        Map<String, SetterSpec> setterSpecs = new HashMap<String, SetterSpec>();
        Map<String, String> propertiesToAdd = new HashMap<String, String>();

        NodeList childNodes = includeOrBeanElement.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node item = childNodes.item(i);
            if ("alias".equals(item.getLocalName())) {
                NamedNodeMap attributes = item.getAttributes();
                Node propertyAttr = attributes.getNamedItem("property");

                String aliasName = getAliasName(item, attributes);
                if (aliasName.isEmpty()) {
                    throw new IllegalStateException(
                        "Found tr:alias element having neither an alias attribute nor "
                            + "text content representing the alias name on bean "
                            + beanName);
                }
                String propertyName = null;
                if (propertyAttr != null) {
                    propertyName = propertyAttr.getNodeValue();
                    if (!propertyName.isEmpty()) {
                        propertiesToInclude.add(propertyName);
                        aliases.put(propertyName, aliasName);
                    }
                }
                NodeList aliasChildNodes = item.getChildNodes();
                for (int j = 0; j < aliasChildNodes.getLength(); j++) {
                    Node aliasChild = aliasChildNodes.item(j);
                    if ("adapter".equals(aliasChild.getLocalName())) {
                        xmlAdapterSpecs.put(aliasName, getAdapterSpec(beanName, propertyName, aliasChild));
                    } else if ("compute".equals(aliasChild.getLocalName())) {
                        expressionSpecs.put(aliasName, getExpressionSpec(beanName, aliasChild));
                    } else if ("set".equals(aliasChild.getLocalName())) {
                        setterSpecs.put(aliasName, getSetterSpec(beanName, aliasChild));
                    }
                }
            } else if ("add".equals(item.getLocalName())) {
                NamedNodeMap attributes = item.getAttributes();
                Node propertyAttr = attributes.getNamedItem("property");
                Node typeAttr = attributes.getNamedItem("type");

                String propertyName = propertyAttr.getNodeValue();
                String typeName = typeAttr.getNodeValue();
                if (typeName.isEmpty() || propertyName.isEmpty()) {
                    throw new IllegalStateException(
                        "tr:add element requires both property attribute and type attribute on bean "
                            + beanName);
                }
                propertiesToAdd.put(propertyName, typeName);
            }
        }
        String properties = includeOrBeanElement.getAttribute("properties");
        if (!properties.trim()
                .isEmpty()) {
            List<String> propertiesList = Arrays.asList(properties.split(" "));
            Set<String> intersection = new HashSet<String>(propertiesToInclude);
            intersection.retainAll(propertiesList);
            if (!intersection.isEmpty()) {
                throw new IllegalArgumentException("On bean " + beanName + ", the following properties appear " +
                        "both in the properties attribute and as alias: " + intersection.toString()
                        + ". Remove a property from the properties attribute if you want to define an alias for it.");
            }
            propertiesToInclude.addAll(propertiesList);
        }

        BeanInclusion beanInclusion = new BeanInclusion(beanName,
                packageRoot,
                prefix
        );
        beanInclusion.setProperties(propertiesToInclude);
        beanInclusion.setPropertiesToAdd(propertiesToAdd);
        beanInclusion.setAliases(aliases);
        beanInclusion.setBeanAlias(includeOrBeanElement.getAttribute("alias"));
        beanInclusion.setXmlAdapters(xmlAdapterSpecs);
        beanInclusion.setExpressions(expressionSpecs);
        beanInclusion.setSetters(setterSpecs);
        return beanInclusion; // return BeanInclusion
    }

    private AdapterSpec getAdapterSpec(String beanName, String propertyName,
        Node aliasChild) {
        if (propertyName == null || propertyName.isEmpty()) {
            throw new IllegalStateException(
                "Found tr:alias element with tr:adapter having an empty "
                    + "property attribute on bean "
                    + beanName);
        }

        NamedNodeMap adapterAttributes = aliasChild.getAttributes();
        Node classAttr = adapterAttributes.getNamedItem("class");
        Node toAttr = adapterAttributes.getNamedItem("to");
        if (classAttr == null) {
            throw new IllegalArgumentException(
                "Found tr:adapter element for tr:alias"
                    +
                    " property=\""
                    + propertyName
                    + "\" without class attribute on bean "
                    +
                    beanName);
        }
        String adaptsTo = "java.lang.String";
        if (toAttr != null && !toAttr.getNodeValue()
            .isEmpty()) {
            adaptsTo = toAttr.getNodeValue();
        }
        return new AdapterSpec(classAttr.getNodeValue(), adaptsTo);
    }

    private SetterSpec getSetterSpec(String beanName, Node aliasChild) {
        NamedNodeMap adapterAttributes = aliasChild.getAttributes();
        Node paramTypeAttr = adapterAttributes.getNamedItem("paramType");
        Node paramNameAttr = adapterAttributes.getNamedItem("paramName");

        String paramType = (paramTypeAttr == null ? "java.lang.String"
            : paramTypeAttr.getNodeValue());
        String paramName =
            (paramNameAttr == null ? "value" : paramNameAttr.getNodeValue());

        String regex = null;
        List<String> assignments = new ArrayList<String>();
        NodeList setterChildNodes = aliasChild.getChildNodes();
        for (int k = 0; k < setterChildNodes.getLength(); k++) {
            Node setterChild = setterChildNodes.item(k);
            if ("regex".equals(setterChild.getLocalName())) {
                regex = setterChild.getTextContent();
            } else if ("assign".equals(setterChild.getLocalName())) {
                assignments.add(setterChild.getTextContent());
            } else {
                throw new IllegalArgumentException(
                    "Unexpected child " + setterChild.getLocalName() +
                        " in tr:set element " + beanName);
            }
        }
        if (assignments.isEmpty()) {
            throw new IllegalArgumentException("Found tr:set element without "
                + "tr:assign children "
                + "on bean " + beanName);
        }
        return new SetterSpec(paramType, paramName, regex, assignments);
    }

    private ExpressionSpec getExpressionSpec(String beanName, Node aliasChild) {
        NamedNodeMap adapterAttributes = aliasChild.getAttributes();
        Node exprAttr = adapterAttributes.getNamedItem("expr");
        Node toAttr = adapterAttributes.getNamedItem("to");
        String computesTo = "java.lang.String";
        if (toAttr != null && !toAttr.getNodeValue()
            .isEmpty()) {
            computesTo = toAttr.getNodeValue();
        }
        String expr = null;
        if(exprAttr != null) {
            expr = exprAttr.getNodeValue();
        }
        String regex = null;
        String regexPropertyExpr = null;
        NodeList setterChildNodes = aliasChild.getChildNodes();
        for (int k = 0; k < setterChildNodes.getLength(); k++) {
            Node setterChild = setterChildNodes.item(k);
            if ("regex".equals(setterChild.getLocalName())) {
                regex = setterChild.getTextContent();
                NamedNodeMap regexAttributes = setterChild.getAttributes();
                Node regexPropertyExprAttr = regexAttributes.getNamedItem("propertyExpr");
                if(regexPropertyExprAttr == null) {
                    throw new IllegalArgumentException("Attribute 'property'"
                        + "is required for tr:regex element of tr:compute "
                        + "on bean " + beanName);
                }
                regexPropertyExpr = regexPropertyExprAttr.getNodeValue();
            } else if ("expr".equals(setterChild.getLocalName())) {
                if (expr != null) {
                    throw new IllegalArgumentException("Only one of expr attribute "
                        + "and expr element "
                        + "is allowed on bean " + beanName);
                }
                expr = setterChild.getTextContent();
            } else {
                throw new IllegalArgumentException(
                    "Unexpected child " + setterChild.getLocalName() +
                        " in tr:set element of " + beanName);
            }
        }
        if (expr == null) {
            throw new IllegalArgumentException(
                "Found tr:compute element without expr attribute " +
                    "on bean " + beanName);
        }
        return new ExpressionSpec(expr, computesTo, regex, regexPropertyExpr);
    }

    private String getAliasName(Node item, NamedNodeMap attributes) {
        String aliasName;
        Node aliasAttr = attributes.getNamedItem("alias");
        if (aliasAttr != null) {
            aliasName = aliasAttr.getNodeValue();
        } else {
            aliasName = item.getTextContent();
        }
        return aliasName;
    }

    private void addBeanInclusion(Map<String, List<BeanInclusion>> includedClasses, BeanInclusion beanInclusion) {
        List<BeanInclusion> list = includedClasses.get(beanInclusion.getSimpleName());
        if (list == null) {
            list = new ArrayList<BeanInclusion>();
            includedClasses.put(beanInclusion.getSimpleName(), list);
        }
        list.add(beanInclusion);
    }
}
