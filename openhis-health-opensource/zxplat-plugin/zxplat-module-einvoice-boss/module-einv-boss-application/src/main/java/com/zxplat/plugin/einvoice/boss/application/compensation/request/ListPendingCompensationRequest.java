/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.compensation.request;

import lombok.Data;

import java.time.LocalDate;

/**
 * 待补偿列表查询条件。
 */
@Data
public class ListPendingCompensationRequest {

    /** 开始日期（含）。 */
    private LocalDate dateFrom;

    /** 结束日期（含）。 */
    private LocalDate dateTo;

    /** 患者姓名或就诊号关键词。 */
    private String keyword;

    /** 异常类型过滤（LOST/AMOUNT/FAILED/STALE）。 */
    private String exceptionType;

    /** 业务线过滤（REG/AMB）。 */
    private String issueKind;

    /** 页码（从 1 起）。 */
    private Integer pageNo = 1;

    /** 每页条数。 */
    private Integer pageSize = 20;
}
