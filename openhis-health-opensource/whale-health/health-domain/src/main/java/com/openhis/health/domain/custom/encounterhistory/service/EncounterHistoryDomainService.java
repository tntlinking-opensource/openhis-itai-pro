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
 * 就诊历史写侧领域服务。
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

        EncounterHistory saved = repository.save(history);

        // Event：发布转科里程碑已记录事件。
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
        if (encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_HISTORY_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        EncounterHistory history = new EncounterHistory();
        history.setName(CustomConstants.EncounterHistoryMilestoneName.BED_CARD_PRINT);
        history.setEncounterId(encounterId);
        history.setTargetLocationId(bedId);
        history.assertRequiredFields();
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
     * @preconditions 更正策略（新增更正行 vs 受控修改）应由应用服务按场景选择；当前实现采用受控修改，并保留审计字段（BR-EHR-006）。
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
        // 业务分支处理。
        if (command.getOutWayCode() != null) {
            history.setOutWayCode(command.getOutWayCode());
        }

        history.assertRequiredFields();
        history.assertRequestRefPair();
        repository.save(history);

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
