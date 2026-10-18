/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.config.settings.service;

import com.zxplat.plugin.peis.application.config.settings.dto.PeisConfigDto;
import com.zxplat.plugin.peis.application.config.settings.mapper.PeisModuleSettingsMapper;
import com.zxplat.plugin.peis.application.config.settings.request.SaveConfigRequest;
import com.zxplat.plugin.peis.application.config.settings.request.SearchConfigRequest;
import com.zxplat.plugin.peis.application.config.settings.response.SearchConfigResponse;
import com.zxplat.framework.core.application.paging.PageWindowGate;
import com.zxplat.framework.core.application.paging.PagedResultBuilder;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.plugin.peis.domain.peisconfig.entity.PeisConfig;
import com.zxplat.plugin.peis.domain.peisconfig.queries.model.PeisConfigQueryModel;
import com.zxplat.plugin.peis.domain.peisconfig.service.PeisConfigDomainService;
import com.zxplat.plugin.peis.domain.peisconfig.service.PeisConfigQueryService;
import com.zxplat.domain.share.constants.PeisCatalogConstants;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
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
    /** 分页窗口入站门控（B.10.11）。 */
    private final PageWindowGate pageWindowGate;

    /**
     * 分页检索 PEIS 模块参数（CFG-13）。
     */
    public SearchConfigResponse searchConfig(SearchConfigRequest request) {
        // Gate：请求体缺省时补默认分页，再校验窗口
        SearchConfigRequest query = ensureConfigRequest(request);
        pageWindowGate.requirePositivePageWindow(
            query, ErrorCodeConstants.PEIS_CONFIG_INVALID_ARGUMENT, "模块参数分页参数非法");
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

    /**
     * 缺省请求或未传分页键时补 pageNo=1 / pageSize=50。
     *
     * @param request 入站请求，可为 null
     * @return 非 null 且带合法分页窗口的请求
     */
    private SearchConfigRequest ensureConfigRequest(SearchConfigRequest request) {
        SearchConfigRequest query = request;
        if (query == null) {
            query = new SearchConfigRequest();
        }
        if (query.getPageNo() == null || query.getPageNo() < 1) {
            query.setPageNo(1);
        }
        if (query.getPageSize() == null || query.getPageSize() < 1) {
            query.setPageSize(50);
        }
        return query;
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
