/*
 * Copyright © 2026 OpenHIS Contributors
 */
package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.support;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientDoctorStationTcmConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * BR-IP-TCM-009：住院中药签退/删除门控（执行/发药/费用）。
 */
@Component
@RequiredArgsConstructor
public class InpatientTcmRxRevokeGateSupport {

    private final ProcedureQueryService procedureQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final ChargeItemQueryService chargeItemQueryService;

    /**
     * 断言目标行无净执行、无已完成发药、无不可回退费用。
     *
     * @param encounterId 就诊主键
     * @param medReqIds   药品申请主键列表
     * @throws BusinessException 存在净执行、已完成发药或已结算费用时
     */
    public void assertNoNetExecutionOrCompletedDispenseOrLockedCharge(Long encounterId, List<Long> medReqIds) {
        if (medReqIds == null || medReqIds.isEmpty()) {
            return;
        }
        // 逐行校验签退/删除前置：执行、发药、费用三向门控
        for (Long medReqId : medReqIds) {
            List<ProcedureRecordQueryModel> procedures = procedureQueryService.listNetRemainingCompletedProcedureRecords(
                encounterId, medReqId, TableNameConstants.MED_MEDICATION_REQUEST);
            // 存在净剩余已完成执行，禁止签退
            if (!procedures.isEmpty()) {
                throw new BusinessException(
                    ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
                    InpatientDoctorStationTcmConstants.MSG_REVOKE_BLOCKED);
            }
            List<MedicationDispense> dispenses = medicationDispenseQueryService.listByMedReqIds(List.of(medReqId));
            for (MedicationDispense dispense : dispenses) {
                // 发药已达完成/部分完成/待退，禁止签退
                if (isCompletedDispense(dispense)) {
                    throw new BusinessException(
                        ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
                        InpatientDoctorStationTcmConstants.MSG_REVOKE_BLOCKED);
                }
            }
            ChargeItemQueryCommand chargeCmd = new ChargeItemQueryCommand();
            chargeCmd.setServiceIds(List.of(medReqId));
            List<ChargeItem> chargeItems = chargeItemQueryService.listChargeItemsByServiceIds(chargeCmd);
            for (ChargeItem chargeItem : chargeItems) {
                // 费用已结算，禁止签退
                if (isLockedCharge(chargeItem)) {
                    throw new BusinessException(
                        ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
                        InpatientDoctorStationTcmConstants.MSG_REVOKE_BLOCKED);
                }
            }
        }
    }

    /**
     * 判断发药记录是否处于不可签退的完成态。
     *
     * @param dispense 发药实体
     * @return true 表示已完成、部分完成或待退
     */
    private static boolean isCompletedDispense(MedicationDispense dispense) {
        if (dispense == null || dispense.getStatusEnum() == null) {
            return false;
        }
        DispenseStatus status = dispense.getStatusEnum();
        if (DispenseStatus.COMPLETED.equals(status)) {
            return true;
        }
        if (DispenseStatus.PART_COMPLETED.equals(status)) {
            return true;
        }
        if (DispenseStatus.PENDING_REFUND.equals(status)) {
            return true;
        }
        return false;
    }

    /**
     * 判断费用行是否已结算且不可回退。
     *
     * @param chargeItem 费用行
     * @return true 表示中药申请对应费用已 SETTLED
     */
    private static boolean isLockedCharge(ChargeItem chargeItem) {
        if (chargeItem == null || Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
            return false;
        }
        if (!TableNameConstants.MED_MEDICATION_REQUEST.equals(chargeItem.getServiceTable())) {
            return false;
        }
        if (ChargeItemStatus.SETTLED.getCode().equals(chargeItem.getStatusEnum())) {
            return true;
        }
        return false;
    }
}
