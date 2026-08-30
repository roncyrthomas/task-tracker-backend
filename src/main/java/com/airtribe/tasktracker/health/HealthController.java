package com.airtribe.tasktracker.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        // LinkedHashMap, not Map.of(...) — Map.of() throws NullPointerException
        // on a null value, and this shape needs "error"/"meta" to serialize as null.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", Map.of("status", "UP"));
        body.put("error", null);
        body.put("meta", null);
        return body;
    }
}
