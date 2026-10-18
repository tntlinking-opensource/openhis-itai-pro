/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.advice;

import com.zxplat.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.zxplat.domain.share.constants.DoctorStationAdviceConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;

import java.math.BigDecimal;

/**
 * 医生站药品开立：断言 {@code unit_code} 可按目录五类（+剂量/游离追溯）换算为拆零。
 *
 * <p>防止成组临嘱把溶媒「袋」等异品单位落到本药 {@code med_medication_request.unit_code}，
 * 导致护士执行库存 Gate 再报「发放单位不在目录…」。</p>
 */
public final class MedicationAdviceUnitConversionGate {

    /**
     * 禁止实例化：仅静态门禁入口。
     */
    private MedicationAdviceUnitConversionGate() {
    }

    /**
     * 断言业务开立单位可换算为库存拆零（与发药/执行 {@link MedicationProductQuantityToSplitUnitConverter} 同口径）。
     *
     * @param productRow       药品目录读模型；可空则跳过（由调用方保证 CREATE 必有定义）
     * @param businessUnitCode 开立/总量单位编码
     * @throws BusinessException 单位空或无法换算
     */
    public static void assertUnitConvertibleToSplit(
        ProductDefinitionDisplayDto productRow,
        String businessUnitCode
    ) {
        if (productRow == null) {
            return;
        }
        if (StringUtils.isEmpty(businessUnitCode)) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_MEDICATION_UNIT_CODE_REQUIRED);
        }
        try {
            MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
                ProductDefinitionDisplaySupport.toUnitConversionSource(productRow),
                businessUnitCode.trim(),
                BigDecimal.ONE);
        } catch (BusinessException conversionFailure) {
            String productName = ProductDefinitionDisplaySupport.resolveProductDisplayName(productRow, "药品");
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_MEDICATION_UNIT_NOT_IN_CATALOG_PREFIX
                    + productName
                    + DoctorStationAdviceConstants.MSG_MEDICATION_UNIT_NOT_IN_CATALOG_SUFFIX);
        }
    }
}
