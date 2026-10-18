/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.miniapp.domain.auth.service;

import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.plugin.miniapp.domain.patientbinding.repository.MapPatientBindingRepository;
import com.zxplat.plugin.miniapp.domain.practitionerbinding.repository.MapPractitionerBindingRepository;
import com.zxplat.plugin.miniapp.domain.session.repository.MapSessionRepository;
import com.zxplat.plugin.miniapp.domain.wechataccount.repository.MapWechatAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * MapAuthDomainService 关键：登录/切换入参校验。
 */
@ExtendWith(MockitoExtension.class)
class MapAuthDomainServiceTest {

    @Mock
    private MapWechatAccountRepository mapWechatAccountRepository;
    @Mock
    private MapSessionRepository mapSessionRepository;
    @Mock
    private MapPatientBindingRepository mapPatientBindingRepository;
    @Mock
    private MapPractitionerBindingRepository mapPractitionerBindingRepository;

    @InjectMocks
    private MapAuthDomainService mapAuthDomainService;

    @Test
    void loginOrCreateSession_rejectsBlankAppIdOrOpenid() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> mapAuthDomainService.loginOrCreateSession(" ", "openid-1", null, 72));
        assertTrue(ex.getMessage().contains("appId"));
        verify(mapWechatAccountRepository, never()).save(any());

        ValidationException ex2 = assertThrows(ValidationException.class,
                () -> mapAuthDomainService.loginOrCreateSession("wx-app", "", null, 72));
        assertTrue(ex2.getMessage().contains("openid"));
        verify(mapSessionRepository, never()).save(any());
    }

    @Test
    void switchPatient_rejectsNullPatientId() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> mapAuthDomainService.switchPatient(1L, 2L, null));
        assertTrue(ex.getMessage().contains("patientId"));
        verify(mapPatientBindingRepository, never())
                .findFirstByTenantIdAndWechatAccountIdAndPatientIdAndUnboundAtIsNullAndIsDeletedFalse(
                        any(), any(), any());
    }

    @Test
    void bindPatient_rejectsNullPatientId() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> mapAuthDomainService.bindPatient(1L, null, "manual", null, null, null, true));
        assertTrue(ex.getMessage().contains("patientId"));
        verify(mapPatientBindingRepository, never()).save(any());
    }
}
