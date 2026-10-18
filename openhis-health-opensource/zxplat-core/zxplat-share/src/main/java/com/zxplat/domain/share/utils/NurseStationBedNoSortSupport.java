/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.utils;

import com.zxplat.framework.core.application.util.StringUtils;

/**
 * 护士站床号自然序（按展示名，不按 busNo/id）。
 *
 * <p>例：504-4 &lt; 504-5 &lt; 505-4；505-5 &lt; 505-5（加床） &lt; 505-6；未分床垫底。
 * （比较符在 JavaDoc 中写作 &lt;。）</p>
 */
public final class NurseStationBedNoSortSupport {

    /** 未分床垫底键。 */
    public static final int UNASSIGNED = Integer.MAX_VALUE;

    private NurseStationBedNoSortSupport() {
    }

    /**
     * 床号排序键。
     *
     * @param room  房号
     * @param bed   床位号
     * @param extra 0=普通，1=加床
     */
    public record BedNoSortKey(int room, int bed, int extra) {
    }

    /**
     * 解析床位展示名为排序键。
     *
     * @param rawBedName 床位名；可空
     * @return 排序键；空名视为未分床
     */
    public static BedNoSortKey parse(String rawBedName) {
        if (StringUtils.isEmpty(rawBedName)) {
            return new BedNoSortKey(UNASSIGNED, UNASSIGNED, 0);
        }
        String trimmed = rawBedName.trim();
        if (trimmed.isEmpty()) {
            return new BedNoSortKey(UNASSIGNED, UNASSIGNED, 0);
        }
        int extra = 0;
        if (trimmed.contains("加床")) {
            extra = 1;
        }
        String cleaned = trimmed;
        if (extra == 1) {
            cleaned = trimmed.replace("（加床）", "")
                .replace("(加床)", "")
                .replace("加床", "")
                .trim();
        }
        cleaned = cleaned.replace('－', '-').replace('—', '-');
        int dash = cleaned.indexOf('-');
        if (dash > 0) {
            int room = parseLeadingInt(cleaned.substring(0, dash));
            int bed = parseLeadingInt(cleaned.substring(dash + 1));
            if (room >= 0 && bed >= 0) {
                return new BedNoSortKey(room, bed, extra);
            }
        }
        int single = parseLeadingInt(cleaned);
        if (single >= 0) {
            return new BedNoSortKey(single, 0, extra);
        }
        return new BedNoSortKey(UNASSIGNED - 1, 0, extra);
    }

    /**
     * 比较两个床位展示名。
     *
     * @param leftBedName  左床位名
     * @param rightBedName 右床位名
     * @return 负值 left 在前；正值 right 在前；0 相等
     */
    public static int compareBedNo(String leftBedName, String rightBedName) {
        BedNoSortKey left = parse(leftBedName);
        BedNoSortKey right = parse(rightBedName);
        if (left.room() != right.room()) {
            return Integer.compare(left.room(), right.room());
        }
        if (left.bed() != right.bed()) {
            return Integer.compare(left.bed(), right.bed());
        }
        if (left.extra() != right.extra()) {
            return Integer.compare(left.extra(), right.extra());
        }
        String leftRaw = leftBedName;
        if (leftRaw == null) {
            leftRaw = "";
        }
        String rightRaw = rightBedName;
        if (rightRaw == null) {
            rightRaw = "";
        }
        return leftRaw.compareTo(rightRaw);
    }

    /**
     * 解析前导整数；无数字返回 -1。
     *
     * @param raw 原始片段
     * @return 非负整数或 -1
     */
    private static int parseLeadingInt(String raw) {
        if (raw == null || raw.isEmpty()) {
            return -1;
        }
        int i = 0;
        while (i < raw.length() && Character.isDigit(raw.charAt(i))) {
            i++;
        }
        if (i == 0) {
            return -1;
        }
        try {
            return Integer.parseInt(raw.substring(0, i));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
