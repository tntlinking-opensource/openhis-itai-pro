/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.ordertemplate.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 更新组套模板行命令（仅覆盖非空字段；不建议变更行类型与主引用）。
 */
@Getter
@Setter
public class UpdateOrderTemplateLineCommand {
    /**
     * 组套模板行标识。
     */
    private Long lineId;
    /**
     * 分组键（可空）。
     */
    private String groupKey;
    /**
     * 行内排序（越小越靠前；可空）。
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
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
