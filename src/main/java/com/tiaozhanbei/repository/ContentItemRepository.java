package com.tiaozhanbei.repository;

import com.tiaozhanbei.entity.ContentItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentItemRepository extends JpaRepository<ContentItem, Long> {
    List<ContentItem> findByContentTypeAndIsPublishedTrueAndIsDeletedFalseOrderByPublishedTimeDesc(String contentType);
    List<ContentItem> findByContentTypeAndIsDeletedFalseOrderByCreatedTimeDesc(String contentType);
}
