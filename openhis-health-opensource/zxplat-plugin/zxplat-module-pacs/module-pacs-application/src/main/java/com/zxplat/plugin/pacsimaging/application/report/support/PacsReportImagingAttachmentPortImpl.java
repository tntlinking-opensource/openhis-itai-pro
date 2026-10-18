/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.application.report.support;

import com.zxplat.domain.share.extension.pacsimaging.PacsReportImagingAttachmentPort;
import com.zxplat.domain.share.extension.pacsimaging.PacsReportImagingAttachmentView;
import com.zxplat.domain.share.extension.pacsimaging.PacsReportKeyImageView;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.plugin.pacsimaging.application.capture.port.MinioKeyImageStore;
import com.zxplat.plugin.pacsimaging.application.viewer.request.LaunchViewerRequest;
import com.zxplat.plugin.pacsimaging.application.viewer.response.LaunchViewerResponse;
import com.zxplat.plugin.pacsimaging.application.viewer.service.PacsViewerAppService;
import com.zxplat.plugin.pacsimaging.domain.imagingstudy.queries.ImagingStudyQueryService;
import com.zxplat.plugin.pacsimaging.domain.imagingstudy.queries.model.ImagingStudyQueryModel;
import com.zxplat.plugin.pacsimaging.domain.media.queries.MediaQueryService;
import com.zxplat.plugin.pacsimaging.domain.media.queries.model.MediaQueryModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 报告影像附件 Port：供 REP 书写/审核装配关键图与阅片深链。
 *
 * <p>无 Study / 无端点时降级为空阅片字段，不抛异常阻断报告。</p>
 */
@Component
@RequiredArgsConstructor
public class PacsReportImagingAttachmentPortImpl implements PacsReportImagingAttachmentPort {

    private final MediaQueryService mediaQueryService;
    private final ImagingStudyQueryService imagingStudyQueryService;
    private final MinioKeyImageStore minioKeyImageStore;
    private final PacsViewerAppService pacsViewerAppService;

    /**
     * 加载报告影像附件。
     *
     * <p>注释锚点：Pre Study/关键图 → 可选 Asm 阅片 URL。</p>
     *
     * @param workItemId 工单主键
     * @return 附件视图（永非 null）
     */
    @Override
    public PacsReportImagingAttachmentView loadAttachment(Long workItemId) {
        if (workItemId == null) {
            return PacsReportImagingAttachmentView.empty();
        }
        PacsReportImagingAttachmentView view = new PacsReportImagingAttachmentView();
        // Pre：Study
        Optional<ImagingStudyQueryModel> studyOptional = imagingStudyQueryService.findByWorkItemId(workItemId);
        if (studyOptional.isPresent()) {
            ImagingStudyQueryModel study = studyOptional.get();
            view.setHasStudy(Boolean.TRUE);
            view.setStudyInstanceUid(study.getStudyInstanceUid());
            this.tryFillViewer(view, workItemId);
        }
        // Pre：关键图预签名
        view.setKeyImages(this.loadKeyImages(workItemId));
        return view;
    }

    private void tryFillViewer(PacsReportImagingAttachmentView view, Long workItemId) {
        try {
            LaunchViewerRequest request = new LaunchViewerRequest();
            request.setWorkItemId(workItemId);
            LaunchViewerResponse launch = pacsViewerAppService.launchViewer(request);
            if (launch == null) {
                return;
            }
            view.setViewerAvailable(Boolean.TRUE);
            view.setViewerLaunchUrl(launch.getLaunchUrl());
            view.setViewerExpiresAt(launch.getExpiresAt());
            if (launch.getStudyInstanceUid() != null) {
                view.setStudyInstanceUid(launch.getStudyInstanceUid());
            }
        } catch (BusinessException ignored) {
            // 端点未配或 Study 异常：报告仍可写，仅无阅片深链
            view.setViewerAvailable(Boolean.FALSE);
            view.setViewerLaunchUrl(null);
            view.setViewerExpiresAt(null);
        }
    }

    private List<PacsReportKeyImageView> loadKeyImages(Long workItemId) {
        List<MediaQueryModel> models = mediaQueryService.listByWorkItemId(workItemId);
        List<PacsReportKeyImageView> rows = new ArrayList<>();
        for (MediaQueryModel model : models) {
            if (model == null) {
                continue;
            }
            PacsReportKeyImageView row = new PacsReportKeyImageView();
            row.setMediaId(model.getId());
            row.setContentType(model.getContentType());
            row.setSourceEnum(model.getSourceEnum());
            row.setCreatedAt(model.getCreatedAt());
            row.setPreviewUrl(minioKeyImageStore.presignGet(model.getObjectKey()));
            rows.add(row);
        }
        return rows;
    }
}
