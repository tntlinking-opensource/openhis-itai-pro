/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.inpatient;

import com.openhis.health.application.shared.advice.InpatientOrderLookupLoader;
import com.openhis.health.domain.administration.encounter.command.RevertTransferOrDischargeHoldCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.custom.encounterhistory.command.RetireMilestoneCommand;
import com.openhis.health.domain.custom.encounterhistory.queries.model.EncounterHistoryMilestoneQueryModel;
import com.openhis.health.domain.custom.encounterhistory.service.EncounterHistoryDomainService;
import com.openhis.health.domain.custom.encounterhistory.service.EncounterHistoryQueryService;
import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestQueryModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 转科/出院 {@code ON_HOLD} 正向/逆向就诊编排（GAP-ATD-006/008）。
 *
 * <p>供住院医生站医嘱撤销与护士站校对退回共用，避免父级 {@code inpatient.support} 落点。</p>
 * <p>逆向须按<strong>当前撤销的</strong> {@code service_request.id} 对齐里程碑，禁止取时间线首条
 * （同就诊多次开立出院/转科后首条对不上会导致 {@code ON_HOLD} 悬挂，护士无法再校对）。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientTransferDischargeHoldOrchestrator {

    /** 诊疗申请读侧查询（转科/出院类别与已校对残留判定）。 */
    private final ServiceRequestQueryService serviceRequestQueryService;
    /** 就诊读侧查询（住院类与 ON_HOLD 状态校验）。 */
    private final EncounterQueryService encounterQueryService;
    /** 就诊里程碑读侧查询（按 requestId 对齐挂起医嘱）。 */
    private final EncounterHistoryQueryService encounterHistoryQueryService;
    /** 就诊里程碑写侧（回滚成功后废弃本条转科/出院里程碑，避免脏首条干扰）。 */
    private final EncounterHistoryDomainService encounterHistoryDomainService;
    /** 就诊写侧领域服务（转科/出院挂起逆向回滚）。 */
    private final EncounterDomainService encounterDomainService;
    /** 住院医嘱 → 就诊主键解析（读模型 order_type 分流）。 */
    private final InpatientOrderLookupLoader inpatientOrderLookupLoader;

    /**
     * 诊疗撤销（退回/签退/停嘱）后，若其为 ON_HOLD 里程碑且无异类已校对残留，则回滚就诊至 {@code IN_PROGRESS}。
     *
     * @param serviceRequestId  刚撤销的诊疗申请主键
     * @param operatorId        操作人
     * @param revertedAt        撤销时刻
     * @param conflictErrorCode 并发/状态冲突映射（如 EX-IP-AUD-203）
     */
    public void tryRevertHoldAfterServiceRequestReverted(
        Long serviceRequestId,
        Long operatorId,
        LocalDateTime revertedAt,
        String conflictErrorCode) {
        if (serviceRequestId == null) {
            return;
        }
        try {
            ServiceRequestQueryModel head = serviceRequestQueryService.mustGetServiceRequest(serviceRequestId);
            String category = head.getCategoryEnum();
            boolean isTransfer = InpatientNurseStationConstants.SERVICE_CATEGORY_TRANSFER.equals(category);
            boolean isDischarge = InpatientNurseStationConstants.SERVICE_CATEGORY_DISCHARGE.equals(category);
            // 非转科/出院类诊疗撤销不参与 ON_HOLD 回滚，避免误改就诊状态
            if (!isTransfer && !isDischarge) {
                return;
            }
            Long encounterId = inpatientOrderLookupLoader.resolveEncounterId(
                ReadModelOrderTypeConstants.SERVICE_REQUEST, serviceRequestId);
            if (encounterId == null) {
                return;
            }
            EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
                encounterId,
                EncounterClass.IMP.getCode(),
                conflictErrorCode,
                "仅住院就诊允许转科/出院联动");
            // 已办理出院进入 PENDING 等非 ON_HOLD 态时本编排不回滚（另轨召回/反结）
            if (!EncounterStatus.ON_HOLD.getCode().equals(encounter.getStatusEnum())) {
                return;
            }
            // Pre：必须存在与本条 service_request 对齐的转科/出院里程碑（禁止取时间线首条）
            EncounterHistoryMilestoneQueryModel alignedMilestone =
                this.findAlignedTransferOrDischargeMilestone(encounterId, isTransfer, serviceRequestId);
            if (alignedMilestone == null) {
                return;
            }
            // 仍有其它已校对的转科/出院医嘱时，就诊须保持 ON_HOLD
            if (serviceRequestQueryService.hasVerifiedTransferOrDischargeRequest(encounterId, serviceRequestId)) {
                return;
            }
            RevertTransferOrDischargeHoldCommand command = new RevertTransferOrDischargeHoldCommand();
            command.setOperatorId(operatorId);
            RevertTransferOrDischargeHoldCommand.RevertTransferOrDischargeHoldItemCommand item =
                new RevertTransferOrDischargeHoldCommand.RevertTransferOrDischargeHoldItemCommand();
            item.setEncounterId(encounterId);
            item.setTransferHold(isTransfer);
            item.setServiceRequestId(serviceRequestId);
            item.setRevertedAt(revertedAt);
            command.setItems(Collections.singletonList(item));
            // Cmd：ON_HOLD → IN_PROGRESS，出院轨清空 outWayCode
            encounterDomainService.revertTransferOrDischargeHold(command);
            // Cmd：废弃本条里程碑，防止同就诊再次开立后「脏首条」再次错配
            this.retireAlignedMilestone(alignedMilestone.getId(), operatorId);
        } catch (BusinessException ex) {
            if (ErrorCodeConstants.ENCOUNTER_INVALID_STATUS.equals(ex.getErrorCode())) {
                throw new BusinessException(conflictErrorCode, "患者流转状态已变更，请刷新后重试");
            }
            throw ex;
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(conflictErrorCode, "患者流转状态已变更，请刷新后重试");
        }
    }

    /**
     * 按就诊 + 里程碑类型 + 当前诊疗申请主键定位转科/出院里程碑。
     *
     * <p>开立出院/转科时即写入里程碑；同就诊可有多条历史行。逆向与幂等校对须
     * {@code requestId} 精确对齐，不得取 {@code createdAt} 升序首条。</p>
     *
     * @param encounterId      就诊主键
     * @param transfer         true=转科里程碑，false=出院里程碑
     * @param serviceRequestId 当前操作的诊疗申请主键
     * @return 对齐里程碑；未命中 null
     */
    private EncounterHistoryMilestoneQueryModel findAlignedTransferOrDischargeMilestone(
        Long encounterId,
        boolean transfer,
        Long serviceRequestId) {
        if (encounterId == null || serviceRequestId == null) {
            return null;
        }
        String milestoneName;
        if (transfer) {
            milestoneName = CustomConstants.EncounterHistoryMilestoneName.TRANSFER;
        } else {
            milestoneName = CustomConstants.EncounterHistoryMilestoneName.DISCHARGE;
        }
        List<EncounterHistoryMilestoneQueryModel> milestones =
            encounterHistoryQueryService.listMilestonesByEncounter(encounterId);
        for (EncounterHistoryMilestoneQueryModel milestone : milestones) {
            if (milestone == null) {
                continue;
            }
            if (!milestoneName.equals(milestone.getName())) {
                continue;
            }
            // 仅认本条医嘱开立时写入的里程碑
            if (serviceRequestId.equals(milestone.getRequestId())) {
                return milestone;
            }
        }
        return null;
    }

    /**
     * 回滚 ON_HOLD 成功后废弃对齐里程碑（软删，读侧过滤器不再返回）。
     *
     * @param encounterHistoryId 里程碑主键
     * @param operatorId         操作人
     */
    private void retireAlignedMilestone(Long encounterHistoryId, Long operatorId) {
        if (encounterHistoryId == null) {
            return;
        }
        RetireMilestoneCommand retireCommand = new RetireMilestoneCommand();
        retireCommand.setEncounterHistoryId(encounterHistoryId);
        retireCommand.setOperatorId(operatorId);
        retireCommand.setReason(InpatientNurseStationConstants.MSG_RETIRE_TRANSFER_DISCHARGE_MILESTONE_ON_HOLD_REVERT);
        encounterHistoryDomainService.retireMilestone(retireCommand);
    }
}
