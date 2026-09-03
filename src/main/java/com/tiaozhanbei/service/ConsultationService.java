package com.tiaozhanbei.service;

import com.tiaozhanbei.dto.ConsultationRequest;
import com.tiaozhanbei.entity.Consultation;
import com.tiaozhanbei.entity.Lawyer;
import com.tiaozhanbei.repository.ConsultationRepository;
import com.tiaozhanbei.repository.LawyerRepository;
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
    private final LawyerRepository lawyerRepository;

    @Autowired
    public ConsultationService(ConsultationRepository consultationRepository, LawyerRepository lawyerRepository) {
        this.consultationRepository = consultationRepository;
        this.lawyerRepository = lawyerRepository;
    }

    public List<Map<String, Object>> getUserConsultations(Long userId) {
        logger.info("Getting consultations for user: {}", userId);
        List<Consultation> consultations = consultationRepository.findByUserIdAndIsDeletedFalseOrderByCreatedTimeDesc(userId);
        
        return consultations.stream().map(cons -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", cons.getId());
            map.put("title", cons.getTitle());
            map.put("content", cons.getContent());
            map.put("phone", cons.getPhone());
            map.put("type", cons.getType());
            map.put("lawyerId", cons.getLawyerId());
            map.put("status", cons.getStatus());
            map.put("reply", cons.getReply());
            map.put("repliedTime", cons.getRepliedTime() == null ? null : cons.getRepliedTime().format(DATE_FORMATTER));
            map.put("time", cons.getCreatedTime().format(DATE_FORMATTER));
            return map;
        }).collect(Collectors.toList());
    }

    public Consultation createConsultation(Long userId, ConsultationRequest request) {
        if (request == null || isBlank(request.getTitle()) || isBlank(request.getContent()) || isBlank(request.getType())) {
            throw new IllegalArgumentException("咨询标题、内容和类型不能为空");
        }
        logger.info("Creating consultation for user: {}, title: {}", userId, request.getTitle());
        if (request.getTitle().trim().length() > 200 || request.getContent().trim().length() > 5000) {
            throw new IllegalArgumentException("咨询内容长度超出限制");
        }
        
        if (request.getLawyerId() != null) {
            Lawyer lawyer = lawyerRepository.findById(request.getLawyerId()).orElse(null);
            if (lawyer == null || Boolean.TRUE.equals(lawyer.getIsDeleted()) || !Boolean.TRUE.equals(lawyer.getIsAvailable())) {
                throw new IllegalArgumentException("预约律师不存在或暂不可预约");
            }
        }
        Consultation consultation = new Consultation();
        consultation.setUserId(userId);
        consultation.setTitle(request.getTitle());
        consultation.setContent(request.getContent());
        consultation.setPhone(request.getPhone());
        consultation.setType(request.getType());
        consultation.setLawyerId(request.getLawyerId());
        consultation.setStatus("pending");

        return consultationRepository.save(consultation);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
