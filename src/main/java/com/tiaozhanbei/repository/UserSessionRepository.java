package com.tiaozhanbei.repository;

import com.tiaozhanbei.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {
}
