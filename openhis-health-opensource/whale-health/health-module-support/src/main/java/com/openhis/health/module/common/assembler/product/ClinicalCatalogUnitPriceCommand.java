/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.module.common.assembler.product;

import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 临床开嘱/划价目录单价解析入参（组套 IF-08 预览不查价；套用期定价走 prepicked 目录 IF）。
 *
 * <p>读侧（IF-DOR-11 / IF-IP-PRC-01）：药耗按取药科室模板或 {@code pharmacyLocationId} 解析库位后 FIFO 批号售价；
 * 写侧（IF-DOR-14 / IF-IP-PRC-02/04）：{@code clientResolvedDispenseLocationId} 非空时直接在该库位 FIFO，
 * <strong>不再</strong> match 模板。诊疗 {@code SALE} 择优。</p>
 */
@Getter
@Builder
public class ClinicalCatalogUnitPriceCommand {

    /** 产品定义物理表名。 */
    private final String productTableName;

    /** 产品定义主键。 */
    private final Long productDefinitionId;

    /** 药品/耗材目录 categoryCode（模板 distribution_category_code 对齐维度；可空则回落默认分类）。 */
    private final String categoryCode;

    /** 给药途径等用法编码（药品模板 calcScore 维度；可空）。 */
    private final String methodCode;

    /** 机构/科室所属组织（取药科室模板解析）。 */
    private final Long organizationId;

    /** 可选：读侧用户指定药房/库房（IF-DOR-11 / IF-IP-PRC-01 收窄候选集；非写路径 clientResolved）。 */
    private final Long pharmacyLocationId;

    /**
     * 写路径透传：IF-DOR-14 {@code dispenseLocationId} / IF-IP-PRC-02/04 {@code performLocation}。
     * 非空时 FIFO 直接在该库位取批号，<strong>不再</strong>调用取药科室模板二次解析。
     */
    private final Long clientResolvedDispenseLocationId;

    /** 可选：就诊 id；非空时对药耗扣减同就诊发放占用后再取 FIFO 首条库存批号。 */
    private final Long encounterId;

    /** 已批量加载的定价行。 */
    private final List<ChargeItemDefinition> priceLines;

    /** 可选：占用换算用产品读模型（与 {@link #encounterId} 配套）。 */
    private final ProductDefinitionDisplayDto productRowForOccupancy;

    /** 可选：调用方已算出的 FIFO 首条库存批号，非空则跳过库存再查。 */
    private final String leadingInventoryLotNumber;
}
