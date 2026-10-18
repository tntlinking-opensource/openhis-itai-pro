/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.peisconfig.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.plugin.peis.domain.peisconfig.command.BatchSavePeisConfigCommand;
import com.zxplat.plugin.peis.domain.peisconfig.command.SavePeisConfigCommand;
import com.zxplat.plugin.peis.domain.peisconfig.entity.PeisConfig;
import com.zxplat.plugin.peis.domain.peisconfig.repository.PeisConfigRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.PeisCatalogConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * PEIS 模块参数写侧领域服务（DM-PEIS-004）。
 */
@Service
@RequiredArgsConstructor
public class PeisConfigDomainService {

    private final PeisConfigRepository repository;

    /**
     * 幂等 upsert 单键配置。
     *
     * @param cmd 保存命令
     * @return 持久化后的实体
     */
    public PeisConfig save(SavePeisConfigCommand cmd) {
        // 前置校验
        if (cmd == null || cmd.getTenantId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_CONFIG_INVALID_ARGUMENT, "租户标识不能为空");
        }
        String configKey = trimToNull(cmd.getConfigKey());
        if (StringUtils.isEmpty(configKey) || cmd.getConfigValue() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_CONFIG_INVALID_ARGUMENT, "配置键与值不能为空");
        }
        assertConfigKeyAllowed(configKey);

        // 加载/组装聚合
        PeisConfig entity;
        if (cmd.getId() != null) {
            entity = repository.findById(cmd.getId()).orElseThrow(
                () -> new BusinessException(
                    ErrorCodeConstants.PEIS_CONFIG_NOT_FOUND, "PEIS 配置不存在"));
            if (Boolean.TRUE.equals(entity.getIsDeleted())) {
                throw new BusinessException(
                    ErrorCodeConstants.PEIS_CONFIG_NOT_FOUND, "PEIS 配置不存在");
            }
        } else {
            Optional<PeisConfig> existing =
                repository.findByTenantIdAndConfigKeyAndIsDeletedFalse(cmd.getTenantId(), configKey);
            if (existing.isPresent()) {
                entity = existing.get();
            } else {
                entity = new PeisConfig();
                entity.setTenantId(cmd.getTenantId());
            }
        }
        entity.setOrgId(cmd.getOrgId());
        entity.setConfigKey(configKey);
        entity.setConfigValue(cmd.getConfigValue());
        entity.assertCoreFields();

        // 持久化
        return repository.save(entity);
    }

    /**
     * 批量保存配置（逐条 upsert）。
     *
     * @param cmd 批量命令
     * @return 持久化后的实体列表
     */
    public List<PeisConfig> batchSave(BatchSavePeisConfigCommand cmd) {
        // 前置校验
        if (cmd == null || cmd.getConfigs() == null || cmd.getConfigs().isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_CONFIG_INVALID_ARGUMENT, "批量配置至少包含一条");
        }

        // 加载/组装聚合
        List<PeisConfig> saved = new ArrayList<>();
        for (SavePeisConfigCommand item : cmd.getConfigs()) {
            saved.add(save(item));
        }

        // 持久化
        return saved;
    }

    private void assertConfigKeyAllowed(String configKey) {
        if (configKey.startsWith(PeisCatalogConstants.FORBIDDEN_SETTING_PREFIX)) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_CONFIG_KEY_FORBIDDEN,
                "配置键不可占用 framework Setting 前缀");
        }
        if (!configKey.startsWith(PeisCatalogConstants.CONFIG_KEY_PREFIX)) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_CONFIG_KEY_UNKNOWN,
                "配置键须以 peis. 为前缀");
        }
        if (!PeisCatalogConstants.ALLOWED_CONFIG_KEYS.contains(configKey)) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_CONFIG_KEY_UNKNOWN,
                "配置键不在允许白名单内");
        }
    }
}
