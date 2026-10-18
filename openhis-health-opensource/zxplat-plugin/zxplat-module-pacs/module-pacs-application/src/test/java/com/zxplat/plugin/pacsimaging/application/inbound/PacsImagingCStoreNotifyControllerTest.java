/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.application.inbound;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantContext;
import com.zxplat.plugin.pacsimaging.application.capture.request.OnCStoreReceivedRequest;
import com.zxplat.plugin.pacsimaging.application.capture.response.OnCStoreReceivedResponse;
import com.zxplat.plugin.pacsimaging.application.capture.service.PacsImageCaptureAppService;
import com.zxplat.plugin.pacsimaging.application.config.ImagingMiddlewareProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PacsImagingCStoreNotifyController} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PacsImagingCStoreNotifyControllerTest {

    @Mock
    private PacsImageCaptureAppService pacsImageCaptureAppService;
    @Mock
    private TenantContext tenantContext;

    private ImagingMiddlewareProperties properties;
    private PacsImagingCStoreNotifyController controller;

    @BeforeEach
    void setUp() {
        this.properties = new ImagingMiddlewareProperties();
        this.properties.getNotify().setToken("zxplat-imaging-notify");
        this.properties.getNotify().setDefaultTenantId(9L);
        this.controller = new PacsImagingCStoreNotifyController(
            pacsImageCaptureAppService, properties, tenantContext);
    }

    @Test
    void cstoreNotify_rejectsWrongToken() {
        OnCStoreReceivedRequest request = new OnCStoreReceivedRequest();
        request.setAccessionNumber("ACC-1");
        request.setStudyInstanceUid("1.2.3");

        ResponseEntity<OnCStoreReceivedResponse> response =
            controller.cstoreNotify("bad-token", "9", request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(pacsImageCaptureAppService, never()).onCStoreReceived(any());
        verify(tenantContext, never()).setCurrentTenantId(any());
    }

    @Test
    void cstoreNotify_invokesAppServiceWhenTokenOk() {
        OnCStoreReceivedRequest request = new OnCStoreReceivedRequest();
        request.setAccessionNumber("ACC-1");
        request.setStudyInstanceUid("1.2.3");

        OnCStoreReceivedResponse appResponse = new OnCStoreReceivedResponse();
        appResponse.setWorkItemId(101L);
        appResponse.setStudyInstanceUid("1.2.3");
        when(pacsImageCaptureAppService.onCStoreReceived(any())).thenReturn(appResponse);

        ResponseEntity<OnCStoreReceivedResponse> response =
            controller.cstoreNotify("zxplat-imaging-notify", null, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(101L, response.getBody().getWorkItemId());
        verify(tenantContext).setCurrentTenantId(9L);
        verify(pacsImageCaptureAppService).onCStoreReceived(request);
        verify(tenantContext).setCurrentTenantId(null);
    }

    @Test
    void cstoreNotify_rejectsWhenTenantMissing() {
        properties.getNotify().setDefaultTenantId(null);
        OnCStoreReceivedRequest request = new OnCStoreReceivedRequest();
        request.setAccessionNumber("ACC-1");
        request.setStudyInstanceUid("1.2.3");

        BusinessException ex = assertThrows(
            BusinessException.class,
            () -> controller.cstoreNotify("zxplat-imaging-notify", null, request));
        assertEquals("TENANT_REQUIRED", ex.getErrorCode());
        verify(pacsImageCaptureAppService, never()).onCStoreReceived(any());
    }

    @Test
    void cstoreNotify_prefersHeaderTenantOverDefault() {
        OnCStoreReceivedRequest request = new OnCStoreReceivedRequest();
        request.setAccessionNumber("ACC-2");
        request.setStudyInstanceUid("1.2.4");
        when(pacsImageCaptureAppService.onCStoreReceived(any())).thenReturn(new OnCStoreReceivedResponse());

        ResponseEntity<OnCStoreReceivedResponse> response =
            controller.cstoreNotify("zxplat-imaging-notify", "42", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(tenantContext).setCurrentTenantId(42L);
        assertNull(response.getBody().getWorkItemId());
    }
}
