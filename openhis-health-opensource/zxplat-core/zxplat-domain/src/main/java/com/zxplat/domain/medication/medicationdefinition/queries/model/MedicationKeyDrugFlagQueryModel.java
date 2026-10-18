/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdefinition.queries.model;

import com.zxplat.domain.share.constants.PrescriptionSplitConstants;
import com.zxplat.domain.share.enums.KeyDrugType;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.Getter;
import lombok.Setter;

/**
 * 药典重点药标识轻量投影（AS-RPT-011 性能路径）：按窗内出现的 productId 批量解析，禁止全库扫目录。
 *
 * <p>含四旗布尔标识与药品性质码；麻/精一/精二按 {@code pharmacologyCategoryCode} 命中。</p>
 */
@Getter
@Setter
public class MedicationKeyDrugFlagQueryModel {

    /** 药品定义主键。 */
    private Long id;

    /** 基药标识 {@code basic_flag}。 */
    private Boolean basicFlag;

    /** 国家重点监控标识 {@code national_monitor_flag}。 */
    private Boolean nationalMonitorFlag;

    /** 集采标识 {@code central_purchase_flag}。 */
    private Boolean centralPurchaseFlag;

    /** 抗生素标识 {@code antibiotic_flag}。 */
    private Boolean antibioticFlag;

    /**
     * 药品性质码（术语 {@code medicine_properties} / 列 {@code pharmacology_category_code}）。
     */
    private String pharmacologyCategoryCode;

    /**
     * 判断本行是否命中指定重点药类型。
     *
     * <p>基药/监控/集采/抗生素看对应布尔旗为 true；麻醉/精一/精二看药品性质码等值。</p>
     *
     * @param keyDrugTypeCode {@link KeyDrugType#getCode()}；空/未知返回 false
     * @return 命中时 true
     */
    public boolean matchesKeyDrugType(String keyDrugTypeCode) {
        KeyDrugType keyDrugType = EnumUtil.fromCode(KeyDrugType.class, keyDrugTypeCode);
        if (keyDrugType == null) {
            return false;
        }
        if (keyDrugType == KeyDrugType.BASIC) {
            return Boolean.TRUE.equals(this.basicFlag);
        }
        if (keyDrugType == KeyDrugType.NATIONAL_MONITOR) {
            return Boolean.TRUE.equals(this.nationalMonitorFlag);
        }
        if (keyDrugType == KeyDrugType.CENTRAL_PURCHASE) {
            return Boolean.TRUE.equals(this.centralPurchaseFlag);
        }
        if (keyDrugType == KeyDrugType.ANTIBIOTIC) {
            return Boolean.TRUE.equals(this.antibioticFlag);
        }
        // 麻醉/精一/精二：药品性质码等值（空性质不命中）
        String expectedProperty = this.resolveMedicinePropertyCode(keyDrugType);
        if (expectedProperty == null) {
            return false;
        }
        if (StringUtils.isEmpty(this.pharmacologyCategoryCode)) {
            return false;
        }
        return expectedProperty.equals(this.pharmacologyCategoryCode.trim());
    }

    /**
     * 将麻/精重点药类型映射为术语 medicine_properties 码；非性质类返回 null。
     *
     * @param keyDrugType 重点药类型
     * @return 性质码；非麻/精类型 null
     */
    private String resolveMedicinePropertyCode(KeyDrugType keyDrugType) {
        if (keyDrugType == KeyDrugType.NARCOTIC) {
            return PrescriptionSplitConstants.MEDICINE_PROPERTY_NARCOTIC;
        }
        if (keyDrugType == KeyDrugType.PSYCH_ONE) {
            return PrescriptionSplitConstants.MEDICINE_PROPERTY_PSYCH_ONE;
        }
        if (keyDrugType == KeyDrugType.PSYCH_TWO) {
            return PrescriptionSplitConstants.MEDICINE_PROPERTY_PSYCH_TWO;
        }
        return null;
    }
}
