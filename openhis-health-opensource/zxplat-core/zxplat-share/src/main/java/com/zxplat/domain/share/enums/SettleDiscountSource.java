/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

import com.zxplat.domain.share.utils.EnumUtil;
import lombok.Getter;

/**
 * 收费明细结算折扣来源（{@code adm_charge_item.settle_discount_source_enum}；DM-ADM-011 / DM-CUS-016）。
 */
@Getter
public enum SettleDiscountSource implements EnumInterface {

    /** 未参与打折。 */
    NONE(0L, "NONE", "无折扣", "", 1L),

    /** 命中配置固定折扣率。 */
    CONFIG(0L, "CONFIG", "配置固定率", "", 2L),

    /** 命中「款员定率」配置，本笔采用款员所选率。 */
    CASHIER(0L, "CASHIER", "款员定率", "", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造。
     *
     * @param codeSystemId 编码系统（本枚举未挂术语时可 0）
     * @param code         落库 code
     * @param display      展示名
     * @param definition   定义说明
     * @param sortOrder    排序
     */
    SettleDiscountSource(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    /**
     * 按持久化 code 解析；未命中返回 {@code null}。
     *
     * @param code 来源 code
     * @return 枚举项或 null
     */
    public static SettleDiscountSource fromCode(String code) {
        return EnumUtil.fromCode(SettleDiscountSource.class, code);
    }
}
