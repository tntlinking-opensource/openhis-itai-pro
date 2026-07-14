/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.setting.application.dto;

import com.openhis.whale.core.application.dto.OptionDto;
import lombok.Data;

import java.util.List;

@Data
public class SettingInitDto {

    private List<OptionDto> providers;
}
