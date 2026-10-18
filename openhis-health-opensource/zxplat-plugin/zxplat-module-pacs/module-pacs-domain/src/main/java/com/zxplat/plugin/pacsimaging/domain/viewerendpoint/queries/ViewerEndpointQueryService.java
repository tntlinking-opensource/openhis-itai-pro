/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.domain.viewerendpoint.queries;

import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import com.zxplat.plugin.pacsimaging.domain.viewerendpoint.entity.ViewerEndpoint;
import com.zxplat.plugin.pacsimaging.domain.viewerendpoint.queries.model.ViewerEndpointQueryModel;
import com.zxplat.plugin.pacsimaging.domain.viewerendpoint.repository.ViewerEndpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * ViewerEndpoint 读侧查询服务。
 */
@Service
@RequiredArgsConstructor
public class ViewerEndpointQueryService {

    private static final String MATCHED_ORG_MODALITY = "ORG_MODALITY";
    private static final String MATCHED_ORG_DEFAULT = "ORG_DEFAULT";
    private static final String MATCHED_GLOBAL = "GLOBAL";

    private final ViewerEndpointRepository viewerEndpointRepository;

    /**
     * 按机构列出端点；机构为空时列全局行。
     *
     * <p>注释锚点：软删过滤 → 一次仓储读取 → 组装 QueryModel。</p>
     *
     * @param organizationId 机构主键，可空
     * @return 读模型列表
     */
    public List<ViewerEndpointQueryModel> list(Long organizationId) {
        Long tenantId = TenantUtils.getCurrentTenantId();
        List<ViewerEndpoint> rows;
        if (organizationId == null) {
            rows = viewerEndpointRepository.findByTenantIdAndOrganizationIdIsNullOrderByCreatedAtDesc(tenantId);
        } else {
            rows = viewerEndpointRepository.findByTenantIdAndOrganizationIdOrderByCreatedAtDesc(tenantId, organizationId);
        }
        List<ViewerEndpointQueryModel> models = new ArrayList<>();
        for (ViewerEndpoint row : rows) {
            models.add(this.toModel(row, null));
        }
        return models;
    }

    /**
     * 解析启用端点：机构+模态 → 机构默认 → 全局默认。
     *
     * @param organizationId 机构主键，可空
     * @param modalityCode 模态码，可空
     * @return 可选读模型（含 matchedBy）
     */
    public Optional<ViewerEndpointQueryModel> resolve(Long organizationId, String modalityCode) {
        Long tenantId = TenantUtils.getCurrentTenantId();
        String normalizedModality = this.normalizeModalityCode(modalityCode);
        List<ViewerEndpoint> enabledRows = viewerEndpointRepository.findByTenantIdAndEnabledTrue(tenantId);
        if (normalizedModality != null) {
            Optional<ViewerEndpoint> orgModality = this.findFirst(enabledRows, organizationId, normalizedModality);
            if (orgModality.isPresent()) {
                return Optional.of(this.toModel(orgModality.get(), MATCHED_ORG_MODALITY));
            }
        }
        Optional<ViewerEndpoint> orgDefault = this.findFirst(enabledRows, organizationId, null);
        if (orgDefault.isPresent()) {
            return Optional.of(this.toModel(orgDefault.get(), MATCHED_ORG_DEFAULT));
        }
        Optional<ViewerEndpoint> global = this.findFirst(enabledRows, null, null);
        return global.map(row -> this.toModel(row, MATCHED_GLOBAL));
    }

    private Optional<ViewerEndpoint> findFirst(
        List<ViewerEndpoint> rows, Long organizationId, String modalityCode) {
        for (ViewerEndpoint row : rows) {
            if (!Objects.equals(row.getOrganizationId(), organizationId)) {
                continue;
            }
            if (!Objects.equals(this.normalizeModalityCode(row.getModalityCode()), modalityCode)) {
                continue;
            }
            return Optional.of(row);
        }
        return Optional.empty();
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

    private ViewerEndpointQueryModel toModel(ViewerEndpoint endpoint, String matchedBy) {
        ViewerEndpointQueryModel model = new ViewerEndpointQueryModel();
        model.setId(endpoint.getId());
        model.setOrganizationId(endpoint.getOrganizationId());
        model.setModalityCode(endpoint.getModalityCode());
        model.setBaseUrl(endpoint.getBaseUrl());
        model.setEnabled(endpoint.getEnabled());
        model.setMatchedBy(matchedBy);
        return model;
    }
}
