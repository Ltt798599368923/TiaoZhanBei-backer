package com.tiaozhanbei.service;

import com.tiaozhanbei.dto.ContractRequest;
import com.tiaozhanbei.entity.Contract;
import com.tiaozhanbei.repository.ContractRepository;
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
public class ContractService {
    private static final Logger logger = LoggerFactory.getLogger(ContractService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ContractRepository contractRepository;

    @Autowired
    public ContractService(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    public List<Map<String, Object>> getUserContracts(Long userId) {
        logger.info("Getting contracts for user: {}", userId);
        List<Contract> contracts = contractRepository.findByUserIdAndIsDeletedFalseOrderByCreatedTimeDesc(userId);
        
        return contracts.stream().map(contract -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", contract.getId());
            map.put("type", contract.getType());
            map.put("title", contract.getTitle());
            map.put("fileName", contract.getFileName());
            map.put("status", contract.getStatus());
            map.put("reviewResult", contract.getReviewResult());
            map.put("time", contract.getCreatedTime().format(DATE_FORMATTER));
            return map;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getUserContractsByType(Long userId, String type) {
        logger.info("Getting contracts for user: {} with type: {}", userId, type);
        List<Contract> contracts = contractRepository.findByUserIdAndTypeAndIsDeletedFalseOrderByCreatedTimeDesc(userId, type);
        
        return contracts.stream().map(contract -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", contract.getId());
            map.put("type", contract.getType());
            map.put("title", contract.getTitle());
            map.put("fileName", contract.getFileName());
            map.put("status", contract.getStatus());
            map.put("time", contract.getCreatedTime().format(DATE_FORMATTER));
            return map;
        }).collect(Collectors.toList());
    }

    public Contract createContract(Long userId, ContractRequest request) {
        logger.info("Creating contract for user: {}, type: {}, title: {}", userId, request.getType(), request.getTitle());
        
        Contract contract = new Contract();
        contract.setUserId(userId);
        contract.setType(request.getType());
        contract.setTitle(request.getTitle());
        contract.setFileName(request.getFileName());
        contract.setFilePath(request.getFilePath());
        contract.setStatus("pending");

        return contractRepository.save(contract);
    }

    public Contract getContractById(Long userId, Long contractId) {
        Contract contract = contractRepository.findById(contractId).orElse(null);
        if (contract != null && contract.getUserId().equals(userId) && !contract.getIsDeleted()) {
            return contract;
        }
        return null;
    }

    public boolean deleteContract(Long userId, Long contractId) {
        logger.info("Deleting contract: {} for user: {}", contractId, userId);
        Contract contract = contractRepository.findById(contractId).orElse(null);
        
        if (contract == null || !contract.getUserId().equals(userId)) {
            return false;
        }

        contract.setIsDeleted(true);
        contractRepository.save(contract);
        return true;
    }
}
