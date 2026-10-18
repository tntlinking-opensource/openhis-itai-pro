/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.application.capture.response;

import com.zxplat.framework.core.application.dto.PagedResult;
import com.zxplat.plugin.pacsimaging.application.capture.dto.ModalityWorklistRowDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MWL 待扫工单列表响应。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListModalityWorklistResponse extends PagedResult<ModalityWorklistRowDto> {
}
