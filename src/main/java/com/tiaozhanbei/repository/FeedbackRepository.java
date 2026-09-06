package com.tiaozhanbei.repository;

import com.tiaozhanbei.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findByUserIdAndIsDeletedFalseOrderByCreatedTimeDesc(Long userId);
    List<Feedback> findByIsDeletedFalseOrderByCreatedTimeDesc();
}
