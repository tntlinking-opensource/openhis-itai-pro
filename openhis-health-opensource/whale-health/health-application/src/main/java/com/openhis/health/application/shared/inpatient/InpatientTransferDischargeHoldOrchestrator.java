/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.inpatient;

import com.openhis.health.domain.administration.encounter.command.RevertTransferOrDischargeHoldCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.custom.encounterhistory.queries.model.EncounterHistoryMilestoneQueryModel;
import com.openhis.health.domain.custom.encounterhistory.service.EncounterHistoryQueryService;
import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestQueryModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.health.application.shared.advice.InpatientOrderLookupLoader;
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
 */
@Component
@RequiredArgsConstructor
public class InpatientTransferDischargeHoldOrchestrator {

    /** 诊疗申请读侧查询（转科/出院类别与已校对残留判定）。 */
    private final ServiceRequestQueryService serviceRequestQueryService;
    /** 就诊读侧查询（住院类与 ON_HOLD 状态校验）。 */
    private final EncounterQueryService encounterQueryService;
    /** 就诊里程碑读侧查询（解析发起挂起的 service_request）。 */
    private final EncounterHistoryQueryService encounterHistoryQueryService;
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
            if (!EncounterStatus.ON_HOLD.getCode().equals(encounter.getStatusEnum())) {
                return;
            }
            Long milestoneRequestId = this.resolveOnHoldInitiatingServiceRequestId(encounterId, isTransfer);
            // 仅当撤销的是发起 ON_HOLD 的那条里程碑医嘱时才允许回滚
            if (milestoneRequestId == null || !milestoneRequestId.equals(serviceRequestId)) {
                return;
            }
            // 仍有另一类已校对的转科/出院医嘱时，就诊须保持 ON_HOLD
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
            encounterDomainService.revertTransferOrDischargeHold(command);
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
     * 转科/出院挂起：从就诊里程碑解析发起挂起的 service_request id。
     *
     * @param encounterId 就诊 id
     * @param transfer    true=转科里程碑，false=出院里程碑
     * @return 发起请求 id；未找到 null
     */
    private Long resolveOnHoldInitiatingServiceRequestId(Long encounterId, boolean transfer) {
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
            return milestone.getRequestId();
        }
        return null;
    }
}
