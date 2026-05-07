package com.tiaozhanbei.service;

import com.tiaozhanbei.dto.FavoriteRequest;
import com.tiaozhanbei.entity.Favorite;
import com.tiaozhanbei.repository.FavoriteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FavoriteService {
    private static final Logger logger = LoggerFactory.getLogger(FavoriteService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final FavoriteRepository favoriteRepository;

    @Autowired
    public FavoriteService(FavoriteRepository favoriteRepository) {
        this.favoriteRepository = favoriteRepository;
    }

    public List<Map<String, Object>> getUserFavorites(Long userId) {
        logger.info("Getting favorites for user: {}", userId);
        List<Favorite> favorites = favoriteRepository.findByUserIdAndIsDeletedFalseOrderByCreatedTimeDesc(userId);
        
        return favorites.stream().map(fav -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", fav.getId());
            map.put("title", fav.getTitle());
            map.put("desc", fav.getDescription());
            map.put("time", fav.getCreatedTime().format(DATE_FORMATTER));
            map.put("icon", fav.getIcon());
            return map;
        }).collect(Collectors.toList());
    }

    public Favorite addFavorite(Long userId, FavoriteRequest request) {
        logger.info("Adding favorite for user: {}, title: {}", userId, request.getTitle());
        
        if (request.getContentId() != null && request.getContentType() != null) {
            boolean exists = favoriteRepository.existsByUserIdAndContentIdAndContentTypeAndIsDeletedFalse(
                    userId, request.getContentId(), request.getContentType());
            if (exists) {
                logger.warn("Favorite already exists");
                return null;
            }
        }

        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setTitle(request.getTitle());
        favorite.setDescription(request.getDescription());
        favorite.setIcon(request.getIcon());
        favorite.setContentType(request.getContentType());
        favorite.setContentId(request.getContentId());

        return favoriteRepository.save(favorite);
    }

    public boolean removeFavorite(Long userId, Long favoriteId) {
        logger.info("Removing favorite: {} for user: {}", favoriteId, userId);
        Favorite favorite = favoriteRepository.findById(favoriteId).orElse(null);
        
        if (favorite == null || !favorite.getUserId().equals(userId)) {
            return false;
        }

        favorite.setIsDeleted(true);
        favoriteRepository.save(favorite);
        return true;
    }
}
