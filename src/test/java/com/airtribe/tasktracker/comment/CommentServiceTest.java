package com.airtribe.tasktracker.comment;

import com.airtribe.tasktracker.common.exception.ForbiddenException;
import com.airtribe.tasktracker.task.Task;
import com.airtribe.tasktracker.task.TaskService;
import com.airtribe.tasktracker.user.User;
import com.airtribe.tasktracker.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private TaskService taskService;
    @Mock private UserService userService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private CommentService service() {
        return new CommentService(commentRepository, taskService, userService, eventPublisher);
    }

    @Test
    void addCommentSavesAndReturnsComment() {
        UUID taskId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Task task = new Task();
        task.setId(taskId);
        User author = new User();
        author.setId(authorId);
        when(taskService.getTaskForMember(taskId, authorId)).thenReturn(task);
        when(userService.findById(authorId)).thenReturn(author);
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        Comment comment = service().addComment(taskId, authorId, "Looks good to me");

        assertThat(comment.getBody()).isEqualTo("Looks good to me");
        assertThat(comment.getTask()).isSameAs(task);
        assertThat(comment.getAuthor()).isSameAs(author);
    }

    @Test
    void addCommentPropagatesForbiddenFromTaskLookup() {
        UUID taskId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        when(taskService.getTaskForMember(taskId, authorId))
                .thenThrow(new ForbiddenException("You are not a member of this team."));

        assertThatThrownBy(() -> service().addComment(taskId, authorId, "Hi"))
                .isInstanceOf(ForbiddenException.class);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void listCommentsRequiresTaskMembershipThenDelegatesToRepository() {
        UUID taskId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        Task task = new Task();
        task.setId(taskId);
        when(taskService.getTaskForMember(taskId, requesterId)).thenReturn(task);
        Page<Comment> page = Page.empty();
        when(commentRepository.findByTaskId(taskId, PageRequest.of(0, 20))).thenReturn(page);

        Page<Comment> result = service().listComments(taskId, requesterId, PageRequest.of(0, 20));

        assertThat(result).isEqualTo(page);
    }
}
