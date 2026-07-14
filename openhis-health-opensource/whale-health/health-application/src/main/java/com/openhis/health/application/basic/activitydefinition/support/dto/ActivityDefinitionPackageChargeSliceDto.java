/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.activitydefinition.support.dto;

import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 父项 {@link com.openhis.health.domain.administration.chargeitem.entity.ChargeItem} 按套餐子项拆分后的逻辑行。
 *
 * <p>院内全局仅落父 charge 一条；需要按子项表达数量/金额/目录时调用
 * {@link com.openhis.health.application.basic.activitydefinition.support.ActivityDefinitionPackageChildResolver#allocateChargeSlices}。
 * 消费方自行映射医保编码、feedetl_sn 等对外字段。</p>
 */
@Getter
@Setter
public class ActivityDefinitionPackageChargeSliceDto {

    /** 院内父收费明细主键。 */
    private Long chargeItemId;

    /** 切片序号（0 起；套餐多行时递增）。 */
    private int sliceIndex;

    /** 是否套餐子项行（false 表示非套餐或子表为空时的父项单行）。 */
    private Boolean packageChildLine;

    /** 子项诊疗 id；非子项行时为 null。 */
    private Long childrenId;

    /** 本行对应的目录读模型（子项或父项）。 */
    private ProductDefinitionDisplayDto catalogProduct;

    /** 行数量（父数量 × children_num 或父项原数量）。 */
    private BigDecimal quantity;

    /** 行单价（分摊后）。 */
    private BigDecimal unitPrice;

    /** 行金额（分摊后；多行之和等于父 charge totalPrice）。 */
    private BigDecimal lineAmount;
}
