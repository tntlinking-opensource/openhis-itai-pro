/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.enums;
/**
 * 票据状态枚举（对齐 DM-ADM-015 §2.3 状态机）。
 *
 * <p>status_enum 字段整数值与本枚举一一对应；禁止在业务代码中使用字面量。</p>
 */
public enum InvoiceStatus implements EnumInterface {
    DRAFT(0L, "DRAFT", "草稿：票据已创建但未开具", "", 1L),
    ISSUED(0L, "ISSUED", "已开具：电子票据已生成并关联单号", "", 2L),
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
