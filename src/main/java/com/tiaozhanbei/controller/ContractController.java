package com.tiaozhanbei.controller;

import com.tiaozhanbei.dto.ApiResponse;
import com.tiaozhanbei.dto.ContractRequest;
import com.tiaozhanbei.entity.Contract;
import com.tiaozhanbei.service.ContractService;
import com.tiaozhanbei.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contract")
public class ContractController {
    private static final Logger logger = LoggerFactory.getLogger(ContractController.class);

    private final ContractService contractService;
    private final FileStorageService fileStorageService;

    @Autowired
    public ContractController(ContractService contractService, FileStorageService fileStorageService) {
        this.contractService = contractService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/list/{userId}")
    public ApiResponse<List<Map<String, Object>>> getContracts(@PathVariable Long userId) {
        logger.info("Getting contracts for user: {}", userId);
        try {
            List<Map<String, Object>> contracts = contractService.getUserContracts(userId);
            return ApiResponse.success(contracts);
        } catch (Exception e) {
            logger.error("Get contracts failed", e);
            return ApiResponse.error("获取合同列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/list/{userId}/{type}")
    public ApiResponse<List<Map<String, Object>>> getContractsByType(
            @PathVariable Long userId,
            @PathVariable String type) {
        logger.info("Getting contracts for user: {} with type: {}", userId, type);
        try {
            List<Map<String, Object>> contracts = contractService.getUserContractsByType(userId, type);
            return ApiResponse.success(contracts);
        } catch (Exception e) {
            logger.error("Get contracts by type failed", e);
            return ApiResponse.error("获取合同列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/create/{userId}")
    public ApiResponse<Contract> createContract(
            @PathVariable Long userId,
            @RequestBody ContractRequest request) {
        logger.info("Creating contract for user: {}, type: {}", userId, request.getType());
        try {
            if (request.getType() == null || request.getTitle() == null || request.getTitle().trim().isEmpty()) {
                return ApiResponse.error("合同类型和标题不能为空");
            }

            Contract contract = contractService.createContract(userId, request);
            return ApiResponse.success("提交成功", contract);
        } catch (Exception e) {
            logger.error("Create contract failed", e);
            return ApiResponse.error("提交合同审查失败: " + e.getMessage());
        }
    }

    @PostMapping("/upload/{userId}")
    public ApiResponse<Contract> uploadContract(
            @PathVariable Long userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type,
            @RequestParam("title") String title) {
        logger.info("Uploading contract for user: {}, type: {}, fileName: {}", userId, type, file.getOriginalFilename());
        try {
            if (type == null || title == null || title.trim().isEmpty()) {
                return ApiResponse.error("合同类型和标题不能为空");
            }

            if (file.isEmpty()) {
                return ApiResponse.error("请选择要上传的文件");
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.trim().isEmpty()) {
                originalFilename = "合同文件";
            }
            String storedPath = fileStorageService.store(file, "contracts");

            ContractRequest request = new ContractRequest();
            request.setType(type);
            request.setTitle(title);
            request.setFileName(originalFilename);
            request.setFilePath(storedPath);

            Contract contract = contractService.createContract(userId, request);
            return ApiResponse.success("上传成功", contract);
        } catch (Exception e) {
            logger.error("Create contract failed", e);
            return ApiResponse.error("提交合同审查失败: " + e.getMessage());
        }
    }

    @GetMapping("/file/{userId}/{contractId}")
    public ResponseEntity<Resource> downloadContractFile(@PathVariable Long userId, @PathVariable Long contractId) throws Exception {
        Contract contract = contractService.getContractById(userId, contractId);
        if (contract == null) {
            return ResponseEntity.notFound().build();
        }
        return fileStorageService.download(contract.getFilePath(), contract.getFileName());
    }

    @GetMapping("/detail/{userId}/{contractId}")
    public ApiResponse<Map<String, Object>> getContractDetail(
            @PathVariable Long userId,
            @PathVariable Long contractId) {
        logger.info("Getting contract detail: {} for user: {}", contractId, userId);
        try {
            Contract contract = contractService.getContractById(userId, contractId);
            if (contract == null) {
                return ApiResponse.error("合同不存在或无权访问");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("id", contract.getId());
            result.put("type", contract.getType());
            result.put("title", contract.getTitle());
            result.put("fileName", contract.getFileName());
            result.put("status", contract.getStatus());
            result.put("reviewResult", contract.getReviewResult());
            result.put("createdTime", contract.getCreatedTime());

            return ApiResponse.success(result);
        } catch (Exception e) {
            logger.error("Get contract detail failed", e);
            return ApiResponse.error("获取合同详情失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{userId}/{contractId}")
    public ApiResponse<Void> deleteContract(
            @PathVariable Long userId,
            @PathVariable Long contractId) {
        logger.info("Deleting contract: {} for user: {}", contractId, userId);
        try {
            boolean success = contractService.deleteContract(userId, contractId);
            if (success) {
                return ApiResponse.success("删除成功", null);
            } else {
                return ApiResponse.error("合同不存在或无权操作");
            }
        } catch (Exception e) {
            logger.error("Delete contract failed", e);
            return ApiResponse.error("删除合同失败: " + e.getMessage());
        }
    }
}
