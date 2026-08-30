package com.airtribe.tasktracker.user;

import com.airtribe.tasktracker.common.exception.ConflictException;
import com.airtribe.tasktracker.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void createUserSavesAndReturnsUser() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.createUser("Alice", "a@b.com", "hashed");

        assertThat(result.getName()).isEqualTo("Alice");
        assertThat(result.getEmail()).isEqualTo("a@b.com");
        assertThat(result.getPasswordHash()).isEqualTo("hashed");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUserRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser("Alice", "a@b.com", "hashed"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("a@b.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    void findByIdReturnsUserWhenPresent() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThat(userService.findById(id)).isSameAs(user);
    }

    @Test
    void findByIdThrowsNotFoundWhenAbsent() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(id))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateProfileChangesNameAndAvatarAndSaves() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setName("Old Name");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.updateProfile(id, "New Name", "https://img/avatar.png");

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getAvatarUrl()).isEqualTo("https://img/avatar.png");
    }
}
