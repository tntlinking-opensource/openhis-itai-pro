/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.servicelocationtemplate.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/**
 * 取药科室模板查询模型。
 */
@Getter
@Setter
public class ServiceLocationTemplateQueryModel {
    /**
     * 规则主键。
     */
    private Long id;
    /**
     * 开立科室/机构标识。
     */
    private Long organizationId;
    /**
     * 物品来源表名（药品/耗材等）。
     */
    private String itemTable;
    /**
     * 发放类别编码。
     */
    private String distributionCategoryCode;
    /**
     * 默认发药位置标识。
     */
    private Long defLocationId;
    /**
     * 物品定义主键（通配为空）。
     */
    private Long itemDefinitionId;
    /**
     * 耗材种类编码（术语 device_type_code；通配为空）。
     */
    private String typeCode;
    /**
     * 给药途径编码（通配为空）。
     */
    private String methodCode;
    /**
     * 生效开始时刻（空表示全天起点）。
     */
    private LocalTime startTime;
    /**
     * 生效结束时刻（空表示全天终点）。
     */
    private LocalTime endTime;
    /**
     * 匹配优先级（数值越小越优先）。
     */
    private Integer priority;
    /**
     * 展示顺序（可空）。
     */
    private Integer displayOrder;
    /**
     * 备注（可空）。
     */
    private String remark;
}
