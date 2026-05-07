package com.tiaozhanbei.repository;

import com.tiaozhanbei.entity.DocumentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, Long> {
    List<DocumentTemplate> findByIsDeletedFalseOrderByCreatedTimeDesc();
    List<DocumentTemplate> findByCategoryAndIsDeletedFalseOrderByCreatedTimeDesc(String category);
}
