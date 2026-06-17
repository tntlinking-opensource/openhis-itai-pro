/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.basic.frequency.dto;

import com.openhis.health.application.basic.frequency.service.FrequencyAppService;
import com.openhis.whale.core.application.dto.OptionDto;
import lombok.Data;

import java.util.List;

/**
 * 用药频次维护页初始化数据：状态下拉、周期类型、适用场景、跨日策略与周期单位等固定选项。
 *
 * <p>各选项的 {@code code} 与领域/库内约定一致，须与 {@link FrequencyAppService#init()}
 * 保持同步。</p>
 */
@Data
public class FrequencyInitDto {

    /** 启用状态：1=启用，0=停用。 */
    private List<OptionDto> statusOptions;

    /** 周期类型（FrequencyCycleType code）。 */
    private List<OptionDto> cycleTypeOptions;

    /** 适用场景：outpatient/inpatient/nursing/all。 */
    private List<OptionDto> sceneOptions;

    /** 跨日选项：same-day / cross-day / by-shift。 */
    private List<OptionDto> allowCrossDayOptions;

    /** 执行周期单位：min / h / d / wk / mo。 */
    private List<OptionDto> periodUnitOptions;
}
