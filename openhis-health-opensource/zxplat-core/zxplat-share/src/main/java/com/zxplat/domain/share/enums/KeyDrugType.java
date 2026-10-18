/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

import lombok.Getter;

/**
 * 重点药类型（列表筛选码）：选中某类型即筛选「该标识命中」的药品；空/未知码表示不限。
 *
 * <p>布尔旗路径：药典 {@code basicFlag}/{@code nationalMonitorFlag}/{@code centralPurchaseFlag}/
 * {@code antibioticFlag}。药品性质路径：药典 {@code pharmacologyCategoryCode}
 * （术语 {@code medicine_properties}：麻醉/精一/精二）。用于药品目录、库存明细、重点药品使用统计、
 * 处方点评特殊药筛选与发药报表药品性质筛选（替代原「基药是/否」）。</p>
 */
@Getter
public enum KeyDrugType implements EnumInterface {

    /** 基药（{@code basic_flag = true}）。 */
    BASIC(0L, "BASIC", "基药", "筛选基药标识为是的药品", 1L),

    /** 国家重点监控药品（{@code national_monitor_flag = true}）。 */
    NATIONAL_MONITOR(0L, "NATIONAL_MONITOR", "国家重点监控药品", "筛选国家重点监控标识为是的药品", 2L),

    /** 集采药品（{@code central_purchase_flag = true}）。 */
    CENTRAL_PURCHASE(0L, "CENTRAL_PURCHASE", "集采药品", "筛选集采标识为是的药品", 3L),

    /** 抗生素（{@code antibiotic_flag = true}）。 */
    ANTIBIOTIC(0L, "ANTIBIOTIC", "抗生素", "筛选抗生素标识为是的药品", 4L),

    /** 麻醉药品（{@code pharmacology_category_code = 2}，术语 medicine_properties）。 */
    NARCOTIC(0L, "NARCOTIC", "麻醉药品", "筛选药品性质为麻醉药品的目录行", 5L),

    /** 一类精神药品（{@code pharmacology_category_code = 4}）。 */
    PSYCH_ONE(0L, "PSYCH_ONE", "一类精神药品", "筛选药品性质为一类精神药品的目录行", 6L),

    /** 二类精神药品（{@code pharmacology_category_code = 5}）。 */
    PSYCH_TWO(0L, "PSYCH_TWO", "二类精神药品", "筛选药品性质为二类精神药品的目录行", 7L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造。
     *
     * @param codeSystemId 编码系统
     * @param code         持久化/API code
     * @param display      展示名
     * @param definition   说明
     * @param sortOrder    排序
     */
    KeyDrugType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }
}
