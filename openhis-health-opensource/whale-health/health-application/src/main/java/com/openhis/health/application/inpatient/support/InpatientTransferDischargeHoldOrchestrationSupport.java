/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.support;

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
import com.openhis.health.module.querysupport.order.InpatientOrderLookupLoader;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 转科/出院 {@code ON_HOLD} 正向/逆向就诊编排支持（GAP-ATD-006/008）。
 */
@Component
@RequiredArgsConstructor
public class InpatientTransferDischargeHoldOrchestrationSupport {

    private final ServiceRequestQueryService serviceRequestQueryService;
    private final EncounterQueryService encounterQueryService;
    private final EncounterHistoryQueryService encounterHistoryQueryService;
    private final EncounterDomainService encounterDomainService;
    private final InpatientOrderLookupLoader inpatientOrderLookupLoader;

    /**
     * 诊疗撤销（退回/签退/停嘱）后，若其为 ON_HOLD 里程碑且无异类已校对残留，则回滚就诊至 {@code IN_PROGRESS}。
     *
     * @param serviceRequestId 刚撤销的诊疗申请主键
     * @param operatorId       操作人
     * @param revertedAt       撤销时刻
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
            if (milestoneRequestId == null || !milestoneRequestId.equals(serviceRequestId)) {
                return;
            }
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

    private Long resolveOnHoldInitiatingServiceRequestId(Long encounterId, boolean transfer) {
        String milestoneName = transfer
            ? CustomConstants.EncounterHistoryMilestoneName.TRANSFER
            : CustomConstants.EncounterHistoryMilestoneName.DISCHARGE;
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
