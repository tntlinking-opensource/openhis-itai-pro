/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.tracenumber.queries;

import com.zxplat.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 追溯记录审计分页入参（DM-CUS-010 §6.2.5）。
 */
@Getter
@Setter
public class TraceRecordPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 追溯码关键字。 */
    private String traceNoKeyword;

    /** 库位标识。 */
    private Long locationId;

    /** 操作类型 code。 */
    private String operationType;

    /** 业务单号。 */
    private String busNo;

    /** 记录时间下限。 */
    private LocalDateTime recordTimeStart;

    /** 记录时间上限。 */
    private LocalDateTime recordTimeEnd;
}
