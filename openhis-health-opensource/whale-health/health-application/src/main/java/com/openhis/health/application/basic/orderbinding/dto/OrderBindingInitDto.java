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

package com.openhis.health.application.basic.orderbinding.dto;

import com.openhis.whale.core.application.dto.OptionDto;
import lombok.Data;

import java.util.List;

/**
 * 服务设备模板绑定配置页初始化数据：状态、范围、绑定类型、单位与各来源/目标候选下拉。
 */
@Data
public class OrderBindingInitDto {

    /** 启用状态：1 启用，2 停用。 */
    private List<OptionDto> statusOptions;

    /** 绑定范围（如全院），码值对齐 {@link com.openhis.health.domain.share.enums.ServiceDeviceTemplateRangeCode}。 */
    private List<OptionDto> rangeOptions;

    /** 绑定类型：1 号源 2 用法 3 诊疗，与 {@link com.openhis.health.domain.share.enums.ServiceDeviceTemplateTypeCode} 映射一致。 */
    private List<OptionDto> bindTypeOptions;

    /** 计量单位选项（术语集 unit）。 */
    private List<OptionDto> unitOptions;

    /** 用法来源（术语 method）候选。 */
    private List<OptionDto> usageSourceOptions;

    /** 诊疗活动定义候选（用于绑定类型=诊疗时选择来源项）。 */
    private List<OptionDto> activitySourceOptions;

    /** 号源/服务（挂号服务等）候选。 */
    private List<OptionDto> sourceSourceOptions;

    /** 耗材/设备定义候选（目标为设备时辅助选择，与页面流程配合）。 */
    private List<OptionDto> deviceDefOptions;

    /** 目标物理表选项：诊疗定义表或设备定义表。 */
    private List<OptionDto> targetTableOptions;
}
