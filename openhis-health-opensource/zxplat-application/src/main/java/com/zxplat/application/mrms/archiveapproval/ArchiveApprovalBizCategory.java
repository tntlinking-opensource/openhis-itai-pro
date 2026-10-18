/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.archiveapproval;

/**
 * 归档审批业务类别（应用层路由键，非领域枚举）。
 *
 * <p>对应 AS-MRMS-APR-001 {@code bizCategory}。</p>
 */
public enum ArchiveApprovalBizCategory {

    /** 门诊文书。 */
    OP_DOCUMENT("op_document"),

    /** 住院文书。 */
    IP_DOCUMENT("ip_document"),

    /** 病案首页。 */
    CASE_HOMEPAGE("case_homepage");

    private final String code;

    ArchiveApprovalBizCategory(String code) {
        this.code = code;
    }

    /**
     * 存储/契约 code。
     *
     * @return code
     */
    public String getCode() {
        return code;
    }

    /**
     * 按 code 解析；非法返回 null。
     *
     * @param code 契约码
     * @return 枚举或 null
     */
    public static ArchiveApprovalBizCategory fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim();
        for (ArchiveApprovalBizCategory value : values()) {
            if (value.code.equalsIgnoreCase(normalized)) {
                return value;
            }
        }
        return null;
    }
}
