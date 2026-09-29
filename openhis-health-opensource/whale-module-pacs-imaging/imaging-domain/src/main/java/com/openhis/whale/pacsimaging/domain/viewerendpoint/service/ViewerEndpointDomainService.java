/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.domain.viewerendpoint.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.pacsimaging.domain.viewerendpoint.command.SaveViewerEndpointCommand;
import com.openhis.whale.pacsimaging.domain.viewerendpoint.entity.ViewerEndpoint;
import com.openhis.whale.pacsimaging.domain.viewerendpoint.repository.ViewerEndpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * ViewerEndpoint 写侧领域服务。
 *
 * <p>职责：保存阅片根地址；启用时停用同键旧启用行。</p>
 */
@Service
@RequiredArgsConstructor
public class ViewerEndpointDomainService {

    private final ViewerEndpointRepository viewerEndpointRepository;

    /**
     * 保存阅片端点。
     *
     * <p>注释锚点：校验 URL → 停用同键旧行 → 插入或更新。</p>
     *
     * @param cmd 保存命令
     * @return 保存后的聚合根
     */
    public ViewerEndpoint save(SaveViewerEndpointCommand cmd) {
        this.validateSaveCommand(cmd);
        String modalityCode = this.normalizeModalityCode(cmd.getModalityCode());
        // 校验 URL
        ViewerEndpoint probe = new ViewerEndpoint();
        probe.assertBaseUrl(cmd.getBaseUrl());
        String baseUrl = cmd.getBaseUrl().trim();
        boolean enabled = Boolean.TRUE.equals(cmd.getEnabled());
        // 停用同键旧行
        if (enabled) {
            this.disableSameKeyEnabledRows(cmd.getTenantId(), cmd.getOrganizationId(), modalityCode, cmd.getId());
        }
        ViewerEndpoint endpoint;
        if (cmd.getId() != null) {
            endpoint = viewerEndpointRepository.findById(cmd.getId())
                .orElseThrow(() -> new BusinessException(
                    ErrorCodeConstants.PACS_VIEWER_ENDPOINT_NOT_FOUND, "阅片端点不存在"));
            endpoint.setUpdatedBy(cmd.getOperatorId());
        } else {
            endpoint = new ViewerEndpoint();
            endpoint.setTenantId(cmd.getTenantId());
            endpoint.setCreatedBy(cmd.getOperatorId());
            endpoint.setUpdatedBy(cmd.getOperatorId());
        }
        endpoint.setOrganizationId(cmd.getOrganizationId());
        endpoint.setModalityCode(modalityCode);
        endpoint.setBaseUrl(baseUrl);
        endpoint.setEnabled(enabled);
        return viewerEndpointRepository.save(endpoint);
    }

    private void disableSameKeyEnabledRows(Long tenantId, Long organizationId, String modalityCode, Long excludeId) {
        List<ViewerEndpoint> enabledRows = viewerEndpointRepository.findByTenantIdAndEnabledTrue(tenantId);
        for (ViewerEndpoint row : enabledRows) {
            if (excludeId != null && excludeId.equals(row.getId())) {
                continue;
            }
            if (!Objects.equals(row.getOrganizationId(), organizationId)) {
                continue;
            }
            if (!Objects.equals(this.normalizeModalityCode(row.getModalityCode()), modalityCode)) {
                continue;
            }
            row.disable();
            viewerEndpointRepository.save(row);
        }
    }

    private String normalizeModalityCode(String modalityCode) {
        if (StringUtils.isEmpty(modalityCode)) {
            return null;
        }
        String trimmed = modalityCode.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    private void validateSaveCommand(SaveViewerEndpointCommand cmd) {
        if (cmd == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_VIEWER_ENDPOINT_INVALID, "命令不能为空");
        }
        if (cmd.getEnabled() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_VIEWER_ENDPOINT_INVALID, "enabled 不能为空");
        }
        if (cmd.getTenantId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_VIEWER_ENDPOINT_INVALID, "tenantId 不能为空");
        }
        if (cmd.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_VIEWER_ENDPOINT_INVALID, "operatorId 不能为空");
        }
    }
}
