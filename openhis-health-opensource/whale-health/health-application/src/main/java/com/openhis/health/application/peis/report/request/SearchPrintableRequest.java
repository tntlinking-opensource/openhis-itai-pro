/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.report.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 可打印报告列表请求（IF-PEIS-REP-01）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchPrintableRequest extends AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    /** 机构标识。 */
    private Long orgId;

    /** 定稿日期起（含）。 */
    private LocalDate dateFrom;

    /** 定稿日期止（含）。 */
    private LocalDate dateTo;
}
