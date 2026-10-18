/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.product;

import static com.zxplat.framework.core.application.util.StringUtils.trimToEmpty;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.TableNameConstants;


/**
 * 产品定义物理表名规范化与类型判断（医嘱目录/库存对齐共用）。
 */
public final class ProductTableNameHelper {

    /** 工具类禁止实例化。 */
    private ProductTableNameHelper() {
    }

    /**
     * 规范化产品定义表名：trim 后用于与 {@link TableNameConstants} 忽略大小写比较。
     *
     * @param productTableName 原始表名
     * @return trim 后的表名；null 时为空串
     */
    public static String normalize(String productTableName) {
        return trimToEmpty(productTableName);
    }

    /**
     * 是否为药品定义表（{@code med_medication_definition}）。
     *
     * @param productTableName 产品物理表名
     * @return 忽略大小写匹配药品表时为 true
     */
    public static boolean isMedication(String productTableName) {
        return TableNameConstants.MED_MEDICATION_DEFINITION.equalsIgnoreCase(normalize(productTableName));
    }

    /**
     * 是否为耗材定义表（{@code adm_device_definition}）。
     *
     * @param productTableName 产品物理表名
     * @return 忽略大小写匹配耗材表时为 true
     */
    public static boolean isDevice(String productTableName) {
        return TableNameConstants.ADM_DEVICE_DEFINITION.equalsIgnoreCase(normalize(productTableName));
    }

    /**
     * 是否为诊疗项目定义表（{@code wkf_activity_definition}）。
     *
     * @param productTableName 产品物理表名
     * @return 忽略大小写匹配诊疗表时为 true
     */
    public static boolean isActivity(String productTableName) {
        return TableNameConstants.WKF_ACTIVITY_DEFINITION.equalsIgnoreCase(normalize(productTableName));
    }

    /**
     * 是否为药品或耗材（库存/发药侧常用合并判断）。
     *
     * @param productTableName 产品物理表名
     * @return 药品或耗材表时为 true
     */
    public static boolean isMedicationOrDevice(String productTableName) {
        return isMedication(productTableName) || isDevice(productTableName);
    }

    /**
     * 库存可用行与发放预减行的对齐键：表名、定义或物料主键、库位三维拼接。
     *
     * @param table               产品物理表名
     * @param definitionOrItemId  定义或物料主键
     * @param locationId          库位主键
     * @return 三维复合键
     */
    public static String tripleCompositeKey(String table, Long definitionOrItemId, Long locationId) {
        char sep = GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR;
        return table + sep + definitionOrItemId + sep + locationId;
    }
}
