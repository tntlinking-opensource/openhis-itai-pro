/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdefinition.queries.model;

import com.openhis.health.domain.share.enums.KeyDrugType;
import com.openhis.health.domain.share.utils.EnumUtil;
import lombok.Getter;
import lombok.Setter;

/**
 * 药典重点药四旗轻量投影（AS-RPT-011 性能路径）：按窗内出现的 productId 批量解析，禁止全库扫目录。
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
     * 判断本行是否命中指定重点药类型（仅 {@code flag = true} 计命中）。
     *
     * @param keyDrugTypeCode {@link KeyDrugType#getCode()}；空/未知返回 false
     * @return 对应旗为 true 时 true
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
        return false;
    }
}
