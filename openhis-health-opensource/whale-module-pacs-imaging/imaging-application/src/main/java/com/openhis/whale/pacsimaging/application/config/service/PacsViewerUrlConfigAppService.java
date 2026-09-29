/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.config.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.pacsimaging.application.config.dto.ViewerEndpointRowDto;
import com.openhis.whale.pacsimaging.application.config.mapper.PacsViewerUrlConfigMapper;
import com.openhis.whale.pacsimaging.application.config.request.ListViewerEndpointsRequest;
import com.openhis.whale.pacsimaging.application.config.request.ResolveViewerEndpointRequest;
import com.openhis.whale.pacsimaging.application.config.request.SaveViewerEndpointRequest;
import com.openhis.whale.pacsimaging.application.config.response.ListViewerEndpointsResponse;
import com.openhis.whale.pacsimaging.application.config.response.ResolveViewerEndpointResponse;
import com.openhis.whale.pacsimaging.application.config.response.SaveViewerEndpointResponse;
import com.openhis.whale.pacsimaging.application.config.share.ImagingOperatorResolver;
import com.openhis.whale.pacsimaging.domain.viewerendpoint.command.SaveViewerEndpointCommand;
import com.openhis.whale.pacsimaging.domain.viewerendpoint.entity.ViewerEndpoint;
import com.openhis.whale.pacsimaging.domain.viewerendpoint.queries.ViewerEndpointQueryService;
import com.openhis.whale.pacsimaging.domain.viewerendpoint.queries.model.ViewerEndpointQueryModel;
import com.openhis.whale.pacsimaging.domain.viewerendpoint.service.ViewerEndpointDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 阅片服务地址配置（AS-PACS-IM-003）。
 *
 * <p>接入：{@code POST /api/app/pacsViewerUrlConfig/{methodName}}。</p>
 */
@Service
@RequiredArgsConstructor
public class PacsViewerUrlConfigAppService {

    private final ViewerEndpointDomainService viewerEndpointDomainService;
    private final ViewerEndpointQueryService viewerEndpointQueryService;
    private final PacsViewerUrlConfigMapper pacsViewerUrlConfigMapper;
    private final ImagingOperatorResolver imagingOperatorResolver;

    /**
     * IF-PACS-IM-CFG-01：端点列表。
     *
     * @param request 机构过滤（可空）
     * @return 端点行
     */
    public ListViewerEndpointsResponse listViewerEndpoints(ListViewerEndpointsRequest request) {
        List<ViewerEndpointQueryModel> models = viewerEndpointQueryService.list(request.getOrgId());
        List<ViewerEndpointRowDto> records = pacsViewerUrlConfigMapper.toRowDtos(models);
        ListViewerEndpointsResponse response = new ListViewerEndpointsResponse();
        response.setRecords(records);
        return response;
    }

    /**
     * IF-PACS-IM-CFG-01：保存端点。
     *
     * @param request 保存入参
     * @return 保存结果
     */
    @Transactional
    public SaveViewerEndpointResponse saveViewerEndpoint(SaveViewerEndpointRequest request) {
        Long operatorId = imagingOperatorResolver.requireOperatorId();
        SaveViewerEndpointCommand cmd = new SaveViewerEndpointCommand();
        cmd.setId(request.getId());
        cmd.setOrganizationId(request.getOrgId());
        cmd.setModalityCode(request.getModalityCode());
        cmd.setBaseUrl(request.getBaseUrl());
        cmd.setEnabled(request.getEnabled());
        cmd.setTenantId(TenantUtils.getCurrentTenantId());
        cmd.setOperatorId(operatorId);
        ViewerEndpoint saved = viewerEndpointDomainService.save(cmd);
        return pacsViewerUrlConfigMapper.toSaveResponse(saved);
    }

    /**
     * IF-PACS-IM-CFG-02：匹配端点。
     *
     * @param request 机构/模态
     * @return 命中的根地址与匹配来源
     */
    public ResolveViewerEndpointResponse resolveViewerEndpoint(ResolveViewerEndpointRequest request) {
        ViewerEndpointQueryModel model = viewerEndpointQueryService
            .resolve(request.getOrgId(), request.getModalityCode())
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.PACS_VIEWER_ENDPOINT_NOT_FOUND, "未匹配到启用的阅片端点"));
        return pacsViewerUrlConfigMapper.toResolveResponse(model);
    }
}
