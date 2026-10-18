/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

import lombok.Getter;

/**
 * 检查工单 orderLink 关联态（存库 code，对齐 DM-PACS-001）。
 */
@Getter
public enum PacsOrderLinkStatus implements EnumInterface {

    /** 有效关联。 */
    ACTIVE(0L, "active", "有效", "", 1L),
    /** 该项已撤/作废。 */
    CANCELLED(0L, "cancelled", "已作废", "", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    PacsOrderLinkStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }
}
