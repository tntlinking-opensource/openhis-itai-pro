/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.enums;

/**
 * 文书编辑申请状态（持久化 {@code status_enum}；身份仅为 {@link #getCode()}）。
 *
 * <p>与病案侧 {@code HomepageEditApplicationStatus} 同形四态；禁止在实体上再镜像 String 常量。</p>
 */
public enum DocumentEditApplicationStatus {

    /** 起草中：可暂存工作稿。 */
    DRAFTING("DRAFTING", "起草中"),
    /** 待审核：已交审。 */
    PENDING_REVIEW("PENDING_REVIEW", "待审核"),
    /** 已通过：已回写文书。 */
    APPROVED("APPROVED", "已通过"),
    /** 已驳回：终态，须新开申请。 */
    REJECTED("REJECTED", "已驳回");

    private final String code;
    private final String display;

    DocumentEditApplicationStatus(String code, String display) {
        this.code = code;
        this.display = display;
    }

    /**
     * 持久化 code。
     *
     * @return 大写状态码
     */
    public String getCode() {
        return code;
    }

    /**
     * 展示名。
     *
     * @return 中文
     */
    public String getDisplay() {
        return display;
    }

    /**
     * 按持久化 code 解析；未命中返回 null。
     *
     * @param code {@code status_enum} 值
     * @return 枚举或 null
     */
    public static DocumentEditApplicationStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim();
        for (DocumentEditApplicationStatus item : values()) {
            if (item.code.equals(normalized)) {
                return item;
            }
        }
        return null;
    }
}
