/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.model;

import lombok.Data;

/**
 * 应用差异预览样例行。
 */
@Data
public class ApplyPreviewSampleView {

    /** 动作。 */
    private String action;

    /** 国家目录编码。 */
    private String hilistCode;

    /** 项目码。 */
    private String projectCode;

    /** 影响摘要。 */
    private String effectSummary;
}
