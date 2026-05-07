package com.tiaozhanbei.service;

import com.tiaozhanbei.dto.LoginRequest;
import com.tiaozhanbei.dto.LoginResponse;
import com.tiaozhanbei.entity.User;
import com.tiaozhanbei.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public LoginResponse login(LoginRequest request) {
        logger.info("User login attempt, code: {}", request.getCode());

        String openId = "mock_openid_" + UUID.randomUUID().toString().substring(0, 8);

        Optional<User> existingUser = userRepository.findByOpenId(openId);
        User user;

        if (existingUser.isPresent()) {
            user = existingUser.get();
            if (request.getNickname() != null || request.getAvatar() != null) {
                if (request.getNickname() != null) {
                    user.setNickname(request.getNickname());
                }
                if (request.getAvatar() != null) {
                    user.setAvatar(request.getAvatar());
                }
                user = userRepository.save(user);
            }
        } else {
            user = new User();
            user.setOpenId(openId);
            user.setNickname(request.getNickname() != null ? request.getNickname() : "用户" + System.currentTimeMillis() % 10000);
            user.setAvatar(request.getAvatar());
            user = userRepository.save(user);
        }

        String token = generateToken(user);
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId(),
                user.getNickname(),
                user.getAvatar(),
                user.getPhone()
        );

        logger.info("User login successful, userId: {}", user.getId());
        return new LoginResponse(token, userInfo);
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId).orElse(null);
    }

    public User updateUser(Long userId, String nickname, String avatar, String phone) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (!userOpt.isPresent()) {
            return null;
        }

        User user = userOpt.get();
        if (nickname != null) {
            user.setNickname(nickname);
        }
        if (avatar != null) {
            user.setAvatar(avatar);
        }
        if (phone != null) {
            user.setPhone(phone);
        }

        return userRepository.save(user);
    }

    private String generateToken(User user) {
        return "token_" + user.getId() + "_" + UUID.randomUUID().toString();
    }
}
