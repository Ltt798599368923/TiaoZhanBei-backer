package com.tiaozhanbei.repository;

import com.tiaozhanbei.entity.Consultation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConsultationRepository extends JpaRepository<Consultation, Long> {
    List<Consultation> findByUserIdAndIsDeletedFalseOrderByCreatedTimeDesc(Long userId);
    List<Consultation> findByStatusAndIsDeletedFalseOrderByCreatedTimeDesc(String status);
    List<Consultation> findByIsDeletedFalseOrderByCreatedTimeDesc();
    long countByIsDeletedFalse();
    long countByStatusAndIsDeletedFalse(String status);
}
