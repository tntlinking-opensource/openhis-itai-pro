/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.notice.response;

import lombok.Data;

/**
 * 应用文件响应（IF-CHS-CAT-005-W-03）。
 */
@Data
public class ApplyNoticeDocResponse {

    /** 成功应用行数。 */
    private int appliedLineCount;

    /** 失败行数。 */
    private int failedLineCount;

    /** 院内 lv 回写条数。 */
    private int writebackCount;

    /** 未收费明细同步条数。 */
    private int syncedUnchargedCount;
}
