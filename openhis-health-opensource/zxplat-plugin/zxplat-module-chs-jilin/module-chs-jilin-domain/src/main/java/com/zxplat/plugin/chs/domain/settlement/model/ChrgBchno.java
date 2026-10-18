/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.settlement.model;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;

/** 平台收费批次号（2204/2206/2207 分组键） */
public record ChrgBchno(String value) {

    public ChrgBchno {
        if (isEmpty(value)) {
            throw new IllegalArgumentException("chrg_bchno 不能为空");
        }
        value = value.trim();
    }

    public static ChrgBchno of(String value) {
        return new ChrgBchno(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
