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
 * 价格调整审批状态枚举。
 */
public enum PriceAdjustmentStatus implements EnumInterface {
    /**
     * DRAFT。
     */
    DRAFT(0L, "draft", "草稿", "", 1L),
    /**
     * PENDING。
     */
    PENDING(0L, "pending", "待审批", "", 2L),
    /**
     * APPROVED。
     */
    APPROVED(0L, "approved", "已通过", "", 3L),
    /**
     * REJECTED。
     */
    REJECTED(0L, "rejected", "已驳回", "", 4L),
    /**
     * DEPRECATED。
     */
    DEPRECATED(0L, "deprecated", "已废弃", "", 5L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * PriceAdjustmentStatus。
     */
    PriceAdjustmentStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
