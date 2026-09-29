/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.viewer.service;

import com.openhis.health.domain.pacs.workitem.queries.model.PacsWorkItemDetailQueryModel;
import com.openhis.health.domain.pacs.workitem.service.PacsWorkItemQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.pacsimaging.application.viewer.request.LaunchViewerRequest;
import com.openhis.whale.pacsimaging.application.viewer.response.LaunchViewerResponse;
import com.openhis.whale.pacsimaging.application.viewer.token.ViewerTokenIssuer;
import com.openhis.whale.pacsimaging.domain.imagingstudy.queries.ImagingStudyQueryService;
import com.openhis.whale.pacsimaging.domain.imagingstudy.queries.model.ImagingStudyQueryModel;
import com.openhis.whale.pacsimaging.domain.viewerendpoint.queries.ViewerEndpointQueryService;
import com.openhis.whale.pacsimaging.domain.viewerendpoint.queries.model.ViewerEndpointQueryModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

/**
 * 影像阅片启动（AS-PACS-IM-002）。
 *
 * <p>接入：{@code POST /api/app/pacsViewer/{methodName}}。</p>
 */
@Service
@RequiredArgsConstructor
public class PacsViewerAppService {

    private final PacsWorkItemQueryService pacsWorkItemQueryService;
    private final ImagingStudyQueryService imagingStudyQueryService;
    private final ViewerEndpointQueryService viewerEndpointQueryService;
    private final ViewerTokenIssuer viewerTokenIssuer;

    /**
     * IF-PACS-VIEW-01：打开阅片。
     *
     * <p>注释锚点：Gate → Pre 工单/Study/端点 → Asm 拼 URL。</p>
     *
     * @param request 工单主键
     * @return 启动 URL 与过期时间
     */
    public LaunchViewerResponse launchViewer(LaunchViewerRequest request) {
        // Gate
        if (request == null || request.getWorkItemId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_NOT_FOUND, "workItemId 不能为空");
        }
        // Pre：工单
        PacsWorkItemDetailQueryModel workItem = pacsWorkItemQueryService.getDetail(request.getWorkItemId());
        // Pre：Study
        ImagingStudyQueryModel study = imagingStudyQueryService.findByWorkItemId(workItem.getId())
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.PACS_IMAGING_STUDY_NOT_FOUND, "尚未绑定 Study，无法打开阅片"));
        if (StringUtils.isEmpty(study.getStudyInstanceUid())) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_IMAGING_STUDY_NOT_FOUND, "StudyUID 为空，无法打开阅片");
        }
        // Pre：端点
        ViewerEndpointQueryModel endpoint = viewerEndpointQueryService
            .resolve(workItem.getOrganizationId(), workItem.getModalityCode())
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.PACS_VIEWER_ENDPOINT_NOT_FOUND, "未配置启用的阅片端点"));
        // Asm
        ViewerTokenIssuer.IssuedViewerToken issued = viewerTokenIssuer.issue(study.getStudyInstanceUid());
        String launchUrl = this.buildLaunchUrl(
            endpoint.getBaseUrl(), study.getStudyInstanceUid(), issued.token());
        LaunchViewerResponse response = new LaunchViewerResponse();
        response.setLaunchUrl(launchUrl);
        response.setStudyInstanceUid(study.getStudyInstanceUid());
        response.setExpiresAt(DateTimeFormatter.ISO_INSTANT.format(issued.expiresAt()));
        return response;
    }

    private String buildLaunchUrl(String baseUrl, String studyInstanceUid, String token) {
        String root = "";
        if (baseUrl != null) {
            root = baseUrl.trim();
        }
        if (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        String encodedUid = URLEncoder.encode(studyInstanceUid, StandardCharsets.UTF_8);
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        return root + "/viewer?StudyInstanceUIDs=" + encodedUid
            + "&lng=zh&token=" + encodedToken;
    }
}
