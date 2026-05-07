package com.tiaozhanbei.controller;

import com.tiaozhanbei.dto.ApiResponse;
import com.tiaozhanbei.entity.DocumentTemplate;
import com.tiaozhanbei.service.DocumentTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/template")
public class DocumentTemplateController {
    private static final Logger logger = LoggerFactory.getLogger(DocumentTemplateController.class);

    private final DocumentTemplateService documentTemplateService;

    @Autowired
    public DocumentTemplateController(DocumentTemplateService documentTemplateService) {
        this.documentTemplateService = documentTemplateService;
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
    public ApiResponse<DocumentTemplate> getTemplateDetail(@PathVariable Long templateId) {
        logger.info("Getting template detail: {}", templateId);
        try {
            DocumentTemplate template = documentTemplateService.getTemplateById(templateId);
            if (template == null || template.getIsDeleted()) {
                return ApiResponse.error("模板不存在");
            }
            return ApiResponse.success(template);
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
}
