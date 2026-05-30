package com.openhis.whale.mrms.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 首页字段填充来源，写入 field_sources_json。
 */
@Getter
@RequiredArgsConstructor
public enum FieldFillSource {
    MANUAL("manual"),
    AUTO("auto"),
    MIXED("mixed");

    private final String code;

    public static FieldFillSource fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (FieldFillSource source : values()) {
            if (source.code.equals(code)) {
                return source;
            }
        }
        return null;
    }
}
