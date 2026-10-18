/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.ordertemplate.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 更新组套模板行命令。
 *
 * <p>剂量、频次等可空字段仍按非空覆盖；行种类与三类目录主键按维护端全量对齐整行替换，
 * 以便已保存组套换药后写回新药品。</p>
 */
@Getter
@Setter
public class UpdateOrderTemplateLineCommand {
    /**
     * 组套模板行标识。
     */
    private Long lineId;
    /**
     * 行类型（药品/诊疗/耗材）。有值时与三类目录主键一并整行替换。
     */
    private String lineKindEnum;
    /**
     * 药品定义标识。换药时写入新主键；非药品行置空以清掉原引用。
     */
    private Long medicationDefinitionId;
    /**
     * 诊疗项目定义标识。改诊疗时写入；药品/耗材行置空。
     */
    private Long activityDefinitionId;
    /**
     * 耗材定义标识。改耗材时写入；药品/诊疗行置空。
     */
    private Long deviceDefinitionId;
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
