/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.utils;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.whale.core.infrastructure.util.ChineseConvertUtils;

import java.util.Locale;
import java.util.Objects;

/**
 * 检索码工具类：将汉字名称转换为拼音首字母码（pyStr）与五笔首字母码（wbStr）。
 *
 * <p>底层依赖 {@code ChineseConvertUtils}（whale-core 提供，基于 pinyin4j 库）；
 * 本工具类作为领域共享层的统一入口，方便所有需要生成检索码的聚合根复用（如
 * ActivityDefinition、MedicationDefinition 等含 pyStr/wbStr 字段的实体）。</p>
 *
 * <p>使用前提：输入名称不能为 null；空串时返回空串。</p>
 */
public final class SearchCodeUtil {

    /**
     * 工具类不允许实例化。
     */
    private SearchCodeUtil() {
    }

    /**
     * 将汉字字符串转换为拼音首字母码（大写）。
     *
     * <p>示例：{@code toPyStr("心电图")} → {@code "XDT"}（实际大写由底层库决定）。
     * 非汉字字符（ASCII 字母、数字、符号）原样追加到结果。</p>
     *
     * @param name 原始名称，可含汉字与 ASCII 混合字符；不能为 null
     * @return 拼音首字母码；输入为空串时返回空串
     */
    public static String toPyStr(String name) {
        if (name == null || name.isEmpty()) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        return ChineseConvertUtils.toPinyinFirstLetter(name).toUpperCase();
    }

    /**
     * 将汉字字符串转换为五笔首字母码（大写）。
     *
     * <p>示例：{@code toWbStr("心电图")} → 对应各字五笔根码首字母大写拼合。
     * 非汉字字符（ASCII 字母、数字、符号）原样追加到结果。</p>
     *
     * @param name 原始名称，可含汉字与 ASCII 混合字符；不能为 null
     * @return 五笔首字母码；输入为空串时返回空串
     */
    public static String toWbStr(String name) {
        if (name == null || name.isEmpty()) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        return ChineseConvertUtils.toWBFirstLetter(name).toUpperCase();
    }

    /**
     * 用户输入关键字子串模糊检索用的 LIKE 模式：与 JPA {@code cb.lower(列)} 比较，**一律不区分大小写**。
     *
     * <p>凡「姓名、编码、业务号、拼音、五笔」等由用户键盘输入驱动的 {@code LIKE}，列侧统一 {@code cb.lower(root.get(...))}，
     * 模式侧统一使用本方法返回值；调用方须先对原始关键字做 {@code trim}。两侧通配符使用 {@link GeneralSharedConstants#LIKE_PERCENT}。</p>
     *
     * <p>排序口径（硬门槛）：关键字非空时须「先精确后模糊」——见 {@link KeywordExactFirstOrderSupport}
     * 与通用规范 B.2.1；禁止仅 LIKE 而无精确优先 ORDER BY（分页场景尤甚）。</p>
     *
     * @param trimmedKeyword 已去除首尾空白的关键字（允许为空串，得到 {@code %%}）
     * @return 形如 {@code %关键字小写%} 的匹配串
     */
    public static String toCaseInsensitiveLikePattern(String trimmedKeyword) {
        Objects.requireNonNull(trimmedKeyword, "trimmedKeyword");
        return GeneralSharedConstants.LIKE_PERCENT
            + trimmedKeyword.toLowerCase(Locale.ROOT)
            + GeneralSharedConstants.LIKE_PERCENT;
    }

    /**
     * 关键字忽略大小写「全文精确」比较值：与 JPA {@code cb.lower(列)} / {@code EQUAL} 配对。
     *
     * <p>用于 {@link KeywordExactFirstOrderSupport} 判定精确命中；调用方须先 {@code trim}。
     * <strong>禁止</strong>再手写 {@code toLowerCase} 散落业务路径。</p>
     *
     * @param trimmedKeyword 已去除首尾空白的关键字
     * @return {@code Locale.ROOT} 小写全文（无通配符）
     */
    public static String toCaseInsensitiveExactValue(String trimmedKeyword) {
        Objects.requireNonNull(trimmedKeyword, "trimmedKeyword");
        return trimmedKeyword.toLowerCase(Locale.ROOT);
    }

    /**
     * 是否为纯拉丁字母关键字（典型拼音/五笔首码输入）。
     *
     * <p>目录选品可据此只扫 {@code py_str}/{@code wb_str} 等检索码列，避免对中文名称列做宽 OR LIKE。</p>
     *
     * @param trimmedKeyword 已 trim 的关键字
     * @return 非空且每一位均为 a-z/A-Z 时为 true
     */
    public static boolean isAsciiLetterKeyword(String trimmedKeyword) {
        if (trimmedKeyword == null || trimmedKeyword.isEmpty()) {
            return false;
        }
        for (int i = 0; i < trimmedKeyword.length(); i++) {
            char ch = trimmedKeyword.charAt(i);
            if (ch >= 'a' && ch <= 'z') {
                continue;
            }
            if (ch >= 'A' && ch <= 'Z') {
                continue;
            }
            return false;
        }
        return true;
    }
}
