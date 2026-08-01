/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.notice.dto;

import lombok.Data;

/**
 * 应用差异预览样例 DTO。
 */
@Data
public class ApplyPreviewSampleDto {

    /** 动作。 */
    private String action;

    /** 国家目录编码。 */
    private String hilistCode;

    /** 项目码。 */
    private String projectCode;

    /** 影响摘要。 */
    private String effectSummary;
}
