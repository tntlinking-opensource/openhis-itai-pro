/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.medicationdispense.inpatient.response;

import com.zxplat.application.report.medicationdispense.inpatient.dto.InpatientMedicationDispenseDetailRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;

/**
 * 住院发药报表明细分页响应（IF-RPT-IMD-03）。
 *
 * <p>content 为两路并集平铺行；分页元数据经 {@code PagedResultBuilder} 装配。</p>
 */
public class PageInpatientMedicationDispenseReportDetailResponse
    extends PagedResult<InpatientMedicationDispenseDetailRowDto> {
}
