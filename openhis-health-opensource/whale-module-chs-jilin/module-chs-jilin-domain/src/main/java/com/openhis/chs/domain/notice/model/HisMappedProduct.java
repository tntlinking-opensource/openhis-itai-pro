/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 院内已启用、需走医保的目录项（诊疗 / 药品 / 耗材）。
 */
@Data
public class HisMappedProduct {

    /** 产品表名。 */
    private String productTable;
    /** 产品定义主键。 */
    private Long productId;
    /** 院内业务编码。 */
    private String busNo;
    /** 项目名称。 */
    private String name;
    /** 国家医保编码；可空。 */
    private String ybNo;
    /** 院内甲乙类；可空。 */
    private Integer chrgitmLv;
    /** 对应国家目录 list_type。 */
    private String listType;
    /** ACTIVITY / MEDICATION / DEVICE。 */
    private String productKind;
    /** 当前 SALE 售价；可空。 */
    private BigDecimal salePrice;
}
