/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.pharmacy.returnupstream.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductDefinitionItemDto {

    /** 产品定义主键。 */
    private Long productDefinitionId;
    /** 产品定义来源表名。 */
    private String productTableName;
    /** 产品展示名称。 */
    private String productName;
    /** merchandiseName 字段。 */
    private String merchandiseName;
    /** 计量单位编码。 */
    private String unitCode;
    /** minUnitCode 字段。 */
    private String minUnitCode;
    /** 品类编码。 */
    private String categoryCode;
    /** 单价。 */
    private BigDecimal price;
    /** purchasePrice 字段。 */
    private BigDecimal purchasePrice;
    /** 生产厂家。 */
    private String manufacturer;
    /** pyStr 字段。 */
    private String pyStr;
    /** wbStr 字段。 */
    private String wbStr;
}
