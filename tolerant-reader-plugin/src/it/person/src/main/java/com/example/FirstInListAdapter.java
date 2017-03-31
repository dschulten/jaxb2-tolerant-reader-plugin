package com.example;

import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.adapters.XmlAdapter;

public class FirstInListAdapter extends XmlAdapter<List<Integer>, Integer> {

    @Override
    public List<Integer> marshal(Integer s) throws Exception {
        return Collections.singletonList(s);
    }

    @Override
    public Integer unmarshal(List<Integer> list) throws Exception {
        return list == null || list.isEmpty() ? null : list.get(0);
    }

}
