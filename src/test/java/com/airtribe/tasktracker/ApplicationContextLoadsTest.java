package com.airtribe.tasktracker;

import org.junit.jupiter.api.Test;

class ApplicationContextLoadsTest extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // Proves the full Spring context (all controllers, services, repositories,
        // and the WebSocket/security config) wires together without a missing-bean
        // or circular-dependency failure.
    }
}
