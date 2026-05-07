package com.tiaozhanbei.service;

import com.tiaozhanbei.dto.ConsultationRequest;
import com.tiaozhanbei.entity.Consultation;
import com.tiaozhanbei.repository.ConsultationRepository;
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
public class ConsultationService {
    private static final Logger logger = LoggerFactory.getLogger(ConsultationService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ConsultationRepository consultationRepository;

    @Autowired
    public ConsultationService(ConsultationRepository consultationRepository) {
        this.consultationRepository = consultationRepository;
    }

    public List<Map<String, Object>> getUserConsultations(Long userId) {
        logger.info("Getting consultations for user: {}", userId);
        List<Consultation> consultations = consultationRepository.findByUserIdAndIsDeletedFalseOrderByCreatedTimeDesc(userId);
        
        return consultations.stream().map(cons -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", cons.getId());
            map.put("title", cons.getTitle());
            map.put("content", cons.getContent());
            map.put("status", cons.getStatus());
            map.put("time", cons.getCreatedTime().format(DATE_FORMATTER));
            return map;
        }).collect(Collectors.toList());
    }

    public Consultation createConsultation(Long userId, ConsultationRequest request) {
        logger.info("Creating consultation for user: {}, title: {}", userId, request.getTitle());
        
        Consultation consultation = new Consultation();
        consultation.setUserId(userId);
        consultation.setTitle(request.getTitle());
        consultation.setContent(request.getContent());
        consultation.setStatus("pending");

        return consultationRepository.save(consultation);
    }

    public Consultation getConsultationById(Long userId, Long consultationId) {
        Consultation consultation = consultationRepository.findById(consultationId).orElse(null);
        if (consultation != null && consultation.getUserId().equals(userId) && !consultation.getIsDeleted()) {
            return consultation;
        }
        return null;
    }

    public boolean deleteConsultation(Long userId, Long consultationId) {
        logger.info("Deleting consultation: {} for user: {}", consultationId, userId);
        Consultation consultation = consultationRepository.findById(consultationId).orElse(null);
        
        if (consultation == null || !consultation.getUserId().equals(userId)) {
            return false;
        }

        consultation.setIsDeleted(true);
        consultationRepository.save(consultation);
        return true;
    }
}
