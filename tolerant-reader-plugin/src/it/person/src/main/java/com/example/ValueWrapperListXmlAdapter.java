package com.example;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import com.example.person.ValueWrapper;

public class ValueWrapperListXmlAdapter extends XmlAdapter<List<ValueWrapper>, Hobbies> {

    @Override
    public Hobbies unmarshal(List<ValueWrapper> valueWrappers) throws Exception {
        Hobbies ret = new Hobbies();
        List<String> items = ret.getItems();
        for (ValueWrapper valueWrapper : valueWrappers) {
            items.add(valueWrapper.getText());
        }
        return ret;
    }

    @Override
    public List<ValueWrapper> marshal(Hobbies hobbies) throws Exception {
        List<ValueWrapper> ret = new ArrayList<ValueWrapper>();
        if (hobbies != null) {
            List<String> items = hobbies.getItems();
            for (String item : items) {
                ValueWrapper valueWrapper = new ValueWrapper();
                valueWrapper.setText(item);
                ret.add(valueWrapper);
            }
        }
        return ret;
    }
}
