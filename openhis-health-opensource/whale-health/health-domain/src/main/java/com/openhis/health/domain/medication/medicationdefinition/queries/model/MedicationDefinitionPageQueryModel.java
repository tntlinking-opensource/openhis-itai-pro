/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 药品目录分页查询模型。
 */
@Getter
@Setter
public class MedicationDefinitionPageQueryModel {
    /** 主键。 */
    private Long id;
    /** 业务号。 */
    private String busNo;
    /** 名称。 */
    private String name;
    /** 类别编码。 */
    private String categoryCode;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** 启用标志。 */
    private Boolean activeFlag;
    /** 医保匹配标志。 */
    private Boolean ybMatchFlag;
    /** 医保号。 */
    private String ybNo;
    /** totalvolume。 */
    private String totalVolume;
    /** maxunit。 */
    private BigDecimal maxUnit;
    /** 默认库房点位。 */
    private Long locationId;
    /** 供应商主键。 */
    private Long supplyId;
    /** 生产厂家文本。 */
    private String manufacturerText;
    /** 处方药标志。 */
    private Boolean rxFlag;
    /** 采购单位编码。 */
    private String purchaseUnitCode;
    /** 拆零单位编码。 */
    private String splitUnitCode;
    /** 包装单位编码。 */
    private String packageUnitCode;
    /** 库存单位编码。 */
    private String stockUnitCode;
    /** 剂量单位编码。 */
    private String doseUnitCode;
    /** 采购→拆零倍数。 */
    private BigDecimal purchaseUnitRatio;
    /** 库存→拆零倍数。 */
    private BigDecimal stockUnitRatio;
    /** 销售→拆零倍数。 */
    private BigDecimal packageUnitRatio;
    /** 剂量→拆零倍数。 */
    private BigDecimal doseUnitRatio;
}
