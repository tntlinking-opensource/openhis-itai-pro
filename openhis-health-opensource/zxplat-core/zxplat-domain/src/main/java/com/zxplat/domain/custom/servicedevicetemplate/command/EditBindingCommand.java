/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.servicedevicetemplate.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 更新诊疗耗材绑定命令（仅覆盖非空字段）。
 */
@Getter
@Setter
public class EditBindingCommand {
    /**
     * 绑定记录标识。
     */
    private Long bindingId;
    /**
     * 绑定范围编码（可空；非空则覆盖）。
     */
    private String rangeCode;
    /**
     * 绑定类型编码（可空；非空则覆盖）。
     */
    private String typeCode;
    /**
     * 被绑定对象表名（可空；非空则覆盖）。
     */
    private String itemTable;
    /**
     * 被绑定对象主键（可空；非空则覆盖）。
     */
    private String itemId;
    /**
     * 被绑定诊疗/耗材对象表名（可空；非空则覆盖）。
     */
    private String devActTable;
    /**
     * 被绑定诊疗/耗材对象主键（可空；非空则覆盖）。
     */
    private Long devActId;
    /**
     * 数量（可空；非空则覆盖）。
     */
    private BigDecimal quantity;
    /**
     * 单位编码（可空；非空则覆盖）。
     */
    private String unitCode;
    /**
     * 状态枚举值（可空；非空则覆盖）。
     */
    private String statusEnum;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
