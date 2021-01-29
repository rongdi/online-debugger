package com.rdpaas.debugger.test.controller;

import com.rdpaas.debugger.test.bean.Person;
import com.rdpaas.debugger.test.service.TestService;
import com.rdpaas.debugger.test.utils.MyList;
import com.rdpaas.debugger.test.utils.MyMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/**
 * 被调试接口
 * @author rongdi
 * @date 2021/1/24
 */
@RestController
public class TestController {

    @Autowired
    private TestService testService;

    private Integer flag = 1;

    @RequestMapping("/test")
    public Person test(@RequestParam String name) throws Exception {
        Person ret = testService.getPerson(name);
        MyList list1 = new MyList();
        list1.addAll(Arrays.asList(1,2,3));
        MyList list2 = new MyList();
        list2.add(new Person("张三",20));
        MyMap map1 = new MyMap();
        map1.put("name","小明");
        MyMap map2 = new MyMap();
        map2.put("person",new Person("李四",30));
        return ret;
    }


}
