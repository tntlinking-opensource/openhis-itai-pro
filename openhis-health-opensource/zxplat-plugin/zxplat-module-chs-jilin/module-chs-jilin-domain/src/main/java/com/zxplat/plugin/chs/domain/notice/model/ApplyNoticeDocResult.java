/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.model;

import lombok.Data;

/**
 * 应用通知文件结果。
 */
@Data
public class ApplyNoticeDocResult {

    /** 成功应用行数。 */
    private int appliedLineCount;

    /** 失败行数。 */
    private int failedLineCount;

    /** 院内 lv 回写条数。 */
    private int writebackCount;

    /** 未收费明细同步条数。 */
    private int syncedUnchargedCount;
}
