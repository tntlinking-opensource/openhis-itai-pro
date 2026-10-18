/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdefinition.queries.model;

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
    /** 单次最小剂量。 */
    private BigDecimal minUnit;
    /** maxunit。 */
    private BigDecimal maxUnit;
    /** 默认库房点位。 */
    private Long locationId;
    /** 供应商主键。 */
    private Long supplyId;
    /** 生产厂家主键（cus_supplier.id，type=生产厂家）。 */
    private Long manufacturerId;
    /**
     * 生产厂家展示名（非持久化列；由 AppService 按 {@link #manufacturerId} 解析供应商名称回填）。
     */
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
    /**
     * 追溯码所贴单位编码（可游离于五类业务单位之外；药品目录必填）。
     */
    private String traceUnitCode;
    /**
     * 1 个追溯码单位对应的拆零数量；有值时须 &gt; 0。
     */
    private BigDecimal traceUnitRatio;
}
