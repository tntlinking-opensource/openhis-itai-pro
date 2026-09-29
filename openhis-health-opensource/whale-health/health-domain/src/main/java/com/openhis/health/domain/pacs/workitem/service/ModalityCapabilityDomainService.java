/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.workitem.service;

import com.openhis.health.domain.pacs.workitem.command.SaveModalityCapabilityCommand;
import com.openhis.health.domain.pacs.workitem.entity.PacsModalityCapability;
import com.openhis.health.domain.pacs.workitem.repository.PacsModalityCapabilityRepository;
import com.openhis.health.domain.pacs.workitem.repository.PacsWorkItemOrderRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 模态能力写侧（AS-PACS-CFG-001）。
 *
 * <p>列表与接单覆盖读侧见 {@link ModalityCapabilityQueryService}。</p>
 */
@Service
@RequiredArgsConstructor
public class ModalityCapabilityDomainService {

    private final PacsModalityCapabilityRepository capabilityRepository;
    private final PacsWorkItemOrderRepository orderRepository;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

    /**
     * 新增或更新。
     *
     * @param cmd 命令
     * @return 实体
     */
    public PacsModalityCapability save(SaveModalityCapabilityCommand cmd) {
        // 前置校验：activity、模态、租户必填
        if (cmd == null || cmd.getActivityId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "activityId 不能为空");
        }
        if (StringUtils.isEmpty(cmd.getModalityCode())) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "modalityCode 不能为空");
        }
        if (cmd.getTenantId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "租户不能为空");
        }
        activityDefinitionQueryService.findOptionalActivityDefinitionEntityById(cmd.getActivityId())
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.ACTIVITY_DEFINITION_NOT_FOUND, "诊疗项目定义不存在"));

        PacsModalityCapability entity;
        // 加载/组装：有 id 更新；否则按租户+activity 复用已有行或新建
        if (cmd.getId() != null) {
            entity = capabilityRepository.findById(cmd.getId())
                .orElseThrow(() -> new BusinessException(
                    ErrorCodeConstants.PACS_CAPABILITY_NOT_FOUND, "模态能力配置不存在"));
        } else {
            Optional<PacsModalityCapability> existing = capabilityRepository
                .findByTenantIdAndActivityId(cmd.getTenantId(), cmd.getActivityId());
            if (existing.isPresent()) {
                entity = existing.get();
            } else {
                entity = new PacsModalityCapability();
                entity.setTenantId(cmd.getTenantId());
                entity.setCreatedBy(cmd.getOperatorId());
                if (entity.getIsDeleted() == null) {
                    entity.setIsDeleted(Boolean.FALSE);
                }
            }
        }
        entity.setActivityId(cmd.getActivityId());
        entity.setModalityCode(cmd.getModalityCode().trim());
        Boolean enabledFlag = Boolean.TRUE;
        if (cmd.getEnabledFlag() != null) {
            enabledFlag = cmd.getEnabledFlag();
        }
        entity.setEnabledFlag(enabledFlag);
        entity.setOrganizationId(cmd.getOrganizationId());
        entity.setUpdatedBy(cmd.getOperatorId());
        entity.setUpdatedAt(LocalDateTime.now());
        // 持久化
        return capabilityRepository.save(entity);
    }

    /**
     * 停用。
     *
     * @param id 主键
     * @param operatorId 操作人
     * @return 实体
     */
    public PacsModalityCapability disable(Long id, Long operatorId) {
        PacsModalityCapability entity = capabilityRepository.findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.PACS_CAPABILITY_NOT_FOUND, "模态能力配置不存在"));
        entity.setEnabledFlag(Boolean.FALSE);
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedAt(LocalDateTime.now());
        return capabilityRepository.save(entity);
    }

    /**
     * 软删（有在途 orderLink 引用则拒绝）。
     *
     * @param id 主键
     * @param operatorId 操作人
     */
    public void softDelete(Long id, Long operatorId) {
        PacsModalityCapability entity = capabilityRepository.findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.PACS_CAPABILITY_NOT_FOUND, "模态能力配置不存在"));
        // 有关联工单医嘱则拒绝删除，避免接单历史断链
        if (orderRepository.existsByActivityId(entity.getActivityId())) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_CAPABILITY_IN_USE, "存在关联工单医嘱，不可删除");
        }
        entity.setIsDeleted(Boolean.TRUE);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setDeletedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedAt(LocalDateTime.now());
        capabilityRepository.save(entity);
    }
}
