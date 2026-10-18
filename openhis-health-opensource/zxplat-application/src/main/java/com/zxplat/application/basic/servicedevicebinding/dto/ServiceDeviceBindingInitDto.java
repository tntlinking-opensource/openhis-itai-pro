/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.servicedevicebinding.dto;

import com.zxplat.framework.core.application.dto.OptionDto;
import lombok.Data;

import java.util.List;

/**
 * 服务设备模板绑定配置页初始化数据：状态、范围、绑定类型、单位与各来源/目标候选下拉。
 */
@Data
public class ServiceDeviceBindingInitDto {

    /** 发布状态：ACTIVE / INACTIVE。 */
    private List<OptionDto> statusOptions;

    /** 绑定范围：门诊 / 住院（与 ServiceDeviceTemplateRangeCode 一致；不含历史 HOSPITAL）。 */
    private List<OptionDto> rangeOptions;

    /** 绑定类型：USAGE / ACTIVITY。 */
    private List<OptionDto> bindTypeOptions;

    /** 计量单位选项（术语集 unit）。 */
    private List<OptionDto> unitOptions;

    /** 用法来源（术语 method）候选。 */
    private List<OptionDto> usageSourceOptions;

    /** 诊疗活动定义候选（用于绑定类型=诊疗时选择来源项）。 */
    private List<OptionDto> activitySourceOptions;

    /** 耗材/设备定义候选（目标为设备时辅助选择，与页面流程配合）。 */
    private List<OptionDto> deviceDefOptions;

    /** 目标物理表选项：诊疗定义表或设备定义表。 */
    private List<OptionDto> targetTableOptions;
}
