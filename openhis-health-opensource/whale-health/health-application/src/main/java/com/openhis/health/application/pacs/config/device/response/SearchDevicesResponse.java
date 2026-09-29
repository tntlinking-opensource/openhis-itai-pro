/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.config.device.response;

import com.openhis.health.application.pacs.config.device.dto.PacsDeviceRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备台账列表响应。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchDevicesResponse extends PagedResult<PacsDeviceRowDto> {
}
