package com.tiaozhanbei.repository;

import com.tiaozhanbei.entity.Lawyer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LawyerRepository extends JpaRepository<Lawyer, Long> {
    List<Lawyer> findByIsAvailableTrueAndIsDeletedFalseOrderByCreatedTimeDesc();
    List<Lawyer> findByIsDeletedFalseOrderByCreatedTimeDesc();
}
