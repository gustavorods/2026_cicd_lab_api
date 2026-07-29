package com.cicdlab.api.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HelloControllerTest {
    @Test
    void shouldReturnHelloMessage() {
        HelloController controller = new HelloController();

        String response = controller.hello();

        assertEquals("Hello CI/CD", response);
    }

    @Test
    void shouldReturnOlaMessage() {
        HelloController controller = new HelloController();

        String response = controller.ola();

        assertEquals("Olá mundo!!", response);
    }
}
