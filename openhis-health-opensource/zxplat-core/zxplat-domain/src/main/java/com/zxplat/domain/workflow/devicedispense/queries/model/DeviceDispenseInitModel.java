/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicedispense.queries.model;

import com.zxplat.framework.core.application.dto.OptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 耗材发放工作台初始化读模型。
 */
@Getter
@Setter
/**
 * DeviceDispenseInitModel。
 */
public class DeviceDispenseInitModel {

    /** 发放状态筛选项（下拉）。 */
    private List<OptionDto> statusOptions = new ArrayList<>();
}
