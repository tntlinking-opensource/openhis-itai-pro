/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.weistat.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将首页「天/时/分」昏迷时长文本拆为卫统 {@code RYQ_T/XS/F}、{@code RYH_T/XS/F}。
 *
 * <p>兼容常见写法：{@code 1天2时3分}、{@code 1/2/3}、仅数字三段空格分隔。</p>
 */
public final class WeistatComaDurationSplitter {

    private static final Pattern CN_PATTERN = Pattern.compile(
            "(\\d+)\\s*天\\s*(\\d+)\\s*时\\s*(\\d+)\\s*分?");

    private static final Pattern SLASH_PATTERN = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)\\s*/\\s*(\\d+)");

    /** 禁止实例化。 */
    private WeistatComaDurationSplitter() {
    }

    /**
     * 拆分结果（天/时/分；解析失败则全空串）。
     *
     * @param days   天
     * @param hours  时
     * @param minutes 分
     */
    public record Parts(String days, String hours, String minutes) {
    }

    /**
     * 解析昏迷时长文本。
     *
     * @param raw 首页 {@code brnDamgBfadmComaDura}/{@code brnDamgAfadmComaDura}
     * @return 三部分；无法解析则空串
     */
    public static Parts split(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Parts("", "", "");
        }
        String text = raw.trim();
        Matcher cn = CN_PATTERN.matcher(text);
        if (cn.find()) {
            return new Parts(cn.group(1), cn.group(2), cn.group(3));
        }
        Matcher slash = SLASH_PATTERN.matcher(text);
        if (slash.find()) {
            return new Parts(slash.group(1), slash.group(2), slash.group(3));
        }
        return new Parts("", "", "");
    }
}
