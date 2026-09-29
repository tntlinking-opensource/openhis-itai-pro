/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

import lombok.Getter;

/**
 * 关键图/截图来源（存库 code，对齐 DM-PACS-003）。
 */
@Getter
public enum MediaSource implements EnumInterface {

    /** 关键图。 */
    KEY_IMAGE(0L, "KEY_IMAGE", "关键图", "检查关键图像", 1L),
    /** 截图。 */
    SCREENSHOT(0L, "SCREENSHOT", "截图", "阅片截图", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    MediaSource(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }
}
