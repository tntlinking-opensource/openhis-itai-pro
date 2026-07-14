/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dispense.response;

import com.openhis.health.application.outpatient.pharmacy.dispense.dto.EncounterMedicationDispenseSlipRowDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * 就诊药品发药单分页响应（IF-OPP-03）。
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为数据列表。</p>
 */
public class PageEncounterMedicationDispenseSlipsResponse extends PagedResult<EncounterMedicationDispenseSlipRowDto> {
}
