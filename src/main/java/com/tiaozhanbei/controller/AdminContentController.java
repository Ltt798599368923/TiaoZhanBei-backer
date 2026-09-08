package com.tiaozhanbei.controller;

import com.tiaozhanbei.dto.ApiResponse;
import com.tiaozhanbei.dto.ContentImportRequest;
import com.tiaozhanbei.entity.ContentItem;
import com.tiaozhanbei.entity.Feedback;
import com.tiaozhanbei.entity.Lawyer;
import com.tiaozhanbei.entity.SystemNotice;
import com.tiaozhanbei.repository.ContentItemRepository;
import com.tiaozhanbei.repository.FeedbackRepository;
import com.tiaozhanbei.repository.LawyerRepository;
import com.tiaozhanbei.repository.SystemNoticeRepository;
import com.tiaozhanbei.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/admin")
public class AdminContentController {
    private static final List<String> CONTENT_TYPES = Arrays.asList("article", "law", "book", "video");

    @Value("${admin.token:}")
    private String adminToken;

    private final ContentItemRepository contentItemRepository;
    private final LawyerRepository lawyerRepository;
    private final FeedbackRepository feedbackRepository;
    private final SystemNoticeRepository systemNoticeRepository;
    private final FileStorageService fileStorageService;

    public AdminContentController(ContentItemRepository contentItemRepository, LawyerRepository lawyerRepository,
                                  FeedbackRepository feedbackRepository, SystemNoticeRepository systemNoticeRepository,
                                  FileStorageService fileStorageService) {
        this.contentItemRepository = contentItemRepository;
        this.lawyerRepository = lawyerRepository;
        this.feedbackRepository = feedbackRepository;
        this.systemNoticeRepository = systemNoticeRepository;
        this.fileStorageService = fileStorageService;
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
        String validationMessage = validateContent(body);
        if (validationMessage != null) return ApiResponse.error(validationMessage);
        normalizeContent(body);
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
        String validationMessage = validateContent(item);
        if (validationMessage != null) return ApiResponse.error(validationMessage);
        normalizeContent(item);
        if (Boolean.TRUE.equals(item.getIsPublished()) && item.getPublishedTime() == null) item.setPublishedTime(LocalDateTime.now());
        return ApiResponse.success("更新成功", contentItemRepository.save(item));
    }

    @PostMapping(value = "/content/{id}/file", consumes = "multipart/form-data")
    public ApiResponse<ContentItem> uploadContentFile(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable Long id,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        if (!authorized(token)) return forbidden();
        ContentItem item = contentItemRepository.findById(id).orElse(null);
        if (item == null || Boolean.TRUE.equals(item.getIsDeleted())) return ApiResponse.error("内容不存在");
        if (!"book".equals(item.getContentType())) {
            return ApiResponse.error("仅法规阅读资料支持上传附件；普法文章请直接填写正文");
        }
        try {
            String storedPath = fileStorageService.store(file, "content");
            String fileName = file.getOriginalFilename();
            item.setFileName(isBlank(fileName) ? "内容附件" : fileName);
            item.setFilePath(storedPath);
            return ApiResponse.success("附件上传成功", contentItemRepository.save(item));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("附件上传失败，请稍后重试");
        }
    }

    @PostMapping("/content/import")
    public ApiResponse<Map<String, Object>> importContent(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestBody ContentImportRequest request) {
        if (!authorized(token)) return forbidden();
        if (request == null || request.getItems().isEmpty()) return ApiResponse.error("导入清单不能为空");
        if (request.getItems().size() > 100) return ApiResponse.error("单次最多导入 100 条资料");

        int created = 0;
        int updated = 0;
        List<String> rejected = new ArrayList<>();
        for (ContentImportRequest.Item body : request.getItems()) {
            String contentType = body == null || isBlank(body.getContentType()) ? "book" : body.getContentType().trim();
            if (body == null || !CONTENT_TYPES.contains(contentType) || isBlank(body.getImportKey())
                    || isBlank(body.getTitle()) || isBlank(body.getContent())) {
                rejected.add(body == null || isBlank(body.getTitle()) ? "未命名资料" : body.getTitle());
                continue;
            }

            ContentItem item = contentItemRepository
                    .findFirstByContentTypeAndImportKey(contentType, body.getImportKey().trim())
                    .orElse(null);
            boolean isNew = item == null;
            if (isNew) {
                item = new ContentItem();
                item.setContentType(contentType);
                item.setImportKey(body.getImportKey().trim());
                item.setCreatedTime(LocalDateTime.now());
            }

            item.setIsDeleted(false);
            item.setTitle(body.getTitle().trim());
            item.setSummary(trimToLength(body.getSummary(), 1000));
            item.setContent(body.getContent().trim());
            item.setSourceName(trimToLength(body.getSourceName(), 200));
            item.setSourceUrl(trimToLength(body.getSourceUrl(), 1000));
            if (request.getPublish() != null) {
                item.setIsPublished(request.getPublish());
                if (Boolean.TRUE.equals(request.getPublish()) && item.getPublishedTime() == null) {
                    item.setPublishedTime(LocalDateTime.now());
                }
            }
            contentItemRepository.save(item);
            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("updated", updated);
        result.put("rejected", rejected);
        result.put("publish", request.getPublish());
        return ApiResponse.success("法规资料导入完成", result);
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

    @GetMapping("/feedbacks")
    public ApiResponse<List<Feedback>> feedbacks(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!authorized(token)) return forbidden();
        return ApiResponse.success(feedbackRepository.findByIsDeletedFalseOrderByCreatedTimeDesc());
    }

    @PutMapping("/feedbacks/{id}")
    public ApiResponse<Feedback> updateFeedback(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                                @PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        if (!authorized(token)) return forbidden();
        if (body == null) return ApiResponse.error("请求内容不能为空");
        Feedback feedback = feedbackRepository.findById(id).orElse(null);
        if (feedback == null || Boolean.TRUE.equals(feedback.getIsDeleted())) return ApiResponse.error("反馈不存在");
        String status = body.getOrDefault("status", feedback.getStatus());
        if (status != null && !Arrays.asList("pending", "processing", "resolved").contains(status)) return ApiResponse.error("不支持的处理状态");
        String previousReply = feedback.getReply();
        String reply = body.containsKey("reply") ? body.get("reply") : previousReply;
        reply = reply == null ? "" : reply.trim();
        if (reply.length() > 2000) return ApiResponse.error("回复不能超过 2000 字");
        feedback.setStatus(status);
        feedback.setReply(reply);
        feedback = feedbackRepository.save(feedback);
        if (!reply.isEmpty() && !reply.equals(previousReply == null ? "" : previousReply.trim())) {
            SystemNotice notice = new SystemNotice();
            notice.setUserId(feedback.getUserId());
            notice.setTitle("意见反馈已回复");
            notice.setContent("反馈处理回复：" + reply);
            notice.setNoticeType("feedback_reply");
            systemNoticeRepository.save(notice);
        }
        return ApiResponse.success("反馈处理已更新", feedback);
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

    @PostMapping(value = "/lawyers/avatar", consumes = "multipart/form-data")
    public ApiResponse<java.util.Map<String, String>> uploadLawyerAvatar(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        if (!authorized(token)) return forbidden();
        try {
            String storedPath = fileStorageService.storeAvatar(file);
            String fileName = Paths.get(storedPath).getFileName().toString();
            return ApiResponse.success("头像上传成功", Collections.singletonMap("url", "/api/files/avatars/" + fileName));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("头像上传失败，请稍后重试");
        }
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

    private String trimToLength(String value, int maxLength) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String validateContent(ContentItem item) {
        if (item == null) return "请求内容不能为空";
        String type = item.getContentType();
        if (Arrays.asList("article", "law", "book").contains(type) && isBlank(item.getContent())) {
            return "普法文章、法规动态和法规阅读都必须填写可在线阅读的正文";
        }
        if ("video".equals(type) && isBlank(item.getSourceUrl())) {
            return "视频内容请填写可播放的视频链接";
        }
        return null;
    }

    private void normalizeContent(ContentItem item) {
        if ("article".equals(item.getContentType())) {
            // 普法文章只使用后台正文在线阅读，避免旧链接再次成为阅读入口。
            item.setSourceUrl(null);
        }
    }
}
