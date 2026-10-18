/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.activitydefinition;

import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 父项 {@link com.zxplat.domain.administration.chargeitem.entity.ChargeItem} 按诊疗子项展开后的逻辑行。
 *
 * <p>院内落<strong>父账单 + 子项账单</strong>（{@code parent_id}）；开立/划价/回显只见父项。
 * 结算/医保调用 {@link ActivityDefinitionPackageChildResolver#allocateChargeSlices} 时有子项只出子项行，
 * 金额取子项账单冻价，各行之和等于父 {@code totalPrice}。与医嘱组套（OrderTemplate）无关。</p>
 */
@Getter
@Setter
public class ActivityDefinitionPackageChargeSliceDto {

    /** 院内父收费明细主键。 */
    private Long chargeItemId;

    /** 切片序号（0 起；多子项时递增）。 */
    private int sliceIndex;

    /** 是否子项账单切片（false 表示无子项时的父项单行）。 */
    private Boolean packageChildLine;

    /** 子项诊疗 id；非子项行时为 null。 */
    private Long childrenId;

    /** 本行对应的目录读模型（子项或父项）。 */
    private ProductDefinitionDisplayDto catalogProduct;

    /** 行数量（子账单 quantity 或父项原数量）。 */
    private BigDecimal quantity;

    /** 行单价（子账单冻价或父项原单价）。 */
    private BigDecimal unitPrice;

    /** 行金额（子账单 totalPrice 或父项原金额；多子项之和等于父 charge totalPrice）。 */
    private BigDecimal lineAmount;
}
