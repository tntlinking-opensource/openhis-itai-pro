/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.settlement.model;

import org.apache.commons.lang3.StringUtils;

/** 平台收费批次号（2204/2206/2207 分组键）。 */
public record ChrgBchno(String value) {

    public ChrgBchno {
        if (StringUtils.isBlank(value)) {
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
