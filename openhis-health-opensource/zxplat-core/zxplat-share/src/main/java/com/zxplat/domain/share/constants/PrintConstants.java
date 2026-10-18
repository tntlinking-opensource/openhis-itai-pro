/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 打印域字面量常量（分页上限、PrintJson 校验口径等；**非**状态/客户端类型枚举）。
 *
 * <p>平台租户见 {@link GeneralSharedConstants#PLATFORM_TENANT_ID}；
 * 有限码值集合见 {@link com.zxplat.domain.share.enums.PrintTemplateStatus}、
 * {@link com.zxplat.domain.share.enums.PrintLogStatus}、
 * {@link com.zxplat.domain.share.enums.PrintClientType}。</p>
 */
public final class PrintConstants {

    /** PrintJson 根 {@code engine} 固定值（DM-PRT-001 §3.2）。 */
    public static final String PRINT_JSON_ENGINE = "Print";

    /** 日汇总扫描单次上限（内存聚合，非对外分页契约）。 */
    public static final int DAILY_SUMMARY_SCAN_PAGE_SIZE = 10000;

    /** 打印相关列表接口 {@code pageSize} 上限。 */
    public static final int PAGE_SIZE_MAX = 1000;

    /** 按业务主键回查已打印：单批 keys 上限（PRT-GLOBAL-001 §6）。 */
    public static final int QUERY_PRINTED_KEYS_MAX = 500;

    /** 按业务主键回查已打印：扫描 SUCCESS 日志条数上限。 */
    public static final int QUERY_PRINTED_LOG_SCAN_MAX = 2000;

    /** 回查默认回溯天数（未传 queryDate 时）。 */
    public static final int QUERY_PRINTED_DEFAULT_LOOKBACK_DAYS = 30;

    /** bizRef 合打主键数组默认字段名（摆药单 / 用药单）。 */
    public static final String BIZ_REF_KEY_FIELD_LIST_ROW_KEYS = "listRowKeys";

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
