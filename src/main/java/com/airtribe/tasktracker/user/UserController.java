package com.airtribe.tasktracker.user;

import com.airtribe.tasktracker.common.web.ApiResponse;
import com.airtribe.tasktracker.security.UserPrincipal;
import com.airtribe.tasktracker.user.dto.UpdateProfileRequest;
import com.airtribe.tasktracker.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(UserResponse.from(userService.findById(principal.getUserId())));
    }

    @PutMapping("/me")
    public ApiResponse<UserResponse> updateMe(@AuthenticationPrincipal UserPrincipal principal,
                                               @Valid @RequestBody UpdateProfileRequest request) {
        User updated = userService.updateProfile(principal.getUserId(), request.name(), request.avatarUrl());
        return ApiResponse.ok(UserResponse.from(updated));
    }
}
