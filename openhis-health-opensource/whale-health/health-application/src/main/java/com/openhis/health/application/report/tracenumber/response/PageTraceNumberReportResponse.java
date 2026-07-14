/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.tracenumber.response;

import com.openhis.health.application.report.tracenumber.dto.TraceNumberReportRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * IF-TRC-RPT-02：追溯码查询报表分页出参（B.10.11 · {@link PagedResult}）。
 */
@Getter
@Setter
public class PageTraceNumberReportResponse extends PagedResult<TraceNumberReportRowDto> {

    /** deep link 回显追溯码。 */
    private String prefillTraceNo;

    /** deep link 回显是否打开详情 Drawer。 */
    private Boolean openDetail;
}
