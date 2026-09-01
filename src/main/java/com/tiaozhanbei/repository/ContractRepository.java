package com.tiaozhanbei.repository;

import com.tiaozhanbei.entity.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {
    List<Contract> findByUserIdAndIsDeletedFalseOrderByCreatedTimeDesc(Long userId);
    List<Contract> findByUserIdAndTypeAndIsDeletedFalseOrderByCreatedTimeDesc(Long userId, String type);
    List<Contract> findByStatusAndIsDeletedFalseOrderByCreatedTimeDesc(String status);
    List<Contract> findByIsDeletedFalseOrderByCreatedTimeDesc();
    long countByIsDeletedFalse();
    long countByStatusAndIsDeletedFalse(String status);
}
