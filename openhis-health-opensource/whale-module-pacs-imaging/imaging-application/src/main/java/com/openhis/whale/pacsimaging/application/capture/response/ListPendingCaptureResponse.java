/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.capture.response;

import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.pacsimaging.application.capture.dto.PendingCaptureRowDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 待采集列表响应。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListPendingCaptureResponse extends PagedResult<PendingCaptureRowDto> {
}
