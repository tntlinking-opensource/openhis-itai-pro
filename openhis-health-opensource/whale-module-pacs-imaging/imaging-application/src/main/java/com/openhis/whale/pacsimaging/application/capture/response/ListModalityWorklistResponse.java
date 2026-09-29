/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.capture.response;

import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.pacsimaging.application.capture.dto.ModalityWorklistRowDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MWL 待扫工单列表响应。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListModalityWorklistResponse extends PagedResult<ModalityWorklistRowDto> {
}
