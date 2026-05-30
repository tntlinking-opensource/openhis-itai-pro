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
 * 供应请求单据状态枚举。
 *
 * <p>存储口径：{@code wkf_supply_request.status_enum}。</p>
 */
public enum SupplyStatus implements EnumInterface {
    /**
     * PENDING_APPROVAL。
     */
    PENDING_APPROVAL(0L, "PENDING_APPROVAL", "待审核", "", 1L),
    /**
     * APPROVAL。
     */
    APPROVAL(0L, "APPROVAL", "审核中", "", 2L),
    /**
     * AGREE。
     */
    AGREE(0L, "AGREE", "同意", "", 3L),
    /**
     * REJECT。
     */
    REJECT(0L, "REJECT", "驳回", "", 4L),
    /**
     * WITHDRAW。
     */
    WITHDRAW(0L, "WITHDRAW", "已撤回", "", 9L),
    /**
     * PENDING_REVIEW。
     */
    PENDING_REVIEW(0L, "PENDING_REVIEW", "待复审", "", 10L),
    /**
     * EXPIRED_INVALIDATED。
     */
    EXPIRED_INVALIDATED(0L, "EXPIRED_INVALIDATED", "已失效", "", 11L),
    /**
     * SEND。
     */
    SEND(0L, "SEND", "已发送", "", 12L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * SupplyStatus。
     */
    SupplyStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
