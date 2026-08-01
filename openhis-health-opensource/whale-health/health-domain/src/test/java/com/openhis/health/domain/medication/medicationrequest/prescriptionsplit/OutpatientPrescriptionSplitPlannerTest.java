/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationrequest.prescriptionsplit;

import com.openhis.health.domain.medication.medicationrequest.command.PrescriptionSplitLineContext;
import com.openhis.health.domain.share.constants.DailySettlementSharedConstants;
import com.openhis.health.domain.share.constants.PrescriptionSplitConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link OutpatientPrescriptionSplitPlanner} 分方规则单元测试。
 */
class OutpatientPrescriptionSplitPlannerTest {

    @Test
    void groupedLinesStillCountEachMedicationKindTowardFiveLimit() {
        List<PrescriptionSplitLineContext> lines = new ArrayList<>();
        Long groupId = 100L;
        for (long medicationId = 1L; medicationId <= 6L; medicationId++) {
            lines.add(line(10L + medicationId, medicationId, groupId, "PO", "BID", null, null));
        }
        BusinessException ex = assertThrows(
            BusinessException.class,
            () -> OutpatientPrescriptionSplitPlanner.plan(lines, disabledAmountLimit())
        );
        assertEquals("单组药品超出处方医嘱行数或金额上限，无法分方", ex.getMessage());
    }

    @Test
    void sameMedicationIdEachRequestLineCountsTowardFiveLimit() {
        List<PrescriptionSplitLineContext> lines = new ArrayList<>();
        for (long requestId = 1L; requestId <= 6L; requestId++) {
            // 用法不同，避免 BR-MRQ-031 同药同用法互斥干扰行数计数断言
            lines.add(line(requestId, 9001L, null, "PO", "FREQ-" + requestId, null, null));
        }
        List<OutpatientPrescriptionSplitPlanner.PrescriptionUnitPlan> plans =
            OutpatientPrescriptionSplitPlanner.plan(lines, disabledAmountLimit());
        assertEquals(2, plans.size());
        assertEquals(5, plans.get(0).getRequestIds().size());
        assertEquals(1, plans.get(1).getRequestIds().size());
    }

    @Test
    void sameMedicationAndUsageMustSplitIntoDifferentPrescriptionUnits() {
        List<PrescriptionSplitLineContext> lines = List.of(
            line(1L, 9001L, null, "PO", "BID", null, null),
            line(2L, 9001L, null, "PO", "BID", null, null)
        );
        List<OutpatientPrescriptionSplitPlanner.PrescriptionUnitPlan> plans =
            OutpatientPrescriptionSplitPlanner.plan(lines, disabledAmountLimit());
        assertEquals(2, plans.size());
        assertEquals(1, plans.get(0).getRequestIds().size());
        assertEquals(1, plans.get(1).getRequestIds().size());
    }

    @Test
    void sameMedicationDifferentUsageMayStayOnSamePrescription() {
        List<PrescriptionSplitLineContext> lines = List.of(
            line(1L, 9001L, null, "PO", "BID", null, null),
            line(2L, 9001L, null, "IV", "QD", null, null)
        );
        List<OutpatientPrescriptionSplitPlanner.PrescriptionUnitPlan> plans =
            OutpatientPrescriptionSplitPlanner.plan(lines, disabledAmountLimit());
        assertEquals(1, plans.size());
        assertEquals(2, plans.get(0).getRequestIds().size());
    }

    @Test
    void sixDistinctMedicationsSplitIntoTwoPrescriptionUnits() {
        List<PrescriptionSplitLineContext> lines = new ArrayList<>();
        for (long medicationId = 1L; medicationId <= 6L; medicationId++) {
            lines.add(line(medicationId, medicationId, null, "PO", "BID", null, null));
        }
        List<OutpatientPrescriptionSplitPlanner.PrescriptionUnitPlan> plans =
            OutpatientPrescriptionSplitPlanner.plan(lines, disabledAmountLimit());
        assertEquals(2, plans.size());
        assertEquals(5, plans.get(0).getRequestIds().size());
        assertEquals(1, plans.get(1).getRequestIds().size());
    }

    @Test
    void defersDuplicatePackAndFillsBinToFiveBeforeOpeningNextPrescription() {
        Long groupId = 100L;
        Long sodium250mlId = 102L;
        String ivTidUsageMethod = "IV";
        String ivTidUsageRate = "TID";
        List<PrescriptionSplitLineContext> lines = List.of(
            line(1L, 101L, groupId, ivTidUsageMethod, ivTidUsageRate, null, null),
            line(2L, sodium250mlId, groupId, ivTidUsageMethod, ivTidUsageRate, null, null),
            line(3L, 103L, null, "PO", "STAT", null, null),
            line(4L, sodium250mlId, null, ivTidUsageMethod, ivTidUsageRate, null, null),
            line(5L, 104L, null, "PO", "STAT", null, null),
            line(6L, 105L, null, "PO", "STAT", null, null),
            line(7L, 106L, null, "PO", "STAT", null, null)
        );
        List<OutpatientPrescriptionSplitPlanner.PrescriptionUnitPlan> plans =
            OutpatientPrescriptionSplitPlanner.plan(lines, disabledAmountLimit());
        assertEquals(2, plans.size());
        assertEquals(5, plans.get(0).getRequestIds().size());
        assertEquals(2, plans.get(1).getRequestIds().size());
        assertEquals(List.of(4L, 7L), plans.get(1).getRequestIds());
    }

    @Test
    void westernAndChinesePatentMustSplitIntoDifferentPrescriptionUnits() {
        List<PrescriptionSplitLineContext> lines = List.of(
            lineWithCategory(1L, 9001L, null, "PO", "BID", null, null,
                DailySettlementSharedConstants.MEDICATION_CATEGORY_WESTERN_CODE),
            lineWithCategory(2L, 9002L, null, "PO", "BID", null, null,
                DailySettlementSharedConstants.MEDICATION_CATEGORY_PATENT_CHINESE_CODE)
        );
        List<OutpatientPrescriptionSplitPlanner.PrescriptionUnitPlan> plans =
            OutpatientPrescriptionSplitPlanner.plan(lines, disabledAmountLimit());
        assertEquals(2, plans.size());
        assertEquals(PrescriptionSplitConstants.RX_TYPE_OUTPATIENT_WESTERN, plans.get(0).getRxTypeCode());
        assertEquals(PrescriptionSplitConstants.RX_TYPE_OUTPATIENT_PATENT, plans.get(1).getRxTypeCode());
    }

    @Test
    void chinesePatentOnlyGetsPatentRxType() {
        List<PrescriptionSplitLineContext> lines = List.of(
            lineWithCategory(1L, 9101L, null, "PO", "BID", null, null,
                DailySettlementSharedConstants.MEDICATION_CATEGORY_PATENT_CHINESE_CODE)
        );
        List<OutpatientPrescriptionSplitPlanner.PrescriptionUnitPlan> plans =
            OutpatientPrescriptionSplitPlanner.plan(lines, disabledAmountLimit());
        assertEquals(1, plans.size());
        assertEquals(PrescriptionSplitConstants.RX_TYPE_OUTPATIENT_PATENT, plans.get(0).getRxTypeCode());
    }

    @Test
    void ordinaryWesternDrugGetsOutpatientWesternRxType() {
        // 药品性质=普通 + 目录西药 → 门诊西药；即使历史上拆零码「2」也曾误当性质码
        List<PrescriptionSplitLineContext> lines = List.of(
            lineWithMedicineProperty(
                1L,
                9201L,
                PrescriptionSplitConstants.MEDICINE_PROPERTY_NORMAL,
                DailySettlementSharedConstants.MEDICATION_CATEGORY_WESTERN_CODE)
        );
        List<OutpatientPrescriptionSplitPlanner.PrescriptionUnitPlan> plans =
            OutpatientPrescriptionSplitPlanner.plan(lines, disabledAmountLimit());
        assertEquals(1, plans.size());
        assertEquals(PrescriptionSplitConstants.RX_TYPE_OUTPATIENT_WESTERN, plans.get(0).getRxTypeCode());
    }

    @Test
    void narcoticMedicinePropertyGetsNarcoticPsychOneRxType() {
        List<PrescriptionSplitLineContext> lines = List.of(
            lineWithMedicineProperty(
                1L,
                9301L,
                PrescriptionSplitConstants.MEDICINE_PROPERTY_NARCOTIC,
                DailySettlementSharedConstants.MEDICATION_CATEGORY_WESTERN_CODE)
        );
        List<OutpatientPrescriptionSplitPlanner.PrescriptionUnitPlan> plans =
            OutpatientPrescriptionSplitPlanner.plan(lines, disabledAmountLimit());
        assertEquals(1, plans.size());
        assertEquals(PrescriptionSplitConstants.RX_TYPE_NARCOTIC_PSYCH_ONE, plans.get(0).getRxTypeCode());
    }

    @Test
    void overlappingDiagnosisSetsMayStayOnSamePrescription() {
        // A 绑 B+C，D 仅绑 B：有交集 → 同方
        PrescriptionSplitLineContext drugA = line(1L, 9001L, null, "PO", "BID", null, null);
        drugA.setDiagnosisSetKey("10,20");
        PrescriptionSplitLineContext drugD = line(2L, 9002L, null, "PO", "QD", null, null);
        drugD.setDiagnosisSetKey("10");
        List<OutpatientPrescriptionSplitPlanner.PrescriptionUnitPlan> plans =
            OutpatientPrescriptionSplitPlanner.plan(List.of(drugA, drugD), disabledAmountLimit());
        assertEquals(1, plans.size());
        assertEquals(2, plans.get(0).getRequestIds().size());
    }

    @Test
    void disjointDiagnosisSetsMustSplitIntoDifferentPrescriptionUnits() {
        PrescriptionSplitLineContext drugA = line(1L, 9001L, null, "PO", "BID", null, null);
        drugA.setDiagnosisSetKey("10,20");
        PrescriptionSplitLineContext drugD = line(2L, 9002L, null, "PO", "QD", null, null);
        drugD.setDiagnosisSetKey("30");
        List<OutpatientPrescriptionSplitPlanner.PrescriptionUnitPlan> plans =
            OutpatientPrescriptionSplitPlanner.plan(List.of(drugA, drugD), disabledAmountLimit());
        assertEquals(2, plans.size());
    }

    @Test
    void emptyDiagnosisCannotMergeWithBoundDiagnosis() {
        PrescriptionSplitLineContext unbound = line(1L, 9001L, null, "PO", "BID", null, null);
        PrescriptionSplitLineContext bound = line(2L, 9002L, null, "PO", "QD", null, null);
        bound.setDiagnosisSetKey("10");
        List<OutpatientPrescriptionSplitPlanner.PrescriptionUnitPlan> plans =
            OutpatientPrescriptionSplitPlanner.plan(List.of(unbound, bound), disabledAmountLimit());
        assertEquals(2, plans.size());
    }

    private static BigDecimal disabledAmountLimit() {
        return PrescriptionSplitConstants.OUTPATIENT_AMOUNT_LIMIT_DISABLED_SENTINEL_YUAN;
    }

    private static PrescriptionSplitLineContext line(
        Long requestId,
        Long medicationId,
        Long groupId,
        String methodCode,
        String rateCode,
        BigDecimal dose,
        String doseUnitCode
    ) {
        return lineWithCategory(
            requestId,
            medicationId,
            groupId,
            methodCode,
            rateCode,
            dose,
            doseUnitCode,
            DailySettlementSharedConstants.MEDICATION_CATEGORY_WESTERN_CODE);
    }

    private static PrescriptionSplitLineContext lineWithCategory(
        Long requestId,
        Long medicationId,
        Long groupId,
        String methodCode,
        String rateCode,
        BigDecimal dose,
        String doseUnitCode,
        String medicationCategoryCode
    ) {
        PrescriptionSplitLineContext ctx = new PrescriptionSplitLineContext();
        ctx.setRequestId(requestId);
        ctx.setMedicationId(medicationId);
        ctx.setGroupId(groupId);
        ctx.setMethodCode(methodCode);
        ctx.setRateCode(rateCode);
        ctx.setDose(dose);
        ctx.setDoseUnitCode(doseUnitCode);
        ctx.setMedType("11");
        ctx.setPharmacologyCategoryCode(PrescriptionSplitConstants.MEDICINE_PROPERTY_NORMAL);
        ctx.setMedicationCategoryCode(medicationCategoryCode);
        ctx.setTcmFlag(false);
        ctx.setChargeTotalPrice(BigDecimal.ONE);
        ctx.setPharmacyLocationId(1L);
        return ctx;
    }

    private static PrescriptionSplitLineContext lineWithMedicineProperty(
        Long requestId,
        Long medicationId,
        String pharmacologyCategoryCode,
        String medicationCategoryCode
    ) {
        PrescriptionSplitLineContext ctx = lineWithCategory(
            requestId,
            medicationId,
            null,
            "PO",
            "BID",
            null,
            null,
            medicationCategoryCode);
        ctx.setPharmacologyCategoryCode(pharmacologyCategoryCode);
        return ctx;
    }
}
