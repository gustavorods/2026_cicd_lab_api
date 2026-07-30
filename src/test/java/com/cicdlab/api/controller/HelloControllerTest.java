package com.cicdlab.api.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HelloControllerTest {
    @Test
    void shouldReturnHelloMessage() {
        HelloController controller = new HelloController("Hello CI/CD!");

        String response = controller.hello();

        assertEquals("Hello CI/CD! 🚀", response);
    }
}
