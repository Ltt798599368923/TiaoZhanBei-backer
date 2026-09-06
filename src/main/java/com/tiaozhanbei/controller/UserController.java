package com.tiaozhanbei.controller;

import com.tiaozhanbei.dto.ApiResponse;
import com.tiaozhanbei.dto.LoginRequest;
import com.tiaozhanbei.dto.LoginResponse;
import com.tiaozhanbei.entity.User;
import com.tiaozhanbei.service.FileStorageService;
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
    private final FileStorageService fileStorageService;

    @Autowired
    public UserController(UserService userService, FileStorageService fileStorageService) {
        this.userService = userService;
        this.fileStorageService = fileStorageService;
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

            return ApiResponse.success(toUserInfo(user));
        } catch (Exception e) {
            logger.error("Get user info failed", e);
            return ApiResponse.error("获取用户信息失败: " + e.getMessage());
        }
    }

    @PutMapping("/update/{userId}")
    public ApiResponse<Map<String, Object>> updateUser(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> request) {
        logger.info("Updating user: {}", userId);
        try {
            User user = userService.updateUser(
                    userId,
                    stringValue(request.get("nickname")),
                    stringValue(request.get("avatar")),
                    stringValue(request.get("phone"))
            );

            if (user != null && request.containsKey("notificationEnabled")) {
                Object preference = request.get("notificationEnabled");
                user.setNotificationEnabled(preference instanceof Boolean ? (Boolean) preference : Boolean.parseBoolean(String.valueOf(preference)));
                user = userService.save(user);
            }

            if (user == null) {
                return ApiResponse.error("用户不存在");
            }

            return ApiResponse.success("更新成功", toUserInfo(user));
        } catch (Exception e) {
            logger.error("Update user failed", e);
            return ApiResponse.error("更新用户信息失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/avatar/{userId}", consumes = "multipart/form-data")
    public ApiResponse<Map<String, Object>> uploadAvatar(@PathVariable Long userId,
                                                          @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            User user = userService.getUserById(userId);
            if (user == null) return ApiResponse.error("用户不存在");
            String storedPath = fileStorageService.storeAvatar(file);
            String fileName = java.nio.file.Paths.get(storedPath).getFileName().toString();
            user.setAvatar("/api/files/avatars/" + fileName);
            return ApiResponse.success("头像更新成功", toUserInfo(userService.save(user)));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            logger.error("Upload user avatar failed", e);
            return ApiResponse.error("头像上传失败，请稍后重试");
        }
    }

    private Map<String, Object> toUserInfo(User user) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());
        result.put("phone", user.getPhone());
        result.put("notificationEnabled", !Boolean.FALSE.equals(user.getNotificationEnabled()));
        return result;
    }

    private String stringValue(Object value) { return value == null ? null : String.valueOf(value); }
}
