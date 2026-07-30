package com.pnm.docmind.config;

import org.springframework.stereotype.Component;

@Component
public class FlywayTest {

    public FlywayTest() {
        System.out.println("Flyway bean loaded");
    }

}
