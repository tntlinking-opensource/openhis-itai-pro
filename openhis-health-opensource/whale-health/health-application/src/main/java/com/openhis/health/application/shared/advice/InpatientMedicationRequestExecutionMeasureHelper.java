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
import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;

/**
 * 住院药嘱 {@code quantity}/{@code unit_code} 与发放行对齐口径（护士执行写发药、药房配药门禁共用）。
 *
 * <p>药品申请长临共用同一套字段，语义由 {@code therapy_enum} 区分，<strong>与 {@code dose}/{@code dose_unit_code} 无关</strong>
 * （剂量仅供医生站展示，不参与发药、扣库、配药校验）：</p>
 * <ul>
 *   <li><strong>长期</strong>：{@code quantity}/{@code unit_code} 表示<strong>一次执行</strong>产生的发药数量与发药单位；每条发放行须与此一致（拆零单位比较）。</li>
 *   <li><strong>临时</strong>：{@code quantity}/{@code unit_code} 表示开立<strong>总量</strong>与单位；单行不与总量逐条相等，多段合计由药房 {@code BR-IPP-006} 收口。</li>
 * </ul>
 */
public final class InpatientMedicationRequestExecutionMeasureHelper {

    /**
     * 工具类禁止实例化。
     */
    private InpatientMedicationRequestExecutionMeasureHelper() {
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
     * 长期医嘱配药：发放行须与申请上「一次执行」的 {@code quantity}/{@code unit_code} 在拆零单位下一致。
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
        if (medicationRequestDetail == null) {
            return;
        }
        BigDecimal expectedQuantity = medicationRequestDetail.getQuantity();
        String expectedUnitCode = resolveDispenseUnitCodeFromRequest(medicationRequestDetail.getUnitCode());
        assertDispenseMeasureMatchesRequest(
            expectedQuantity,
            expectedUnitCode,
            dispenseQuantity,
            dispenseUnitCode,
            productRow,
            "配药失败：发放数量与医嘱单次执行发药量不一致",
            "发放单位与医嘱发药单位不一致");
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
