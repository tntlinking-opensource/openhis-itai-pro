/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.commonresult.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.plugin.peis.domain.commonresult.command.DeactivatePeisCommonResultCommand;
import com.zxplat.plugin.peis.domain.commonresult.command.SavePeisCommonResultCommand;
import com.zxplat.plugin.peis.domain.commonresult.entity.PeisCommonResult;
import com.zxplat.plugin.peis.domain.commonresult.repository.PeisCommonResultRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.PeisCatalogConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * PEIS 常见结果写侧领域服务（DM-PEIS-003）。
 */
@Service
@RequiredArgsConstructor
public class PeisCommonResultDomainService {

    private final PeisCommonResultRepository repository;

    /**
     * 新建或更新常见结果模板。
     *
     * @param cmd 保存命令
     * @return 持久化后的实体
     */
    public PeisCommonResult save(SavePeisCommonResultCommand cmd) {
        // 前置校验
        if (cmd == null || cmd.getTenantId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_COMMON_RESULT_INVALID_ARGUMENT, "租户标识不能为空");
        }
        String display = trimToNull(cmd.getDisplay());
        if (StringUtils.isEmpty(display)) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_COMMON_RESULT_INVALID_ARGUMENT, "常见结果名称不能为空");
        }
        String statusEnum = trimToNull(cmd.getStatusEnum());
        if (StringUtils.isEmpty(statusEnum)) {
            statusEnum = PeisCatalogConstants.COMMON_RESULT_STATUS_ACTIVE;
        }

        // 加载/组装聚合
        PeisCommonResult entity;
        if (cmd.getId() == null) {
            entity = new PeisCommonResult();
            entity.setTenantId(cmd.getTenantId());
            assertDisplayUnique(cmd.getTenantId(), cmd.getObservationDefinitionId(), cmd.getDeptOrgId(), display);
        } else {
            entity = repository.findByIdAndIsDeletedFalse(cmd.getId()).orElseThrow(
                () -> new BusinessException(
                    ErrorCodeConstants.PEIS_COMMON_RESULT_NOT_FOUND, "常见结果不存在"));
            if (!Objects.equals(entity.getTenantId(), cmd.getTenantId())) {
                throw new BusinessException(
                    ErrorCodeConstants.PEIS_COMMON_RESULT_INVALID_ARGUMENT, "租户与常见结果不匹配");
            }
            boolean displayChanged = !display.equals(entity.getDisplay());
            boolean odChanged = !Objects.equals(cmd.getObservationDefinitionId(), entity.getObservationDefinitionId());
            boolean deptChanged = !Objects.equals(cmd.getDeptOrgId(), entity.getDeptOrgId());
            if (displayChanged || odChanged || deptChanged) {
                assertDisplayUnique(
                    entity.getTenantId(), cmd.getObservationDefinitionId(), cmd.getDeptOrgId(), display);
            }
        }

        entity.setOrgId(cmd.getOrgId());
        entity.setObservationDefinitionId(cmd.getObservationDefinitionId());
        entity.setDeptOrgId(cmd.getDeptOrgId());
        entity.setDisplay(display);
        entity.setValueString(trimToNull(cmd.getValueString()));
        entity.setInterpretationCode(trimToNull(cmd.getInterpretationCode()));
        entity.setSortNum(cmd.getSortNum());
        entity.setStatusEnum(statusEnum);
        entity.assertCoreFields();

        // 持久化
        return repository.save(entity);
    }

    /**
     * 停用常见结果（active → inactive）。
     *
     * @param cmd 停用命令
     * @return 停用后实体
     */
    public PeisCommonResult deactivate(DeactivatePeisCommonResultCommand cmd) {
        // 前置校验
        if (cmd == null || cmd.getId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_COMMON_RESULT_INVALID_ARGUMENT, "常见结果主键不能为空");
        }

        // 加载/组装聚合
        PeisCommonResult entity = repository.findByIdAndIsDeletedFalse(cmd.getId()).orElseThrow(
            () -> new BusinessException(
                ErrorCodeConstants.PEIS_COMMON_RESULT_NOT_FOUND, "常见结果不存在"));
        entity.deactivate();

        // 持久化
        return repository.save(entity);
    }

    private void assertDisplayUnique(
        Long tenantId, Long observationDefinitionId, Long deptOrgId, String display) {
        boolean exists = repository
            .existsByTenantIdAndObservationDefinitionIdAndDeptOrgIdAndDisplayAndStatusEnumAndIsDeletedFalse(
                tenantId,
                observationDefinitionId,
                deptOrgId,
                display,
                PeisCatalogConstants.COMMON_RESULT_STATUS_ACTIVE);
        if (exists) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_COMMON_RESULT_DISPLAY_CONFLICT,
                "同租户、小项与科室下常见结果名称已存在");
        }
    }
}
