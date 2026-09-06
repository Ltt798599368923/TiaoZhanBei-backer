package com.tiaozhanbei.service;

import com.tiaozhanbei.dto.ConsultationRequest;
import com.tiaozhanbei.dto.BookingUpdateRequest;
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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Arrays;
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
            map.put("lawyerName", getLawyerName(cons.getLawyerId()));
            map.put("status", cons.getStatus());
            map.put("reply", cons.getReply());
            map.put("appointmentTime", cons.getAppointmentTime() == null ? null : cons.getAppointmentTime().format(DATE_FORMATTER));
            map.put("contactMethod", cons.getContactMethod());
            map.put("bookingNote", cons.getBookingNote());
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
            if (!isValidMobile(request.getPhone())) {
                throw new IllegalArgumentException("预约律师请填写正确的 11 位手机号码");
            }
            if (consultationRepository.existsByUserIdAndLawyerIdAndIsDeletedFalseAndStatusIn(
                    userId, request.getLawyerId(), Arrays.asList("pending", "processing", "replied", "confirmed", "need_info"))) {
                throw new IllegalArgumentException("您已向该律师提交预约，请先等待处理结果");
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
        if (consultation.getLawyerId() != null) {
            throw new IllegalArgumentException("律师预约请等待平台反馈");
        }
        if ("closed".equals(consultation.getStatus())) {
            throw new IllegalArgumentException("该咨询会话已结束，请新建咨询");
        }
        ConsultationMessage message = saveMessage(consultationId, "user", content, null);
        consultation.setStatus("pending");
        consultationRepository.save(consultation);
        return toMessageMap(message);
    }

    public Map<String, Object> appendAdminMessage(Long consultationId, String content) {
        Consultation consultation = getActiveConsultation(consultationId);
        if ((consultation.getLawyerId() != null && isTerminalBookingStatus(consultation.getStatus()))
                || (consultation.getLawyerId() == null && "closed".equals(consultation.getStatus()))) {
            throw new IllegalArgumentException("该预约或咨询已结束，无法继续处理");
        }
        ConsultationMessage message = saveMessage(consultationId, "admin", content, null);
        consultation.setReply(message.getContent());
        consultation.setRepliedTime(message.getCreatedTime());
        if (consultation.getLawyerId() == null && !"closed".equals(consultation.getStatus())) {
            consultation.setStatus("replied");
        }
        consultationRepository.save(consultation);
        return toMessageMap(message);
    }

    public Map<String, Object> updateBooking(Long consultationId, BookingUpdateRequest request) {
        Consultation consultation = getActiveConsultation(consultationId);
        if (consultation.getLawyerId() == null) {
            throw new IllegalArgumentException("该记录不是律师预约");
        }
        if (request == null || isBlank(request.getStatus())) {
            throw new IllegalArgumentException("请选择预约处理状态");
        }
        String status = request.getStatus().trim();
        if (!Arrays.asList("pending", "processing", "confirmed", "need_info", "declined", "completed").contains(status)) {
            throw new IllegalArgumentException("预约处理状态无效");
        }
        if (isTerminalBookingStatus(consultation.getStatus())) {
            throw new IllegalArgumentException("该预约已结束，无法继续处理");
        }

        String contactMethod = trimToNull(request.getContactMethod());
        String bookingNote = trimToNull(request.getBookingNote());
        LocalDateTime appointmentTime = parseAppointmentTime(request.getAppointmentTime());
        if ("confirmed".equals(status) && (appointmentTime == null || contactMethod == null)) {
            throw new IllegalArgumentException("确认预约时请填写预约时间和沟通方式");
        }
        if (("need_info".equals(status) || "declined".equals(status)) && bookingNote == null) {
            throw new IllegalArgumentException("请填写处理说明");
        }
        if (contactMethod != null && contactMethod.length() > 100) {
            throw new IllegalArgumentException("沟通方式不能超过 100 字");
        }
        if (bookingNote != null && bookingNote.length() > 2000) {
            throw new IllegalArgumentException("处理说明不能超过 2000 字");
        }

        consultation.setStatus(status);
        consultation.setAppointmentTime(appointmentTime);
        consultation.setContactMethod(contactMethod);
        consultation.setBookingNote(bookingNote);
        ConsultationMessage message = saveMessage(consultationId, "admin", bookingUpdateMessage(status, appointmentTime, contactMethod, bookingNote), null);
        consultation.setReply(message.getContent());
        consultation.setRepliedTime(message.getCreatedTime());
        Consultation saved = consultationRepository.save(consultation);

        Map<String, Object> result = new HashMap<>();
        result.put("consultation", saved);
        result.put("message", toMessageMap(message));
        return result;
    }

    public boolean userOwnsActiveConsultation(Long userId, Long consultationId) {
        try {
            Consultation consultation = getActiveConsultation(consultationId);
            return consultation.getUserId().equals(userId);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public boolean userOwnsRealtimeConsultation(Long userId, Long consultationId) {
        try {
            Consultation consultation = getActiveConsultation(consultationId);
            return consultation.getUserId().equals(userId)
                    && consultation.getLawyerId() == null
                    && !"closed".equals(consultation.getStatus());
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

    private boolean isValidMobile(String value) {
        return value != null && value.trim().matches("^1\\d{10}$");
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    private LocalDateTime parseAppointmentTime(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) return null;
        try {
            return LocalDateTime.parse(normalized);
        } catch (Exception ignored) {
            throw new IllegalArgumentException("预约时间格式不正确");
        }
    }

    private boolean isTerminalBookingStatus(String status) {
        return "closed".equals(status) || "cancelled".equals(status)
                || "declined".equals(status) || "completed".equals(status);
    }

    private String bookingUpdateMessage(String status, LocalDateTime appointmentTime,
                                        String contactMethod, String bookingNote) {
        String summary;
        if ("confirmed".equals(status)) {
            summary = "预约已确认：" + appointmentTime.format(DATE_FORMATTER) + "，沟通方式：" + contactMethod;
        } else if ("need_info".equals(status)) {
            summary = "请补充预约信息";
        } else if ("declined".equals(status)) {
            summary = "本次预约暂无法承接";
        } else if ("completed".equals(status)) {
            summary = "本次预约已完成";
        } else if ("processing".equals(status)) {
            summary = "平台正在核对预约安排";
        } else {
            summary = "预约处理状态已更新";
        }
        return bookingNote == null ? summary : summary + "。" + bookingNote;
    }

    private String getLawyerName(Long lawyerId) {
        if (lawyerId == null) return null;
        return lawyerRepository.findById(lawyerId)
                .filter(lawyer -> !Boolean.TRUE.equals(lawyer.getIsDeleted()))
                .map(Lawyer::getName)
                .orElse(null);
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

    public boolean cancelBooking(Long userId, Long consultationId) {
        Consultation consultation = consultationRepository.findById(consultationId).orElse(null);
        if (consultation == null || !consultation.getUserId().equals(userId)
                || consultation.getLawyerId() == null || Boolean.TRUE.equals(consultation.getIsDeleted())) {
            return false;
        }
        if (isTerminalBookingStatus(consultation.getStatus())) {
            throw new IllegalArgumentException("该预约已结束，无法取消");
        }
        consultation.setStatus("cancelled");
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
