/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import java.util.Objects;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestDetailQueryModel;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 住院药嘱 {@code quantity}/{@code unit_code} 与发放行对齐口径（护士执行写发药、药房配药门禁共用）。
 *
 * <p>药品申请长临共用同一套字段，语义由 {@code therapy_enum} 区分，<strong>与 {@code dose}/{@code dose_unit_code} 无关</strong>
 * （剂量仅供医生站展示，不参与发药、扣库、配药校验）：</p>
 * <ul>
 *   <li><strong>长期</strong>：{@code quantity}/{@code unit_code} 表示<strong>一次执行</strong>产生的发药数量与发药单位；每条发放行须与此一致（拆零单位比较）。</li>
 *   <li><strong>临时</strong>：{@code quantity}/{@code unit_code} 表示开立<strong>总量</strong>与单位；单行不与总量逐条相等，多段合计由药房 {@code BR-IPP-006} 收口。</li>
 *   <li><strong>拆零下限（BR-IP-EXE-036）</strong>：临床槽换算拆零量 &lt; 1 时，计费/发药/扣库抬到 1 个拆零单位；列表切批见
 *       {@link StandingOrderMinSplitUnitBatchExpandSupport}。院点一盒一点（包装单位）优先于本规则。</li>
 * </ul>
 */
public final class InpatientMedicationRequestExecutionMeasureHelper {

    /**
     * 工具类禁止实例化。
     */
    private InpatientMedicationRequestExecutionMeasureHelper() {
    }

    /**
     * 执行写路径计费/发药量（业务数量 + 单位）。
     */
    public static final class BillableMeasure {
        /** 业务数量（已含一盒一点或拆零下限抬量）。 */
        private final BigDecimal quantity;
        /** 业务单位（一盒一点为包装单位；拆零下限为拆零单位；否则临床单位）。 */
        private final String unitCode;

        /**
         * @param quantity 业务数量
         * @param unitCode 业务单位
         */
        public BillableMeasure(BigDecimal quantity, String unitCode) {
            this.quantity = quantity;
            this.unitCode = unitCode;
        }

        /**
         * @return 业务数量
         */
        public BigDecimal getQuantity() {
            return quantity;
        }

        /**
         * @return 业务单位
         */
        public String getUnitCode() {
            return unitCode;
        }
    }

    /**
     * 护士执行/发药写入口径：取药品申请 {@code quantity}，缺省 1。
     *
     * @param requestQuantity 药品申请数量
     * @return 非 null 数量
     */
    public static BigDecimal resolveDispenseQuantityFromRequest(BigDecimal requestQuantity) {
        if (requestQuantity != null) {
            return requestQuantity;
        }
        return BigDecimal.ONE;
    }

    /**
     * 按槽位解析发药数量：开嘱日首次槽且填了首日量时用 {@code firstDose}，否则用常规单次量（BR-IP-ORD-030）。
     *
     * <p>长嘱不可拆一盒一点（{@code packageBatchExecution=true}）时固定为 1 个包装单位量，
     * 与临嘱开一盒同构，忽略单次/首日量。</p>
     *
     * @param therapyEnum            治疗类型 code
     * @param requestQuantity        常规单次/总量
     * @param firstDose              首日/首次用量，可空
     * @param planTime               本槽 planTime
     * @param firstPlanTime          开嘱日首次 planTime，可空
     * @param packageBatchExecution  是否一盒一点整包装写量
     * @return 本槽发药数量
     */
    public static BigDecimal resolveDispenseQuantityForExecutionSlot(
        String therapyEnum,
        BigDecimal requestQuantity,
        BigDecimal firstDose,
        LocalDateTime planTime,
        LocalDateTime firstPlanTime,
        boolean packageBatchExecution) {
        if (packageBatchExecution) {
            // 一盒一点：整包装数量恒为 1（单位见 resolveDispenseUnitCodeForExecutionSlot）
            return BigDecimal.ONE;
        }
        if (!isLongTermTherapy(therapyEnum)) {
            return resolveDispenseQuantityFromRequest(requestQuantity);
        }
        if (firstDose == null) {
            return resolveDispenseQuantityFromRequest(requestQuantity);
        }
        if (!LongTermFirstExecutionSlotSupport.isFirstExecutionSlot(planTime, firstPlanTime)) {
            return resolveDispenseQuantityFromRequest(requestQuantity);
        }
        // 首次槽换量：首日用量须 >0（域已校验），此处直接采用
        return firstDose;
    }

    /**
     * 按槽位解析发药数量（默认可拆/非一盒一点）。
     *
     * @param therapyEnum     治疗类型 code
     * @param requestQuantity 常规单次/总量
     * @param firstDose       首日/首次用量，可空
     * @param planTime        本槽 planTime
     * @param firstPlanTime   开嘱日首次 planTime，可空
     * @return 本槽发药数量
     */
    public static BigDecimal resolveDispenseQuantityForExecutionSlot(
        String therapyEnum,
        BigDecimal requestQuantity,
        BigDecimal firstDose,
        LocalDateTime planTime,
        LocalDateTime firstPlanTime) {
        return resolveDispenseQuantityForExecutionSlot(
            therapyEnum, requestQuantity, firstDose, planTime, firstPlanTime, false);
    }

    /**
     * 按槽位解析发药单位：首次槽有首日单位则用之，否则常规单位。
     *
     * <p>一盒一点时固定目录包装单位；包装单位缺失时回落常规单位。</p>
     *
     * @param therapyEnum            治疗类型
     * @param requestUnitCode        常规单位
     * @param firstDoseUnitCode      首日单位，可空
     * @param planTime               本槽 planTime
     * @param firstPlanTime          首次 planTime
     * @param packageBatchExecution  是否一盒一点
     * @param packageUnitCode        目录包装单位，一盒一点时优先
     * @return 单位编码（可能 null）
     */
    public static String resolveDispenseUnitCodeForExecutionSlot(
        String therapyEnum,
        String requestUnitCode,
        String firstDoseUnitCode,
        LocalDateTime planTime,
        LocalDateTime firstPlanTime,
        boolean packageBatchExecution,
        String packageUnitCode) {
        if (packageBatchExecution && !StringUtils.isEmpty(packageUnitCode)) {
            return packageUnitCode.trim();
        }
        if (isLongTermTherapy(therapyEnum)
            && LongTermFirstExecutionSlotSupport.isFirstExecutionSlot(planTime, firstPlanTime)
            && !StringUtils.isEmpty(firstDoseUnitCode)) {
            return firstDoseUnitCode.trim();
        }
        return resolveDispenseUnitCodeFromRequest(requestUnitCode);
    }

    /**
     * 按槽位解析发药单位（默认可拆/非一盒一点）。
     *
     * @param therapyEnum       治疗类型
     * @param requestUnitCode   常规单位
     * @param firstDoseUnitCode 首日单位，可空
     * @param planTime          本槽 planTime
     * @param firstPlanTime     首次 planTime
     * @return 单位编码（可能 null）
     */
    public static String resolveDispenseUnitCodeForExecutionSlot(
        String therapyEnum,
        String requestUnitCode,
        String firstDoseUnitCode,
        LocalDateTime planTime,
        LocalDateTime firstPlanTime) {
        return resolveDispenseUnitCodeForExecutionSlot(
            therapyEnum, requestUnitCode, firstDoseUnitCode, planTime, firstPlanTime, false, null);
    }

    /**
     * 解析本槽<strong>临床</strong>发药量/单位（不含拆零下限抬量；一盒一点仍固定整包装）。
     *
     * <p>列表切批累加须用本方法口径，禁止用已抬量后的拆零去切批。</p>
     *
     * @param therapyEnum           治疗类型
     * @param requestQuantity       常规单次/总量
     * @param firstDose             首日量
     * @param requestUnitCode       常规单位
     * @param firstDoseUnitCode     首日单位
     * @param planTime              本槽
     * @param firstPlanTime         首次槽
     * @param packageBatchExecution 是否一盒一点
     * @param packageUnitCode       包装单位
     * @return 临床业务量
     */
    public static BillableMeasure resolveClinicalDispenseMeasureForExecutionSlot(
        String therapyEnum,
        BigDecimal requestQuantity,
        BigDecimal firstDose,
        String requestUnitCode,
        String firstDoseUnitCode,
        LocalDateTime planTime,
        LocalDateTime firstPlanTime,
        boolean packageBatchExecution,
        String packageUnitCode) {
        BigDecimal quantity = resolveDispenseQuantityForExecutionSlot(
            therapyEnum, requestQuantity, firstDose, planTime, firstPlanTime, packageBatchExecution);
        String unitCode = resolveDispenseUnitCodeForExecutionSlot(
            therapyEnum,
            requestUnitCode,
            firstDoseUnitCode,
            planTime,
            firstPlanTime,
            packageBatchExecution,
            packageUnitCode);
        return new BillableMeasure(quantity, unitCode);
    }

    /**
     * 解析本槽<strong>计费/发药</strong>量：一盒一点优先；否则临床量在拆零 &lt; 1 时抬到 1 个拆零单位（BR-IP-EXE-036）。
     *
     * @param therapyEnum           治疗类型
     * @param requestQuantity       常规单次/总量
     * @param firstDose             首日量
     * @param requestUnitCode       常规单位
     * @param firstDoseUnitCode     首日单位
     * @param planTime              本槽
     * @param firstPlanTime         首次槽
     * @param packageBatchExecution 是否一盒一点
     * @param packageUnitCode       包装单位
     * @param productRow            药品目录（拆零换算与拆零单位；可空则不做下限抬量）
     * @return 计费/发药业务量
     */
    public static BillableMeasure resolveBillableDispenseMeasureForExecutionSlot(
        String therapyEnum,
        BigDecimal requestQuantity,
        BigDecimal firstDose,
        String requestUnitCode,
        String firstDoseUnitCode,
        LocalDateTime planTime,
        LocalDateTime firstPlanTime,
        boolean packageBatchExecution,
        String packageUnitCode,
        ProductDefinitionDisplayDto productRow) {
        BillableMeasure clinical = resolveClinicalDispenseMeasureForExecutionSlot(
            therapyEnum,
            requestQuantity,
            firstDose,
            requestUnitCode,
            firstDoseUnitCode,
            planTime,
            firstPlanTime,
            packageBatchExecution,
            packageUnitCode);
        if (packageBatchExecution) {
            // 一盒一点已是整包装，不再叠拆零下限
            return clinical;
        }
        return applyMinSplitUnitFloor(clinical.getQuantity(), clinical.getUnitCode(), productRow);
    }

    /**
     * 临床业务量换算拆零后若 &lt; 1，抬到 1 个拆零单位（数量 1 + 拆零单位码）。
     *
     * @param clinicalQuantity 临床数量
     * @param clinicalUnitCode 临床单位
     * @param productRow       目录
     * @return 抬量后或原临床量
     */
    public static BillableMeasure applyMinSplitUnitFloor(
        BigDecimal clinicalQuantity,
        String clinicalUnitCode,
        ProductDefinitionDisplayDto productRow) {
        if (productRow == null || clinicalQuantity == null || StringUtils.isEmpty(clinicalUnitCode)) {
            return new BillableMeasure(clinicalQuantity, clinicalUnitCode);
        }
        if (StringUtils.isEmpty(productRow.getSplitUnitCode())) {
            return new BillableMeasure(clinicalQuantity, clinicalUnitCode);
        }
        try {
            MedicationProductUnitConversionSource conversionSource =
                ProductDefinitionDisplaySupport.toUnitConversionSource(productRow);
            BigDecimal split = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
                conversionSource, clinicalUnitCode.trim(), clinicalQuantity);
            if (StandingOrderMinSplitUnitBatchExpandSupport.isBelowOneSplitUnit(split)) {
                // 不足 1 拆零：计费/发药走一整拆零单位
                return new BillableMeasure(BigDecimal.ONE, productRow.getSplitUnitCode().trim());
            }
        } catch (BusinessException conversionFailure) {
            // 无法换算：保持临床量，避免误抬
            return new BillableMeasure(clinicalQuantity, clinicalUnitCode);
        }
        return new BillableMeasure(clinicalQuantity, clinicalUnitCode);
    }

    /**
     * 是否长期治疗类型。
     *
     * @param therapyEnum therapy code
     * @return true=长期
     */
    private static boolean isLongTermTherapy(String therapyEnum) {
        if (StringUtils.isEmpty(therapyEnum)) {
            return false;
        }
        return TherapyType.LONG_TERM.getCode().equals(therapyEnum.trim());
    }

    /**
     * 护士执行/发药写入口径：取药品申请 {@code unit_code}（trim 后，可能为 null）。
     *
     * @param requestUnitCode 药品申请单位
     * @return trim 后的单位编码
     */
    public static String resolveDispenseUnitCodeFromRequest(String requestUnitCode) {
        if (StringUtils.isEmpty(requestUnitCode)) {
            return null;
        }
        return requestUnitCode.trim();
    }

    /**
     * 发放行数量/单位是否等于 1 个包装单位（拆零量比较）。
     *
     * @param dispenseQuantity 发放数量
     * @param dispenseUnitCode 发放单位
     * @param productRow       目录
     * @return true=整包装量
     */
    public static boolean matchesPackageBatchQuantity(
        BigDecimal dispenseQuantity,
        String dispenseUnitCode,
        ProductDefinitionDisplayDto productRow) {
        if (productRow == null
            || StringUtils.isEmpty(productRow.getPackageUnitCode())
            || dispenseQuantity == null
            || StringUtils.isEmpty(dispenseUnitCode)) {
            return false;
        }
        try {
            MedicationProductUnitConversionSource conversionSource =
                ProductDefinitionDisplaySupport.toUnitConversionSource(productRow);
            BigDecimal expectedSplit = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
                conversionSource, productRow.getPackageUnitCode().trim(), BigDecimal.ONE);
            BigDecimal dispenseSplit = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
                conversionSource, dispenseUnitCode.trim(), dispenseQuantity);
            return expectedSplit.compareTo(dispenseSplit) == 0;
        } catch (BusinessException conversionFailure) {
            // 单位无法换算：不视为整包装
            return false;
        }
    }

    /**
     * 长期医嘱配药：发放行须与申请上「一次执行」的 {@code quantity}/{@code unit_code} 在拆零单位下一致；
     * 一盒一点模式则须等于 1 个包装单位（与执行写量同构）。
     *
     * @param medicationRequestDetail 药品申请详情
     * @param dispenseQuantity        发放行数量
     * @param dispenseUnitCode        发放行单位
     * @param productRow              药品目录（单位换算）
     * @param packageBatchExecution   是否长嘱不可拆一盒一点写量
     * @throws BusinessException {@link ErrorCodeConstants#EX_OPP_204_QUANTITY_MISMATCH}
     */
    public static void assertLongTermDispenseMatchesPerExecutionRequest(
        MedicationRequestDetailQueryModel medicationRequestDetail,
        BigDecimal dispenseQuantity,
        String dispenseUnitCode,
        ProductDefinitionDisplayDto productRow,
        boolean packageBatchExecution
    ) {
        if (medicationRequestDetail == null) {
            return;
        }
        if (packageBatchExecution) {
            // 一盒一点：发放须为一整包装（数量 1 + 包装单位，或以拆零量等于 packageUnitRatio）
            String packageUnit = null;
            if (productRow != null && !StringUtils.isEmpty(productRow.getPackageUnitCode())) {
                packageUnit = productRow.getPackageUnitCode().trim();
            }
            assertDispenseMeasureMatchesRequest(
                BigDecimal.ONE,
                packageUnit,
                dispenseQuantity,
                dispenseUnitCode,
                productRow,
                "配药失败：发放数量与长嘱整包装执行量不一致",
                "发放单位与包装单位不一致");
            return;
        }
        BigDecimal expectedQuantity = medicationRequestDetail.getQuantity();
        String expectedUnitCode = resolveDispenseUnitCodeFromRequest(medicationRequestDetail.getUnitCode());
        // 拆零下限：期望量与执行写路径抬量后口径一致（BR-IP-EXE-036）
        BillableMeasure billable = applyMinSplitUnitFloor(expectedQuantity, expectedUnitCode, productRow);
        assertDispenseMeasureMatchesRequest(
            billable.getQuantity(),
            billable.getUnitCode(),
            dispenseQuantity,
            dispenseUnitCode,
            productRow,
            "配药失败：发放数量与医嘱单次执行发药量不一致",
            "发放单位与医嘱发药单位不一致");
    }

    /**
     * 长期医嘱配药（默认可拆：按单次执行量校验）。
     *
     * @param medicationRequestDetail 药品申请详情
     * @param dispenseQuantity        发放行数量
     * @param dispenseUnitCode        发放行单位
     * @param productRow              药品目录（单位换算）
     * @throws BusinessException {@link ErrorCodeConstants#EX_OPP_204_QUANTITY_MISMATCH}
     */
    public static void assertLongTermDispenseMatchesPerExecutionRequest(
        MedicationRequestDetailQueryModel medicationRequestDetail,
        BigDecimal dispenseQuantity,
        String dispenseUnitCode,
        ProductDefinitionDisplayDto productRow
    ) {
        assertLongTermDispenseMatchesPerExecutionRequest(
            medicationRequestDetail, dispenseQuantity, dispenseUnitCode, productRow, false);
    }

    /**
     * 临时医嘱配药：校验发放单位与申请 {@code unit_code} 一致或在目录单位带内可换算。
     *
     * @param medicationRequestDetail 药品申请详情
     * @param dispenseUnitCode        发放行单位
     * @param productRow              药品目录
     * @throws BusinessException {@link ErrorCodeConstants#EX_OPP_204_QUANTITY_MISMATCH}
     */
    public static void assertTemporaryDispenseUnitMatchesRequest(
        MedicationRequestDetailQueryModel medicationRequestDetail,
        String dispenseUnitCode,
        ProductDefinitionDisplayDto productRow
    ) {
        if (medicationRequestDetail == null || StringUtils.isEmpty(dispenseUnitCode)) {
            return;
        }
        String expectedUnitCode = resolveDispenseUnitCodeFromRequest(medicationRequestDetail.getUnitCode());
        if (Objects.equals(expectedUnitCode, dispenseUnitCode.trim())) {
            return;
        }
        if (productRow == null) {
            throw new BusinessException(
                ErrorCodeConstants.EX_OPP_204_QUANTITY_MISMATCH,
                "发放单位与医嘱发药单位不一致");
        }
        BigDecimal probeQuantity = BigDecimal.ONE;
        MedicationProductUnitConversionSource conversionSource =
            ProductDefinitionDisplaySupport.toUnitConversionSource(productRow);
        try {
            BigDecimal expectedSplit = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
                conversionSource, expectedUnitCode, probeQuantity);
            BigDecimal dispenseSplit = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
                conversionSource, dispenseUnitCode, probeQuantity);
            if (expectedSplit.compareTo(dispenseSplit) == 0) {
                return;
            }
        } catch (BusinessException conversionFailure) {
            // 单位无法换算时走下方字面量不一致
        }
        throw new BusinessException(
            ErrorCodeConstants.EX_OPP_204_QUANTITY_MISMATCH,
            "发放单位与医嘱发药单位不一致");
    }

    /**
     * 在拆零单位下比较申请口径与发放行数量、单位。
     */
    private static void assertDispenseMeasureMatchesRequest(
        BigDecimal expectedQuantity,
        String expectedUnitCode,
        BigDecimal dispenseQuantity,
        String dispenseUnitCode,
        ProductDefinitionDisplayDto productRow,
        String quantityMismatchMessage,
        String unitMismatchMessage
    ) {
        if (dispenseQuantity == null || expectedQuantity == null) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_204_QUANTITY_MISMATCH, quantityMismatchMessage);
        }
        if (productRow == null) {
            if (!Objects.equals(expectedUnitCode, dispenseUnitCode)) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_204_QUANTITY_MISMATCH, unitMismatchMessage);
            }
            if (expectedQuantity.compareTo(dispenseQuantity) != 0) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_204_QUANTITY_MISMATCH, quantityMismatchMessage);
            }
            return;
        }
        MedicationProductUnitConversionSource conversionSource =
            ProductDefinitionDisplaySupport.toUnitConversionSource(productRow);
        BigDecimal expectedSplit = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
            conversionSource, expectedUnitCode, expectedQuantity);
        BigDecimal dispenseSplit = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
            conversionSource, dispenseUnitCode, dispenseQuantity);
        if (expectedSplit.compareTo(dispenseSplit) != 0) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_204_QUANTITY_MISMATCH, quantityMismatchMessage);
        }
    }
}
