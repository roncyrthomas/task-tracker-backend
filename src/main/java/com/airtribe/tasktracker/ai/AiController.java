package com.airtribe.tasktracker.ai;

import com.airtribe.tasktracker.ai.dto.GenerateDescriptionRequest;
import com.airtribe.tasktracker.ai.dto.GenerateDescriptionResponse;
import com.airtribe.tasktracker.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/generate-description")
    public ApiResponse<GenerateDescriptionResponse> generate(@Valid @RequestBody GenerateDescriptionRequest request) {
        String description = aiService.generateDescription(request.title(), request.notes());
        return ApiResponse.ok(new GenerateDescriptionResponse(description));
    }
}
