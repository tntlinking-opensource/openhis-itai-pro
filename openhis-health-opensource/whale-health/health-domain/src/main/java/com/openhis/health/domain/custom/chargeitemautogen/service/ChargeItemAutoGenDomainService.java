/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.chargeitemautogen.service;

import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.custom.chargeitemautogen.command.*;
import com.openhis.health.domain.custom.chargeitemautogen.entity.ChargeItemAutoGen;
import com.openhis.health.domain.custom.chargeitemautogen.repository.ChargeItemAutoGenRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.events.custom.chargeitemautogen.ChargeItemAutoGenEditedEvent;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;

/**
 * 收费明细自动生成规则（{@code cus_charge_item_auto_gen}）聚合写侧领域服务。
 *
 * <p>职责：按就诊+触发源+执行科室+定价行维护自动计费规则；组合键唯一（BR-CAG）。</p>
 * <p>不变量：DISCARDED 终态不可编辑；持久化先于 {@link ChargeItemAutoGenEditedEvent} 发布。</p>
 * <p>仅注入本聚合 {@link ChargeItemAutoGenRepository}。</p>
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
        // 数量未传时默认 1，保证自动计费有有效计量。
        if (command.getQuantityValue() == null) {
            rule.setQuantityValue(BigDecimal.ONE);
        } else {
            rule.setQuantityValue(command.getQuantityValue());
        }
        // 优先级未传时默认 0，多规则并存时按最低优先级参与匹配。
        if (command.getPriority() == null) {
            rule.setPriority(0);
        } else {
            rule.setPriority(command.getPriority());
        }
        rule.setRemark(command.getRemark());
        // 业务时刻未传时默认日末，与历史硬编码口径一致。
        LocalTime businessTime = command.getBusinessTime();
        if (businessTime == null) {
            businessTime = InpatientNurseStationConstants.DAY_END_CLOCK;
        }
        rule.setBusinessTime(businessTime);
        // 计费人未传时回落操作人，保证新建行可断言非空。
        Long billingPractitionerId = command.getBillingPractitionerId();
        if (billingPractitionerId == null) {
            billingPractitionerId = command.getOperatorId();
        }
        rule.setBillingPractitionerId(billingPractitionerId);

        // 状态未传时默认 ACTIVE（DM §6.1.1 缺省约定）。
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
        // 同就诊+触发源+执行科室+定价组合已存在则拒绝重复建规。
        if (exists) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_IDENTITY_CONFLICT, "规则组合键已存在");
        }

        // 持久化
        ChargeItemAutoGen saved = repository.save(rule);

        // 发布事件：规则已创建（OP_CREATE）
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

        // 命令传入规则名称时覆盖展示名称。
        if (command.getRuleName() != null) {
            rule.setRuleName(command.getRuleName());
        }
        // 命令传入住院就诊时覆盖规则归属就诊。
        if (command.getEncounterId() != null) {
            rule.setEncounterId(command.getEncounterId());
        }
        // 命令传入触发源表时覆盖上游业务表锚点。
        if (command.getTriggerSourceTable() != null) {
            rule.setTriggerSourceTable(command.getTriggerSourceTable());
        }
        // 命令传入触发源标识时覆盖上游业务主键。
        if (command.getTriggerSourceId() != null) {
            rule.setTriggerSourceId(command.getTriggerSourceId());
        }
        // 命令传入目标定价时覆盖计费项目定义。
        if (command.getChargeDefinitionId() != null) {
            rule.setChargeDefinitionId(command.getChargeDefinitionId());
        }
        // 命令传入执行科室时覆盖计费执行科室。
        if (command.getPerformingOrgId() != null) {
            rule.setPerformingOrgId(command.getPerformingOrgId());
        }
        // 命令传入数量时覆盖自动计费数量。
        if (command.getQuantityValue() != null) {
            rule.setQuantityValue(command.getQuantityValue());
        }
        // 命令传入优先级时覆盖规则匹配权重。
        if (command.getPriority() != null) {
            rule.setPriority(command.getPriority());
        }
        // 命令传入备注时覆盖规则说明。
        if (command.getRemark() != null) {
            rule.setRemark(command.getRemark());
        }
        // 命令传入业务时刻时覆盖滚费落账时钟。
        if (command.getBusinessTime() != null) {
            rule.setBusinessTime(command.getBusinessTime());
        }
        // 命令传入计费人时覆盖滚费操作人锚点。
        if (command.getBillingPractitionerId() != null) {
            rule.setBillingPractitionerId(command.getBillingPractitionerId());
        }
        // 存量无业务时刻时补日末，避免更新断言失败。
        if (rule.getBusinessTime() == null) {
            rule.setBusinessTime(InpatientNurseStationConstants.DAY_END_CLOCK);
        }
        // 存量无计费人时回落本次操作人。
        if (rule.getBillingPractitionerId() == null) {
            rule.setBillingPractitionerId(command.getOperatorId());
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
        // 同就诊+触发源+执行科室+定价组合已存在则拒绝重复建规。
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
