package com.airtribe.tasktracker.user;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndFindsByEmail() {
        User user = new User();
        user.setName("Bob");
        user.setEmail("bob@example.com");
        user.setPasswordHash("hashed");
        userRepository.saveAndFlush(user);

        assertThat(userRepository.findByEmail("bob@example.com")).isPresent();
        assertThat(userRepository.existsByEmail("bob@example.com")).isTrue();
    }

    @Test
    void rejectsDuplicateEmailAtDbLevel() {
        User first = new User();
        first.setName("Carl");
        first.setEmail("carl@example.com");
        first.setPasswordHash("hashed");
        userRepository.saveAndFlush(first);

        User duplicate = new User();
        duplicate.setName("Carl 2");
        duplicate.setEmail("carl@example.com");
        duplicate.setPasswordHash("hashed2");

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
