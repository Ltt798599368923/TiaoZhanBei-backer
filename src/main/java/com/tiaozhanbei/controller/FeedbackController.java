package com.tiaozhanbei.controller;

import com.tiaozhanbei.dto.ApiResponse;
import com.tiaozhanbei.entity.Feedback;
import com.tiaozhanbei.repository.FeedbackRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {
    private final FeedbackRepository feedbackRepository;

    public FeedbackController(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    @GetMapping("/list/{userId}")
    public ApiResponse<List<Feedback>> list(@PathVariable Long userId) {
        return ApiResponse.success(feedbackRepository.findByUserIdAndIsDeletedFalseOrderByCreatedTimeDesc(userId));
    }

    @PostMapping("/create/{userId}")
    public ApiResponse<Feedback> create(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        String content = body == null ? "" : body.getOrDefault("content", "").trim();
        if (content.length() < 5) return ApiResponse.error("请至少填写 5 个字的反馈内容");
        if (content.length() > 2000) return ApiResponse.error("反馈内容不能超过 2000 字");
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setContent(content);
        feedback.setStatus("pending");
        return ApiResponse.success("反馈已提交", feedbackRepository.save(feedback));
    }
}
