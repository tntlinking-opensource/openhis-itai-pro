/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.peisconfig.service;

import com.openhis.health.domain.peis.peisconfig.entity.PeisConfig;
import com.openhis.health.domain.peis.peisconfig.queries.model.PeisConfigQueryModel;
import com.openhis.health.domain.peis.peisconfig.repository.PeisConfigRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * PEIS 模块参数读侧查询服务（DM-PEIS-004）。
 *
 * <p>仅本聚合 {@link PeisConfigRepository}（B.6.4）。</p>
 */
@Service
@RequiredArgsConstructor
public class PeisConfigQueryService {

    private final PeisConfigRepository repository;

    /**
     * 按租户与键读取单条配置。
     *
     * @param tenantId  租户
     * @param configKey 配置键
     * @return 未软删实体
     */
    public PeisConfig getByKey(Long tenantId, String configKey) {
        // 软删过滤 → 仓储查询 → 组装 QueryModel（无写库）
        return findOptionalByKey(tenantId, configKey).orElseThrow(
            () -> new BusinessException(
                ErrorCodeConstants.PEIS_CONFIG_NOT_FOUND, "PEIS 配置不存在"));
    }

    /**
     * 按租户与键读取单条配置（不存在则 empty）。
     */
    public Optional<PeisConfig> findOptionalByKey(Long tenantId, String configKey) {
        if (tenantId == null || StringUtils.isEmpty(configKey)) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_CONFIG_INVALID_ARGUMENT, "租户与配置键不能为空");
        }
        return repository.findByTenantIdAndConfigKeyAndIsDeletedFalse(tenantId, configKey.trim());
    }

    /**
     * 分页检索租户下全部配置（configKey 升序）。
     *
     * @param tenantId 租户
     * @param pageable 分页
     * @return 分页读模型
     */
    public Page<PeisConfigQueryModel> searchConfigs(Long tenantId, Pageable pageable) {
        // 软删过滤 → 仓储查询 → 组装 QueryModel（无写库）
        if (tenantId == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_CONFIG_INVALID_ARGUMENT, "租户标识不能为空");
        }
        return repository.findByTenantIdAndIsDeletedFalseOrderByConfigKeyAsc(tenantId, pageable)
            .map(this::toQueryModel);
    }

    private PeisConfigQueryModel toQueryModel(PeisConfig entity) {
        PeisConfigQueryModel model = new PeisConfigQueryModel();
        model.setId(entity.getId());
        model.setConfigKey(entity.getConfigKey());
        model.setConfigValue(entity.getConfigValue());
        model.setOrgId(entity.getOrgId());
        return model;
    }
}
