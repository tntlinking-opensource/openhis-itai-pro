/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.module.common.assembler.product;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;


/**
 * 产品定义物理表名规范化与类型判断（医嘱目录/库存对齐共用）。
 */
public final class ProductTableNameSupport {

    private ProductTableNameSupport() {
    }

    /**
     * 规范化产品定义表名：trim 后用于与 {@link TableNameConstants} 忽略大小写比较。
     */
    public static String normalize(String productTableName) {
        return trimToEmpty(productTableName);
    }

    public static boolean isMedication(String productTableName) {
        return TableNameConstants.MED_MEDICATION_DEFINITION.equalsIgnoreCase(normalize(productTableName));
    }

    public static boolean isDevice(String productTableName) {
        return TableNameConstants.ADM_DEVICE_DEFINITION.equalsIgnoreCase(normalize(productTableName));
    }

    public static boolean isActivity(String productTableName) {
        return TableNameConstants.WKF_ACTIVITY_DEFINITION.equalsIgnoreCase(normalize(productTableName));
    }

    public static boolean isMedicationOrDevice(String productTableName) {
        return isMedication(productTableName) || isDevice(productTableName);
    }

    /**
     * 库存可用行与发放预减行的对齐键：表名、定义或物料主键、库位三维拼接。
     */
    public static String tripleCompositeKey(String table, Long definitionOrItemId, Long locationId) {
        char sep = GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR;
        return table + sep + definitionOrItemId + sep + locationId;
    }
}