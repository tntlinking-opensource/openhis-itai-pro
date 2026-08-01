/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusgfapproval.response;

import com.openhis.campus.application.campusgfrequestrecord.response.GfRatioRequestDto;
import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageFinishedResponse extends PagedResult<GfRatioRequestDto> {
}
