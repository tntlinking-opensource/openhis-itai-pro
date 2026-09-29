/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.viewer.service;

import com.openhis.health.domain.pacs.workitem.queries.model.PacsWorkItemDetailQueryModel;
import com.openhis.health.domain.pacs.workitem.service.PacsWorkItemQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.pacsimaging.application.viewer.request.LaunchViewerRequest;
import com.openhis.whale.pacsimaging.application.viewer.response.LaunchViewerResponse;
import com.openhis.whale.pacsimaging.application.viewer.token.ViewerTokenIssuer;
import com.openhis.whale.pacsimaging.domain.imagingstudy.queries.ImagingStudyQueryService;
import com.openhis.whale.pacsimaging.domain.imagingstudy.queries.model.ImagingStudyQueryModel;
import com.openhis.whale.pacsimaging.domain.viewerendpoint.queries.ViewerEndpointQueryService;
import com.openhis.whale.pacsimaging.domain.viewerendpoint.queries.model.ViewerEndpointQueryModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link PacsViewerAppService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PacsViewerAppServiceTest {

    @Mock
    private PacsWorkItemQueryService pacsWorkItemQueryService;

    @Mock
    private ImagingStudyQueryService imagingStudyQueryService;

    @Mock
    private ViewerEndpointQueryService viewerEndpointQueryService;

    private PacsViewerAppService appService;

    @BeforeEach
    void setUp() {
        appService = new PacsViewerAppService(
            pacsWorkItemQueryService,
            imagingStudyQueryService,
            viewerEndpointQueryService,
            new ViewerTokenIssuer());
    }

    @Test
    void launchViewer_rejectsWhenStudyMissing() {
        PacsWorkItemDetailQueryModel workItem = new PacsWorkItemDetailQueryModel();
        workItem.setId(11L);
        workItem.setOrganizationId(100L);
        workItem.setModalityCode("CT");
        when(pacsWorkItemQueryService.getDetail(11L)).thenReturn(workItem);
        when(imagingStudyQueryService.findByWorkItemId(11L)).thenReturn(Optional.empty());

        LaunchViewerRequest request = new LaunchViewerRequest();
        request.setWorkItemId(11L);

        BusinessException ex = assertThrows(BusinessException.class, () -> appService.launchViewer(request));
        assertEquals(ErrorCodeConstants.PACS_IMAGING_STUDY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void launchViewer_buildsUrlWithStudyUidAndZh() {
        PacsWorkItemDetailQueryModel workItem = new PacsWorkItemDetailQueryModel();
        workItem.setId(11L);
        workItem.setOrganizationId(100L);
        workItem.setModalityCode("CT");
        when(pacsWorkItemQueryService.getDetail(11L)).thenReturn(workItem);

        ImagingStudyQueryModel study = new ImagingStudyQueryModel();
        study.setWorkItemId(11L);
        study.setStudyInstanceUid("1.2.3.4.5");
        when(imagingStudyQueryService.findByWorkItemId(11L)).thenReturn(Optional.of(study));

        ViewerEndpointQueryModel endpoint = new ViewerEndpointQueryModel();
        endpoint.setBaseUrl("https://ohif.example.com");
        endpoint.setMatchedBy("ORG_MODALITY");
        when(viewerEndpointQueryService.resolve(eq(100L), eq("CT"))).thenReturn(Optional.of(endpoint));

        LaunchViewerRequest request = new LaunchViewerRequest();
        request.setWorkItemId(11L);

        LaunchViewerResponse response = appService.launchViewer(request);
        assertEquals("1.2.3.4.5", response.getStudyInstanceUid());
        assertTrue(response.getLaunchUrl().contains("/viewer?StudyInstanceUIDs=1.2.3.4.5"));
        assertTrue(response.getLaunchUrl().contains("lng=zh"));
        assertTrue(response.getLaunchUrl().contains("token="));
        assertTrue(response.getExpiresAt() != null && !response.getExpiresAt().isEmpty());
    }
}
