package com.tiaozhanbei.repository;

import com.tiaozhanbei.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUserIdAndIsDeletedFalseOrderByCreatedTimeDesc(Long userId);
    boolean existsByUserIdAndContentIdAndContentTypeAndIsDeletedFalse(Long userId, Long contentId, String contentType);
}
