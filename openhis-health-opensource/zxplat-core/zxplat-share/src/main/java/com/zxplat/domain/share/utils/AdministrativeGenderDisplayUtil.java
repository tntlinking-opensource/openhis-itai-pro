/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.utils;

import com.zxplat.domain.share.enums.AdministrativeGender;

/**
 * 行政性别 code → 展示文案（打印/列表/Mapper 共用）。
 *
 * <p>出站对方系统码表（LIS/PACS/首页）见 {@link AdministrativeGenderOutboundUtil}，勿在此混写。</p>
 */
public final class AdministrativeGenderDisplayUtil {

    private AdministrativeGenderDisplayUtil() {
    }

    /**
     * @param genderEnum 持久化 gender code
     * @return 展示文案；未命中枚举时回落为 code 或空串
     */
    public static String toDisplayText(String genderEnum) {
        AdministrativeGender g = EnumUtil.fromCode(AdministrativeGender.class, genderEnum);
        if (g != null) {
            return g.getDisplay();
        }
        if (genderEnum != null) {
            return genderEnum;
        }
        return "";
    }
}
