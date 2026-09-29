/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.config.modality.response;

import com.openhis.health.application.pacs.config.modality.dto.PacsCapabilityRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模态能力列表响应。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchCapabilitiesResponse extends PagedResult<PacsCapabilityRowDto> {
}
