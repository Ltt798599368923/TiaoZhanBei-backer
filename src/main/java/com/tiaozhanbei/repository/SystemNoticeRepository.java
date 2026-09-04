package com.tiaozhanbei.repository;

import com.tiaozhanbei.entity.SystemNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemNoticeRepository extends JpaRepository<SystemNotice, Long> {
    List<SystemNotice> findByIsDeletedFalseOrderByCreatedTimeDesc();
    List<SystemNotice> findByUserIdIsNullAndIsDeletedFalseOrderByCreatedTimeDesc();

    @Query("select n from SystemNotice n where n.isDeleted = false and (n.userId is null or n.userId = :userId) order by n.createdTime desc")
    List<SystemNotice> findVisibleToUser(@Param("userId") Long userId);
}
