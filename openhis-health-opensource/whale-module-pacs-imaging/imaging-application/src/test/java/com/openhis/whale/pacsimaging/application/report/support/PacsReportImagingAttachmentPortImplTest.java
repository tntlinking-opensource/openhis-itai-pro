/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.report.support;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.extension.pacsimaging.PacsReportImagingAttachmentView;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.pacsimaging.application.capture.port.MinioKeyImageStore;
import com.openhis.whale.pacsimaging.application.viewer.request.LaunchViewerRequest;
import com.openhis.whale.pacsimaging.application.viewer.response.LaunchViewerResponse;
import com.openhis.whale.pacsimaging.application.viewer.service.PacsViewerAppService;
import com.openhis.whale.pacsimaging.domain.imagingstudy.queries.ImagingStudyQueryService;
import com.openhis.whale.pacsimaging.domain.imagingstudy.queries.model.ImagingStudyQueryModel;
import com.openhis.whale.pacsimaging.domain.media.queries.MediaQueryService;
import com.openhis.whale.pacsimaging.domain.media.queries.model.MediaQueryModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link PacsReportImagingAttachmentPortImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PacsReportImagingAttachmentPortImplTest {

    @Mock
    private MediaQueryService mediaQueryService;

    @Mock
    private ImagingStudyQueryService imagingStudyQueryService;

    @Mock
    private MinioKeyImageStore minioKeyImageStore;

    @Mock
    private PacsViewerAppService pacsViewerAppService;

    private PacsReportImagingAttachmentPortImpl port;

    @BeforeEach
    void setUp() {
        port = new PacsReportImagingAttachmentPortImpl(
            mediaQueryService, imagingStudyQueryService, minioKeyImageStore, pacsViewerAppService);
    }

    @Test
    void loadAttachment_returnsKeyImagesAndViewer() {
        ImagingStudyQueryModel study = new ImagingStudyQueryModel();
        study.setStudyInstanceUid("1.2.3");
        when(imagingStudyQueryService.findByWorkItemId(11L)).thenReturn(Optional.of(study));

        LaunchViewerResponse launch = new LaunchViewerResponse();
        launch.setLaunchUrl("https://ohif/viewer?StudyInstanceUIDs=1.2.3&lng=zh&token=t");
        launch.setStudyInstanceUid("1.2.3");
        launch.setExpiresAt("2026-09-22T08:00:00Z");
        when(pacsViewerAppService.launchViewer(any(LaunchViewerRequest.class))).thenReturn(launch);

        MediaQueryModel media = new MediaQueryModel();
        media.setId(7L);
        media.setObjectKey("key-images/a.jpg");
        media.setContentType("image/jpeg");
        media.setSourceEnum("KEY_IMAGE");
        media.setCreatedAt(LocalDateTime.of(2026, 9, 22, 7, 0));
        when(mediaQueryService.listByWorkItemId(11L)).thenReturn(List.of(media));
        when(minioKeyImageStore.presignGet("key-images/a.jpg")).thenReturn("https://minio/a.jpg");

        PacsReportImagingAttachmentView view = port.loadAttachment(11L);

        assertTrue(view.getHasStudy());
        assertTrue(view.getViewerAvailable());
        assertEquals("https://ohif/viewer?StudyInstanceUIDs=1.2.3&lng=zh&token=t", view.getViewerLaunchUrl());
        assertEquals(1, view.getKeyImages().size());
        assertEquals("https://minio/a.jpg", view.getKeyImages().get(0).getPreviewUrl());
    }

    @Test
    void loadAttachment_degradesViewerWhenEndpointMissing() {
        ImagingStudyQueryModel study = new ImagingStudyQueryModel();
        study.setStudyInstanceUid("1.2.3");
        when(imagingStudyQueryService.findByWorkItemId(11L)).thenReturn(Optional.of(study));
        when(pacsViewerAppService.launchViewer(any())).thenThrow(
            new BusinessException(ErrorCodeConstants.PACS_VIEWER_ENDPOINT_NOT_FOUND, "未配置"));
        when(mediaQueryService.listByWorkItemId(11L)).thenReturn(List.of());

        PacsReportImagingAttachmentView view = port.loadAttachment(11L);

        assertTrue(view.getHasStudy());
        assertEquals("1.2.3", view.getStudyInstanceUid());
        assertFalse(Boolean.TRUE.equals(view.getViewerAvailable()));
        assertNull(view.getViewerLaunchUrl());
        assertTrue(view.getKeyImages().isEmpty());
    }

    @Test
    void loadAttachment_emptyWhenNoStudy() {
        when(imagingStudyQueryService.findByWorkItemId(11L)).thenReturn(Optional.empty());
        when(mediaQueryService.listByWorkItemId(11L)).thenReturn(List.of());

        PacsReportImagingAttachmentView view = port.loadAttachment(11L);

        assertFalse(Boolean.TRUE.equals(view.getHasStudy()));
        assertFalse(Boolean.TRUE.equals(view.getViewerAvailable()));
    }
}
