package com.tiaozhanbei.controller;

import com.tiaozhanbei.dto.ApiResponse;
import com.tiaozhanbei.entity.ContentItem;
import com.tiaozhanbei.repository.ContentItemRepository;
import com.tiaozhanbei.service.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/content")
public class ContentController {
    private static final List<String> CONTENT_TYPES = Arrays.asList("article", "law", "book", "video");

    private final ContentItemRepository contentItemRepository;
    private final FileStorageService fileStorageService;

    public ContentController(ContentItemRepository contentItemRepository, FileStorageService fileStorageService) {
        this.contentItemRepository = contentItemRepository;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/{type}")
    public ApiResponse<List<ContentItem>> list(@PathVariable String type) {
        if (!CONTENT_TYPES.contains(type)) return ApiResponse.error("不支持的内容类型");
        return ApiResponse.success(contentItemRepository
                .findByContentTypeAndIsPublishedTrueAndIsDeletedFalseOrderByPublishedTimeDesc(type));
    }

    @GetMapping("/{type}/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String type, @PathVariable Long id) {
        if (!CONTENT_TYPES.contains(type)) return ApiResponse.error("不支持的内容类型");
        ContentItem item = contentItemRepository.findById(id).orElse(null);
        if (item == null || Boolean.TRUE.equals(item.getIsDeleted()) || !Boolean.TRUE.equals(item.getIsPublished())
                || !type.equals(item.getContentType())) {
            return ApiResponse.error("内容不存在或未发布");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("id", item.getId());
        result.put("contentType", item.getContentType());
        result.put("title", item.getTitle());
        result.put("summary", item.getSummary());
        result.put("content", item.getContent());
        result.put("sourceName", item.getSourceName());
        result.put("sourceUrl", item.getSourceUrl());
        result.put("coverUrl", item.getCoverUrl());
        result.put("fileName", item.getFileName());
        result.put("hasFile", item.getFilePath() != null && !item.getFilePath().trim().isEmpty());
        result.put("publishedTime", item.getPublishedTime());
        return ApiResponse.success(result);
    }

    @GetMapping("/{type}/{id}/file")
    public ResponseEntity<Resource> downloadFile(@PathVariable String type, @PathVariable Long id) throws Exception {
        // Articles, regulations and videos are always consumed in the mini program.
        // Only the legal-reading library exposes optional supplementary files.
        if (!"book".equals(type)) return ResponseEntity.notFound().build();
        ContentItem item = contentItemRepository.findById(id).orElse(null);
        if (item == null || Boolean.TRUE.equals(item.getIsDeleted()) || !Boolean.TRUE.equals(item.getIsPublished())
                || !type.equals(item.getContentType()) || item.getFilePath() == null || item.getFilePath().trim().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return fileStorageService.download(item.getFilePath(), item.getFileName());
    }
}
