/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.config.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.pacsimaging.application.config.mapper.PacsViewerUrlConfigMapper;
import com.openhis.whale.pacsimaging.application.config.request.ResolveViewerEndpointRequest;
import com.openhis.whale.pacsimaging.application.config.request.SaveViewerEndpointRequest;
import com.openhis.whale.pacsimaging.application.config.response.ResolveViewerEndpointResponse;
import com.openhis.whale.pacsimaging.application.config.share.ImagingOperatorResolver;
import com.openhis.whale.pacsimaging.domain.viewerendpoint.queries.ViewerEndpointQueryService;
import com.openhis.whale.pacsimaging.domain.viewerendpoint.queries.model.ViewerEndpointQueryModel;
import com.openhis.whale.pacsimaging.domain.viewerendpoint.repository.ViewerEndpointRepository;
import com.openhis.whale.pacsimaging.domain.viewerendpoint.service.ViewerEndpointDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * {@link PacsViewerUrlConfigAppService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PacsViewerUrlConfigAppServiceTest {

    @Mock
    private ViewerEndpointRepository viewerEndpointRepository;

    @Mock
    private ViewerEndpointQueryService viewerEndpointQueryService;

    @Mock
    private ImagingOperatorResolver imagingOperatorResolver;

    private PacsViewerUrlConfigAppService appService;

    @BeforeEach
    void setUp() {
        ViewerEndpointDomainService domainService = new ViewerEndpointDomainService(viewerEndpointRepository);
        PacsViewerUrlConfigMapper mapper = new PacsViewerUrlConfigMapper();
        this.appService = new PacsViewerUrlConfigAppService(
            domainService, viewerEndpointQueryService, mapper, imagingOperatorResolver);
    }

    @Test
    void saveViewerEndpoint_rejectsInvalidBaseUrl() {
        when(imagingOperatorResolver.requireOperatorId()).thenReturn(9001L);

        SaveViewerEndpointRequest request = new SaveViewerEndpointRequest();
        request.setBaseUrl("ftp://bad.example/");
        request.setEnabled(Boolean.TRUE);

        try (MockedStatic<TenantUtils> tenantUtils = mockStatic(TenantUtils.class)) {
            tenantUtils.when(TenantUtils::getCurrentTenantId).thenReturn(1L);
            BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.saveViewerEndpoint(request));
            assertEquals(ErrorCodeConstants.PACS_VIEWER_ENDPOINT_INVALID, ex.getErrorCode());
        }
    }

    @Test
    void resolveViewerEndpoint_succeedsWithGlobalDefaultOnly() {
        ViewerEndpointQueryModel model = new ViewerEndpointQueryModel();
        model.setBaseUrl("https://ohif.example/");
        model.setMatchedBy("GLOBAL");
        when(viewerEndpointQueryService.resolve(null, "CT")).thenReturn(Optional.of(model));

        ResolveViewerEndpointRequest request = new ResolveViewerEndpointRequest();
        request.setOrgId(null);
        request.setModalityCode("CT");

        ResolveViewerEndpointResponse response = appService.resolveViewerEndpoint(request);

        assertEquals("https://ohif.example/", response.getBaseUrl());
        assertEquals("GLOBAL", response.getMatchedBy());
    }
}
