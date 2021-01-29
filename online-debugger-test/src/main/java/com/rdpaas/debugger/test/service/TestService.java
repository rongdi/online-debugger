package com.rdpaas.debugger.test.service;

import com.rdpaas.debugger.test.bean.Person;
import org.springframework.stereotype.Service;

@Service
public class TestService {

    public Person getPerson(String name) {
        Person p = new Person();
        p.setAge(20);
        p.setName(name);
        return p;
    }

}
