package de.escalon.xml.xjc;

import com.sun.codemodel.*;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.model.CCustomizations;
import com.sun.tools.xjc.model.CPluginCustomization;
import com.sun.tools.xjc.model.CPropertyInfo;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.FieldOutline;
import com.sun.tools.xjc.outline.Outline;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import javax.xml.bind.annotation.XmlType;
import java.util.*;

/**
 * @author dschulten
 */
public class TolerantReaderPlugin extends Plugin {

    private static final String NAMESPACE_URI = "http://jaxb2-commons.dev.java.net/tolerant-reader";

    /**
     * Name of Option to enable this plugin
     */
    static private final String OPTION_NAME = "Xtolerant-reader";


    /**
     * Creates a new <code>DefaultValuePlugin</code> instance.
     */
    public TolerantReaderPlugin() {
    }


    /**
     * DefaultValuePlugin uses "-Xtolerant-reader" as the command-line argument
     */
    public String getOptionName() {
        return OPTION_NAME;
    }


    /**
     * Return usage information for plugin
     */
    public String getUsage() {
        return "  -" + OPTION_NAME + "    : restricts xjc compilation to classes and properties named in bindings file";
    }

    @Override
    public List<String> getCustomizationURIs() {
        return Arrays.asList(NAMESPACE_URI);
    }


    @Override
    public boolean isCustomizationTagName(String nsUri, String localName) {
        return NAMESPACE_URI.equals(nsUri) && "include".equals(localName);
    }

    /**
     * Run the plugin. We perform the following steps:
     * <p/>
     * <ul> <li>Look for fields that: <ul> <li>Were generated from XSD description</li> <li>The XSD description is of
     * type xsd:element (code level default values are not necessary for fields generated from attributes)</li> <li>A
     * default value is specified</li> <li>Map to one of the supported types</li> </ul> </li> <li>Add a new
     * initializsation expression to every field found</li> </ul>
     */
    @Override
    public boolean run(Outline outline, Options opts, ErrorHandler errHandler)
            throws SAXException {

        processSchemaTags(outline);

//        for( ClassOutline co : outline.getClasses() ) {
//            processClassTags(co);
//
//            FieldOutline fos[] = co.getDeclaredFields();
//            for (FieldOutline fo : fos) {
//                processPropertyTags(fo);
//            }
//        }

        return true;
    }

    private class BeanInclusion {
        private final String simpleName;
        private final Set<String> properties;
        private final String packageRoot;
        private final String trailingName;

        public BeanInclusion(String simpleName, Set<String> properties, String packageRoot) {
            this.simpleName = simpleName;
            this.trailingName = "." + simpleName;
            this.properties = properties;
            this.packageRoot = packageRoot;
        }

        public boolean includesClass(String className) {
            return className.endsWith(trailingName) && packageRoot == null ? true : className.startsWith(packageRoot);
        }

        public boolean includesProperty(String propertyName) {
            return properties.contains(propertyName);
        }

        @Override
        public String toString() {
            return (packageRoot.isEmpty() ? packageRoot : packageRoot + ".") +
                    simpleName + " " + properties.toString();
        }

        public boolean hasSamePropertiesAs(Set<String> includedProperties) {
            return properties.size() == includedProperties.size()
                    && properties.containsAll(includedProperties);
        }
    }

    private void processSchemaTags(Outline outline) {
        CCustomizations customizations = outline.getModel().getCustomizations();
        Map<String, BeanInclusion> beanInclusions = getBeanInclusions(customizations);

        Collection<? extends ClassOutline> classOutlines = outline.getClasses();
        List<ClassOutline> includedClassOutlines = new ArrayList<ClassOutline>();
        for (ClassOutline classOutline : classOutlines) {
            String className = classOutline.target.getName();
            String simpleName = className.substring(className.lastIndexOf('.') + 1);
            BeanInclusion beanInclusion = beanInclusions.get(simpleName);
            if (beanInclusion == null || !beanInclusion.includesClass(className)) {
                removeClass(outline, className);
            } else {
                includedClassOutlines.add(classOutline);
                // suppress properties which are not included
                List<CPropertyInfo> propertyInfos = classOutline.target.getProperties();

                List<JMethod> methodsToRemove = new ArrayList<JMethod>();
                Collection<JMethod> methods = classOutline.implClass.methods();

                Set<String> includedProperties = new HashSet<String>();
                for (CPropertyInfo propertyInfo : propertyInfos) {
                    String propertyInfoName = propertyInfo.getName(false);
                    if (!beanInclusion.includesProperty(propertyInfoName)) {
                        JFieldVar fieldVar = classOutline.implClass.fields().get(propertyInfoName);
                        classOutline.implClass.removeField(fieldVar);
                        for (JMethod method : methods) {
                            if (method.name().endsWith(propertyInfo.getName(true))) {
                                methodsToRemove.add(method);
                            }
                        }
                    } else {
                        includedProperties.add(propertyInfoName);
                    }
                }
                methods.removeAll(methodsToRemove);

                // see if all expected properties are present
                if (!beanInclusion.hasSamePropertiesAs(includedProperties)) {
                    throw new IllegalArgumentException(
                            "Expected bean with properties "
                                    + beanInclusion.toString()
                                    + " but only found properties " +
                                    includedProperties + " in schema");
                }

                Collection<JAnnotationUse> annotations = classOutline.implClass.annotations();
                for (JAnnotationUse annotation : annotations) {
                    if (annotation.getAnnotationClass().name().equals("XmlType")) {
                        classOutline.implClass.removeAnnotation(annotation);
                        break;
                    }
                }
                JAnnotationUse annotate = classOutline.implClass.annotate(XmlType.class);
                annotate.param("name", simpleName);
                // TODO add annotation again, this time only the supported fields
                // in the order defined in schema
                // annotate.paramArray("propOrder", array of fields)
            }
        }

        for (BeanInclusion inclusionEntry : beanInclusions.values()) {
            for (ClassOutline includedClassOutline : includedClassOutlines) {
                String includedClassName = includedClassOutline.target.getName();
                if (inclusionEntry.includesClass(includedClassName)) {
                    continue;
                } else {
                    throw new IllegalArgumentException(
                            "Expected bean " + inclusionEntry.toString() + " not found in schema");
                }
            }
        }

        // TODO check included properties
    }

    private void removeClass(Outline outline, String foundClass) {
        JDefinedClass clazz = outline.getCodeModel()._getClass(foundClass);
        clazz._package().remove(clazz);

        // Zap createXXX method from ObjectFactory
        String removedClassName = clazz.name();
        String factoryName = clazz._package().name() + ".ObjectFactory";
        JDefinedClass objFactory = outline.getCodeModel()._getClass(factoryName);
        Iterator<JMethod> methodIterator = objFactory.methods().iterator();
        while (methodIterator.hasNext()) {
            JMethod method = methodIterator.next();
            if (method.name().endsWith(removedClassName)) {
                methodIterator.remove();
                break;
            }
        }
        // TODO: remove references to removed class from getters and setters
    }

    private Map<String, BeanInclusion> getBeanInclusions(CCustomizations ccs) {
        Map<String, BeanInclusion> includedClasses = new HashMap<String, BeanInclusion>();
        for (CPluginCustomization pluginCustomization : findMyCustomizations(ccs, "include")) {
            pluginCustomization.markAsAcknowledged();
            String bean = pluginCustomization.element.getAttribute("bean");
            String properties = pluginCustomization.element.getAttribute("properties");
            String packageRoot = pluginCustomization.element.getAttribute("packageRoot");
            HashSet<String> propertiesToInclude = new HashSet<String>();
            if (!properties.trim().isEmpty()) {
                Collections.addAll(propertiesToInclude, properties.split(" "));
            }
            includedClasses.put(bean,
                    new BeanInclusion(bean, propertiesToInclude, packageRoot));
        }
        return includedClasses;
    }


    private void processClassTags(ClassOutline co) {
        CCustomizations ccs = co.target.getCustomizations();
        for (CPluginCustomization pc : findMyCustomizations(ccs, "class")) {

            pc.markAsAcknowledged();

            NamedNodeMap attribs = pc.element.getAttributes();
            if (attribs != null) {
                for (int i = 0; i < attribs.getLength(); i++) {
                    Node n = attribs.item(i);
                    if (n.getNamespaceURI() == null) {
                        // One of ours - we could've prefaced the attribute with
                        // our namespace.
                        processMethodModifiers(co, n.getNodeName(), n.getNodeValue());
                    }
                }
            }
        }
    }

    private void processPropertyTags(FieldOutline fo) {
        CCustomizations ccs = fo.getPropertyInfo().getCustomizations();
        for (CPluginCustomization pc : findMyCustomizations(ccs, "property")) {

            pc.markAsAcknowledged();

            String modifiers = pc.element.getAttribute("modifiers");
            if (modifiers != null) {
                processPropertyModifiers(fo.parent(), fo.getPropertyInfo().getName(false), modifiers);
            }
        }
    }

    @SuppressWarnings("unused")
    private void dump(CCustomizations cc) {
        for (int i = 0; i < cc.size(); i++) {
            Node n = cc.get(i).element;
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

    private void processPropertyModifiers(ClassOutline co, String item, String modifiers) {

        JFieldVar fieldVar = co.implClass.fields().get(item);
        if (fieldVar != null) {
            JMods mods = fieldVar.mods();
            setMods(mods, modifiers);
        } else {
            System.err.println(item + " not found.");
            return;
        }
    }

    private void processMethodModifiers(ClassOutline co, String item, String modifiers) {
        JMods mods = null;

        for (JMethod method : co.implClass.methods()) {
            if (item.equals(method.name())) {
                mods = method.mods();
                break;
            }
        }

        if (mods != null) {
            setMods(mods, modifiers);
        } else {
            System.err.println(item + " not found.");
        }
    }

    private void setMods(JMods mods, String modifiers) {
        String[] tokens = modifiers.split(" ");


    }

    private CCustomizations findMyCustomizations(CCustomizations cc, String name) {
        CCustomizations list = new CCustomizations();
        for (CPluginCustomization cpc : cc) {
            Element e = cpc.element;
            if (NAMESPACE_URI.equals(e.getNamespaceURI()) && e.getLocalName().equals(name)) {
                list.add(cpc);
            }
        }

        return list;

    }
}