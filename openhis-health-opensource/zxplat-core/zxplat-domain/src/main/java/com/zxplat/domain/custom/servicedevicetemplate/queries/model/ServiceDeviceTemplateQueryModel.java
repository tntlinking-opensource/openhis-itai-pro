/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.servicedevicetemplate.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 诊疗耗材绑定查询模型。
 */
@Getter
@Setter
public class ServiceDeviceTemplateQueryModel {
    /**
     * 绑定记录标识。
     */
    private Long id;
    /**
     * 状态枚举值。
     */
    private String statusEnum;
    /**
     * 绑定范围编码。
     */
    private String rangeCode;
    /**
     * 绑定类型编码。
     */
    private String typeCode;
    /**
     * 被绑定对象表名。
     */
    private String itemTable;
    /**
     * 被绑定对象主键（字符串形态）。
     */
    private String itemId;
    /**
     * 被绑定诊疗/耗材对象表名。
     */
    private String devActTable;
    /**
     * 被绑定诊疗/耗材对象主键。
     */
    private Long devActId;
    /**
     * 数量（可空）。
     */
    private BigDecimal quantity;
    /**
     * 单位编码（可空）。
     */
    private String unitCode;
}
