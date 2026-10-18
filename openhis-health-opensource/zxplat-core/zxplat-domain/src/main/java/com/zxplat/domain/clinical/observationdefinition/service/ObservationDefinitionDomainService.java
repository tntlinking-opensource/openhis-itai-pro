/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.observationdefinition.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.domain.clinical.observationdefinition.command.DeactivateObservationDefinitionCommand;
import com.zxplat.domain.clinical.observationdefinition.command.DeleteObservationDefinitionCommand;
import com.zxplat.domain.clinical.observationdefinition.command.SaveObservationDefinitionCommand;
import com.zxplat.domain.clinical.observationdefinition.entity.ObservationDefinition;
import com.zxplat.domain.clinical.observationdefinition.repository.ObservationDefinitionRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.ObservationDefinitionConstants;
import com.zxplat.domain.share.events.clinical.observationdefinition.ObservationDefinitionDeactivatedEvent;
import com.zxplat.domain.share.events.clinical.observationdefinition.ObservationDefinitionSavedEvent;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 临床观察定义写侧领域服务（DM-CLN-005）。
 *
 * <p>支撑 PEIS CFG-03；不写观察实例。事件在 persist 成功后发布（beforeCommit 由框架处理）。</p>
 */
@Service
@RequiredArgsConstructor
public class ObservationDefinitionDomainService {

    private final ObservationDefinitionRepository repository;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 新建或更新观察定义目录行。
     *
     * @param cmd 保存命令
     * @return 持久化后的实体
     */
    public ObservationDefinition save(SaveObservationDefinitionCommand cmd) {
        // 前置校验：租户与核心字段
        if (cmd == null || cmd.getTenantId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_DEFINITION_INVALID_ARGUMENT, "租户标识不能为空");
        }
        String code = trimToNull(cmd.getCode());
        String display = trimToNull(cmd.getDisplay());
        String valueType = trimToNull(cmd.getValueTypeEnum());
        String category = trimToNull(cmd.getCategoryCode());
        if (StringUtils.isEmpty(code) || StringUtils.isEmpty(display)
            || StringUtils.isEmpty(valueType) || StringUtils.isEmpty(category)) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_DEFINITION_INVALID_ARGUMENT,
                "观察定义编码、名称、值类型与分类不能为空");
        }

        ObservationDefinition entity;
        if (cmd.getId() == null) {
            // 加载/组装聚合：新建
            entity = new ObservationDefinition();
            entity.setTenantId(cmd.getTenantId());
            // 唯一校验：租户内编码不可重复
            if (repository.existsByTenantIdAndCodeAndIsDeletedFalse(cmd.getTenantId(), code)) {
                throw new BusinessException(
                    ErrorCodeConstants.OBSERVATION_DEFINITION_CODE_CONFLICT, "观察定义编码已存在");
            }
        } else {
            // 加载/组装聚合：更新已有行
            entity = repository.findByIdAndIsDeletedFalse(cmd.getId()).orElseThrow(
                () -> new BusinessException(
                    ErrorCodeConstants.OBSERVATION_DEFINITION_NOT_FOUND, "观察定义不存在"));
            // 改码时校验新码未被其他行占用
            if (!code.equals(entity.getCode())
                && repository.existsByTenantIdAndCodeAndIsDeletedFalse(entity.getTenantId(), code)) {
                throw new BusinessException(
                    ErrorCodeConstants.OBSERVATION_DEFINITION_CODE_CONFLICT, "观察定义编码已存在");
            }
        }

        entity.setOrgId(cmd.getOrgId());
        entity.setCode(code);
        entity.setDisplay(display);
        entity.setValueTypeEnum(valueType);
        entity.setUnitCode(trimToNull(cmd.getUnitCode()));
        entity.setCategoryCode(category);
        String status = trimToNull(cmd.getStatusEnum());
        if (StringUtils.isEmpty(status)) {
            entity.setStatusEnum(ObservationDefinitionConstants.STATUS_ACTIVE);
        } else {
            entity.setStatusEnum(status);
        }
        entity.assertCoreFields();
        entity.assertCategoryAllowed();

        // 持久化
        ObservationDefinition saved = repository.save(entity);
        // 发布事件
        domainEventPublisher.publish(new ObservationDefinitionSavedEvent(
            saved.getId(), saved.getCode(), saved.getCategoryCode(), saved.getStatusEnum()));
        return saved;
    }

    /**
     * 停用观察定义（active → inactive；在检引用由 AS 预检，BR-CLN-OD-02）。
     *
     * @param cmd 停用命令
     * @return 停用后实体
     */
    public ObservationDefinition deactivate(DeactivateObservationDefinitionCommand cmd) {
        // 前置校验
        if (cmd == null || cmd.getId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_DEFINITION_INVALID_ARGUMENT, "观察定义主键不能为空");
        }
        // 加载/组装聚合
        ObservationDefinition entity = repository.findByIdAndIsDeletedFalse(cmd.getId()).orElseThrow(
            () -> new BusinessException(
                ErrorCodeConstants.OBSERVATION_DEFINITION_NOT_FOUND, "观察定义不存在"));
        // 状态迁移
        entity.deactivate();
        // 持久化
        ObservationDefinition saved = repository.save(entity);
        // 发布事件
        domainEventPublisher.publish(new ObservationDefinitionDeactivatedEvent(saved.getId()));
        return saved;
    }

    /**
     * 软删观察定义目录行（{@code is_deleted=true}；列表默认不可见）。
     *
     * @param cmd 删除命令
     */
    public void delete(DeleteObservationDefinitionCommand cmd) {
        // 前置校验
        if (cmd == null || cmd.getId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_DEFINITION_INVALID_ARGUMENT, "观察定义主键不能为空");
        }
        // 加载/组装聚合
        ObservationDefinition entity = repository.findByIdAndIsDeletedFalse(cmd.getId()).orElseThrow(
            () -> new BusinessException(
                ErrorCodeConstants.OBSERVATION_DEFINITION_NOT_FOUND, "观察定义不存在"));
        // 状态迁移：逻辑删除
        entity.markDeleted(cmd.getOperatorId());
        // 持久化
        repository.save(entity);
    }
}
