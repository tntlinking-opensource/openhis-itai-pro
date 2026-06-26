/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.custom.chargeitemautogen.service;

import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.custom.chargeitemautogen.command.*;
import com.openhis.health.domain.custom.chargeitemautogen.entity.ChargeItemAutoGen;
import com.openhis.health.domain.custom.chargeitemautogen.repository.ChargeItemAutoGenRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.custom.chargeitemautogen.ChargeItemAutoGenEditedEvent;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;

/**
 * 收费明细自动生成规则写侧领域服务。
 */
@Service
@RequiredArgsConstructor
public class ChargeItemAutoGenDomainService {

    private final ChargeItemAutoGenRepository repository;
    private final IDomainEventPublisher domainEventPublisher;


    /**
     * 新增自动计费规则。
     *
     * @param command 新增命令
     * @return 新建规则主键（表 id）
     * @throws BusinessException 组合键冲突时
     *
     * <p>Pre：应用层须先断言 {@code chargeDefinitionId} 对应收费项定价存在。</p>
     */
    public Long createAutoGenRule(CreateAutoGenRuleCommand command) {
        ChargeItemAutoGen rule = new ChargeItemAutoGen();
        rule.setRuleName(command.getRuleName());
        rule.setEncounterId(command.getEncounterId());
        rule.setTriggerSourceTable(command.getTriggerSourceTable());
        rule.setTriggerSourceId(command.getTriggerSourceId());
        rule.setChargeDefinitionId(command.getChargeDefinitionId());
        rule.setPerformingOrgId(command.getPerformingOrgId());
        // 业务分支处理：数量缺省为 1。
        if (command.getQuantityValue() == null) {
            rule.setQuantityValue(BigDecimal.ONE);
        } else {
            rule.setQuantityValue(command.getQuantityValue());
        }
        // 业务分支处理：优先级缺省为 0。
        if (command.getPriority() == null) {
            rule.setPriority(0);
        } else {
            rule.setPriority(command.getPriority());
        }
        rule.setRemark(command.getRemark());

        // 业务分支处理：默认状态 ACTIVE（DM §6.1.1 缺省约定）。
        if (command.getStatusEnum() == null) {
            rule.initActiveStatus();
        } else {
            rule.setStatusEnum(command.getStatusEnum());
        }

        rule.assertRequiredFields();

        boolean exists = repository.existsByEncounterIdAndTriggerSourceTableAndTriggerSourceIdAndPerformingOrgIdAndChargeDefinitionId(
            rule.getEncounterId(),
            rule.getTriggerSourceTable(),
            rule.getTriggerSourceId(),
            rule.getPerformingOrgId(),
            rule.getChargeDefinitionId()
        );
        // 业务分支处理：组合键冲突时抛异常。
        if (exists) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_IDENTITY_CONFLICT, "规则组合键已存在");
        }

        ChargeItemAutoGen saved = repository.save(rule);

        // Event：发布新增成功事件（OP_CREATE）。
        domainEventPublisher.publish(new ChargeItemAutoGenEditedEvent(
            saved.getId(),
            CustomConstants.ChargeItemAutoGenOperation.OP_CREATE,
            saved.getTriggerSourceTable(),
            saved.getTriggerSourceId(),
            saved.getChargeDefinitionId()
        , command.getOperatorId()));
        return saved.getId();
    }

    /**
     * 更新自动计费规则（仅覆盖非空字段）。
     *
     * @param command 更新命令
     * @throws BusinessException 规则不存在、不可编辑、目标定价不存在或组合键冲突时
     */
    public void updateAutoGenRule(UpdateAutoGenRuleCommand command) {
        // Pre：校验目标行存在且未软删（BR-CAG-003/010）。
        ChargeItemAutoGen rule = loadMutableRule(command.getRuleId());

        // 业务分支处理：规则名称变更。
        if (command.getRuleName() != null) {
            rule.setRuleName(command.getRuleName());
        }
        // 业务分支处理：住院就诊变更。
        if (command.getEncounterId() != null) {
            rule.setEncounterId(command.getEncounterId());
        }
        // 业务分支处理：触发源表变更。
        if (command.getTriggerSourceTable() != null) {
            rule.setTriggerSourceTable(command.getTriggerSourceTable());
        }
        // 业务分支处理：触发源标识变更。
        if (command.getTriggerSourceId() != null) {
            rule.setTriggerSourceId(command.getTriggerSourceId());
        }
        // 业务分支处理：目标定价变更。
        if (command.getChargeDefinitionId() != null) {
            rule.setChargeDefinitionId(command.getChargeDefinitionId());
        }
        // 业务分支处理：执行科室标识变更。
        if (command.getPerformingOrgId() != null) {
            rule.setPerformingOrgId(command.getPerformingOrgId());
        }
        // 业务分支处理：数量值变更。
        if (command.getQuantityValue() != null) {
            rule.setQuantityValue(command.getQuantityValue());
        }
        // 业务分支处理：优先级变更。
        if (command.getPriority() != null) {
            rule.setPriority(command.getPriority());
        }
        // 业务分支处理：备注变更。
        if (command.getRemark() != null) {
            rule.setRemark(command.getRemark());
        }

        rule.assertRequiredFields();

        boolean exists = repository.existsByEncounterIdAndTriggerSourceTableAndTriggerSourceIdAndPerformingOrgIdAndChargeDefinitionIdAndIdNot(
            rule.getEncounterId(),
            rule.getTriggerSourceTable(),
            rule.getTriggerSourceId(),
            rule.getPerformingOrgId(),
            rule.getChargeDefinitionId(),
            rule.getId()
        );
        // 业务分支处理：组合键冲突时抛异常。
        if (exists) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_IDENTITY_CONFLICT, "规则组合键已存在");
        }

        ChargeItemAutoGen saved = repository.save(rule);

        // Event：发布更新成功事件（OP_UPDATE）。
        domainEventPublisher.publish(new ChargeItemAutoGenEditedEvent(
            saved.getId(),
            CustomConstants.ChargeItemAutoGenOperation.OP_UPDATE,
            saved.getTriggerSourceTable(),
            saved.getTriggerSourceId(),
            saved.getChargeDefinitionId()
        , command.getOperatorId()));
    }

    /**
     * 软删自动计费规则。
     *
     * @param command 删除命令
     * @throws BusinessException 规则不存在时
     */
    public void deleteAutoGenRule(DeleteAutoGenRuleCommand command) {
        // Pre：校验目标行存在且未软删（BR-CAG-010）。
        ChargeItemAutoGen rule = loadMutableRule(command.getRuleId());

        rule.markDeleted();

        ChargeItemAutoGen saved = repository.save(rule);

        // Event：发布删除成功事件（OP_DELETE）。
        domainEventPublisher.publish(new ChargeItemAutoGenEditedEvent(
            saved.getId(),
            CustomConstants.ChargeItemAutoGenOperation.OP_DELETE,
            saved.getTriggerSourceTable(),
            saved.getTriggerSourceId(),
            saved.getChargeDefinitionId()
        , command.getOperatorId()));
    }

    /**
     * 启用自动计费规则（INACTIVE -> ACTIVE）。
     *
     * @param command 启用命令
     * @throws BusinessException 规则不存在或状态不允许启用时
     */
    public void activateAutoGenRule(ActivateAutoGenRuleCommand command) {
        // Pre：校验目标行存在且未软删（BR-CAG-003/010）。
        ChargeItemAutoGen rule = loadMutableRule(command.getRuleId());

        rule.activate();

        ChargeItemAutoGen saved = repository.save(rule);

        // Event：发布状态变更事件（OP_STATUS_CHANGE）。
        domainEventPublisher.publish(new ChargeItemAutoGenEditedEvent(
            saved.getId(),
            CustomConstants.ChargeItemAutoGenOperation.OP_STATUS_CHANGE,
            saved.getTriggerSourceTable(),
            saved.getTriggerSourceId(),
            saved.getChargeDefinitionId()
        , command.getOperatorId()));
    }

    /**
     * 停用自动计费规则（ACTIVE -> INACTIVE）。
     *
     * @param command 停用命令
     * @throws BusinessException 规则不存在或状态不允许停用时
     */
    public void suspendAutoGenRule(SuspendAutoGenRuleCommand command) {
        // Pre：校验目标行存在且未软删（BR-CAG-003/010）。
        ChargeItemAutoGen rule = loadMutableRule(command.getRuleId());

        rule.suspend();

        ChargeItemAutoGen saved = repository.save(rule);

        // Event：发布状态变更事件（OP_STATUS_CHANGE）。
        domainEventPublisher.publish(new ChargeItemAutoGenEditedEvent(
            saved.getId(),
            CustomConstants.ChargeItemAutoGenOperation.OP_STATUS_CHANGE,
            saved.getTriggerSourceTable(),
            saved.getTriggerSourceId(),
            saved.getChargeDefinitionId()
        , command.getOperatorId()));
    }

    /**
     * 加载可变更的规则行：须存在、未软删，且非 DISCARDED 终态。
     */
    private ChargeItemAutoGen loadMutableRule(Long ruleId) {
        ChargeItemAutoGen rule = repository.findById(ruleId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_NOT_FOUND, "规则不存在"));
        if (Boolean.TRUE.equals(rule.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_NOT_FOUND, "规则不存在");
        }
        rule.assertEditable();
        return rule;
    }
}
