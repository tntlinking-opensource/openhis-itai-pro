/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.catalog;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 耗材目录写侧 immutable 比对入参（中性；禁止依赖 Feature Request）。
 *
 * <p>由耗材目录 AppService 从 {@code UpdateDeviceCatalogRequest} 投影后传入
 * {@link CatalogEditWriteGate}。</p>
 */
@Getter
@Setter
public class DeviceCatalogEditMutation {

    /** 耗材名称。 */
    private String name;

    /** 商品名称。 */
    private String merchandiseName;

    /** 业务编码。 */
    private String busNo;

    /** 耗材分类。 */
    private String categoryCode;

    /** 耗材类型。 */
    private String typeCode;

    /** 财务类别。 */
    private String itemTypeCode;

    /** 医保类别。 */
    private String ybType;

    /** 采购单位。 */
    private String purchaseUnitCode;

    /** 拆零单位。 */
    private String splitUnitCode;

    /** 包装单位。 */
    private String packageUnitCode;

    /** 库存单位。 */
    private String stockUnitCode;

    /** 剂量单位。 */
    private String doseUnitCode;

    /** 剂量→拆零倍数。 */
    private BigDecimal doseUnitRatio;

    /** 采购→拆零倍数。 */
    private BigDecimal purchaseUnitRatio;

    /** 库存→拆零倍数。 */
    private BigDecimal stockUnitRatio;

    /** 包装→拆零倍数。 */
    private BigDecimal packageUnitRatio;
}
