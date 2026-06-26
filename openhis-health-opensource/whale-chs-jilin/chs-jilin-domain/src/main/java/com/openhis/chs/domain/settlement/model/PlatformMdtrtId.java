/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.settlement.model;

import org.apache.commons.lang3.StringUtils;

/** 平台就诊 ID。 */
public record PlatformMdtrtId(String value) {

    public PlatformMdtrtId {
        if (StringUtils.isBlank(value)) {
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
