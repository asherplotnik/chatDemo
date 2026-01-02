package com.demoBank.chatDemo.controller;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaForwardController {

    @RequestMapping(value = {"/{path:^(?!api$)[^\\.]*}","/**/{path:^(?!api$)[^\\.]*}"})
    public String forward() {
        return "forward:/index.html";
    }
}