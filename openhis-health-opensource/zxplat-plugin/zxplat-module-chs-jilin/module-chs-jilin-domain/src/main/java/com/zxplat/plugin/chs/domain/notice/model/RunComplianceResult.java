/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 合规跑批写结果。
 */
@Data
public class RunComplianceResult {
    /** 新报告主键。 */
    private Long reportId;
    /** 问题条数。 */
    private int issueCount;
    /** 跑批完成时间。 */
    private LocalDateTime ranAt;
}
