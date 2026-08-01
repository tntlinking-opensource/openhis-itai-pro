/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.notice.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 再次合规校验响应（IF-CHS-CAT-005-W-05）。
 */
@Data
public class RerunNoticeComplianceResponse {
    /** 新写入的报告主键。 */
    private Long reportId;
    /** 本次报告问题条数。 */
    private int issueCount;
    /** 跑批完成时间。 */
    private LocalDateTime ranAt;
}
