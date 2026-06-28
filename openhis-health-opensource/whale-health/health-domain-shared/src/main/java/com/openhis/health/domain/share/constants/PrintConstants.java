/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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

    /** BR-PRT-008 禁止键：formatter。 */
    public static final String PRINT_JSON_FORBIDDEN_KEY_FORMATTER = "formatter";

    /** BR-PRT-008 禁止键：styler。 */
    public static final String PRINT_JSON_FORBIDDEN_KEY_STYLER = "styler";

    /** BR-PRT-008 禁止键：customScript。 */
    public static final String PRINT_JSON_FORBIDDEN_KEY_CUSTOM_SCRIPT = "customScript";

    /** BR-PRT-009 元素 type：text。 */
    public static final String PRINT_ELEMENT_TYPE_TEXT = "text";

    /** BR-PRT-009 元素 type：table。 */
    public static final String PRINT_ELEMENT_TYPE_TABLE = "table";

    /** BR-PRT-009 元素 type：hline。 */
    public static final String PRINT_ELEMENT_TYPE_HLINE = "hline";

    /** BR-PRT-009 元素 type：longText。 */
    public static final String PRINT_ELEMENT_TYPE_LONG_TEXT = "longText";

    /** BR-PRT-009 元素 type：image。 */
    public static final String PRINT_ELEMENT_TYPE_IMAGE = "image";

    /** BR-PRT-009 元素 type：barcode。 */
    public static final String PRINT_ELEMENT_TYPE_BARCODE = "barcode";

    /** BR-PRT-009 元素 type：qrcode。 */
    public static final String PRINT_ELEMENT_TYPE_QRCODE = "qrcode";

    /** BR-PRT-009 元素 type：vline。 */
    public static final String PRINT_ELEMENT_TYPE_VLINE = "vline";

    /** BR-PRT-009 元素 type：rect。 */
    public static final String PRINT_ELEMENT_TYPE_RECT = "rect";

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

    /**
     * 判断 PrintJson 节点键是否为 BR-PRT-008 禁止键。
     *
     * @param key JSON 键名
     * @return 禁止时为 {@code true}
     */
    public static boolean isForbiddenPrintJsonKey(String key) {
        if (key == null) {
            return false;
        }
        if (PRINT_JSON_FORBIDDEN_KEY_FORMATTER.equals(key)) {
            return true;
        }
        if (PRINT_JSON_FORBIDDEN_KEY_STYLER.equals(key)) {
            return true;
        }
        return PRINT_JSON_FORBIDDEN_KEY_CUSTOM_SCRIPT.equals(key);
    }

    /**
     * 判断元素 type 是否在 BR-PRT-009 白名单内。
     *
     * @param type {@code printElementType.type}
     * @return 允许时为 {@code true}
     */
    public static boolean isAllowedPrintElementType(String type) {
        if (type == null || type.isEmpty()) {
            return false;
        }
        if (PRINT_ELEMENT_TYPE_TEXT.equals(type)) {
            return true;
        }
        if (PRINT_ELEMENT_TYPE_TABLE.equals(type)) {
            return true;
        }
        if (PRINT_ELEMENT_TYPE_HLINE.equals(type)) {
            return true;
        }
        if (PRINT_ELEMENT_TYPE_LONG_TEXT.equals(type)) {
            return true;
        }
        if (PRINT_ELEMENT_TYPE_IMAGE.equals(type)) {
            return true;
        }
        if (PRINT_ELEMENT_TYPE_BARCODE.equals(type)) {
            return true;
        }
        if (PRINT_ELEMENT_TYPE_QRCODE.equals(type)) {
            return true;
        }
        if (PRINT_ELEMENT_TYPE_VLINE.equals(type)) {
            return true;
        }
        return PRINT_ELEMENT_TYPE_RECT.equals(type);
    }

    private PrintConstants() {
    }
}
