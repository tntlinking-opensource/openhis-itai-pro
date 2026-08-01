/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.config.settings.service;

import com.openhis.health.application.peis.config.settings.dto.PeisConfigDto;
import com.openhis.health.application.peis.config.settings.mapper.PeisModuleSettingsMapper;
import com.openhis.health.application.peis.config.settings.request.SaveConfigRequest;
import com.openhis.health.application.peis.config.settings.request.SearchConfigRequest;
import com.openhis.health.application.peis.config.settings.response.SearchConfigResponse;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.domain.peis.peisconfig.entity.PeisConfig;
import com.openhis.health.domain.peis.peisconfig.queries.model.PeisConfigQueryModel;
import com.openhis.health.domain.peis.peisconfig.service.PeisConfigDomainService;
import com.openhis.health.domain.peis.peisconfig.service.PeisConfigQueryService;
import com.openhis.health.domain.share.constants.PeisCatalogConstants;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * PEIS 系统参数配置应用服务（AS-PEIS-CFG-003）。
 *
 * <p>维护 {@code peis_config} 租户级 KV 参数（CFG-13）。</p>
 */
@Service
@RequiredArgsConstructor
public class PeisModuleSettingsAppService {

    private final PeisConfigQueryService peisConfigQueryService;
    private final PeisConfigDomainService peisConfigDomainService;
    private final PeisModuleSettingsMapper peisModuleSettingsMapper;

    /**
     * 分页检索 PEIS 模块参数（CFG-13）。
     */
    public SearchConfigResponse searchConfig(SearchConfigRequest request) {
        // Gate：请求体缺省时补默认分页
        SearchConfigRequest query = ensureConfigRequest(request);
        Long tenantId = TenantUtils.getCurrentTenantId();
        // Pre：领域分页查询
        Page<PeisConfigQueryModel> page = peisConfigQueryService.searchConfigs(
            tenantId,
            PageRequest.of(query.springPageIndex(), query.getBoundedPageSize()));
        List<PeisConfigDto> content = new ArrayList<>();
        for (PeisConfigQueryModel row : page.getContent()) {
            if (StringUtils.isNotEmpty(query.getConfigKey())
                && !query.getConfigKey().trim().equals(row.getConfigKey())) {
                continue;
            }
            content.add(peisModuleSettingsMapper.toDto(row));
        }
        // Asm：装配分页响应
        return PagedResultBuilder.build(new SearchConfigResponse(), content, page);
    }

    /**
     * 保存 PEIS 模块参数（CFG-13）。
     */
    public PeisConfigDto saveConfig(SaveConfigRequest request) {
        // Gate：必填字段与白名单校验
        validateSaveConfigRequest(request);
        // Cmd：领域 upsert 配置
        PeisConfig saved = peisConfigDomainService.save(
            peisModuleSettingsMapper.toSaveCommand(request, TenantUtils.getCurrentTenantId()));
        // Asm：映射 DTO
        return peisModuleSettingsMapper.toDto(saved);
    }

    private SearchConfigRequest ensureConfigRequest(SearchConfigRequest request) {
        if (request != null) {
            return request;
        }
        SearchConfigRequest defaults = new SearchConfigRequest();
        defaults.setPageNo(1);
        defaults.setPageSize(50);
        return defaults;
    }

    private void validateSaveConfigRequest(SaveConfigRequest request) {
        if (request == null) {
            throw new ValidationException("保存模块参数失败：请求不能为空");
        }
        if (StringUtils.isEmpty(request.getConfigKey())) {
            throw new ValidationException("保存模块参数失败：配置键不能为空");
        }
        if (request.getConfigValue() == null) {
            throw new ValidationException("保存模块参数失败：配置值不能为空");
        }
        String configKey = request.getConfigKey().trim();
        if (!PeisCatalogConstants.ALLOWED_CONFIG_KEYS.contains(configKey)) {
            throw new ValidationException("保存模块参数失败：配置键不在允许白名单内");
        }
    }
}
