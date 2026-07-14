/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.registration.response;

import com.openhis.health.application.inpatient.financial.registration.dto.RegistrationPatientSelectItemDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * 住院登记患者分页检索响应（IF-IP-REG-06）。
 *
 * <p>继承 {@link PagedResult}，{@code pageNo} 为 1-based（B.10.11）。</p>
 */
public class PagePatientsForSelectResponse extends PagedResult<RegistrationPatientSelectItemDto> {
}
