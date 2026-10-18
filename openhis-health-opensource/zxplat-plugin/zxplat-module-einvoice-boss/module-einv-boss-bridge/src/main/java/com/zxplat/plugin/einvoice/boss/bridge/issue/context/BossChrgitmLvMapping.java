/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.bridge.issue.context;

/**
 * 院内医保等级 {@code chrgitmLv} → 博思清单 {@code listDetail.medicalCareType} / remark。
 *
 * <p>院内：1自费 / 2甲 / 3乙 / 4丙；博思：1甲 / 2乙 / 3丙（全自负）。</p>
 */
public final class BossChrgitmLvMapping {

    private BossChrgitmLvMapping() {
    }

    /**
     * @param chrgitmLv 院内医保等级整数码；可空
     * @return 博思清单医保药品分类码与备注文案
     */
    public static Grade resolve(Integer chrgitmLv) {
        if (chrgitmLv == null) {
            return new Grade(null, null);
        }
        return switch (chrgitmLv) {
            case 2 -> new Grade("1", "甲类");
            case 3 -> new Grade("2", "乙类");
            case 4 -> new Grade("3", "丙类");
            case 1 -> new Grade("3", "自费");
            default -> new Grade(null, null);
        };
    }

    public record Grade(String medicalCareType, String remark) {
    }
}
