/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 病案首页编辑申请状态（持久化 {@code status_enum}）。
 */
public enum HomepageEditApplicationStatus implements EnumInterface {

    /** 起草中：可暂存工作稿。 */
    DRAFTING(0L, "DRAFTING", "起草中", "", 0L),
    /** 待审核：已交审。 */
    PENDING_REVIEW(0L, "PENDING_REVIEW", "待审核", "", 1L),
    /** 已通过：已回写首页。 */
    APPROVED(0L, "APPROVED", "已通过", "", 2L),
    /** 已驳回：终态，须新开申请。 */
    REJECTED(0L, "REJECTED", "已驳回", "", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    HomepageEditApplicationStatus(
            Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    public Long getCodeSystemId() {
        return codeSystemId;
    }

    public String getCode() {
        return code;
    }

    public String getDisplay() {
        return display;
    }

    public String getDefinition() {
        return definition;
    }

    public Long getSortOrder() {
        return sortOrder;
    }

    /**
     * 按持久化 code 解析枚举。
     *
     * @param code 持久化串
     * @return 枚举；未知则 null
     */
    public static HomepageEditApplicationStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (HomepageEditApplicationStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
