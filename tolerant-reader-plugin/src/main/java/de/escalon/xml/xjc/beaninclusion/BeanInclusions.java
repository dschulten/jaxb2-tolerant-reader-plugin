package de.escalon.xml.xjc.beaninclusion;

import com.sun.tools.xjc.model.CClassInfo;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class BeanInclusions implements Iterable<List<BeanInclusion>> {
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
