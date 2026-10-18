/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.catalog;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 药品目录写侧 immutable 比对入参（中性；禁止依赖 Feature Request）。
 *
 * <p>由各目录 AppService 从 {@code UpdateMedicationCatalogRequest} 投影后传入
 * {@link CatalogEditWriteGate}。</p>
 */
@Getter
@Setter
public class MedicationCatalogEditMutation {

    /** 通用名称。 */
    private String name;

    /** 商品名称。 */
    private String merchandiseName;

    /** 业务编码。 */
    private String busNo;

    /** 药品分类。 */
    private String categoryCode;

    /** 财务类别。 */
    private String typeCode;

    /** 医保类别。 */
    private String ybType;

    /** 采购单位。 */
    private String purchaseUnitCode;

    /** 拆零单位。 */
    private String splitUnitCode;

    /** 销售单位。 */
    private String packageUnitCode;

    /** 库存单位。 */
    private String stockUnitCode;

    /** 采购→拆零倍数。 */
    private BigDecimal purchaseUnitRatio;

    /** 库存→拆零倍数。 */
    private BigDecimal stockUnitRatio;

    /** 销售→拆零倍数。 */
    private BigDecimal packageUnitRatio;

    /** 药品性质。 */
    private String pharmacologyCategoryCode;

    /** 剂型。 */
    private Integer doseFrom;

    /** 剂量单位。 */
    private String doseUnitCode;
}
