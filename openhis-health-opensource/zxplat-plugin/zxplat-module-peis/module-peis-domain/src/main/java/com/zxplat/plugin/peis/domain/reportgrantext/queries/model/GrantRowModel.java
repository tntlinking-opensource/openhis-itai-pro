/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.reportgrantext.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 报告发放列表行读模型（DM-PEIS-005）。
 */
@Getter
@Setter
public class GrantRowModel {

    /** 总检报告主键。 */
    private Long diagnosticReportId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 发放状态 code。 */
    private String grantStatusCode;

    /** 发放时间。 */
    private LocalDateTime grantTime;

    /** 签收人姓名。 */
    private String recipientName;
}
