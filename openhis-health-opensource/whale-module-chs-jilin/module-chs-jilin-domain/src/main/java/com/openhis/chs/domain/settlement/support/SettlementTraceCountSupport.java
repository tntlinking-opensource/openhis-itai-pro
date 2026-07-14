/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import java.math.BigDecimal;

/**
 * 2207/2304 追溯码数量汇总（按 chargeItem 产品来源表分类）。
 *
 * <p>药品（{@code med_medication_definition}）计入 minpacunt_drug_trac_cnt；
 * 耗材（{@code adm_device_definition}）计入 mcs_trac_cnt；其余产品类型传 0。<。</p> */
public final class SettlementTraceCountSupport {

    public static final String TABLE_MEDICATION = "med_medication_definition";
    public static final String TABLE_DEVICE = "adm_device_definition";

    private SettlementTraceCountSupport() {
    }

    /**
     * 统计药品追溯码单位数。
     *
     * @param productTableName 产品来源表名
     * @param quantity         业务数量
     * @return 追溯码单位数；非药品返回 0
     */
    public static int countDrugTracUnits(String productTableName, BigDecimal quantity) {
        if (isMedication(productTableName)) {
            return toUnitCount(quantity);
        }
        return 0;
    }

    /**
     * 统计耗材追溯码单位数。
     *
     * @param productTableName 产品来源表名
     * @param quantity         业务数量
     * @return 追溯码单位数；非耗材返回 0
     */
    public static int countMcsTracUnits(String productTableName, BigDecimal quantity) {
        if (isDevice(productTableName)) {
            return toUnitCount(quantity);
        }
        return 0;
    }

    /**
     * 判断产品来源是否为药品定义表。
     *
     * @param productTableName 产品来源表名
     * @return 是否为药品
     */
    public static boolean isMedication(String productTableName) {
        return TABLE_MEDICATION.equalsIgnoreCase(trimToEmpty(productTableName));
    }

    /**
     * 判断产品来源是否为耗材定义表。
     *
     * @param productTableName 产品来源表名
     * @return 是否为耗材
     */
    public static boolean isDevice(String productTableName) {
        return TABLE_DEVICE.equalsIgnoreCase(trimToEmpty(productTableName));
    }

    /**
     * 数量向上取整为追溯码单位数。
     *
     * @param quantity 业务数量
     * @return 追溯码单位数；0 或负数返回 0
     */
    private static int toUnitCount(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            return 0;
        }
        return quantity.abs().setScale(0, java.math.RoundingMode.CEILING).intValue();
    }
}
