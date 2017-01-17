package com.example;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import com.example.person.Name;

public class NameXmlAdapter extends XmlAdapter<Name, String> {

    @Override
    public String unmarshal(Name name) throws Exception {
        return name.getFirstName() + " " + (name.getMiddleInitial() != null ? name.getMiddleInitial() + " " : "")
                + name.getLastName();
    }

    @Override
    public Name marshal(String nameString) throws Exception {
      String[] split = nameString.split(" ");
      System.out.println(split[0]);
      System.out.println(split[1]);
      Name name = new Name();
      name.setFirstName(split[0]);
      if (split.length == 3) {
          name.setMiddleInitial(split[1]);
      }
      name.setLastName(split[split.length - 1]);
      return name;
    }

}
