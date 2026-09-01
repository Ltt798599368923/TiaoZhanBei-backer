package com.tiaozhanbei.repository;

import com.tiaozhanbei.entity.SystemNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemNoticeRepository extends JpaRepository<SystemNotice, Long> {
    List<SystemNotice> findByIsDeletedFalseOrderByCreatedTimeDesc();
}
