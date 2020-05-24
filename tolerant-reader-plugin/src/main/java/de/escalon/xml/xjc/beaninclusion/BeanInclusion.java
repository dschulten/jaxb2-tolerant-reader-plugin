package de.escalon.xml.xjc.beaninclusion;

import com.sun.tools.xjc.model.CPropertyInfo;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BeanInclusion {
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
        return className.endsWith(trailingName) && (packageRoot == null
            || (className.startsWith(packageRoot) || className.matches(packageRoot)));
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

    public String getSimpleName() {
        return simpleName;
    }
}
