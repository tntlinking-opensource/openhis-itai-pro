/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

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
 * 住院医嘱签退/校对退回只读门控（AS-IP-DOR-003 EX-IP-ORD-103、AS-IP-NUR-003 BR-IP-AUD-013 共用）。
 *
 * <p>跨聚合断言经各 DM {@code *QueryService} 完成（B.6.4）：净剩 COMPLETED 执行、阻塞性发药、不可回退费用。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientAdviceRevokeGate {

    /** 执行记录只读查询（净剩 COMPLETED 断言）。 */
    private final ProcedureQueryService procedureQueryService;
    /** 药品发放只读查询（阻塞性发药断言）。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    /** 费用行只读查询（待结算/已结算/退款中断言）。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /**
     * 断言药品申请可签退/校对退回：无净剩余执行、无在途/已发/待发退发药、无待结算/已结算/退款中费用。
     *
     * @param encounterId 就诊主键
     * @param medReqIds   药品申请主键列表
     * @param errorCode   业务语义码（如 EX-IP-ORD-103、EX-IP-AUD-301）
     * @param message     用户提示（如 {@code MSG_IP_AUD_REJECT_BLOCKED}）
     */
    public void assertMedicationWithdrawAllowed(
        Long encounterId,
        List<Long> medReqIds,
        String errorCode,
        String message
    ) {
        if (medReqIds == null || medReqIds.isEmpty()) {
            // 调用方分批 Gate 时允许空列表跳过，避免 NPE
            return;
        }
        for (Long medReqId : medReqIds) {
            assertNoNetProcedure(encounterId, medReqId, TableNameConstants.MED_MEDICATION_REQUEST, errorCode, message);
            assertNoBlockingDispense(medReqId, errorCode, message);
            assertNoLockedCharge(encounterId, medReqId, TableNameConstants.MED_MEDICATION_REQUEST, errorCode, message);
        }
    }

    /**
     * 断言诊疗申请可签退/校对退回：无净剩余执行、无待结算/已结算/退款中费用。
     *
     * @param encounterId    就诊主键
     * @param serviceReqIds  诊疗申请主键列表
     * @param errorCode      业务语义码（如 EX-IP-ORD-103、EX-IP-AUD-301）
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
            assertNoLockedCharge(encounterId, serviceReqId, TableNameConstants.WKF_SERVICE_REQUEST, errorCode, message);
        }
    }

    /**
     * 断言无净剩 COMPLETED 执行记录（CANCEL 且 refund_id 指向的对冲后仍有效者视为阻塞）。
     *
     * @param encounterId  就诊主键
     * @param requestId    来源请求主键
     * @param requestTable 来源表持久化名
     * @param errorCode    业务语义码
     * @param message      用户提示
     */
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
            // 仍有净执行：禁止签退/校对退回，防恶意绕过 UI 直调写接口
            throw new BusinessException(errorCode, message);
        }
    }

    /**
     * 断言无阻塞性药品发放（在途/已发/待发退/部分退均不可签退或校对退回）。
     *
     * @param medReqId  药品申请主键
     * @param errorCode 业务语义码
     * @param message   用户提示
     */
    private void assertNoBlockingDispense(Long medReqId, String errorCode, String message) {
        List<MedicationDispense> dispenses = medicationDispenseQueryService.listByMedReqIds(List.of(medReqId));
        for (MedicationDispense dispense : dispenses) {
            if (isBlockingMedicationDispense(dispense)) {
                throw new BusinessException(errorCode, message);
            }
        }
    }

    /**
     * 断言无待结算/已结算/退款中费用行（逆流程须费用已还原；已反结替代的源 SETTLED 行不参与 Gate）。
     *
     * @param encounterId  就诊主键
     * @param serviceId    费用锚点 service_id
     * @param serviceTable 费用锚点 service_table
     * @param errorCode    业务语义码
     * @param message      用户提示
     */
    private void assertNoLockedCharge(
        Long encounterId,
        Long serviceId,
        String serviceTable,
        String errorCode,
        String message) {
        ChargeItemQueryCommand chargeCmd = new ChargeItemQueryCommand();
        chargeCmd.setServiceIds(List.of(serviceId));
        chargeCmd.setEncounterId(encounterId);
        chargeCmd.setExcludeSupersededSettledLines(true);
        List<ChargeItem> chargeItems = chargeItemQueryService.listChargeItemsByServiceIds(chargeCmd);
        for (ChargeItem chargeItem : chargeItems) {
            if (isLockedCharge(chargeItem, serviceTable)) {
                throw new BusinessException(errorCode, message);
            }
        }
    }

    /**
     * 药品发放是否阻塞签退/校对退回。
     *
     * <p>在途（PREPARATION/PREPARED/IN_PROGRESS/ON_HOLD）、已发、待发退/部分退均视为未处理干净。</p>
     *
     * @param dispense 发放行
     * @return 阻塞时为 true
     */
    private static boolean isBlockingMedicationDispense(MedicationDispense dispense) {
        if (dispense == null || dispense.getStatusEnum() == null) {
            return false;
        }
        DispenseStatus status = dispense.getStatusEnum();
        if (DispenseStatus.PREPARATION.equals(status)) {
            return true;
        }
        if (DispenseStatus.PREPARED.equals(status)) {
            return true;
        }
        if (DispenseStatus.IN_PROGRESS.equals(status)) {
            return true;
        }
        if (DispenseStatus.COMPLETED.equals(status)) {
            return true;
        }
        if (DispenseStatus.SUMMARIZED.equals(status)) {
            return true;
        }
        if (DispenseStatus.PART_COMPLETED.equals(status)) {
            return true;
        }
        if (DispenseStatus.ON_HOLD.equals(status)) {
            return true;
        }
        if (DispenseStatus.PENDING_REFUND.equals(status)) {
            return true;
        }
        if (DispenseStatus.PART_REFUND.equals(status)) {
            return true;
        }
        return false;
    }

    /**
     * 费用行是否阻塞签退/校对退回（待结算、已结算、退款中）。
     *
     * @param chargeItem   费用行
     * @param serviceTable 期望的 service_table 锚点
     * @return 阻塞时为 true
     */
    private static boolean isLockedCharge(ChargeItem chargeItem, String serviceTable) {
        if (chargeItem == null || Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
            return false;
        }
        if (!serviceTable.equals(chargeItem.getServiceTable())) {
            // 非本请求锚点的费用行不参与 Gate
            return false;
        }
        if (ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(chargeItem.getStatusEnum())) {
            return true;
        }
        if (ChargeItemStatus.SETTLED.getCode().equals(chargeItem.getStatusEnum())) {
            return true;
        }
        if (ChargeItemStatus.REFUNDING.getCode().equals(chargeItem.getStatusEnum())) {
            return true;
        }
        return false;
    }
}
