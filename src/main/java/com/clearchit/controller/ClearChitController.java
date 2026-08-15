package com.clearchit.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClearChitController {

    @GetMapping("/api/health")
    public String health() {
        return "ClearChit backend is running";
    }
}