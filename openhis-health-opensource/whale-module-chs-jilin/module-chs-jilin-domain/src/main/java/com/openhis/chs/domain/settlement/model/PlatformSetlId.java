/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement.model;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

/** 平台结算 ID（2207/2304 产出） */
public record PlatformSetlId(String value) {

    public PlatformSetlId {
        if (isEmpty(value)) {
            throw new IllegalArgumentException("setl_id 不能为空");
        }
        value = value.trim();
    }

    public static PlatformSetlId of(String value) {
        return new PlatformSetlId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
