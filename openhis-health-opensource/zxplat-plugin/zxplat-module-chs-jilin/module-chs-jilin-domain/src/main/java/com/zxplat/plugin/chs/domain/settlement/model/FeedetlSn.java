/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.settlement.model;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;

/** 费用明细流水号（单次就诊内唯一） */
public record FeedetlSn(String value) {

    public FeedetlSn {
        if (isEmpty(value)) {
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
