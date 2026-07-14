/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement.model;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

/** 平台就诊 ID */
public record PlatformMdtrtId(String value) {

    public PlatformMdtrtId {
        if (isEmpty(value)) {
            throw new IllegalArgumentException("mdtrt_id 不能为空");
        }
        value = value.trim();
    }

    public static PlatformMdtrtId of(String value) {
        return new PlatformMdtrtId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
