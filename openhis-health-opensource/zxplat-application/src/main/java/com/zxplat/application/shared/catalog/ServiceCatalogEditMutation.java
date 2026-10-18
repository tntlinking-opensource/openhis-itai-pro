/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.catalog;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 诊疗目录写侧 immutable 比对入参（中性；禁止依赖 Feature Request）。
 *
 * <p>由诊疗目录 AppService 从 {@code UpdateServiceCatalogRequest} 投影后传入
 * {@link CatalogEditWriteGate}。</p>
 */
@Getter
@Setter
public class ServiceCatalogEditMutation {

    /** 诊疗名称。 */
    private String name;

    /** 业务编码。 */
    private String busNo;

    /** 目录分类。 */
    private String categoryCode;

    /**
     * 诊疗业务类型（术语 {@code activity_type_code}）。
     * 不入写侧 immutable：锁定态仍允许改，供申请单分类 chips 重分类。
     */
    private String typeEnum;

    /** 销售/计价单位。 */
    private String packageUnitCode;

    /** 财务类别。 */
    private String itemTypeCode;

    /** 医保类别。 */
    private String ybType;

    /**
     * 售价；{@code null} 表示请求未传价（锁定态视为不改价）。
     */
    private BigDecimal retailPrice;
}
