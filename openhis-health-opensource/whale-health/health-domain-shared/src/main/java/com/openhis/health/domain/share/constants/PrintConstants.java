/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 打印域字面量常量（分页上限、PrintJson 校验口径等；**非**状态/客户端类型枚举）。
 *
 * <p>平台租户见 {@link GeneralSharedConstants#PLATFORM_TENANT_ID}；
 * 有限码值集合见 {@link com.openhis.health.domain.share.enums.PrintTemplateStatus}、
 * {@link com.openhis.health.domain.share.enums.PrintLogStatus}、
 * {@link com.openhis.health.domain.share.enums.PrintClientType}。</p>
 */
public final class PrintConstants {

    /** PrintJson 根 {@code engine} 固定值（DM-PRT-001 §3.2）。 */
    public static final String PRINT_JSON_ENGINE = "Print";

    /** 日汇总扫描单次上限（内存聚合，非对外分页契约）。 */
    public static final int DAILY_SUMMARY_SCAN_PAGE_SIZE = 10000;

    /** 打印相关列表接口 {@code pageSize} 上限。 */
    public static final int PAGE_SIZE_MAX = 1000;

    /** 日汇总默认回溯天数（未传 dateFrom 时）。 */
    public static final int DAILY_SUMMARY_DEFAULT_LOOKBACK_DAYS = 7;

    /** DM-PRT-001 §3.2 默认空白版式（新建草稿时使用）。 */
    public static final String DEFAULT_BLANK_PRINT_JSON =
            "{\"schemaVersion\":1,\"engine\":\"Print\",\"panels\":[{\"index\":0,\"width\":80,\"height\":200,\"paperHeader\":49.5,\"paperFooter\":780,\"printElements\":[]}]}";

    /** PrintJson panel.paperMode：定高分页（DM-PRT-004 连续纸方案）。 */
    public static final String PRINT_JSON_PAPER_MODE_FIXED = "fixed";

    /** PrintJson panel.paperMode：连续纸/卷纸（DM-PRT-004 连续纸方案）。 */
    public static final String PRINT_JSON_PAPER_MODE_CONTINUOUS = "continuous";

    /** PrintJson panel.paperType：卷纸前缀（须配合 paperMode=continuous）。 */
    public static final String PRINT_JSON_PAPER_TYPE_ROLL = "roll";

    private PrintConstants() {
    }
}
