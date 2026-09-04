package com.tiaozhanbei.controller;

import com.tiaozhanbei.dto.ApiResponse;
import com.tiaozhanbei.entity.DocumentTemplate;
import com.tiaozhanbei.service.DocumentTemplateService;
import com.tiaozhanbei.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/template")
public class DocumentTemplateController {
    private static final Logger logger = LoggerFactory.getLogger(DocumentTemplateController.class);

    private final DocumentTemplateService documentTemplateService;
    private final FileStorageService fileStorageService;

    @Autowired
    public DocumentTemplateController(DocumentTemplateService documentTemplateService, FileStorageService fileStorageService) {
        this.documentTemplateService = documentTemplateService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> getAllTemplates() {
        logger.info("Getting all document templates");
        try {
            List<Map<String, Object>> templates = documentTemplateService.getAllTemplates();
            return ApiResponse.success(templates);
        } catch (Exception e) {
            logger.error("Get templates failed", e);
            return ApiResponse.error("获取文书模板列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/list/{category}")
    public ApiResponse<List<Map<String, Object>>> getTemplatesByCategory(@PathVariable String category) {
        logger.info("Getting document templates by category: {}", category);
        try {
            List<Map<String, Object>> templates = documentTemplateService.getTemplatesByCategory(category);
            return ApiResponse.success(templates);
        } catch (Exception e) {
            logger.error("Get templates by category failed", e);
            return ApiResponse.error("获取文书模板列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/detail/{templateId}")
    public ApiResponse<Map<String, Object>> getTemplateDetail(@PathVariable Long templateId) {
        logger.info("Getting template detail: {}", templateId);
        try {
            DocumentTemplate template = documentTemplateService.getTemplateById(templateId);
            if (template == null || template.getIsDeleted()) {
                return ApiResponse.error("模板不存在");
            }
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("id", template.getId());
            result.put("title", template.getTitle());
            result.put("description", template.getDescription());
            result.put("category", template.getCategory());
            result.put("content", template.getContent());
            result.put("fileName", template.getFileName());
            result.put("hasFile", template.getFilePath() != null && !template.getFilePath().trim().isEmpty());
            result.put("downloadCount", template.getDownloadCount());
            return ApiResponse.success(result);
        } catch (Exception e) {
            logger.error("Get template detail failed", e);
            return ApiResponse.error("获取模板详情失败: " + e.getMessage());
        }
    }

    @PostMapping("/download/{templateId}")
    public ApiResponse<DocumentTemplate> downloadTemplate(@PathVariable Long templateId) {
        logger.info("Downloading template: {}", templateId);
        try {
            DocumentTemplate template = documentTemplateService.incrementDownloadCount(templateId);
            if (template == null) {
                return ApiResponse.error("模板不存在");
            }
            return ApiResponse.success("下载成功", template);
        } catch (Exception e) {
            logger.error("Download template failed", e);
            return ApiResponse.error("下载失败: " + e.getMessage());
        }
    }

    @GetMapping("/file/{templateId}")
    public ResponseEntity<Resource> downloadTemplateFile(@PathVariable Long templateId) throws Exception {
        DocumentTemplate template = documentTemplateService.incrementDownloadCount(templateId);
        if (template == null || Boolean.TRUE.equals(template.getIsDeleted()) || template.getFilePath() == null) {
            return ResponseEntity.notFound().build();
        }
        return fileStorageService.download(template.getFilePath(), template.getFileName());
    }
}
