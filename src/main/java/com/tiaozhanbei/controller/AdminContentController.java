package com.tiaozhanbei.controller;

import com.tiaozhanbei.dto.ApiResponse;
import com.tiaozhanbei.entity.ContentItem;
import com.tiaozhanbei.entity.Lawyer;
import com.tiaozhanbei.repository.ContentItemRepository;
import com.tiaozhanbei.repository.LawyerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminContentController {
    private static final List<String> CONTENT_TYPES = Arrays.asList("article", "law", "book", "video");

    @Value("${admin.token:}")
    private String adminToken;

    private final ContentItemRepository contentItemRepository;
    private final LawyerRepository lawyerRepository;

    public AdminContentController(ContentItemRepository contentItemRepository, LawyerRepository lawyerRepository) {
        this.contentItemRepository = contentItemRepository;
        this.lawyerRepository = lawyerRepository;
    }

    private boolean authorized(String token) {
        if (adminToken != null && !adminToken.trim().isEmpty() && adminToken.trim().equals(token)) return true;
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return false;
        HttpServletRequest request = attributes.getRequest();
        HttpSession session = request.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(AdminPageController.ADMIN_SESSION_ATTRIBUTE));
    }

    private <T> ApiResponse<T> forbidden() {
        return ApiResponse.error(403, "管理员认证失败");
    }

    @GetMapping("/content")
    public ApiResponse<List<ContentItem>> contentList(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                                       @RequestParam String type) {
        if (!authorized(token)) return forbidden();
        if (!CONTENT_TYPES.contains(type)) return ApiResponse.error("不支持的内容类型");
        return ApiResponse.success(contentItemRepository.findByContentTypeAndIsDeletedFalseOrderByCreatedTimeDesc(type));
    }

    @GetMapping("/content/{id}")
    public ApiResponse<ContentItem> contentDetail(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                                   @PathVariable Long id) {
        if (!authorized(token)) return forbidden();
        ContentItem item = contentItemRepository.findById(id).orElse(null);
        if (item == null || Boolean.TRUE.equals(item.getIsDeleted())) return ApiResponse.error("内容不存在");
        return ApiResponse.success(item);
    }

    @PostMapping("/content")
    public ApiResponse<ContentItem> createContent(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                                    @RequestBody ContentItem body) {
        if (!authorized(token)) return forbidden();
        if (body == null || !CONTENT_TYPES.contains(body.getContentType()) || isBlank(body.getTitle())) {
            return ApiResponse.error("内容类型和标题不能为空");
        }
        body.setId(null);
        body.setIsDeleted(false);
        body.setCreatedTime(LocalDateTime.now());
        if (Boolean.TRUE.equals(body.getIsPublished()) && body.getPublishedTime() == null) {
            body.setPublishedTime(LocalDateTime.now());
        }
        return ApiResponse.success("创建成功", contentItemRepository.save(body));
    }

    @PutMapping("/content/{id}")
    public ApiResponse<ContentItem> updateContent(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                                   @PathVariable Long id, @RequestBody ContentItem body) {
        if (!authorized(token)) return forbidden();
        if (body == null) return ApiResponse.error("请求内容不能为空");
        ContentItem item = contentItemRepository.findById(id).orElse(null);
        if (item == null || item.getIsDeleted()) return ApiResponse.error("内容不存在");
        if (body.getContentType() != null && !CONTENT_TYPES.contains(body.getContentType())) return ApiResponse.error("不支持的内容类型");
        if (body.getContentType() != null) item.setContentType(body.getContentType());
        if (body.getTitle() != null) item.setTitle(body.getTitle());
        if (isBlank(item.getTitle())) return ApiResponse.error("标题不能为空");
        if (body.getSummary() != null) item.setSummary(body.getSummary());
        if (body.getContent() != null) item.setContent(body.getContent());
        if (body.getSourceName() != null) item.setSourceName(body.getSourceName());
        if (body.getSourceUrl() != null) item.setSourceUrl(body.getSourceUrl());
        if (body.getCoverUrl() != null) item.setCoverUrl(body.getCoverUrl());
        if (body.getIsPublished() != null) item.setIsPublished(body.getIsPublished());
        if (body.getPublishedTime() != null) item.setPublishedTime(body.getPublishedTime());
        if (Boolean.TRUE.equals(item.getIsPublished()) && item.getPublishedTime() == null) item.setPublishedTime(LocalDateTime.now());
        return ApiResponse.success("更新成功", contentItemRepository.save(item));
    }

    @DeleteMapping("/content/{id}")
    public ApiResponse<Void> deleteContent(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                            @PathVariable Long id) {
        if (!authorized(token)) return forbidden();
        ContentItem item = contentItemRepository.findById(id).orElse(null);
        if (item == null || item.getIsDeleted()) return ApiResponse.error("内容不存在");
        item.setIsDeleted(true);
        contentItemRepository.save(item);
        return ApiResponse.success("删除成功", null);
    }

    @GetMapping("/lawyers")
    public ApiResponse<List<Lawyer>> lawyerList(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!authorized(token)) return forbidden();
        return ApiResponse.success(lawyerRepository.findByIsDeletedFalseOrderByCreatedTimeDesc());
    }

    @GetMapping("/lawyers/{id}")
    public ApiResponse<Lawyer> lawyerDetail(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                             @PathVariable Long id) {
        if (!authorized(token)) return forbidden();
        Lawyer lawyer = lawyerRepository.findById(id).orElse(null);
        if (lawyer == null || Boolean.TRUE.equals(lawyer.getIsDeleted())) return ApiResponse.error("律师不存在");
        return ApiResponse.success(lawyer);
    }

    @PostMapping("/lawyers")
    public ApiResponse<Lawyer> createLawyer(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                             @RequestBody Lawyer body) {
        if (!authorized(token)) return forbidden();
        if (body == null || isBlank(body.getName())) return ApiResponse.error("律师姓名不能为空");
        body.setId(null);
        body.setIsDeleted(false);
        body.setCreatedTime(LocalDateTime.now());
        return ApiResponse.success("创建成功", lawyerRepository.save(body));
    }

    @PutMapping("/lawyers/{id}")
    public ApiResponse<Lawyer> updateLawyer(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                             @PathVariable Long id, @RequestBody Lawyer body) {
        if (!authorized(token)) return forbidden();
        if (body == null) return ApiResponse.error("请求内容不能为空");
        Lawyer lawyer = lawyerRepository.findById(id).orElse(null);
        if (lawyer == null || lawyer.getIsDeleted()) return ApiResponse.error("律师不存在");
        if (body.getName() != null) lawyer.setName(body.getName());
        if (isBlank(lawyer.getName())) return ApiResponse.error("律师姓名不能为空");
        if (body.getLawFirm() != null) lawyer.setLawFirm(body.getLawFirm());
        if (body.getSpecialties() != null) lawyer.setSpecialties(body.getSpecialties());
        if (body.getIntroduction() != null) lawyer.setIntroduction(body.getIntroduction());
        if (body.getAvatarUrl() != null) lawyer.setAvatarUrl(body.getAvatarUrl());
        if (body.getIsAvailable() != null) lawyer.setIsAvailable(body.getIsAvailable());
        return ApiResponse.success("更新成功", lawyerRepository.save(lawyer));
    }

    @DeleteMapping("/lawyers/{id}")
    public ApiResponse<Void> deleteLawyer(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                           @PathVariable Long id) {
        if (!authorized(token)) return forbidden();
        Lawyer lawyer = lawyerRepository.findById(id).orElse(null);
        if (lawyer == null || lawyer.getIsDeleted()) return ApiResponse.error("律师不存在");
        lawyer.setIsDeleted(true);
        lawyerRepository.save(lawyer);
        return ApiResponse.success("删除成功", null);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
