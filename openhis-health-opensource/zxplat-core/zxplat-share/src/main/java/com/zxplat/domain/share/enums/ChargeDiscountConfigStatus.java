/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

import com.zxplat.domain.share.utils.EnumUtil;

import lombok.Getter;

/**
 * 收费折扣配置状态（DM-CUS-016 §2；仅 ACTIVE 参与命中与折扣相关显隐）。
 */
@Getter
public enum ChargeDiscountConfigStatus implements EnumInterface {
    /** 有效：参与命中与 {@code isDiscountFeatureEnabled} 计数。 */
    ACTIVE(0L, "ACTIVE", "有效", "", 1L),
    /** 停用：配置列表可见，不参与命中与折扣相关显隐。 */
    INACTIVE(0L, "INACTIVE", "停用", "", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定持久化 code 与展示名。
     *
     * @param codeSystemId 编码系统（本枚举未挂术语时可 0）
     * @param code         落库 code
     * @param display      展示名
     * @param definition   定义说明
     * @param sortOrder    排序
     */
    ChargeDiscountConfigStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    /**
     * 按持久化 code 解析；未命中返回 {@code null}。
     *
     * @param code {@code status_enum} 值
     * @return 枚举项或 null
     */
    public static ChargeDiscountConfigStatus fromCode(String code) {
        return EnumUtil.fromCode(ChargeDiscountConfigStatus.class, code);
    }
}
