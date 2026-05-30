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
 * 住院收费明细状态（读模型 {@code status_enum} 持久化码，与历史整数码字符串一致）。
 */
public enum ChargeDetailStatus implements EnumInterface {

    /** 待激活（医嘱草稿）。 */
    PENDING(0L, "10", "待激活", "", 10L),

    /** 预结算（预结算快照）。 */
    PRE_SETTLED(0L, "15", "预结算", "", 15L),

    /** 可计费（已执行激活）。 */
    BILLABLE(0L, "20", "可计费", "", 20L),

    /** 已结算。 */
    SETTLED(0L, "30", "已结算", "", 30L),

    /** 退费申请中。 */
    REFUND_PENDING(0L, "40", "退费申请中", "", 40L),

    /** 退费完成。 */
    REFUNDED(0L, "50", "退费完成", "", 50L),

    /** 已取消（执行撤销）。 */
    CANCELLED(0L, "90", "已取消", "", 90L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    ChargeDetailStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
