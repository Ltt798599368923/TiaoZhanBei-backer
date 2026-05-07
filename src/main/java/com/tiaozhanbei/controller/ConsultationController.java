package com.tiaozhanbei.controller;

import com.tiaozhanbei.dto.ApiResponse;
import com.tiaozhanbei.dto.ConsultationRequest;
import com.tiaozhanbei.entity.Consultation;
import com.tiaozhanbei.service.ConsultationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/consultation")
public class ConsultationController {
    private static final Logger logger = LoggerFactory.getLogger(ConsultationController.class);

    private final ConsultationService consultationService;

    @Autowired
    public ConsultationController(ConsultationService consultationService) {
        this.consultationService = consultationService;
    }

    @GetMapping("/list/{userId}")
    public ApiResponse<List<Map<String, Object>>> getConsultations(@PathVariable Long userId) {
        logger.info("Getting consultations for user: {}", userId);
        try {
            List<Map<String, Object>> consultations = consultationService.getUserConsultations(userId);
            return ApiResponse.success(consultations);
        } catch (Exception e) {
            logger.error("Get consultations failed", e);
            return ApiResponse.error("获取咨询列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/create/{userId}")
    public ApiResponse<Consultation> createConsultation(
            @PathVariable Long userId,
            @RequestBody ConsultationRequest request) {
        logger.info("Creating consultation for user: {}", userId);
        try {
            if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
                return ApiResponse.error("咨询标题不能为空");
            }

            Consultation consultation = consultationService.createConsultation(userId, request);
            return ApiResponse.success("提交成功", consultation);
        } catch (Exception e) {
            logger.error("Create consultation failed", e);
            return ApiResponse.error("提交咨询失败: " + e.getMessage());
        }
    }

    @GetMapping("/detail/{userId}/{consultationId}")
    public ApiResponse<Consultation> getConsultationDetail(
            @PathVariable Long userId,
            @PathVariable Long consultationId) {
        logger.info("Getting consultation detail: {} for user: {}", consultationId, userId);
        try {
            Consultation consultation = consultationService.getConsultationById(userId, consultationId);
            if (consultation == null) {
                return ApiResponse.error("咨询不存在或无权访问");
            }
            return ApiResponse.success(consultation);
        } catch (Exception e) {
            logger.error("Get consultation detail failed", e);
            return ApiResponse.error("获取咨询详情失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{userId}/{consultationId}")
    public ApiResponse<Void> deleteConsultation(
            @PathVariable Long userId,
            @PathVariable Long consultationId) {
        logger.info("Deleting consultation: {} for user: {}", consultationId, userId);
        try {
            boolean success = consultationService.deleteConsultation(userId, consultationId);
            if (success) {
                return ApiResponse.success("删除成功", null);
            } else {
                return ApiResponse.error("咨询不存在或无权操作");
            }
        } catch (Exception e) {
            logger.error("Delete consultation failed", e);
            return ApiResponse.error("删除咨询失败: " + e.getMessage());
        }
    }
}
