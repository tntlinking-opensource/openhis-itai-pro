/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 收费员交账头状态（DM-FIN-003 {@code status_enum}）。
 */
public enum CashierSettlementStatus implements EnumInterface {

    /** 已交账归档，挂载 payment 不可再纳入其它交账。 */
    HANDED_OVER(0L, "HANDED_OVER", "已交账", "有效交账记录", 0L),

    /** 取消交账（预留，本期不写侧不实现）。 */
    CANCELLED(0L, "CANCELLED", "取消交账", "预留", 1L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    CashierSettlementStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
