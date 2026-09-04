package com.tiaozhanbei.controller;

import com.tiaozhanbei.dto.ApiResponse;
import com.tiaozhanbei.entity.*;
import com.tiaozhanbei.repository.*;
import com.tiaozhanbei.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Value("${admin.token:}")
    private String adminToken;

    @Autowired private UserRepository userRepository;
    @Autowired private ConsultationRepository consultationRepository;
    @Autowired private ContractRepository contractRepository;
    @Autowired private DocumentTemplateRepository documentTemplateRepository;
    @Autowired private FavoriteRepository favoriteRepository;
    @Autowired private SystemNoticeRepository systemNoticeRepository;
    @Autowired private FileStorageService fileStorageService;

    private boolean checkAuth(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        return adminToken != null && !adminToken.trim().isEmpty() && adminToken.trim().equals(token);
    }

    private <T> ApiResponse<T> authError() {
        return ApiResponse.error(403, "管理员认证失败");
    }

    // ==================== 认证 ====================
    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@RequestBody Map<String, String> body) {
        String pwd = body.getOrDefault("password", "");
        if (checkAuth(pwd)) {
            Map<String, String> result = new HashMap<>();
            result.put("token", adminToken);
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
            m.put("status", c.getStatus());
            m.put("reply", c.getReply());
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
        if (body == null) return ApiResponse.error("请求内容不能为空");
        String previousReply = c.getReply();
        if (body.containsKey("status")) c.setStatus(body.get("status"));
        if (body.containsKey("reply")) {
            String reply = body.get("reply");
            c.setReply(reply == null ? null : reply.trim());
            c.setRepliedTime(reply == null || reply.trim().isEmpty() ? null : LocalDateTime.now());
            if (reply != null && !reply.trim().isEmpty() && "pending".equals(c.getStatus())) c.setStatus("replied");
        }
        consultationRepository.save(c);
        if (c.getReply() != null && !c.getReply().trim().isEmpty() && !c.getReply().equals(previousReply)) {
            createUserNotice(c.getUserId(), "咨询已回复", "您的咨询《" + c.getTitle() + "》已有处理回复，请在“我的咨询”中查看详情。", "consultation_reply");
        }
        return ApiResponse.success("操作成功", null);
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
        return ApiResponse.success("创建成功", documentTemplateRepository.save(template));
    }

    @PostMapping(value = "/templates/upload", consumes = "multipart/form-data")
    public ApiResponse<DocumentTemplate> uploadTemplate(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam String title,
            @RequestParam String category,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String content,
            @RequestParam("file") MultipartFile file) {
        if (!checkAuth(token)) return authError();
        if (title.trim().isEmpty() || category.trim().isEmpty()) return ApiResponse.error("标题和分类不能为空");
        try {
            DocumentTemplate template = new DocumentTemplate();
            template.setTitle(title.trim());
            template.setCategory(category.trim());
            template.setDescription(description);
            template.setContent(content);
            template.setFileName(file.getOriginalFilename());
            template.setFilePath(fileStorageService.store(file, "templates"));
            template.setDownloadCount(0);
            template.setIsDeleted(false);
            return ApiResponse.success("上传成功", documentTemplateRepository.save(template));
        } catch (Exception e) {
            logger.error("Upload template failed", e);
            return ApiResponse.error("模板上传失败: " + e.getMessage());
        }
    }

    @PutMapping("/templates/{id}")
    public ApiResponse<DocumentTemplate> updateTemplate(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable Long id, @RequestBody DocumentTemplate body) {
        if (!checkAuth(token)) return authError();
        DocumentTemplate t = documentTemplateRepository.findById(id).orElse(null);
        if (t == null) return ApiResponse.error("模板不存在");
        if (body.getTitle() != null) t.setTitle(body.getTitle());
        if (body.getDescription() != null) t.setDescription(body.getDescription());
        if (body.getCategory() != null) t.setCategory(body.getCategory());
        if (body.getContent() != null) t.setContent(body.getContent());
        if (body.getFilePath() != null) t.setFilePath(body.getFilePath());
        if (body.getFileName() != null) t.setFileName(body.getFileName());
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
