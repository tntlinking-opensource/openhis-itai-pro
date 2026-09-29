/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.device.service;

import com.openhis.health.domain.pacs.device.command.SavePacsDeviceCommand;
import com.openhis.health.domain.pacs.device.entity.PacsDevice;
import com.openhis.health.domain.pacs.device.repository.PacsDeviceRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PacsDeviceDomainService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PacsDeviceDomainServiceTest {

    @Mock
    private PacsDeviceRepository deviceRepository;

    @InjectMocks
    private PacsDeviceDomainService domainService;

    @Test
    void save_happyPath_persistsEntity() {
        SavePacsDeviceCommand cmd = new SavePacsDeviceCommand();
        cmd.setDeviceCode("CT-01");
        cmd.setName("CT 一号机");
        cmd.setModalityCode("CT");
        cmd.setAeTitle("CT_AE");
        cmd.setTenantId(1L);
        cmd.setOperatorId(900L);

        when(deviceRepository.findByTenantIdAndDeviceCode(1L, "CT-01"))
            .thenReturn(Optional.empty());
        when(deviceRepository.save(any(PacsDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PacsDevice saved = domainService.save(cmd);

        assertNotNull(saved);
        assertEquals("CT-01", saved.getDeviceCode());
        assertEquals("CT 一号机", saved.getName());
        assertEquals("CT", saved.getModalityCode());
        verify(deviceRepository).save(any(PacsDevice.class));
    }

    @Test
    void save_duplicateDeviceCode_throwsCodeDuplicate() {
        SavePacsDeviceCommand cmd = new SavePacsDeviceCommand();
        cmd.setDeviceCode("CT-01");
        cmd.setName("CT 二号机");
        cmd.setModalityCode("CT");
        cmd.setTenantId(1L);
        cmd.setOperatorId(900L);

        PacsDevice existing = new PacsDevice();
        existing.setId(10L);
        existing.setDeviceCode("CT-01");
        existing.setTenantId(1L);
        existing.setIsDeleted(Boolean.FALSE);

        when(deviceRepository.findByTenantIdAndDeviceCode(eq(1L), eq("CT-01")))
            .thenReturn(Optional.of(existing));

        BusinessException exception = assertThrows(BusinessException.class, () -> domainService.save(cmd));
        assertEquals(ErrorCodeConstants.PACS_DEVICE_CODE_DUPLICATE, exception.getErrorCode());
    }
}
