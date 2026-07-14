/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.encounterhistory.service;

import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.custom.encounterhistory.command.*;
import com.openhis.health.domain.custom.encounterhistory.entity.EncounterHistory;
import com.openhis.health.domain.custom.encounterhistory.repository.EncounterHistoryRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.custom.encounterhistory.*;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/**
 * 就诊历史里程碑（{@code cus_encounter_history}）聚合写侧领域服务。
 *
 * <p>职责：转科/离院/入科/床卡打印等里程碑记录、受控更正与废弃。</p>
 * <p>不变量：申请引用成对（BR-EHR）；跨聚合就诊校验由应用层 Gate 完成；持久化先于里程碑事件。</p>
 * <p>仅注入本聚合 {@link EncounterHistoryRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class EncounterHistoryDomainService {

    private final EncounterHistoryRepository repository;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 记录转科里程碑。
     *
     * @param command 命令
     * @return 新建里程碑标识
     * @throws BusinessException 必填字段或业务规则不满足时
     * @preconditions 就诊存在且未终止（BR-EHR-008）由应用服务在调用前完成跨聚合校验。
     */
    public Long recordTransferMilestone(RecordTransferMilestoneCommand command) {
        EncounterHistory history = new EncounterHistory();
        history.setName(CustomConstants.EncounterHistoryMilestoneName.TRANSFER);
        history.setEncounterId(command.getEncounterId());
        history.setRequestId(command.getRequestId());
        history.setRequestTableName(command.getRequestTableName());
        history.setOriginalOrganizationId(command.getOriginalOrganizationId());
        history.setTargetOrganizationId(command.getTargetOrganizationId());
        history.setOriginalLocationId(command.getOriginalLocationId());
        history.setTargetLocationId(command.getTargetLocationId());

        history.assertRequiredFields();
        history.assertRequestRefPair();
        history.assertTransferOrgTransitionNotEmpty();

        // 持久化
        EncounterHistory saved = repository.save(history);

        // 发布事件：转科里程碑已记录
        domainEventPublisher.publish(new TransferMilestoneRecordedEvent(saved.getId(), saved.getEncounterId(), saved.getRequestId(), command.getOperatorId()));
        return saved.getId();
    }

    /**
     * 记录离院里程碑。
     *
     * @param command 命令
     * @return 新建里程碑标识
     * @throws BusinessException 必填字段或业务规则不满足时
     * @preconditions 就诊存在等跨聚合校验由应用服务完成；出院方式字典校验若需跨域查询同由应用服务完成。
     */
    public Long recordDischargeMilestone(RecordDischargeMilestoneCommand command) {
        EncounterHistory history = new EncounterHistory();
        history.setName(CustomConstants.EncounterHistoryMilestoneName.DISCHARGE);
        history.setEncounterId(command.getEncounterId());
        history.setRequestId(command.getRequestId());
        history.setRequestTableName(command.getRequestTableName());
        history.setOriginalOrganizationId(command.getOriginalOrganizationId());
        history.setTargetOrganizationId(command.getTargetOrganizationId());
        history.setOriginalLocationId(command.getOriginalLocationId());
        history.setTargetLocationId(command.getTargetLocationId());
        history.setOutWayCode(command.getOutWayCode());

        history.assertRequiredFields();
        history.assertRequestRefPair();
        history.assertDischargeOutWayPresent();

        EncounterHistory saved = repository.save(history);

        // Event：发布离院里程碑已记录事件。
        domainEventPublisher.publish(new DischargeMilestoneRecordedEvent(saved.getId(), saved.getEncounterId(), saved.getOutWayCode(), command.getOperatorId()));
        return saved.getId();
    }

    /**
     * 记录床卡打印里程碑（患者管理打印审计）。
     *
     * @param encounterId 就诊标识
     * @param bedId 床位位置（可空）
     * @param operatorId 操作人
     * @return 里程碑主键
     */
    public Long recordBedCardPrintMilestone(Long encounterId, Long bedId, Long operatorId) {
        // 前置校验：就诊标识必填
        if (encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_HISTORY_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        EncounterHistory history = new EncounterHistory();
        history.setName(CustomConstants.EncounterHistoryMilestoneName.BED_CARD_PRINT);
        history.setEncounterId(encounterId);
        history.setTargetLocationId(bedId);
        history.assertRequiredFields();
        // 持久化
        EncounterHistory saved = repository.save(history);
        return saved.getId();
    }

    /**
     * 记录入科里程碑。
     *
     * @param command 命令
     * @return 新建里程碑标识
     * @throws BusinessException 目标科室为空时
     * @preconditions 就诊存在且为住院类型等跨聚合校验由应用服务完成。
     */
    public Long recordAdmissionMilestone(RecordAdmissionMilestoneCommand command) {
        if (command.getTargetOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_HISTORY_INVALID_ARGUMENT, "目标科室不能为空");
        }
        EncounterHistory history = new EncounterHistory();
        history.setName(CustomConstants.EncounterHistoryMilestoneName.ADMISSION_TO_DEPT);
        history.setEncounterId(command.getEncounterId());
        history.setTargetOrganizationId(command.getTargetOrganizationId());
        history.setTargetLocationId(command.getTargetLocationId());

        history.assertRequiredFields();

        EncounterHistory saved = repository.save(history);

        // Event：发布入科里程碑已记录事件。
        domainEventPublisher.publish(new AdmissionMilestoneRecordedEvent(saved.getId(), saved.getEncounterId(), command.getOperatorId()));
        return saved.getId();
    }

    /**
     * 更正里程碑。
     *
     * @param command 命令
     * @throws BusinessException 更正原因为空或里程碑不存在时
     */
    public void correctMilestone(CorrectMilestoneCommand command) {
        if (command.getReason() == null || command.getReason().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_HISTORY_INVALID_ARGUMENT, "更正原因不能为空");
        }
        // Pre：加载目标里程碑。
        EncounterHistory history = repository.findById(command.getEncounterHistoryId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ENCOUNTER_HISTORY_NOT_FOUND, "里程碑不存在"));

        // DOC-CONFLICT: DM/SDD 提到“新增更正行”策略，但表结构未包含“关联原行”等字段；本轮按“受控字段修改”落地。
        history.setOriginalOrganizationId(command.getOriginalOrganizationId());
        history.setTargetOrganizationId(command.getTargetOrganizationId());
        history.setOriginalLocationId(command.getOriginalLocationId());
        history.setTargetLocationId(command.getTargetLocationId());
        // 命令传入离院方式码时覆盖里程碑离院方式。
        if (command.getOutWayCode() != null) {
            history.setOutWayCode(command.getOutWayCode());
        }

        history.assertRequiredFields();
        history.assertRequestRefPair();
        // 持久化
        repository.save(history);

        // 发布事件：里程碑已更正
        domainEventPublisher.publish(new MilestoneCorrectedEvent(history.getId(), command.getReason(), command.getOperatorId()));
    }

    /**
     * 废弃里程碑（逻辑删除）。
     *
     * @param command 命令
     * @throws BusinessException 废弃原因为空或里程碑不存在时
     */
    public void retireMilestone(RetireMilestoneCommand command) {
        if (command.getReason() == null || command.getReason().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_HISTORY_INVALID_ARGUMENT, "废弃原因不能为空");
        }
        // Pre：加载目标里程碑。
        EncounterHistory history = repository.findById(command.getEncounterHistoryId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ENCOUNTER_HISTORY_NOT_FOUND, "里程碑不存在"));

        history.markRetired();

        repository.save(history);

        // Event：发布里程碑已废弃事件。
        domainEventPublisher.publish(new MilestoneRetiredEvent(history.getId(), command.getReason(), command.getOperatorId()));
    }
}
