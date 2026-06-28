/*
 * Copyright © 2026 OpenHIS Contributors
 */
package com.openhis.health.application.inpatient.doctorstation.advice.support;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * GAP-STALE-005 / SYS-IMP-007：住院签退写路径 Gate（EX-IP-ORD-103）单元测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InpatientAdviceRevokeGateSupportTest {

    private static final Long ENCOUNTER_ID = 100L;
    private static final Long MED_REQ_ID = 200L;
    private static final Long SVC_REQ_ID = 300L;
    private static final String ERROR_CODE = "EX-IP-ORD-103";
    private static final String MESSAGE = "已执行、已发药或费用不可回退，无法签退";

    @Mock
    private ProcedureQueryService procedureQueryService;

    @Mock
    private MedicationDispenseQueryService medicationDispenseQueryService;

    @Mock
    private ChargeItemQueryService chargeItemQueryService;

    @InjectMocks
    private InpatientAdviceRevokeGateSupport gateSupport;

    /** T1：已执行未结算 → 签退拒绝 EX-IP-ORD-103。 */
    @Test
    void assertMedicationWithdrawAllowed_rejectsWhenNetProcedureRemains() {
        when(procedureQueryService.listNetRemainingCompletedProcedureRecords(
            ENCOUNTER_ID, MED_REQ_ID, TableNameConstants.MED_MEDICATION_REQUEST))
            .thenReturn(List.of(new ProcedureRecordQueryModel()));

        assertThatThrownBy(() -> gateSupport.assertMedicationWithdrawAllowed(
            ENCOUNTER_ID, List.of(MED_REQ_ID), ERROR_CODE, MESSAGE))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ERROR_CODE);
    }

    /** T1 变体：待结算费用存在 → 签退拒绝。 */
    @Test
    void assertMedicationWithdrawAllowed_rejectsWhenPendingSettlementCharge() {
        ChargeItem charge = new ChargeItem();
        charge.setServiceTable(TableNameConstants.MED_MEDICATION_REQUEST);
        charge.setStatusEnum(ChargeItemStatus.PENDING_SETTLEMENT.getCode());
        charge.setIsDeleted(false);
        when(chargeItemQueryService.listChargeItemsByServiceIds(any()))
            .thenReturn(List.of(charge));

        assertThatThrownBy(() -> gateSupport.assertMedicationWithdrawAllowed(
            ENCOUNTER_ID, List.of(MED_REQ_ID), ERROR_CODE, MESSAGE))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ERROR_CODE);
    }

    /** T1 变体：已完成发药 → 签退拒绝。 */
    @Test
    void assertMedicationWithdrawAllowed_rejectsWhenCompletedDispense() {
        MedicationDispense dispense = new MedicationDispense();
        dispense.setStatusEnum(DispenseStatus.COMPLETED);
        when(medicationDispenseQueryService.listByMedReqIds(List.of(MED_REQ_ID)))
            .thenReturn(List.of(dispense));

        assertThatThrownBy(() -> gateSupport.assertMedicationWithdrawAllowed(
            ENCOUNTER_ID, List.of(MED_REQ_ID), ERROR_CODE, MESSAGE))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ERROR_CODE);
    }

    /** T2：仅签发未校对 / 无阻断事实 → 签退 Gate 通过。 */
    @Test
    void assertMedicationWithdrawAllowed_passesWhenNoBlockingFacts() {
        when(procedureQueryService.listNetRemainingCompletedProcedureRecords(any(), any(), any()))
            .thenReturn(Collections.emptyList());
        when(medicationDispenseQueryService.listByMedReqIds(any()))
            .thenReturn(Collections.emptyList());
        when(chargeItemQueryService.listChargeItemsByServiceIds(any()))
            .thenReturn(Collections.emptyList());

        assertThatCode(() -> gateSupport.assertMedicationWithdrawAllowed(
            ENCOUNTER_ID, List.of(MED_REQ_ID), ERROR_CODE, MESSAGE))
            .doesNotThrowAnyException();
    }

    /** T3：已 SETTLED 费用 → 签退拒绝。 */
    @Test
    void assertServiceRequestWithdrawAllowed_rejectsWhenSettledCharge() {
        ChargeItem charge = new ChargeItem();
        charge.setServiceTable(TableNameConstants.WKF_SERVICE_REQUEST);
        charge.setStatusEnum(ChargeItemStatus.SETTLED.getCode());
        charge.setIsDeleted(false);
        when(chargeItemQueryService.listChargeItemsByServiceIds(any()))
            .thenReturn(List.of(charge));

        assertThatThrownBy(() -> gateSupport.assertServiceRequestWithdrawAllowed(
            ENCOUNTER_ID, List.of(SVC_REQ_ID), ERROR_CODE, MESSAGE))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ERROR_CODE);
    }

    /** T4：Stale Tab 直调 — 空列表跳过 Gate。 */
    @Test
    void assertMedicationWithdrawAllowed_skipsWhenEmptyIds() {
        assertThatCode(() -> gateSupport.assertMedicationWithdrawAllowed(
            ENCOUNTER_ID, Collections.emptyList(), ERROR_CODE, MESSAGE))
            .doesNotThrowAnyException();
    }
}
