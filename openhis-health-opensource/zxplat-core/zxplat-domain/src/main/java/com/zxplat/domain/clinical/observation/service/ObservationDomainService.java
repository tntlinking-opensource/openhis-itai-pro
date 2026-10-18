/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.observation.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.domain.clinical.observation.command.CancelObservationCommand;
import com.zxplat.domain.clinical.observation.command.CreateObservationCommand;
import com.zxplat.domain.clinical.observation.command.FinalizeObservationCommand;
import com.zxplat.domain.clinical.observation.command.UpdateObservationCommand;
import com.zxplat.domain.clinical.observation.entity.Observation;
import com.zxplat.domain.clinical.observation.repository.ObservationRepository;
import com.zxplat.domain.clinical.observationdefinition.entity.ObservationDefinition;
import com.zxplat.domain.clinical.observationdefinition.service.ObservationDefinitionQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.ObservationDefinitionConstants;
import com.zxplat.domain.share.constants.ObservationStatusConstants;
import com.zxplat.domain.share.events.clinical.observation.ObservationCancelledEvent;
import com.zxplat.domain.share.events.clinical.observation.ObservationCreatedEvent;
import com.zxplat.domain.share.events.clinical.observation.ObservationFinalizedEvent;
import com.zxplat.domain.share.events.clinical.observation.ObservationUpdatedEvent;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 临床观察实例写侧领域服务（DM-CLN-007）。
 */
@Service
@RequiredArgsConstructor
public class ObservationDomainService {

    private final ObservationRepository repository;
    private final ObservationDefinitionQueryService observationDefinitionQueryService;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 新建观察实例；同 SR+definition 已存在则转更新（BR-CLN-OBS-02）。
     *
     * @param cmd 新建命令
     * @return 持久化后的实体
     */
    public Observation createObservation(CreateObservationCommand cmd) {
        // 前置校验：核心标识与 OD 启用态
        assertCreateCommand(cmd);
        ObservationDefinition definition = loadActiveDefinition(cmd.getDefinitionId());

        if (cmd.getServiceRequestId() != null && cmd.getDefinitionId() != null) {
            Optional<Observation> existing = repository.findByServiceRequestIdAndDefinitionIdAndIsDeletedFalse(
                cmd.getServiceRequestId(), cmd.getDefinitionId());
            if (existing.isPresent()) {
                UpdateObservationCommand updateCmd = new UpdateObservationCommand();
                updateCmd.setObservationId(existing.get().getId());
                updateCmd.setValueQuantity(cmd.getValueQuantity());
                updateCmd.setValueString(cmd.getValueString());
                updateCmd.setValueCodeableConcept(cmd.getValueCodeableConcept());
                updateCmd.setInterpretationCode(cmd.getInterpretationCode());
                updateCmd.setPerformerId(cmd.getPerformerId());
                return updateObservation(updateCmd);
            }
        }

        Observation entity = new Observation();
        entity.setTenantId(cmd.getTenantId());
        entity.setOrgId(cmd.getOrgId());
        entity.setEncounterId(cmd.getEncounterId());
        entity.setServiceRequestId(cmd.getServiceRequestId());
        entity.setDefinitionId(cmd.getDefinitionId());
        String code = trimToNull(cmd.getCode());
        if (StringUtils.isEmpty(code)) {
            entity.setCode(definition.getCode());
        } else {
            entity.setCode(code);
        }
        entity.applyValue(
            cmd.getValueQuantity(),
            trimToNull(cmd.getValueString()),
            trimToNull(cmd.getValueCodeableConcept()),
            trimToNull(cmd.getInterpretationCode()));
        entity.markPreliminary();
        entity.setPerformerId(cmd.getPerformerId());
        entity.assertManualNoSource();
        if (entity.getIsDeleted() == null) {
            entity.setIsDeleted(Boolean.FALSE);
        }

        // 持久化后发布 Created
        Observation saved = repository.save(entity);
        domainEventPublisher.publish(new ObservationCreatedEvent(
            saved.getId(),
            saved.getEncounterId(),
            saved.getServiceRequestId(),
            saved.getDefinitionId(),
            saved.getStatusEnum()));
        return saved;
    }

    /**
     * 更新观察实例；final 态改值走 amended（BR-CLN-OBS-03）。
     *
     * @param cmd 更新命令
     * @return 持久化后的实体
     */
    public Observation updateObservation(UpdateObservationCommand cmd) {
        if (cmd == null || cmd.getObservationId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "观察实例主键不能为空");
        }
        Observation entity = repository.findById(cmd.getObservationId()).orElseThrow(
            () -> new BusinessException(
                ErrorCodeConstants.OBSERVATION_NOT_FOUND, "观察实例不存在"));
        if (Boolean.TRUE.equals(entity.getIsDeleted())) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_NOT_FOUND, "观察实例不存在");
        }
        if (ObservationStatusConstants.STATUS_CANCELLED.equals(entity.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_STATUS, "已作废的观察实例不可修改");
        }
        // final 后改值须先迁 amended
        if (ObservationStatusConstants.STATUS_FINAL.equals(entity.getStatusEnum())) {
            entity.markAmended();
        }
        entity.applyValue(
            cmd.getValueQuantity(),
            trimToNull(cmd.getValueString()),
            trimToNull(cmd.getValueCodeableConcept()),
            trimToNull(cmd.getInterpretationCode()));
        if (cmd.getPerformerId() != null) {
            entity.setPerformerId(cmd.getPerformerId());
        }
        entity.assertManualNoSource();
        entity.setUpdatedAt(LocalDateTime.now());

        Observation saved = repository.save(entity);
        domainEventPublisher.publish(new ObservationUpdatedEvent(
            saved.getId(),
            saved.getEncounterId(),
            saved.getServiceRequestId(),
            saved.getDefinitionId(),
            saved.getStatusEnum()));
        return saved;
    }

    /**
     * 确认观察实例为 final。
     *
     * @param cmd 确认命令
     * @return 持久化后的实体
     */
    public Observation finalizeObservation(FinalizeObservationCommand cmd) {
        if (cmd == null || cmd.getObservationId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "观察实例主键不能为空");
        }
        Observation entity = repository.findById(cmd.getObservationId()).orElseThrow(
            () -> new BusinessException(
                ErrorCodeConstants.OBSERVATION_NOT_FOUND, "观察实例不存在"));
        if (Boolean.TRUE.equals(entity.getIsDeleted())) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_NOT_FOUND, "观察实例不存在");
        }
        if (ObservationStatusConstants.STATUS_CANCELLED.equals(entity.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_STATUS, "已作废的观察实例不可确认");
        }
        entity.finalizeObservation();
        if (cmd.getOperatorId() != null) {
            entity.setPerformerId(cmd.getOperatorId());
        }
        entity.setUpdatedAt(LocalDateTime.now());

        Observation saved = repository.save(entity);
        domainEventPublisher.publish(new ObservationFinalizedEvent(
            saved.getId(),
            saved.getEncounterId(),
            saved.getServiceRequestId(),
            saved.getDefinitionId()));
        return saved;
    }

    /**
     * 作废观察实例。
     *
     * @param cmd 作废命令
     * @return 持久化后的实体
     */
    public Observation cancelObservation(CancelObservationCommand cmd) {
        if (cmd == null || cmd.getObservationId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "观察实例主键不能为空");
        }
        Observation entity = repository.findById(cmd.getObservationId()).orElseThrow(
            () -> new BusinessException(
                ErrorCodeConstants.OBSERVATION_NOT_FOUND, "观察实例不存在"));
        if (Boolean.TRUE.equals(entity.getIsDeleted())) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_NOT_FOUND, "观察实例不存在");
        }
        entity.cancel(trimToNull(cmd.getReason()));
        entity.setUpdatedAt(LocalDateTime.now());

        Observation saved = repository.save(entity);
        domainEventPublisher.publish(new ObservationCancelledEvent(saved.getId(), cmd.getReason()));
        return saved;
    }

    private void assertCreateCommand(CreateObservationCommand cmd) {
        if (cmd == null || cmd.getTenantId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "租户标识不能为空");
        }
        if (cmd.getEncounterId() == null || cmd.getDefinitionId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "就诊与观察定义不能为空");
        }
    }

    private ObservationDefinition loadActiveDefinition(Long definitionId) {
        // 加载候选：经 OD QS，与 DES 预检同口径（B.6.4）
        ObservationDefinition definition = observationDefinitionQueryService.findOptionalById(definitionId)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.OBSERVATION_DEFINITION_INACTIVE, "观察定义不存在或已停用"));
        if (!ObservationDefinitionConstants.STATUS_ACTIVE.equals(definition.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_DEFINITION_INACTIVE, "观察定义已停用");
        }
        return definition;
    }
}
