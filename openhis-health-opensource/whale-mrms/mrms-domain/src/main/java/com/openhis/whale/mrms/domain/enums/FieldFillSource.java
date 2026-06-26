/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.enums;

/**
 * 首页字段自动取值来源标记（field_sources_json 值域）。
 */
public enum FieldFillSource {

    /** 人工修订，自动取值不得覆盖。 */
    MANUAL("manual", "人工"),
    /** 系统自动带入。 */
    AUTO("auto", "自动"),
    /** 人工修订后又被自动刷新覆盖过。 */
    MIXED("mixed", "混合");

    private final String code;
    private final String label;

    FieldFillSource(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 持久化来源码。
     *
     * @return 来源 code
     */
    public String getCode() {
        return code;
    }

    /**
     * 中文展示名。
     *
     * @return 展示标签
     */
    public String getLabel() {
        return label;
    }
}
