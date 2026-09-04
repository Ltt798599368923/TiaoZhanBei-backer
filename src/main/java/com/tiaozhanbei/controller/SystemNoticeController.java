package com.tiaozhanbei.controller;

import com.tiaozhanbei.dto.ApiResponse;
import com.tiaozhanbei.entity.SystemNotice;
import com.tiaozhanbei.repository.SystemNoticeRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notice")
public class SystemNoticeController {
    private final SystemNoticeRepository systemNoticeRepository;

    public SystemNoticeController(SystemNoticeRepository systemNoticeRepository) {
        this.systemNoticeRepository = systemNoticeRepository;
    }

    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> getNotices() {
        List<Map<String, Object>> notices = systemNoticeRepository
                .findByUserIdIsNullAndIsDeletedFalseOrderByCreatedTimeDesc()
                .stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
        return ApiResponse.success(notices);
    }

    @GetMapping("/detail/{noticeId}")
    public ApiResponse<SystemNotice> getNotice(@PathVariable Long noticeId) {
        SystemNotice notice = systemNoticeRepository.findById(noticeId).orElse(null);
        if (notice == null || Boolean.TRUE.equals(notice.getIsDeleted()) || notice.getUserId() != null) {
            return ApiResponse.error("Notice not found");
        }
        return ApiResponse.success(notice);
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<Map<String, Object>>> getUserNotices(@PathVariable Long userId) {
        return ApiResponse.success(systemNoticeRepository.findVisibleToUser(userId).stream()
                .map(this::toSummary)
                .collect(Collectors.toList()));
    }

    @GetMapping("/user/{userId}/detail/{noticeId}")
    public ApiResponse<SystemNotice> getUserNotice(@PathVariable Long userId, @PathVariable Long noticeId) {
        SystemNotice notice = systemNoticeRepository.findById(noticeId).orElse(null);
        if (notice == null || Boolean.TRUE.equals(notice.getIsDeleted())
                || (notice.getUserId() != null && !userId.equals(notice.getUserId()))) {
            return ApiResponse.error("通知不存在或无权访问");
        }
        return ApiResponse.success(notice);
    }

    private Map<String, Object> toSummary(SystemNotice notice) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", notice.getId());
        result.put("title", notice.getTitle());
        result.put("content", notice.getContent());
        result.put("noticeType", notice.getNoticeType());
        result.put("createdTime", notice.getCreatedTime());
        return result;
    }
}
