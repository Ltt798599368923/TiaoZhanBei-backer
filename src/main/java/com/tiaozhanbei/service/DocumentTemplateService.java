package com.tiaozhanbei.service;

import com.tiaozhanbei.entity.DocumentTemplate;
import com.tiaozhanbei.repository.DocumentTemplateRepository;
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
public class DocumentTemplateService {
    private static final Logger logger = LoggerFactory.getLogger(DocumentTemplateService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DocumentTemplateRepository documentTemplateRepository;

    @Autowired
    public DocumentTemplateService(DocumentTemplateRepository documentTemplateRepository) {
        this.documentTemplateRepository = documentTemplateRepository;
    }

    public List<Map<String, Object>> getAllTemplates() {
        logger.info("Getting all document templates");
        List<DocumentTemplate> templates = documentTemplateRepository.findByIsDeletedFalseOrderByCreatedTimeDesc();
        
        return templates.stream().map(template -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", template.getId());
            map.put("title", template.getTitle());
            map.put("description", template.getDescription());
            map.put("category", template.getCategory());
            map.put("downloadCount", template.getDownloadCount());
            map.put("time", template.getCreatedTime().format(DATE_FORMATTER));
            return map;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getTemplatesByCategory(String category) {
        logger.info("Getting document templates by category: {}", category);
        List<DocumentTemplate> templates = documentTemplateRepository.findByCategoryAndIsDeletedFalseOrderByCreatedTimeDesc(category);
        
        return templates.stream().map(template -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", template.getId());
            map.put("title", template.getTitle());
            map.put("description", template.getDescription());
            map.put("category", template.getCategory());
            map.put("downloadCount", template.getDownloadCount());
            map.put("time", template.getCreatedTime().format(DATE_FORMATTER));
            return map;
        }).collect(Collectors.toList());
    }

    public DocumentTemplate getTemplateById(Long templateId) {
        return documentTemplateRepository.findById(templateId).orElse(null);
    }

    public DocumentTemplate incrementDownloadCount(Long templateId) {
        DocumentTemplate template = documentTemplateRepository.findById(templateId).orElse(null);
        if (template != null && !template.getIsDeleted()) {
            template.setDownloadCount(template.getDownloadCount() + 1);
            return documentTemplateRepository.save(template);
        }
        return null;
    }

    public DocumentTemplate createTemplate(DocumentTemplate template) {
        logger.info("Creating document template: {}", template.getTitle());
        return documentTemplateRepository.save(template);
    }
}
