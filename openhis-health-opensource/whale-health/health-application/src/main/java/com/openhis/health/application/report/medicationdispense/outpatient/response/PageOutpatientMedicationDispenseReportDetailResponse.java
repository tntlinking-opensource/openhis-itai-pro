/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.medicationdispense.outpatient.response;

import com.openhis.health.application.report.medicationdispense.outpatient.dto.OutpatientMedicationDispenseDetailRowDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * 门诊发药报表明细分页响应（IF-RPT-OMD-03）。
 *
 * <p>content 为两路并集平铺行；分页元数据经 {@code PagedResultBuilder} 装配。</p>
 */
public class PageOutpatientMedicationDispenseReportDetailResponse
    extends PagedResult<OutpatientMedicationDispenseDetailRowDto> {
}
