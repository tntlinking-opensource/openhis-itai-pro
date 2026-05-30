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
 * 就诊账户结账状态（{@code adm_account.billing_status_enum}，持久化存 {@link #getCode()}）。
 *
 * <p>对齐 DM-ADM-009 §4.2；新开账户默认 {@link #OPEN}。</p>
 */
public enum AccountBillingStatus implements EnumInterface {

    /** 账户开放，可产生费用。 */
    OPEN(
        0L,
        "open",
        "可用",
        "账户开放，可记账与收费。",
        1L),

    /** 住院已出院但尚未完成结算。 */
    CARECOMPLETE_NOTBILLED(
        0L,
        "carecomplete-notbilled",
        "已出院未结账",
        "医疗过程已结束，费用尚未结清。",
        2L),

    /** 账户因坏账关闭。 */
    CLOSED_BADDEBT(
        0L,
        "closed-baddebt",
        "坏账",
        "因坏账关闭，不再正常记账。",
        3L),

    /** 账户作废，不再记录费用。 */
    CLOSED_VOIDED(
        0L,
        "closed-voided",
        "不再记账",
        "账户作废关闭。",
        4L),

    /** 账户正常结算完成并关闭。 */
    CLOSED_COMPLETED(
        0L,
        "closed-completed",
        "已结账",
        "费用已结清并关闭账户。",
        5L),

    /** 账户因合并而关闭。 */
    CLOSED_COMBINED(
        0L,
        "closed-combined",
        "已合并",
        "账户已合并至其它账户行。",
        6L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    AccountBillingStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    @Override
    public Long getCodeSystemId() {
        return codeSystemId;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDisplay() {
        return display;
    }

    @Override
    public String getDefinition() {
        return definition;
    }

    @Override
    public Long getSortOrder() {
        return sortOrder;
    }
}
