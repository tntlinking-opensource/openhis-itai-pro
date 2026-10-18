/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

import lombok.Getter;

/**
 * 检查报告状态（存库 code，对齐 DM-PACS-002）。
 */
@Getter
public enum PacsReportStatus implements EnumInterface {

    /** 草稿；submittedAt 非空时表示待审。 */
    PRELIMINARY(0L, "preliminary", "草稿/待审", "书写中或已提交待审", 1L),
    /** 已审核发布。 */
    FINAL(0L, "final", "已发布", "审核通过", 2L),
    /** 修订版。 */
    AMENDED(0L, "amended", "已修订", "修订后版本", 3L),
    /** 作废。 */
    ENTERED_IN_ERROR(0L, "entered-in-error", "作废", "作废报告", 4L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    PacsReportStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }
}
