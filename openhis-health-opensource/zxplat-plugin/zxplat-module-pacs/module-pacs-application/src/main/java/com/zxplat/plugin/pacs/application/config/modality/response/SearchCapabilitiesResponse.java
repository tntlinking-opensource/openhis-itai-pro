/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.config.modality.response;

import com.zxplat.plugin.pacs.application.config.modality.dto.PacsCapabilityRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模态能力列表响应。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchCapabilitiesResponse extends PagedResult<PacsCapabilityRowDto> {
}
