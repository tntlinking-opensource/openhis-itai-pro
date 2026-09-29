/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.chargediscountconfig.dto;

import com.openhis.whale.core.application.dto.OptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 收费折扣配置页初始化出参（IF-CDC-01；不下发 scope/mode 枚举）。
 */
@Getter
@Setter
public class ChargeDiscountConfigPageInitDto {

    /**
     * 三大目录表名下拉（value=表名，label=药品/诊疗/耗材）。
     */
    private List<OptionDto> productTableOptions;

    /**
     * 配置状态下拉（ACTIVE/INACTIVE）。
     */
    private List<OptionDto> statusOptions;
}
