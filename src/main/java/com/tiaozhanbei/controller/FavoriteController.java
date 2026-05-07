package com.tiaozhanbei.controller;

import com.tiaozhanbei.dto.ApiResponse;
import com.tiaozhanbei.dto.FavoriteRequest;
import com.tiaozhanbei.entity.Favorite;
import com.tiaozhanbei.service.FavoriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favorite")
public class FavoriteController {
    private static final Logger logger = LoggerFactory.getLogger(FavoriteController.class);

    private final FavoriteService favoriteService;

    @Autowired
    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping("/list/{userId}")
    public ApiResponse<List<Map<String, Object>>> getFavorites(@PathVariable Long userId) {
        logger.info("Getting favorites for user: {}", userId);
        try {
            List<Map<String, Object>> favorites = favoriteService.getUserFavorites(userId);
            return ApiResponse.success(favorites);
        } catch (Exception e) {
            logger.error("Get favorites failed", e);
            return ApiResponse.error("获取收藏列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/add/{userId}")
    public ApiResponse<Favorite> addFavorite(
            @PathVariable Long userId,
            @RequestBody FavoriteRequest request) {
        logger.info("Adding favorite for user: {}", userId);
        try {
            if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
                return ApiResponse.error("收藏标题不能为空");
            }

            Favorite favorite = favoriteService.addFavorite(userId, request);
            if (favorite == null) {
                return ApiResponse.error("已存在相同收藏");
            }
            return ApiResponse.success("收藏成功", favorite);
        } catch (Exception e) {
            logger.error("Add favorite failed", e);
            return ApiResponse.error("收藏失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/remove/{userId}/{favoriteId}")
    public ApiResponse<Void> removeFavorite(
            @PathVariable Long userId,
            @PathVariable Long favoriteId) {
        logger.info("Removing favorite: {} for user: {}", favoriteId, userId);
        try {
            boolean success = favoriteService.removeFavorite(userId, favoriteId);
            if (success) {
                return ApiResponse.success("移除成功", null);
            } else {
                return ApiResponse.error("收藏不存在或无权操作");
            }
        } catch (Exception e) {
            logger.error("Remove favorite failed", e);
            return ApiResponse.error("移除收藏失败: " + e.getMessage());
        }
    }
}
