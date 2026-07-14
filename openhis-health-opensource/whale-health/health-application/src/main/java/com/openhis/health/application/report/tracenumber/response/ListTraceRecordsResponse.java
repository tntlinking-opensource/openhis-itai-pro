/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.tracenumber.response;

import com.openhis.health.application.report.tracenumber.dto.TraceNumberRecordRowDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * IF-TRC-RPT-04：单码流转明细出参。
 */
@Getter
@Setter
public class ListTraceRecordsResponse {

    /** 追溯码。 */
    private String traceNo;

    /** 流水明细（时间升序）。 */
    private List<TraceNumberRecordRowDto> records;
}
