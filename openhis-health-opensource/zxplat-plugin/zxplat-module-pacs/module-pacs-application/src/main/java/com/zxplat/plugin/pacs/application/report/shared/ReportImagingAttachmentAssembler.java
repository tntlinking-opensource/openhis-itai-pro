/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.report.shared;

import com.zxplat.plugin.pacs.application.report.shared.dto.ReportImagingAttachmentDto;
import com.zxplat.plugin.pacs.application.report.shared.dto.ReportKeyImageDto;
import com.zxplat.domain.share.extension.pacsimaging.PacsReportImagingAttachmentPort;
import com.zxplat.domain.share.extension.pacsimaging.PacsReportImagingAttachmentView;
import com.zxplat.domain.share.extension.pacsimaging.PacsReportKeyImageView;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 报告影像附件装配：可选调用 imaging SPI，未启用时返回空附件。
 */
@Component
public class ReportImagingAttachmentAssembler {

    private final ObjectProvider<PacsReportImagingAttachmentPort> imagingAttachmentPortProvider;

    public ReportImagingAttachmentAssembler(
        ObjectProvider<PacsReportImagingAttachmentPort> imagingAttachmentPortProvider) {
        this.imagingAttachmentPortProvider = imagingAttachmentPortProvider;
    }

    /**
     * 装配报告影像附件。
     *
     * @param workItemId 工单主键
     * @param hasMedia 工单 hasMedia 标记
     * @return 非 null DTO；imaging 未启用或加载失败时关键图/阅片为空
     */
    public ReportImagingAttachmentDto assemble(Long workItemId, Boolean hasMedia) {
        ReportImagingAttachmentDto dto = new ReportImagingAttachmentDto();
        dto.setHasMedia(Boolean.TRUE.equals(hasMedia));
        PacsReportImagingAttachmentPort port = imagingAttachmentPortProvider.getIfAvailable();
        if (port == null || workItemId == null) {
            return dto;
        }
        PacsReportImagingAttachmentView view = port.loadAttachment(workItemId);
        if (view == null) {
            return dto;
        }
        dto.setHasStudy(Boolean.TRUE.equals(view.getHasStudy()));
        dto.setStudyInstanceUid(view.getStudyInstanceUid());
        dto.setViewerAvailable(Boolean.TRUE.equals(view.getViewerAvailable()));
        dto.setViewerLaunchUrl(view.getViewerLaunchUrl());
        dto.setViewerExpiresAt(view.getViewerExpiresAt());
        dto.setKeyImages(this.toKeyImageDtos(view.getKeyImages()));
        return dto;
    }

    private List<ReportKeyImageDto> toKeyImageDtos(List<PacsReportKeyImageView> views) {
        List<ReportKeyImageDto> rows = new ArrayList<>();
        if (views == null) {
            return rows;
        }
        for (PacsReportKeyImageView view : views) {
            if (view == null) {
                continue;
            }
            ReportKeyImageDto row = new ReportKeyImageDto();
            row.setMediaId(view.getMediaId());
            row.setContentType(view.getContentType());
            row.setSourceEnum(view.getSourceEnum());
            row.setPreviewUrl(view.getPreviewUrl());
            row.setCreatedAt(view.getCreatedAt());
            rows.add(row);
        }
        return rows;
    }
}
