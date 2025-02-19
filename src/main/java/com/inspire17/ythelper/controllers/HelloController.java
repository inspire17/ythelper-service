package com.inspire17.ythelper.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {
    @GetMapping("/hello")
    public String authenticated_hello() {
        return "Welcome";
    }
}
