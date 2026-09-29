/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.registration.response;

import com.openhis.health.application.pacs.registration.dto.PacsRegistrationRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 待登记列表响应（IF-PACS-REG-01）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchPendingRegistrationResponse extends PagedResult<PacsRegistrationRowDto> {
}
