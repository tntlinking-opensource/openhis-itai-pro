/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.bridge.issue.context;

import com.openhis.health.domain.share.constants.OutpatientPharmacyPrescriptionQueryConstants;
import com.openhis.health.domain.share.enums.ChargeTypeCategory;
import com.openhis.health.domain.share.enums.ContractCategory;

/**
 * 费用性质合同分类 → 博思票头医保类型（省医保/市医保等）。
 */
public final class BossContractCategoryMapping {

    /** 工伤（无 ChargeTypeCategory 数字码时的票面编码）。 */
    public static final String WORK_INJURY_CODE = "5";

    private BossContractCategoryMapping() {
    }

    /**
     * 按合同 {@code category_enum} 解析博思医保类型。
     *
     * @param categoryEnum 合同分类码（数字 1～4 或 ContractCategory code）
     * @return 编码与名称；空/未知视为自费
     */
    public static MedCare resolve(String categoryEnum) {
        if (categoryEnum == null || categoryEnum.isBlank()) {
            return selfPay();
        }
        String code = categoryEnum.trim();
        if (ChargeTypeCategory.SELF_PAY.getCode().equals(code)
            || ContractCategory.SELF_CHARGE.getCode().equals(code)) {
            return selfPay();
        }
        if (ChargeTypeCategory.PUBLIC_PAY.getCode().equals(code)
            || ContractCategory.PUBLIC_CHARGE.getCode().equals(code)) {
            return new MedCare(
                ChargeTypeCategory.PUBLIC_PAY.getCode(),
                OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_PUBLIC_CHARGE_LABEL);
        }
        if (ChargeTypeCategory.PROVINCIAL_INSURANCE.getCode().equals(code)
            || ContractCategory.PROVINCIAL_INSURANCE.getCode().equals(code)) {
            return new MedCare(
                ChargeTypeCategory.PROVINCIAL_INSURANCE.getCode(),
                OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_PROVINCIAL_INSURANCE_LABEL);
        }
        if (ChargeTypeCategory.MUNICIPAL_INSURANCE.getCode().equals(code)
            || ContractCategory.MUNICIPAL_INSURANCE.getCode().equals(code)) {
            return new MedCare(
                ChargeTypeCategory.MUNICIPAL_INSURANCE.getCode(),
                OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_MUNICIPAL_INSURANCE_LABEL);
        }
        if (ContractCategory.WORKER_INSURANCE.getCode().equals(code)) {
            return new MedCare(
                WORK_INJURY_CODE,
                OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_WORKER_INSURANCE_LABEL);
        }
        return selfPay();
    }

    private static MedCare selfPay() {
        return new MedCare(
            ChargeTypeCategory.SELF_PAY.getCode(),
            OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_SELF_CHARGE_LABEL);
    }

    public record MedCare(String typeCode, String typeName) {
    }
}
