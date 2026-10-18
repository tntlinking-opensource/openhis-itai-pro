/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;
/**
 * 票据状态枚举（对齐 DM-ADM-015 §2.3 状态机）。
 *
 * <p>status_enum 字段整数值与本枚举一一对应；禁止在业务代码中使用字面量。</p>
 */
public enum InvoiceStatus implements EnumInterface {
/** 草稿：票据已创建但未开具（InvoiceStatus，编码 {@code DRAFT}）。 */
    DRAFT(0L, "DRAFT", "草稿：票据已创建但未开具", "", 1L),
/** 已开具：电子票据已生成并关联单号（InvoiceStatus，编码 {@code ISSUED}）。 */
    ISSUED(0L, "ISSUED", "已开具：电子票据已生成并关联单号", "", 2L),
/** 已作废/冲红：票据已核销并生成红冲记录（InvoiceStatus，编码 {@code CANCELLED}）。 */
    CANCELLED(0L, "CANCELLED", "已作废/冲红：票据已核销并生成红冲记录", "", 4L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    InvoiceStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
     * 与 statusEnum 字段值比较是否相等。
     *
     * @param statusEnum 实体中的 statusEnum 字段值
     * @return 是否相等
     */
    public boolean sameAs(String statusEnum) {
        return this.getCode().equals(statusEnum);
    }

}
