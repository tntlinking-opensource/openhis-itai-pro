/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.report.shared;

import com.zxplat.plugin.pacs.application.report.shared.dto.ReportImagingAttachmentDto;
import com.zxplat.domain.share.extension.pacsimaging.PacsReportImagingAttachmentPort;
import com.zxplat.domain.share.extension.pacsimaging.PacsReportImagingAttachmentView;
import com.zxplat.domain.share.extension.pacsimaging.PacsReportKeyImageView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link ReportImagingAttachmentAssembler} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ReportImagingAttachmentAssemblerTest {

    @Mock
    private ObjectProvider<PacsReportImagingAttachmentPort> portProvider;

    @Mock
    private PacsReportImagingAttachmentPort port;

    @Test
    void assemble_returnsEmptyWhenPortMissing() {
        when(portProvider.getIfAvailable()).thenReturn(null);
        ReportImagingAttachmentAssembler assembler = new ReportImagingAttachmentAssembler(portProvider);

        ReportImagingAttachmentDto dto = assembler.assemble(11L, Boolean.TRUE);

        assertTrue(dto.getHasMedia());
        assertFalse(Boolean.TRUE.equals(dto.getHasStudy()));
        assertTrue(dto.getKeyImages().isEmpty());
    }

    @Test
    void assemble_mapsPortView() {
        when(portProvider.getIfAvailable()).thenReturn(port);
        PacsReportImagingAttachmentView view = new PacsReportImagingAttachmentView();
        view.setHasStudy(Boolean.TRUE);
        view.setStudyInstanceUid("1.2.3");
        view.setViewerAvailable(Boolean.TRUE);
        view.setViewerLaunchUrl("https://ohif/x");
        PacsReportKeyImageView key = new PacsReportKeyImageView();
        key.setMediaId(1L);
        key.setPreviewUrl("https://minio/a.jpg");
        view.setKeyImages(List.of(key));
        when(port.loadAttachment(11L)).thenReturn(view);

        ReportImagingAttachmentAssembler assembler = new ReportImagingAttachmentAssembler(portProvider);
        ReportImagingAttachmentDto dto = assembler.assemble(11L, Boolean.TRUE);

        assertTrue(dto.getHasStudy());
        assertEquals("https://ohif/x", dto.getViewerLaunchUrl());
        assertEquals(1, dto.getKeyImages().size());
        assertEquals("https://minio/a.jpg", dto.getKeyImages().get(0).getPreviewUrl());
    }
}
