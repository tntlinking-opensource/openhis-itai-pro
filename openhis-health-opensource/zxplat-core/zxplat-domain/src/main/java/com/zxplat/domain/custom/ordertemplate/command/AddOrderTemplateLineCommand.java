/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.ordertemplate.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 新增组套模板行命令。
 */
@Getter
@Setter
public class AddOrderTemplateLineCommand {
    /**
     * 组套模板标识。
     */
    private Long orderTemplateId;
    /**
     * 行类型枚举值（药品/诊疗/耗材）。
     */
    private String lineKindEnum;
    /**
     * 药品定义标识（lineKindEnum=药品时必填）。
     */
    private Long medicationDefinitionId;
    /**
     * 诊疗项目定义标识（lineKindEnum=诊疗时必填）。
     */
    private Long activityDefinitionId;
    /**
     * 耗材定义标识（lineKindEnum=耗材时必填）。
     */
    private Long deviceDefinitionId;
    /**
     * 分组键（同组联动展示/开立；可空）。
     */
    private String groupKey;
    /**
     * 行内排序（越小越靠前；可空时由系统按追加顺序给定）。
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
     * 剂量文本（可空，用于自由描述）。
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
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
