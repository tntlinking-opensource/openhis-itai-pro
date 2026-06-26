/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.settlement.model;

import org.apache.commons.lang3.StringUtils;

/** 费用明细流水号（单次就诊内唯一）。 */
public record FeedetlSn(String value) {

    public FeedetlSn {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("feedetl_sn 不能为空");
        }
        value = value.trim();
    }

    public static FeedetlSn of(String value) {
        return new FeedetlSn(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
