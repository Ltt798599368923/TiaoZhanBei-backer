package com.tiaozhanbei.repository;

import com.tiaozhanbei.entity.ContentItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentItemRepository extends JpaRepository<ContentItem, Long> {
    List<ContentItem> findByContentTypeAndIsPublishedTrueAndIsDeletedFalseOrderByPublishedTimeDesc(String contentType);
    List<ContentItem> findByContentTypeAndIsDeletedFalseOrderByCreatedTimeDesc(String contentType);
    Optional<ContentItem> findFirstByContentTypeAndImportKey(String contentType, String importKey);
}
