package com.example;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import com.example.person.Name;
import com.example.person.ValueWrapper;

public class ValueWrapperXmlAdapter extends XmlAdapter<ValueWrapper, String> {

    @Override
    public String unmarshal(ValueWrapper valueWrapper) throws Exception {
        return valueWrapper.getText();
    }

    @Override
    public ValueWrapper marshal(String string) throws Exception {
        ValueWrapper valueWrapper = new ValueWrapper();
        valueWrapper.setText(string);
        return valueWrapper;
    }

}
