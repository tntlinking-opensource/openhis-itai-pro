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
 * 支付流水状态枚举。
 */
public enum PaymentStatus implements EnumInterface {
    /**
     * DRAFT。
     */
    DRAFT(0L, "DRAFT", "草稿（预结算已生成，尚未收款）", "", 0L),
    /**
     * SUCCESS。
     */
    SUCCESS(0L, "SUCCESS", "成功（收款已完成）", "", 1L),
    /**
     * CANCELLED。
     */
    CANCELLED(0L, "CANCELLED", "已取消（收费前放弃）", "", 2L),
    /**
     * REFUNDED。
     */
    REFUNDED(0L, "REFUNDED", "已全额退款", "", 3L),
    /**
     * PARTIAL_REFUNDED。
     */
    PARTIAL_REFUNDED(0L, "PARTIAL_REFUNDED", "已部分退款", "", 4L),
    /**
     * REFUND_PROCESSING。
     */
    REFUND_PROCESSING(0L, "REFUND_PROCESSING", "退款受理中", "", 5L),
    /**
     * ERROR。
     */
    ERROR(0L, "ERROR", "错误（支付处理失败或误录）", "", 9L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * PaymentStatus。
     */
    PaymentStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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

}
