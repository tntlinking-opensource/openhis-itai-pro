/*
 * Copyright © 2026 OpenHIS Contributors
 */
package com.openhis.health.application.inpatient.doctorstation.advice.support;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 住院医嘱签退门控（西药/诊疗/中药共用，对齐 AS EX-IP-ORD-103 / BR-IP-TCM-009）。
 */
@Component
@RequiredArgsConstructor
public class InpatientAdviceRevokeGateSupport {

    private final ProcedureQueryService procedureQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final ChargeItemQueryService chargeItemQueryService;

    /**
     * 断言药品申请可签退：无净剩余执行、无已完成/汇总发药、无待结算/已结算费用。
     *
     * @param encounterId 就诊主键
     * @param medReqIds   药品申请主键列表
     * @param errorCode   业务语义码（如 EX-IP-ORD-103）
     * @param message     用户提示
     */
    public void assertMedicationWithdrawAllowed(
        Long encounterId,
        List<Long> medReqIds,
        String errorCode,
        String message
    ) {
        if (medReqIds == null || medReqIds.isEmpty()) {
            return;
        }
        for (Long medReqId : medReqIds) {
            assertNoNetProcedure(encounterId, medReqId, TableNameConstants.MED_MEDICATION_REQUEST, errorCode, message);
            assertNoBlockingDispense(medReqId, errorCode, message);
            assertNoLockedCharge(medReqId, TableNameConstants.MED_MEDICATION_REQUEST, errorCode, message);
        }
    }

    /**
     * 断言诊疗申请可签退：无净剩余执行、无待结算/已结算费用。
     *
     * @param encounterId    就诊主键
     * @param serviceReqIds  诊疗申请主键列表
     * @param errorCode      业务语义码（如 EX-IP-ORD-103）
     * @param message        用户提示
     */
    public void assertServiceRequestWithdrawAllowed(
        Long encounterId,
        List<Long> serviceReqIds,
        String errorCode,
        String message
    ) {
        if (serviceReqIds == null || serviceReqIds.isEmpty()) {
            return;
        }
        for (Long serviceReqId : serviceReqIds) {
            assertNoNetProcedure(encounterId, serviceReqId, TableNameConstants.WKF_SERVICE_REQUEST, errorCode, message);
            assertNoLockedCharge(serviceReqId, TableNameConstants.WKF_SERVICE_REQUEST, errorCode, message);
        }
    }

    private void assertNoNetProcedure(
        Long encounterId,
        Long requestId,
        String requestTable,
        String errorCode,
        String message
    ) {
        List<ProcedureRecordQueryModel> procedures = procedureQueryService.listNetRemainingCompletedProcedureRecords(
            encounterId, requestId, requestTable);
        if (!procedures.isEmpty()) {
            throw new BusinessException(errorCode, message);
        }
    }

    private void assertNoBlockingDispense(Long medReqId, String errorCode, String message) {
        List<MedicationDispense> dispenses = medicationDispenseQueryService.listByMedReqIds(List.of(medReqId));
        for (MedicationDispense dispense : dispenses) {
            if (isBlockingDispense(dispense)) {
                throw new BusinessException(errorCode, message);
            }
        }
    }

    private void assertNoLockedCharge(Long serviceId, String serviceTable, String errorCode, String message) {
        ChargeItemQueryCommand chargeCmd = new ChargeItemQueryCommand();
        chargeCmd.setServiceIds(List.of(serviceId));
        List<ChargeItem> chargeItems = chargeItemQueryService.listChargeItemsByServiceIds(chargeCmd);
        for (ChargeItem chargeItem : chargeItems) {
            if (isLockedCharge(chargeItem, serviceTable)) {
                throw new BusinessException(errorCode, message);
            }
        }
    }

    private static boolean isBlockingDispense(MedicationDispense dispense) {
        if (dispense == null || dispense.getStatusEnum() == null) {
            return false;
        }
        DispenseStatus status = dispense.getStatusEnum();
        return DispenseStatus.COMPLETED.equals(status)
            || DispenseStatus.SUMMARIZED.equals(status)
            || DispenseStatus.PART_COMPLETED.equals(status)
            || DispenseStatus.PENDING_REFUND.equals(status);
    }

    private static boolean isLockedCharge(ChargeItem chargeItem, String serviceTable) {
        if (chargeItem == null || Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
            return false;
        }
        if (!serviceTable.equals(chargeItem.getServiceTable())) {
            return false;
        }
        return ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(chargeItem.getStatusEnum())
            || ChargeItemStatus.SETTLED.getCode().equals(chargeItem.getStatusEnum());
    }
}
