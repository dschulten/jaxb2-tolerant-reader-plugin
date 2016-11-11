package de.escalon.xml.xjc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.sun.tools.xjc.model.CClassInfo;
import com.sun.tools.xjc.model.CPropertyInfo;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.xml.xsom.XSAnnotation;
import com.sun.xml.xsom.XSAttGroupDecl;
import com.sun.xml.xsom.XSAttributeDecl;
import com.sun.xml.xsom.XSAttributeUse;
import com.sun.xml.xsom.XSComplexType;
import com.sun.xml.xsom.XSContentType;
import com.sun.xml.xsom.XSElementDecl;
import com.sun.xml.xsom.XSFacet;
import com.sun.xml.xsom.XSIdentityConstraint;
import com.sun.xml.xsom.XSModelGroup;
import com.sun.xml.xsom.XSModelGroupDecl;
import com.sun.xml.xsom.XSNotation;
import com.sun.xml.xsom.XSParticle;
import com.sun.xml.xsom.XSSchema;
import com.sun.xml.xsom.XSSimpleType;
import com.sun.xml.xsom.XSWildcard;
import com.sun.xml.xsom.XSXPath;
import com.sun.xml.xsom.impl.util.SchemaWriter;
import com.sun.xml.xsom.visitor.XSTermVisitor;
import com.sun.xml.xsom.visitor.XSVisitor;

import de.escalon.xml.xjc.TolerantReaderPlugin.BeanInclusion;
import de.escalon.xml.xjc.TolerantReaderPlugin.BeanInclusions;

/**
 * Access original schema to find out the correct property order.
 * 
 * 
 * @author xs47xsd
 * @see SchemaWriter for traversal method
 */
public class PropOrderVisitor implements XSVisitor {

    private ClassOutline classOutline;
    private Set<String> propertiesToKeep;
    private List<String> propOrderList = new ArrayList<String>();
    private BeanInclusions beanInclusions;

    public PropOrderVisitor(ClassOutline classOutline, BeanInclusions beanInclusions, Set<String> propertiesToKeep) {
        this.classOutline = classOutline;
        this.beanInclusions = beanInclusions;
        this.propertiesToKeep = propertiesToKeep;
    }

    public List<String> getPropOrderList() {
        return propOrderList;
    }

    public void simpleType(XSSimpleType simpleType) {
//        System.out.println(simpleType);
    }

    public void particle(XSParticle particle) {
        final String extraAtts = "";
        particle.getTerm()
            .visit(new XSTermVisitor() {
                public void elementDecl(XSElementDecl decl) {
                    if (decl.isLocal())
                        PropOrderVisitor.this.elementDecl(decl, extraAtts);
                    else {
                        // element reference
//                        System.out.println(MessageFormat.format("<element ref=\"'{'{0}'}'{1}\"{2}/>",
//                                decl.getTargetNamespace(),
//                                decl.getName(),
//                                extraAtts));
                    }
                }

                public void modelGroupDecl(XSModelGroupDecl decl) {
                    // group reference
//                    System.out.println(MessageFormat.format("<group ref=\"'{'{0}'}'{1}\"{2}/>",
//                            decl.getTargetNamespace(),
//                            decl.getName(),
//                            extraAtts));
                }

                public void modelGroup(XSModelGroup group) {
                    PropOrderVisitor.this.modelGroup(group, extraAtts);
                }

                public void wildcard(XSWildcard wc) {
                    // PropOrderVisitor.this.wildcard("any",wc,extraAtts);
                }
            });
    }

    private void elementDecl(XSElementDecl elementDecl, String extraAtts) {

        if (elementDecl.getForm() != null) {
            extraAtts += " form=\"" + (elementDecl.getForm() ? "qualified" : "unqualified") + "\"";
        }

        String elementName = StringHelper.capitalize(elementDecl.getName());
        CClassInfo classInfo = classOutline.target;
        List<CPropertyInfo> propertiesLookup = classInfo.getProperties();
        for (CPropertyInfo cPropertyInfo : propertiesLookup) {
            // cPropertyInfo knows if it is an element, attr etc. by its subtype
            String propertyPrivateName = cPropertyInfo.getName(false);
            if (cPropertyInfo.getName(true)
                .equals(elementName) && propertiesToKeep.contains(propertyPrivateName)) {

                BeanInclusion beanInclusion = beanInclusions.getBeanInclusion(classInfo);
                if (beanInclusion != null) {
                    String propertyAlias = beanInclusion.getPropertyAlias(propertyPrivateName);
                    if (propertyAlias != null) {
                        propOrderList.add(propertyAlias);
                    } else {
                        propOrderList.add(propertyPrivateName);
                    }
                }
                break;
            }
        }
    }

    public void elementDecl(XSElementDecl elementDecl) {
        elementDecl(elementDecl, "");
    }

    public void xpath(XSXPath xp) {

    }

    public void schema(XSSchema schema) {

    }

    public void notation(XSNotation notation) {

    }

    public void identityConstraint(XSIdentityConstraint decl) {

    }

    public void facet(XSFacet facet) {

    }

    public void complexType(XSComplexType type) {

    }

    public void attributeUse(XSAttributeUse use) {

    }

    public void attributeDecl(XSAttributeDecl decl) {

    }

    public void attGroupDecl(XSAttGroupDecl decl) {

    }

    public void annotation(XSAnnotation ann) {

    }

    public void wildcard(XSWildcard wc) {

    }

    public void modelGroupDecl(XSModelGroupDecl decl) {

    }

    public void modelGroup(XSModelGroup group) {
        modelGroup(group, "");
    }

    private void modelGroup(XSModelGroup group, String extraAtts) {
        final int len = group.getSize();
        for (int i = 0; i < len; i++)
            particle(group.getChild(i));
    }

    public void empty(XSContentType empty) {

    }
}