/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.ordertemplate.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 组套模板行查询模型。
 */
@Getter
@Setter
public class OrderTemplateLineQueryModel {
    /**
     * 组套模板行标识。
     */
    private Long id;
    /**
     * 组套模板标识。
     */
    private Long orderTemplateId;
    /**
     * 行类型枚举值（药品/诊疗/耗材）。
     */
    private String lineKindEnum;
    /**
     * 药品定义标识（lineKindEnum=药品时有值）。
     */
    private Long medicationDefinitionId;
    /**
     * 诊疗项目定义标识（lineKindEnum=诊疗时有值）。
     */
    private Long activityDefinitionId;
    /**
     * 耗材定义标识（lineKindEnum=耗材时有值）。
     */
    private Long deviceDefinitionId;
    /**
     * 分组键（可空）。
     */
    private String groupKey;
    /**
     * 行内排序（越小越靠前）。
     */
    private Integer sortOrder;
    /**
     * 默认数量（可空）。
     */
    private BigDecimal defaultQuantity;
    /**
     * 默认数量单位编码（可空）。
     */
    private String defaultUnitCode;
    /**
     * 默认天数（可空）。
     */
    private Integer defaultDays;
    /**
     * 频次标识（可空）。
     */
    private Long frequencyId;
    /**
     * 用法/途径编码（可空）。
     */
    private String routeCode;
    /**
     * 剂量文本（可空）。
     */
    private String dosageText;
    /**
     * 默认执行科室标识（可空）。
     */
    private Long defaultPerformOrgId;
    /**
     * 行备注（可空）。
     */
    private String lineRemark;
    /**
     * TCM 行级中药付数。
     */
    private BigDecimal chineseHerbsDoseQuantity;
    /**
     * TCM 行级代煎标志。
     */
    private Boolean sufferingFlag;
}
