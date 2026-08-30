package com.airtribe.tasktracker.common.web;

import com.airtribe.tasktracker.common.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(GlobalExceptionHandlerTest.ThrowingController.class)
class GlobalExceptionHandlerTest {

    @RestController
    static class ThrowingController {
        @GetMapping("/api/test/not-found")
        void notFound() { throw new NotFoundException("thing missing"); }

        @GetMapping("/api/test/forbidden")
        void forbidden() { throw new ForbiddenException("nope"); }

        @GetMapping("/api/test/conflict")
        void conflict() { throw new ConflictException("dup"); }

        @GetMapping("/api/test/bad-request")
        void badRequest() { throw new BadRequestException("bad input"); }

        @GetMapping("/api/test/boom")
        void boom() { throw new RuntimeException("secret internal detail"); }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void notFoundMapsTo404() throws Exception {
        mockMvc.perform(get("/api/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").value("thing missing"));
    }

    @Test
    void forbiddenMapsTo403() throws Exception {
        mockMvc.perform(get("/api/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message").value("nope"));
    }

    @Test
    void conflictMapsTo409() throws Exception {
        mockMvc.perform(get("/api/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("dup"));
    }

    @Test
    void badRequestMapsTo400() throws Exception {
        mockMvc.perform(get("/api/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("bad input"));
    }

    @Test
    void unexpectedExceptionMapsTo500WithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/api/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.error.message", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret internal detail"))));
    }
}
