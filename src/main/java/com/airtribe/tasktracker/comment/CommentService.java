package com.airtribe.tasktracker.comment;

import com.airtribe.tasktracker.notification.CommentAddedEvent;
import com.airtribe.tasktracker.task.Task;
import com.airtribe.tasktracker.task.TaskService;
import com.airtribe.tasktracker.user.User;
import com.airtribe.tasktracker.user.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TaskService taskService;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    public CommentService(CommentRepository commentRepository, TaskService taskService, UserService userService,
                           ApplicationEventPublisher eventPublisher) {
        this.commentRepository = commentRepository;
        this.taskService = taskService;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
    }

    public Comment addComment(UUID taskId, UUID authorId, String body) {
        Task task = taskService.getTaskForMember(taskId, authorId);
        User author = userService.findById(authorId);

        Comment comment = new Comment();
        comment.setTask(task);
        comment.setAuthor(author);
        comment.setBody(body);
        Comment saved = commentRepository.save(comment);

        if (task.getAssignee() != null && !task.getAssignee().getId().equals(authorId)) {
            eventPublisher.publishEvent(new CommentAddedEvent(
                    task.getId(), task.getTitle(), task.getTeam().getId(), task.getAssignee().getId(), author.getName()));
        }
        return saved;
    }

    public Page<Comment> listComments(UUID taskId, UUID requesterId, Pageable pageable) {
        taskService.getTaskForMember(taskId, requesterId);
        return commentRepository.findByTaskId(taskId, pageable);
    }
}
