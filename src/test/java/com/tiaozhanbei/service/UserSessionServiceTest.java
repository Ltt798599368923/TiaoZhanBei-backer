package com.tiaozhanbei.service;

import com.tiaozhanbei.entity.User;
import com.tiaozhanbei.entity.UserSession;
import com.tiaozhanbei.repository.UserRepository;
import com.tiaozhanbei.repository.UserSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserSessionServiceTest {

    @Test
    void sessionCanBeResolvedByANewServiceInstance() {
        UserRepository userRepository = mock(UserRepository.class);
        UserSessionRepository sessionRepository = mock(UserSessionRepository.class);
        User user = new User();
        user.setId(42L);

        when(sessionRepository.save(any(UserSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        UserSessionService loginService = new UserSessionService(168, userRepository, sessionRepository);
        String token = loginService.createSession(user);

        ArgumentCaptor<UserSession> savedSession = ArgumentCaptor.forClass(UserSession.class);
        org.mockito.Mockito.verify(sessionRepository).save(savedSession.capture());
        when(sessionRepository.findById(token)).thenReturn(Optional.of(savedSession.getValue()));

        UserSessionService restartedService = new UserSessionService(168, userRepository, sessionRepository);

        assertTrue(restartedService.resolveUserId("Bearer " + token).isPresent());
        assertEquals(42L, restartedService.resolveUserId("Bearer " + token).get());
    }
}
