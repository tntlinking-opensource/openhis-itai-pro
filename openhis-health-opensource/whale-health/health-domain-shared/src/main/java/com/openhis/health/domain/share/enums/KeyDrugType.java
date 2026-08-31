/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

import lombok.Getter;

/**
 * 重点药类型（列表筛选码）：选中某类型即筛选「该布尔标识为 true」的药品；空/未知码表示不限。
 *
 * <p>与药典 {@code basicFlag}/{@code nationalMonitorFlag}/{@code centralPurchaseFlag} 一一对应，
 * 用于药品目录与库存明细的「重点药类型」筛选项（替代原「基药是/否」）。</p>
 */
@Getter
public enum KeyDrugType implements EnumInterface {

    /** 基药（{@code basic_flag = true}）。 */
    BASIC(0L, "BASIC", "基药", "筛选基药标识为是的药品", 1L),

    /** 国家重点监控药品（{@code national_monitor_flag = true}）。 */
    NATIONAL_MONITOR(0L, "NATIONAL_MONITOR", "国家重点监控药品", "筛选国家重点监控标识为是的药品", 2L),

    /** 集采药品（{@code central_purchase_flag = true}）。 */
    CENTRAL_PURCHASE(0L, "CENTRAL_PURCHASE", "集采药品", "筛选集采标识为是的药品", 3L);

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
