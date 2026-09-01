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
@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final WeChatLoginService weChatLoginService;
    private final UserSessionService userSessionService;

    @Autowired
    public UserService(UserRepository userRepository, WeChatLoginService weChatLoginService,
                       UserSessionService userSessionService) {
        this.userRepository = userRepository;
        this.weChatLoginService = weChatLoginService;
        this.userSessionService = userSessionService;
    }

    public LoginResponse login(LoginRequest request) {
        logger.info("User login attempt");

        String openId = weChatLoginService.resolveOpenId(request.getCode(), request.getNickname());

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

        String token = userSessionService.createSession(user);
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
}
