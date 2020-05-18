package de.escalon.xml.xjc;

import java.util.*;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.sun.tools.xjc.model.CClassInfo;
import com.sun.tools.xjc.model.CCustomizations;
import com.sun.tools.xjc.model.CPluginCustomization;
import com.sun.tools.xjc.model.CPropertyInfo;

public class BeanInclusionHelper {

    class BeanInclusions implements Iterable<List<BeanInclusion>> {
        Map<String, List<BeanInclusion>> beanInclusions;

        public BeanInclusions(Map<String, List<BeanInclusion>> beanInclusions) {
            this.beanInclusions = beanInclusions;
        }

        public BeanInclusion getBeanInclusion(CClassInfo classInfo) {
            List<BeanInclusion> beanInclusionList = beanInclusions.get(classInfo.shortName);
            if (beanInclusionList != null) {
                for (BeanInclusion beanInclusion : beanInclusionList) {
                    if (beanInclusion.includesClass(classInfo.getName())) {
                        return beanInclusion;
                    }
                }
            }
            return null;
        }

        public Iterator<List<BeanInclusion>> iterator() {
            return beanInclusions.values()
                    .iterator();
        }
    }

    class BeanInclusion {
        private final String simpleName;
        private Set<String> properties;
        private final String packageRoot;
        private final String trailingName;
        private Map<String, String> aliases;
        private String beanAlias;
        private Map<String, String> propertiesToAdd;
        private final String prefix;
        private Map<String, AdapterSpec> xmlAdapters = Collections.<String, AdapterSpec>emptyMap();
        private Map<String, ExpressionSpec> expressions = Collections.<String, ExpressionSpec>emptyMap();
        private Map<String, SetterSpec> setters = Collections.<String, SetterSpec>emptyMap();;

        public BeanInclusion(String simpleName,
                             String packageRoot, String prefix) {
            this.simpleName = simpleName;
            this.prefix = prefix;
            this.trailingName = "." + simpleName;
            this.packageRoot = packageRoot;
        }

        public Map<String, String> getPropertiesToAdd() {
            return propertiesToAdd;
        }

        public String getPropertyAlias(String property) {
            return aliases.get(property);
        }

        public void setBeanAlias(String beanAlias) {
            this.beanAlias = beanAlias;
        }

        public String getBeanAlias() {
            return beanAlias;
        }

        public String getPrefix() {
            return prefix;
        }

        public AdapterSpec getXmlAdapter(String property) {
            return xmlAdapters.get(property);
        }

        public boolean includesClass(String className) {
            return className.endsWith(trailingName) && (packageRoot == null ? true
                    : className.startsWith(packageRoot) || className.matches(packageRoot));
        }

        public boolean includesProperty(String propertyName) {
            return properties.contains(propertyName);
        }

        @Override
        public String toString() {
            return (packageRoot.isEmpty() ? packageRoot : packageRoot + ".") + simpleName + " " + properties.toString()
                    + " aliases " + aliases.toString();
        }

        public boolean isSatisfiedByProperties(List<CPropertyInfo> propertyInfos) {
            for (String propertyName : properties) {
                CPropertyInfo found = findPropertyInfo(propertyInfos, propertyName);
                if (found == null) {
                    return false;
                }
            }
            return true;
        }

        private CPropertyInfo findPropertyInfo(List<CPropertyInfo> propertyInfos, String propertyName) {
            for (CPropertyInfo cPropertyInfo : propertyInfos) {
                if (propertyName.equals(cPropertyInfo.getName(false))) { // fooBar
                    return cPropertyInfo;
                }
            }
            return null;
        }

        public void setXmlAdapters(Map<String, AdapterSpec> xmlAdapters) {
            this.xmlAdapters = xmlAdapters;
        }

        public void setExpressions(Map<String, ExpressionSpec> expressions) {
            this.expressions = expressions;
        }

        public Map<String, ExpressionSpec> getExpressions() {
            return expressions;
        }

        public void setProperties(HashSet<String> properties) {
            this.properties = properties;
        }

        public void setPropertiesToAdd(Map<String, String> propertiesToAdd) {
            this.propertiesToAdd = propertiesToAdd;
        }

        public void setAliases(HashMap<String, String> aliases) {
            this.aliases = aliases;
        }

        public void setSetters(Map<String, SetterSpec> setters) {
            this.setters = setters;
        }

        public Map<String, SetterSpec> getSetters() {
            return setters;
        }
    }

    class AdapterSpec {
        public final String adapterClass;
        public final String adaptsToType;

        public AdapterSpec(String adapterClass, String adaptsToType) {
            super();
            this.adapterClass = adapterClass;
            this.adaptsToType = adaptsToType;
        }

    }

    /**
     * Represents tr:compute element.
     */
    class ExpressionSpec {

        public final String expression;
        public final String computesToType;

        public ExpressionSpec(String expression, String computesToType) {
            this.expression = expression;
            this.computesToType = computesToType;
        }

    }

    /**
     * Represents tr:set element.
     */
    class SetterSpec {

        public final String paramType;
        public final String paramName;
        public final String regex;
        public final List<String> assignments;

        public SetterSpec(String paramType, String paramName, String regex, List<String> assignments) {
            this.paramType = paramType;
            this.paramName = paramName;
            this.regex = regex;
            this.assignments = assignments;
        }
    }

    BeanInclusions getBeanInclusions(CCustomizations ccs) {
        Map<String, List<BeanInclusion>> includedClasses = new HashMap<String, List<BeanInclusion>>();
        for (CPluginCustomization pluginCustomization : findMyCustomizations(ccs, "include")) {
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
                        BeanInclusion beanInclusion = collectPropertiesAliasesAdaptersAndExpressions(packageRoot, prefix, beanName, beanElement);

                        addBeanInclusion(includedClasses, beanInclusion);
                    }
                }
            } else { // plain tr:include bean="Foo" statement
                BeanInclusion beanInclusion = collectPropertiesAliasesAdaptersAndExpressions(packageRoot, prefix, beanName, includeElement);
                beanInclusion.setBeanAlias(includeElement.getAttribute("alias"));
                addBeanInclusion(includedClasses, beanInclusion);
            }

        }
        return new BeanInclusions(includedClasses);
    }

    private CCustomizations findMyCustomizations(CCustomizations cc, String name) {
        CCustomizations list = new CCustomizations();
        for (CPluginCustomization cpc : cc) {
            Element e = cpc.element;
            if (TolerantReaderPlugin.NAMESPACE_URI.equals(e.getNamespaceURI()) && e.getLocalName()
                    .equals(name)) {
                list.add(cpc);
            }
        }

        return list;

    }

    private BeanInclusion collectPropertiesAliasesAdaptersAndExpressions(String packageRoot, String prefix, String beanName,
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
                            "Found tr:alias element having neither an alias attribute nor text content representing the alias name on bean "
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
                        if (propertyName.isEmpty()) {
                            throw new IllegalStateException(
                                    "Found tr:alias element with tr:adapter having an empty property attribute on bean "
                                            + beanName);
                        }

                        NamedNodeMap adapterAttributes = aliasChild.getAttributes();
                        Node classAttr = adapterAttributes.getNamedItem("class");
                        Node toAttr = adapterAttributes.getNamedItem("to");
                        if (classAttr != null) {
                            String adaptsTo = "java.lang.String";
                            if (toAttr != null && !toAttr.getNodeValue()
                                    .isEmpty()) {
                                adaptsTo = toAttr.getNodeValue();
                            }
                            AdapterSpec adapterSpec = new AdapterSpec(classAttr.getNodeValue(), adaptsTo);
                            xmlAdapterSpecs.put(aliasName, adapterSpec);
                        } else {
                            throw new IllegalArgumentException("Found tr:adapter element for tr:alias" +
                                    " property=\"" + propertyName + "\" without class attribute on bean " +
                                    beanName);
                        }
                    } else if ("compute".equals(aliasChild.getLocalName())) {

                        NamedNodeMap adapterAttributes = aliasChild.getAttributes();
                        Node exprAttr = adapterAttributes.getNamedItem("expr");
                        Node toAttr = adapterAttributes.getNamedItem("to");
                        if (exprAttr != null) {
                            String computesTo = "java.lang.String";
                            if (toAttr != null && !toAttr.getNodeValue()
                                    .isEmpty()) {
                                computesTo = toAttr.getNodeValue();
                            }
                            ExpressionSpec expressionSpec = new ExpressionSpec(exprAttr.getNodeValue(), computesTo);
                            expressionSpecs.put(aliasName, expressionSpec);
                        } else {
                            throw new IllegalArgumentException("Found tr:compute element without expr attribute " +
                                    "on bean " + beanName);
                        }
                    } else if ("set".equals(aliasChild.getLocalName())) {

                        NamedNodeMap adapterAttributes = aliasChild.getAttributes();
                        Node paramTypeAttr = adapterAttributes.getNamedItem("paramType");
                        Node paramNameAttr = adapterAttributes.getNamedItem("paramName");

                        String paramType= (paramTypeAttr == null ? "java.lang.String" : paramTypeAttr.getNodeValue());
                        String paramName= (paramNameAttr == null ? "value" : paramNameAttr.getNodeValue());

                        String regex = null;
                        List<String> assignments = new ArrayList<String>();
                        NodeList setterChildNodes = aliasChild.getChildNodes();
                        for (int k = 0; k < setterChildNodes.getLength(); k++) {
                            Node setterChild = setterChildNodes.item(k);
                            if("regex".equals(setterChild.getLocalName())) {
                                regex = setterChild.getTextContent();
                            } else if ("assign".equals(setterChild.getLocalName())) {
                                assignments.add(setterChild.getTextContent());
                            } else {
                                throw new IllegalArgumentException("Unexpected child " + setterChild.getLocalName() +
                                        " in tr:set element " + beanName);
                            }
                        }
                        SetterSpec setterSpec = new SetterSpec(paramType, paramName, regex, assignments);
                        setterSpecs.put(aliasName, setterSpec);
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
                if (propertyAttr != null && typeAttr != null) {
                    if (!propertyName.isEmpty()) {
                        propertiesToAdd.put(propertyName, typeName);
                    }
                }
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
        List<BeanInclusion> list = includedClasses.get(beanInclusion.simpleName);
        if (list == null) {
            list = new ArrayList<BeanInclusion>();
            includedClasses.put(beanInclusion.simpleName, list);
        }
        list.add(beanInclusion);
    }

}
