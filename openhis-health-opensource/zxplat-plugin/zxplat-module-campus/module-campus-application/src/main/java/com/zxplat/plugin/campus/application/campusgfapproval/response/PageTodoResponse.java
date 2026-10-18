/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusgfapproval.response;

import com.zxplat.plugin.campus.application.campusgfrequestrecord.response.GfRatioRequestDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageTodoResponse extends PagedResult<GfRatioRequestDto> {
}
