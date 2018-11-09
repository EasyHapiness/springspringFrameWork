package com.gupaoedu.demo.mvc.action;

import com.gupaoedu.demo.service.IDemoService;
import com.gupaoedu.mvcframework.annotation.GPAutowired;
import com.gupaoedu.mvcframework.annotation.GPController;
import com.gupaoedu.mvcframework.annotation.GPRequestMapping;
import com.gupaoedu.mvcframework.annotation.GPRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@GPController
@GPRequestMapping("/demo")
public class DemoAction {

    @GPAutowired
    IDemoService iDemoService;

    @GPRequestMapping("/query")
    public void query(HttpServletRequest req, HttpServletResponse resp, @GPRequestParam("name") String name){

        String result = iDemoService.get(name);

        try {
            resp.getWriter().write(result);
        }catch (Exception e){

        }
    }
}
