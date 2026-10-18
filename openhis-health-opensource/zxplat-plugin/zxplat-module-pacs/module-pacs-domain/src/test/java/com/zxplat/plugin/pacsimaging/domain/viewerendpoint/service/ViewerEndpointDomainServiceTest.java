/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.domain.viewerendpoint.service;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.plugin.pacsimaging.domain.viewerendpoint.command.SaveViewerEndpointCommand;
import com.zxplat.plugin.pacsimaging.domain.viewerendpoint.entity.ViewerEndpoint;
import com.zxplat.plugin.pacsimaging.domain.viewerendpoint.repository.ViewerEndpointRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ViewerEndpointDomainService} 业务规则单测。
 *
 * <p>覆盖：非法参数拒绝；启用时同机构+模态仅一条启用。</p>
 */
@ExtendWith(MockitoExtension.class)
class ViewerEndpointDomainServiceTest {

    @Mock
    private ViewerEndpointRepository viewerEndpointRepository;

    @InjectMocks
    private ViewerEndpointDomainService domainService;

    @Test
    void save_nullCommand_throws() {
        assertThrows(BusinessException.class, () -> domainService.save(null));
    }

    @Test
    void save_missingEnabled_throws() {
        SaveViewerEndpointCommand cmd = this.buildCommand(null, "https://ohif.example/", true);
        cmd.setEnabled(null);
        assertThrows(BusinessException.class, () -> domainService.save(cmd));
    }

    @Test
    void save_invalidBaseUrl_throws() {
        SaveViewerEndpointCommand cmd = this.buildCommand(null, "ftp://bad/", true);
        assertThrows(BusinessException.class, () -> domainService.save(cmd));
        verify(viewerEndpointRepository, never()).save(any());
    }

    @Test
    void save_enabled_disablesSameKeyOldRow() {
        ViewerEndpoint old = new ViewerEndpoint();
        old.setId(101L);
        old.setTenantId(1L);
        old.setOrganizationId(10L);
        old.setModalityCode("CT");
        old.setBaseUrl("https://old.example/");
        old.setEnabled(Boolean.TRUE);

        when(viewerEndpointRepository.findByTenantIdAndEnabledTrue(1L)).thenReturn(List.of(old));
        when(viewerEndpointRepository.save(any(ViewerEndpoint.class))).thenAnswer(invocation -> {
            ViewerEndpoint entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(202L);
            }
            return entity;
        });

        SaveViewerEndpointCommand cmd = this.buildCommand(null, "https://new.example/", true);
        cmd.setOrganizationId(10L);
        cmd.setModalityCode("CT");

        ViewerEndpoint saved = domainService.save(cmd);

        assertEquals(202L, saved.getId());
        assertTrue(Boolean.TRUE.equals(saved.getEnabled()));
        assertEquals("https://new.example/", saved.getBaseUrl());

        ArgumentCaptor<ViewerEndpoint> captor = ArgumentCaptor.forClass(ViewerEndpoint.class);
        verify(viewerEndpointRepository, times(2)).save(captor.capture());
        ViewerEndpoint disabled = captor.getAllValues().get(0);
        assertEquals(101L, disabled.getId());
        assertFalse(Boolean.TRUE.equals(disabled.getEnabled()));
    }

    @Test
    void save_updateExisting_keepsId() {
        ViewerEndpoint existing = new ViewerEndpoint();
        existing.setId(55L);
        existing.setTenantId(1L);
        existing.setOrganizationId(10L);
        existing.setModalityCode("MR");
        existing.setBaseUrl("https://old.example/");
        existing.setEnabled(Boolean.FALSE);

        when(viewerEndpointRepository.findById(55L)).thenReturn(Optional.of(existing));
        when(viewerEndpointRepository.findByTenantIdAndEnabledTrue(1L)).thenReturn(List.of());
        when(viewerEndpointRepository.save(any(ViewerEndpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SaveViewerEndpointCommand cmd = this.buildCommand(55L, "https://updated.example/", true);
        cmd.setOrganizationId(10L);
        cmd.setModalityCode("MR");

        ViewerEndpoint saved = domainService.save(cmd);
        assertEquals(55L, saved.getId());
        assertEquals("https://updated.example/", saved.getBaseUrl());
        assertTrue(Boolean.TRUE.equals(saved.getEnabled()));
    }

    private SaveViewerEndpointCommand buildCommand(Long id, String baseUrl, boolean enabled) {
        SaveViewerEndpointCommand cmd = new SaveViewerEndpointCommand();
        cmd.setId(id);
        cmd.setBaseUrl(baseUrl);
        cmd.setEnabled(enabled);
        cmd.setTenantId(1L);
        cmd.setOperatorId(900L);
        return cmd;
    }
}
