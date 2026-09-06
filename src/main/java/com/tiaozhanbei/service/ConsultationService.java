package com.tiaozhanbei.service;

import com.tiaozhanbei.dto.ConsultationRequest;
import com.tiaozhanbei.entity.Consultation;
import com.tiaozhanbei.entity.ConsultationMessage;
import com.tiaozhanbei.entity.Lawyer;
import com.tiaozhanbei.repository.ConsultationRepository;
import com.tiaozhanbei.repository.ConsultationMessageRepository;
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
    private final ConsultationMessageRepository consultationMessageRepository;
    private final LawyerRepository lawyerRepository;

    @Autowired
    public ConsultationService(ConsultationRepository consultationRepository,
                               ConsultationMessageRepository consultationMessageRepository,
                               LawyerRepository lawyerRepository) {
        this.consultationRepository = consultationRepository;
        this.consultationMessageRepository = consultationMessageRepository;
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

        Consultation saved = consultationRepository.save(consultation);
        saveMessage(saved.getId(), "user", request.getContent().trim(), saved.getCreatedTime());
        return saved;
    }

    public List<Map<String, Object>> getMessagesForUser(Long userId, Long consultationId) {
        Consultation consultation = getActiveConsultation(consultationId);
        if (!consultation.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权查看该咨询会话");
        }
        return getMessages(consultation);
    }

    public List<Map<String, Object>> getMessagesForAdmin(Long consultationId) {
        return getMessages(getActiveConsultation(consultationId));
    }

    public Map<String, Object> appendUserMessage(Long userId, Long consultationId, String content) {
        Consultation consultation = getActiveConsultation(consultationId);
        if (!consultation.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权发送该咨询会话消息");
        }
        ConsultationMessage message = saveMessage(consultationId, "user", content, null);
        if (!"closed".equals(consultation.getStatus())) {
            consultation.setStatus("pending");
            consultationRepository.save(consultation);
        }
        return toMessageMap(message);
    }

    public Map<String, Object> appendAdminMessage(Long consultationId, String content) {
        Consultation consultation = getActiveConsultation(consultationId);
        ConsultationMessage message = saveMessage(consultationId, "admin", content, null);
        consultation.setReply(message.getContent());
        consultation.setRepliedTime(message.getCreatedTime());
        if (!"closed".equals(consultation.getStatus())) consultation.setStatus("replied");
        consultationRepository.save(consultation);
        return toMessageMap(message);
    }

    public boolean userOwnsActiveConsultation(Long userId, Long consultationId) {
        try {
            Consultation consultation = getActiveConsultation(consultationId);
            return consultation.getUserId().equals(userId);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public boolean activeConsultationExists(Long consultationId) {
        try {
            getActiveConsultation(consultationId);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
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

    private List<Map<String, Object>> getMessages(Consultation consultation) {
        List<ConsultationMessage> messages = consultationMessageRepository
                .findByConsultationIdOrderByCreatedTimeAsc(consultation.getId());
        if (messages.isEmpty()) {
            if (!isBlank(consultation.getContent())) {
                saveMessage(consultation.getId(), "user", consultation.getContent(), consultation.getCreatedTime());
            }
            if (!isBlank(consultation.getReply())) {
                saveMessage(consultation.getId(), "admin", consultation.getReply(), consultation.getRepliedTime());
            }
            messages = consultationMessageRepository.findByConsultationIdOrderByCreatedTimeAsc(consultation.getId());
        }
        return messages.stream().map(this::toMessageMap).collect(Collectors.toList());
    }

    private ConsultationMessage saveMessage(Long consultationId, String senderRole, String content, java.time.LocalDateTime createdTime) {
        if (isBlank(content)) throw new IllegalArgumentException("消息内容不能为空");
        String text = content.trim();
        if (text.length() > 2000) throw new IllegalArgumentException("单条消息不能超过 2000 字");
        ConsultationMessage message = new ConsultationMessage();
        message.setConsultationId(consultationId);
        message.setSenderRole(senderRole);
        message.setContent(text);
        if (createdTime != null) message.setCreatedTime(createdTime);
        return consultationMessageRepository.save(message);
    }

    private Map<String, Object> toMessageMap(ConsultationMessage message) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", message.getId());
        result.put("consultationId", message.getConsultationId());
        result.put("senderRole", message.getSenderRole());
        result.put("content", message.getContent());
        result.put("createdTime", message.getCreatedTime().format(DATE_FORMATTER));
        return result;
    }

    private Consultation getActiveConsultation(Long consultationId) {
        Consultation consultation = consultationRepository.findById(consultationId).orElse(null);
        if (consultation == null || Boolean.TRUE.equals(consultation.getIsDeleted())) {
            throw new IllegalArgumentException("咨询会话不存在");
        }
        return consultation;
    }
}
