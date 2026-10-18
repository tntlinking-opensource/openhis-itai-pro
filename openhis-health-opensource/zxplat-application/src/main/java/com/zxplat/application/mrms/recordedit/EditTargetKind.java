/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.recordedit;

/**
 * 编辑申请目标类型（应用层路由键，禁止落入领域表）。
 */
public enum EditTargetKind {

    /** 临床文书。 */
    DOCUMENT("document"),

    /** 病案首页。 */
    HOMEPAGE("homepage");

    private final String code;

    EditTargetKind(String code) {
        this.code = code;
    }

    /**
     * 契约 code。
     *
     * @return code
     */
    public String getCode() {
        return code;
    }

    /**
     * 解析；非法返回 null。
     *
     * @param code 契约码
     * @return 枚举或 null
     */
    public static EditTargetKind fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim();
        for (EditTargetKind value : values()) {
            if (value.code.equalsIgnoreCase(normalized)) {
                return value;
            }
        }
        return null;
    }
}
