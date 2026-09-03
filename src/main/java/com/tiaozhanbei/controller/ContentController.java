package com.tiaozhanbei.controller;

import com.tiaozhanbei.dto.ApiResponse;
import com.tiaozhanbei.entity.ContentItem;
import com.tiaozhanbei.repository.ContentItemRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/content")
public class ContentController {
    private static final List<String> CONTENT_TYPES = Arrays.asList("article", "law", "book", "video");

    private final ContentItemRepository contentItemRepository;

    public ContentController(ContentItemRepository contentItemRepository) {
        this.contentItemRepository = contentItemRepository;
    }

    @GetMapping("/{type}")
    public ApiResponse<List<ContentItem>> list(@PathVariable String type) {
        if (!CONTENT_TYPES.contains(type)) return ApiResponse.error("不支持的内容类型");
        return ApiResponse.success(contentItemRepository
                .findByContentTypeAndIsPublishedTrueAndIsDeletedFalseOrderByPublishedTimeDesc(type));
    }

    @GetMapping("/{type}/{id}")
    public ApiResponse<ContentItem> detail(@PathVariable String type, @PathVariable Long id) {
        if (!CONTENT_TYPES.contains(type)) return ApiResponse.error("不支持的内容类型");
        ContentItem item = contentItemRepository.findById(id).orElse(null);
        if (item == null || Boolean.TRUE.equals(item.getIsDeleted()) || !Boolean.TRUE.equals(item.getIsPublished())
                || !type.equals(item.getContentType())) {
            return ApiResponse.error("内容不存在或未发布");
        }
        return ApiResponse.success(item);
    }
}
