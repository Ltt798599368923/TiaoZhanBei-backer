package com.tiaozhanbei.controller;

import com.tiaozhanbei.dto.ApiResponse;
import com.tiaozhanbei.entity.Lawyer;
import com.tiaozhanbei.repository.LawyerRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lawyer")
public class LawyerController {
    private final LawyerRepository lawyerRepository;

    public LawyerController(LawyerRepository lawyerRepository) {
        this.lawyerRepository = lawyerRepository;
    }

    @GetMapping("/list")
    public ApiResponse<List<Lawyer>> list() {
        return ApiResponse.success(lawyerRepository.findByIsAvailableTrueAndIsDeletedFalseOrderByCreatedTimeDesc());
    }

    @GetMapping("/detail/{id}")
    public ApiResponse<Lawyer> detail(@PathVariable Long id) {
        Lawyer lawyer = lawyerRepository.findById(id).orElse(null);
        if (lawyer == null || Boolean.TRUE.equals(lawyer.getIsDeleted()) || !Boolean.TRUE.equals(lawyer.getIsAvailable())) {
            return ApiResponse.error("律师不存在或暂不可预约");
        }
        return ApiResponse.success(lawyer);
    }
}
