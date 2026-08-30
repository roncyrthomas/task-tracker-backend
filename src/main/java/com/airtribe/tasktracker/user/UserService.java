package com.airtribe.tasktracker.user;

import com.airtribe.tasktracker.common.exception.ConflictException;
import com.airtribe.tasktracker.common.exception.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createUser(String name, String email, String passwordHash) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with email " + email + " already exists.");
        }
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        return userRepository.save(user);
    }

    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found."));
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found."));
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public User updateProfile(UUID id, String name, String avatarUrl) {
        User user = findById(id);
        user.setName(name);
        user.setAvatarUrl(avatarUrl);
        return userRepository.save(user);
    }
}
