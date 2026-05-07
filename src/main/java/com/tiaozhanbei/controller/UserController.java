package com.tiaozhanbei.controller;

import com.tiaozhanbei.dto.ApiResponse;
import com.tiaozhanbei.dto.LoginRequest;
import com.tiaozhanbei.dto.LoginResponse;
import com.tiaozhanbei.entity.User;
import com.tiaozhanbei.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        logger.info("Login request received");
        try {
            LoginResponse response = userService.login(request);
            return ApiResponse.success("登录成功", response);
        } catch (Exception e) {
            logger.error("Login failed", e);
            return ApiResponse.error("登录失败: " + e.getMessage());
        }
    }

    @GetMapping("/info/{userId}")
    public ApiResponse<Map<String, Object>> getUserInfo(@PathVariable Long userId) {
        logger.info("Getting user info for: {}", userId);
        try {
            User user = userService.getUserById(userId);
            if (user == null) {
                return ApiResponse.error("用户不存在");
            }

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("nickname", user.getNickname());
            userInfo.put("avatar", user.getAvatar());
            userInfo.put("phone", user.getPhone());

            return ApiResponse.success(userInfo);
        } catch (Exception e) {
            logger.error("Get user info failed", e);
            return ApiResponse.error("获取用户信息失败: " + e.getMessage());
        }
    }

    @PutMapping("/update/{userId}")
    public ApiResponse<Map<String, Object>> updateUser(
            @PathVariable Long userId,
            @RequestBody Map<String, String> request) {
        logger.info("Updating user: {}", userId);
        try {
            User user = userService.updateUser(
                    userId,
                    request.get("nickname"),
                    request.get("avatar"),
                    request.get("phone")
            );

            if (user == null) {
                return ApiResponse.error("用户不存在");
            }

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("nickname", user.getNickname());
            userInfo.put("avatar", user.getAvatar());
            userInfo.put("phone", user.getPhone());

            return ApiResponse.success("更新成功", userInfo);
        } catch (Exception e) {
            logger.error("Update user failed", e);
            return ApiResponse.error("更新用户信息失败: " + e.getMessage());
        }
    }
}
