package com.tiaozhanbei.controller;

import com.tiaozhanbei.dto.ApiResponse;
import com.tiaozhanbei.dto.BookingUpdateRequest;
import com.tiaozhanbei.dto.TemplateImportRequest;
import com.tiaozhanbei.entity.*;
import com.tiaozhanbei.repository.*;
import com.tiaozhanbei.service.FileStorageService;
import com.tiaozhanbei.service.ConsultationService;
import com.tiaozhanbei.service.ConsultationChatHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);
    private static final Set<String> TEMPLATE_CATEGORIES = new HashSet<>(Arrays.asList(
            "complaint", "defense", "appeal", "application", "authorization",
            "preservation", "execution", "statement", "other"
    ));
    private static final Set<String> TEMPLATE_PRACTICE_AREAS = new HashSet<>(Arrays.asList(
            "civil_commercial", "criminal", "administrative", "intellectual_property",
            "state_compensation", "enforcement", "maritime", "environmental", "other"
    ));
    private static final Set<String> TEMPLATE_MATERIAL_TYPES = new HashSet<>(Arrays.asList(
            "template", "example", "guide"
    ));

    @Value("${admin.token:}")
    private String adminToken;

    @Autowired private UserRepository userRepository;
    @Autowired private ConsultationRepository consultationRepository;
    @Autowired private LawyerRepository lawyerRepository;
    @Autowired private ContractRepository contractRepository;
    @Autowired private DocumentTemplateRepository documentTemplateRepository;
    @Autowired private FavoriteRepository favoriteRepository;
    @Autowired private SystemNoticeRepository systemNoticeRepository;
    @Autowired private FeedbackRepository feedbackRepository;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private ConsultationService consultationService;
    @Autowired private ConsultationChatHub consultationChatHub;

    private boolean checkAuth(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (matchesConfiguredToken(token)) return true;
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return false;
        HttpServletRequest request = attributes.getRequest();
        HttpSession session = request.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(AdminPageController.ADMIN_SESSION_ATTRIBUTE));
    }

    private boolean matchesConfiguredToken(String token) {
        return adminToken != null && !adminToken.trim().isEmpty() && adminToken.trim().equals(token);
    }

    private <T> ApiResponse<T> authError() {
        return ApiResponse.error(403, "管理员认证失败");
    }

    // ==================== 认证 ====================
    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@RequestBody Map<String, String> body, HttpSession session) {
        String pwd = body.getOrDefault("password", "");
        if (matchesConfiguredToken(pwd)) {
            session.setAttribute(AdminPageController.ADMIN_SESSION_ATTRIBUTE, Boolean.TRUE);
            Map<String, String> result = new HashMap<>();
            result.put("authenticated", "true");
            return ApiResponse.success("登录成功", result);
        }
        return ApiResponse.error("密码错误");
    }

    // ==================== 仪表盘统计 ====================
    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!checkAuth(token)) return authError();
        Map<String, Object> data = new HashMap<>();
        data.put("userCount", userRepository.countByIsDeletedFalse());
        data.put("consultationCount", consultationRepository.countByIsDeletedFalse());
        data.put("contractCount", contractRepository.countByIsDeletedFalse());
        data.put("templateCount", documentTemplateRepository.countByIsDeletedFalse());
        data.put("favoriteCount", favoriteRepository.countByIsDeletedFalse());
        data.put("pendingConsultations", consultationRepository.countByStatusAndIsDeletedFalse("pending"));
        data.put("pendingContracts", contractRepository.countByStatusAndIsDeletedFalse("pending"));
        data.put("pendingFeedbacks", feedbackRepository.countByStatusAndIsDeletedFalse("pending"));
        return ApiResponse.success(data);
    }

    // ==================== 用户管理 ====================
    @GetMapping("/users")
    public ApiResponse<List<User>> getUsers(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam(defaultValue = "") String keyword) {
        if (!checkAuth(token)) return authError();
        List<User> users;
        if (!keyword.isEmpty()) {
            users = userRepository.findByNicknameContainingAndIsDeletedFalse(keyword);
        } else {
            users = userRepository.findByIsDeletedFalse();
        }
        return ApiResponse.success(users);
    }

    @GetMapping("/users/{userId}")
    public ApiResponse<User> getUser(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                      @PathVariable Long userId) {
        if (!checkAuth(token)) return authError();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ApiResponse.error("用户不存在");
        return ApiResponse.success(user);
    }

    @PutMapping("/users/{userId}")
    public ApiResponse<User> updateUser(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                         @PathVariable Long userId, @RequestBody Map<String, String> body) {
        if (!checkAuth(token)) return authError();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ApiResponse.error("用户不存在");
        if (body.containsKey("nickname")) user.setNickname(body.get("nickname"));
        if (body.containsKey("avatar")) user.setAvatar(body.get("avatar"));
        if (body.containsKey("phone")) user.setPhone(body.get("phone"));
        userRepository.save(user);
        return ApiResponse.success("更新成功", user);
    }

    @DeleteMapping("/users/{userId}")
    public ApiResponse<Void> deleteUser(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                         @PathVariable Long userId) {
        if (!checkAuth(token)) return authError();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ApiResponse.error("用户不存在");
        user.setIsDeleted(true);
        userRepository.save(user);
        return ApiResponse.success("删除成功", null);
    }

    // ==================== 咨询管理 ====================
    @GetMapping("/consultations")
    public ApiResponse<List<Map<String, Object>>> getConsultations(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam(defaultValue = "") String status) {
        if (!checkAuth(token)) return authError();
        List<Consultation> list;
        if (!status.isEmpty()) {
            list = consultationRepository.findByStatusAndIsDeletedFalseOrderByCreatedTimeDesc(status);
        } else {
            list = consultationRepository.findByIsDeletedFalseOrderByCreatedTimeDesc();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Consultation c : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("userId", c.getUserId());
            m.put("title", c.getTitle());
            m.put("content", c.getContent());
            m.put("phone", c.getPhone());
            m.put("type", c.getType());
            m.put("lawyerId", c.getLawyerId());
            m.put("lawyerName", c.getLawyerId() == null ? null : lawyerRepository.findById(c.getLawyerId())
                    .filter(lawyer -> !Boolean.TRUE.equals(lawyer.getIsDeleted()))
                    .map(Lawyer::getName).orElse(null));
            m.put("status", c.getStatus());
            m.put("reply", c.getReply());
            m.put("appointmentTime", c.getAppointmentTime());
            m.put("contactMethod", c.getContactMethod());
            m.put("bookingNote", c.getBookingNote());
            m.put("repliedTime", c.getRepliedTime());
            m.put("createdTime", c.getCreatedTime());
            result.add(m);
        }
        return ApiResponse.success(result);
    }

    @PutMapping("/consultations/{id}")
    public ApiResponse<Void> replyConsultation(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                                @PathVariable Long id, @RequestBody Map<String, String> body) {
        if (!checkAuth(token)) return authError();
        Consultation c = consultationRepository.findById(id).orElse(null);
        if (c == null) return ApiResponse.error("咨询不存在");
        if (c.getLawyerId() != null) return ApiResponse.error("律师预约请使用预约处理接口");
        if (body == null) return ApiResponse.error("请求内容不能为空");
        if (body.containsKey("status")) c.setStatus(body.get("status"));
        consultationRepository.save(c);
        if (body.containsKey("reply")) {
            String reply = body.get("reply");
            if (reply != null && !reply.trim().isEmpty() && !reply.trim().equals(c.getReply())) {
                Map<String, Object> message = consultationService.appendAdminMessage(id, reply);
                consultationChatHub.broadcast(id, message);
            }
        }
        return ApiResponse.success("操作成功", null);
    }

    @PutMapping("/consultations/{id}/booking")
    public ApiResponse<Map<String, Object>> updateBooking(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable Long id, @RequestBody BookingUpdateRequest body) {
        if (!checkAuth(token)) return authError();
        try {
            Map<String, Object> result = consultationService.updateBooking(id, body);
            consultationChatHub.broadcast(id, (Map<String, Object>) result.get("message"));
            return ApiResponse.success("预约处理已更新", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/consultations/{id}/messages")
    public ApiResponse<List<Map<String, Object>>> getConsultationMessages(
            @RequestHeader(value = "X-Admin-Token", required = false) String token, @PathVariable Long id) {
        if (!checkAuth(token)) return authError();
        try {
            return ApiResponse.success(consultationService.getMessagesForAdmin(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/consultations/{id}/messages")
    public ApiResponse<Map<String, Object>> sendConsultationMessage(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        if (!checkAuth(token)) return authError();
        try {
            String content = body == null ? "" : body.get("content");
            Map<String, Object> message = consultationService.appendAdminMessage(id, content);
            consultationChatHub.broadcast(id, message);
            return ApiResponse.success("发送成功", message);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/consultations/{id}")
    public ApiResponse<Void> deleteConsultation(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                                  @PathVariable Long id) {
        if (!checkAuth(token)) return authError();
        Consultation c = consultationRepository.findById(id).orElse(null);
        if (c == null) return ApiResponse.error("咨询不存在");
        c.setIsDeleted(true);
        consultationRepository.save(c);
        return ApiResponse.success("删除成功", null);
    }

    // ==================== 合同管理 ====================
    @GetMapping("/contracts")
    public ApiResponse<List<Map<String, Object>>> getContracts(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam(defaultValue = "") String status) {
        if (!checkAuth(token)) return authError();
        List<Contract> list;
        if (!status.isEmpty()) {
            list = contractRepository.findByStatusAndIsDeletedFalseOrderByCreatedTimeDesc(status);
        } else {
            list = contractRepository.findByIsDeletedFalseOrderByCreatedTimeDesc();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Contract c : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("userId", c.getUserId());
            m.put("type", c.getType());
            m.put("title", c.getTitle());
            m.put("fileName", c.getFileName());
            m.put("status", c.getStatus());
            m.put("createdTime", c.getCreatedTime());
            result.add(m);
        }
        return ApiResponse.success(result);
    }

    @PutMapping("/contracts/{id}")
    public ApiResponse<Void> updateContract(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                             @PathVariable Long id, @RequestBody Map<String, String> body) {
        if (!checkAuth(token)) return authError();
        Contract c = contractRepository.findById(id).orElse(null);
        if (c == null) return ApiResponse.error("合同不存在");
        String previousReview = c.getReviewResult();
        if (body.containsKey("status")) c.setStatus(body.get("status"));
        if (body.containsKey("reviewResult")) c.setReviewResult(body.get("reviewResult"));
        contractRepository.save(c);
        if (c.getReviewResult() != null && !c.getReviewResult().trim().isEmpty() && !c.getReviewResult().equals(previousReview)) {
            createUserNotice(c.getUserId(), "合同审核已更新", "您的合同《" + c.getTitle() + "》已有审核结论，请在合同记录中查看详情。", "contract_review");
        }
        return ApiResponse.success("操作成功", null);
    }

    @DeleteMapping("/contracts/{id}")
    public ApiResponse<Void> deleteContract(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                             @PathVariable Long id) {
        if (!checkAuth(token)) return authError();
        Contract c = contractRepository.findById(id).orElse(null);
        if (c == null) return ApiResponse.error("合同不存在");
        c.setIsDeleted(true);
        contractRepository.save(c);
        return ApiResponse.success("删除成功", null);
    }

    @GetMapping("/contracts/{id}/file")
    public ResponseEntity<Resource> downloadContractFile(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                                          @PathVariable Long id) throws Exception {
        if (!checkAuth(token)) return ResponseEntity.status(403).build();
        Contract contract = contractRepository.findById(id).orElse(null);
        if (contract == null || Boolean.TRUE.equals(contract.getIsDeleted())) return ResponseEntity.notFound().build();
        return fileStorageService.download(contract.getFilePath(), contract.getFileName());
    }

    // ==================== 模板管理 ====================
    @GetMapping("/templates")
    public ApiResponse<List<DocumentTemplate>> getTemplates(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!checkAuth(token)) return authError();
        return ApiResponse.success(documentTemplateRepository.findByIsDeletedFalse());
    }

    @PostMapping("/templates")
    public ApiResponse<DocumentTemplate> createTemplate(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestBody DocumentTemplate template) {
        if (!checkAuth(token)) return authError();
        template.setId(null);
        template.setIsDeleted(false);
        template.setCreatedTime(LocalDateTime.now());
        if (template.getDownloadCount() == null) template.setDownloadCount(0);
        applyTemplateFields(template, template);
        String error = validateTemplate(template);
        if (error != null) return ApiResponse.error(error);
        return ApiResponse.success("创建成功", documentTemplateRepository.save(template));
    }

    @PostMapping(value = "/templates/upload", consumes = "multipart/form-data")
    public ApiResponse<DocumentTemplate> uploadTemplate(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam(value = "id", required = false) Long id,
            @RequestParam String title,
            @RequestParam String category,
            @RequestParam(required = false) String practiceArea,
            @RequestParam(required = false) String materialType,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String content,
            @RequestParam("file") MultipartFile file) {
        if (!checkAuth(token)) return authError();
        if (file == null || file.isEmpty()) return ApiResponse.error("请先选择 Word、PDF 或 TXT 原件");
        try {
            DocumentTemplate template = id == null ? new DocumentTemplate()
                    : documentTemplateRepository.findById(id).orElse(null);
            if (template == null) return ApiResponse.error("模板不存在");
            DocumentTemplate body = new DocumentTemplate();
            body.setTitle(title);
            body.setCategory(category);
            body.setPracticeArea(practiceArea);
            body.setMaterialType(materialType);
            body.setDescription(description);
            body.setContent(content);
            applyTemplateFields(template, body);
            String error = validateTemplate(template, true);
            if (error != null) return ApiResponse.error(error);
            template.setFileName(file.getOriginalFilename());
            template.setFilePath(fileStorageService.store(file, "templates"));
            if (template.getDownloadCount() == null) template.setDownloadCount(0);
            template.setIsDeleted(false);
            return ApiResponse.success(id == null ? "上传成功" : "更新成功", documentTemplateRepository.save(template));
        } catch (Exception e) {
            logger.error("Upload template failed", e);
            return ApiResponse.error("模板上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/templates/import")
    public ApiResponse<Map<String, Object>> importTemplates(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestBody TemplateImportRequest request) {
        if (!checkAuth(token)) return authError();
        if (request == null || request.getItems().isEmpty()) return ApiResponse.error("导入清单不能为空");
        if (request.getItems().size() > 100) return ApiResponse.error("单次最多导入 100 个模板");

        int created = 0;
        int updated = 0;
        List<String> rejected = new ArrayList<>();
        for (TemplateImportRequest.Item body : request.getItems()) {
            if (body == null || isBlank(body.getImportKey()) || isBlank(body.getTitle()) || isBlank(body.getContent())) {
                rejected.add(body == null || isBlank(body.getTitle()) ? "未命名模板" : body.getTitle());
                continue;
            }

            DocumentTemplate template = documentTemplateRepository
                    .findFirstByImportKey(body.getImportKey().trim())
                    .orElse(null);
            boolean isNew = template == null;
            if (isNew) {
                template = new DocumentTemplate();
                template.setImportKey(body.getImportKey().trim());
                template.setCreatedTime(LocalDateTime.now());
                template.setDownloadCount(0);
            }

            template.setIsDeleted(false);
            template.setTitle(body.getTitle().trim());
            template.setDescription(trimToLength(body.getDescription(), 500));
            template.setCategory(normalizeTemplateCategory(body.getCategory(), body.getTitle()));
            template.setPracticeArea(normalizePracticeArea(body.getPracticeArea(), body.getCategory()));
            template.setMaterialType(normalizeMaterialType(body.getMaterialType()));
            template.setContent(body.getContent().trim());
            String error = validateTemplate(template);
            if (error != null) {
                rejected.add(body.getTitle());
                continue;
            }
            documentTemplateRepository.save(template);
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
        return ApiResponse.success("文书模板导入完成", result);
    }

    @PutMapping("/templates/{id}")
    public ApiResponse<DocumentTemplate> updateTemplate(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable Long id, @RequestBody DocumentTemplate body) {
        if (!checkAuth(token)) return authError();
        DocumentTemplate t = documentTemplateRepository.findById(id).orElse(null);
        if (t == null) return ApiResponse.error("模板不存在");
        applyTemplateFields(t, body);
        String error = validateTemplate(t);
        if (error != null) return ApiResponse.error(error);
        return ApiResponse.success("更新成功", documentTemplateRepository.save(t));
    }

    @DeleteMapping("/templates/{id}")
    public ApiResponse<Void> deleteTemplate(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable Long id) {
        if (!checkAuth(token)) return authError();
        DocumentTemplate t = documentTemplateRepository.findById(id).orElse(null);
        if (t == null) return ApiResponse.error("模板不存在");
        t.setIsDeleted(true);
        documentTemplateRepository.save(t);
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

    private void applyTemplateFields(DocumentTemplate target, DocumentTemplate body) {
        if (body.getTitle() != null) target.setTitle(trimToLength(body.getTitle(), 200));
        if (body.getDescription() != null) target.setDescription(trimToLength(body.getDescription(), 500));
        if (body.getCategory() != null) {
            target.setCategory(normalizeTemplateCategory(body.getCategory(), target.getTitle()));
        }
        if (body.getPracticeArea() != null) {
            target.setPracticeArea(normalizePracticeArea(body.getPracticeArea(), target.getCategory()));
        } else if (target.getPracticeArea() == null) {
            target.setPracticeArea("other");
        }
        if (body.getMaterialType() != null) {
            target.setMaterialType(normalizeMaterialType(body.getMaterialType()));
        } else if (target.getMaterialType() == null) {
            target.setMaterialType("template");
        }
        if (body.getContent() != null) {
            String content = body.getContent().trim();
            target.setContent(content.isEmpty() ? null : content);
        }
    }

    private String validateTemplate(DocumentTemplate template) {
        return validateTemplate(template, false);
    }

    private String validateTemplate(DocumentTemplate template, boolean hasUploadedFile) {
        if (isBlank(template.getTitle())) return "标题不能为空";
        if (!TEMPLATE_CATEGORIES.contains(template.getCategory())) return "请选择有效的文书类型";
        if (!TEMPLATE_PRACTICE_AREAS.contains(template.getPracticeArea())) return "请选择有效的业务领域";
        if (!TEMPLATE_MATERIAL_TYPES.contains(template.getMaterialType())) return "请选择有效的资料形态";
        boolean hasContent = !isBlank(template.getContent());
        boolean hasFile = hasUploadedFile || !isBlank(template.getFilePath());
        return hasContent || hasFile ? null : "请填写在线阅读正文，或上传 Word、PDF、TXT 原件";
    }

    private String normalizeTemplateCategory(String value, String title) {
        String category = value == null ? "" : value.trim();
        if (TEMPLATE_CATEGORIES.contains(category)) return category;
        String text = (title == null ? "" : title) + category;
        if (text.contains("答辩")) return "defense";
        if (text.contains("上诉")) return "appeal";
        if (text.contains("委托")) return "authorization";
        if (text.contains("保全")) return "preservation";
        if (text.contains("执行")) return "execution";
        if (text.contains("起诉") || text.contains("自诉") || text.contains("反诉")) return "complaint";
        if (text.contains("申请") || text.contains("申诉") || text.contains("复议")) return "application";
        if (text.contains("意见") || text.contains("陈述")) return "statement";
        return "other";
    }

    private String normalizePracticeArea(String value, String legacyCategory) {
        String practiceArea = value == null ? "" : value.trim();
        if (TEMPLATE_PRACTICE_AREAS.contains(practiceArea)) return practiceArea;
        String text = practiceArea + (legacyCategory == null ? "" : legacyCategory);
        if (text.contains("criminal") || text.contains("刑事")) return "criminal";
        if (text.contains("administrative") || text.contains("行政")) return "administrative";
        if (text.contains("intellectual") || text.contains("知识产权")) return "intellectual_property";
        if (text.contains("compensation") || text.contains("赔偿")) return "state_compensation";
        if (text.contains("enforcement") || text.contains("执行")) return "enforcement";
        if (text.contains("maritime") || text.contains("海事")) return "maritime";
        if (text.contains("environment") || text.contains("环境")) return "environmental";
        if (text.contains("civil") || text.contains("contract") || text.contains("company") || text.contains("民商")) {
            return "civil_commercial";
        }
        return "other";
    }

    private String normalizeMaterialType(String value) {
        String materialType = value == null ? "" : value.trim();
        return TEMPLATE_MATERIAL_TYPES.contains(materialType) ? materialType : "template";
    }

    // ==================== 系统通知 ====================
    @GetMapping("/notices")
    public ApiResponse<List<Map<String, Object>>> getNotices(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!checkAuth(token)) return authError();
        List<SystemNotice> list = systemNoticeRepository.findByIsDeletedFalseOrderByCreatedTimeDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SystemNotice n : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", n.getId());
            m.put("title", n.getTitle());
            m.put("content", n.getContent());
            m.put("userId", n.getUserId());
            m.put("noticeType", n.getNoticeType());
            m.put("createdTime", n.getCreatedTime());
            result.add(m);
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/notices")
    public ApiResponse<SystemNotice> createNotice(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestBody Map<String, String> body) {
        if (!checkAuth(token)) return authError();
        SystemNotice notice = new SystemNotice();
        notice.setTitle(body.get("title"));
        notice.setContent(body.get("content"));
        notice.setNoticeType("system");
        notice.setCreatedTime(LocalDateTime.now());
        return ApiResponse.success("发布成功", systemNoticeRepository.save(notice));
    }

    private void createUserNotice(Long userId, String title, String content, String noticeType) {
        SystemNotice notice = new SystemNotice();
        notice.setUserId(userId);
        notice.setTitle(title);
        notice.setContent(content);
        notice.setNoticeType(noticeType);
        notice.setCreatedTime(LocalDateTime.now());
        systemNoticeRepository.save(notice);
    }

    @DeleteMapping("/notices/{id}")
    public ApiResponse<Void> deleteNotice(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable Long id) {
        if (!checkAuth(token)) return authError();
        SystemNotice n = systemNoticeRepository.findById(id).orElse(null);
        if (n == null) return ApiResponse.error("通知不存在");
        n.setIsDeleted(true);
        systemNoticeRepository.save(n);
        return ApiResponse.success("删除成功", null);
    }
}
