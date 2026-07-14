/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.summarydispense.response;

import com.openhis.health.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummarySlipHeaderDto;
import com.openhis.whale.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 分页查询待核发汇总单头响应（IF-IPS-01）。
 */
/** 分页查询待核发汇总单头响应。 */
@Schema(description = "分页查询待核发汇总单头响应")
public class PagePendingMedicationSummarySlipsResponse extends PagedResult<InpatientPharmacySummarySlipHeaderDto> {

    private static final long serialVersionUID = 1L;
}
