package com.airtribe.tasktracker.comment;

import com.airtribe.tasktracker.comment.dto.CommentResponse;
import com.airtribe.tasktracker.comment.dto.CreateCommentRequest;
import com.airtribe.tasktracker.common.web.ApiResponse;
import com.airtribe.tasktracker.common.web.PageMeta;
import com.airtribe.tasktracker.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks/{taskId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CommentResponse>> create(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @PathVariable UUID taskId,
                                                                 @Valid @RequestBody CreateCommentRequest request) {
        Comment comment = commentService.addComment(taskId, principal.getUserId(), request.body());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(CommentResponse.from(comment)));
    }

    @GetMapping
    public ApiResponse<List<CommentResponse>> list(@AuthenticationPrincipal UserPrincipal principal,
                                                     @PathVariable UUID taskId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int limit) {
        PageRequest pageRequest = PageRequest.of(page, limit, Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<Comment> result = commentService.listComments(taskId, principal.getUserId(), pageRequest);
        List<CommentResponse> data = result.getContent().stream().map(CommentResponse::from).toList();
        return ApiResponse.ok(data, new PageMeta(page, limit, result.getTotalElements()));
    }
}
