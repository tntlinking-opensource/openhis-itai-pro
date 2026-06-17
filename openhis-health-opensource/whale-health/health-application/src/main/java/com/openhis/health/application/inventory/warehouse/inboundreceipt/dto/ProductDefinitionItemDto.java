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

package com.openhis.health.application.inventory.warehouse.inboundreceipt.dto;

import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.domain.share.enums.PriceType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 *
 * <p>与前端选品表、明细行 {@code itemTable}/{@code itemId} 对齐：{@code productTableName} 与 {@code productDefinitionId}
 * 组成物品复合键；{@code unitCode} 等编码类字段均为字典或计量体系下的<strong>持久化字符串</strong>（B.10.8.1：
 * 与枚举/字典 {@code getCode()} 同义，禁止用排序号、整型或数字串冒充后再映射）。</p>
 *
 */
@Getter
@Setter
public class ProductDefinitionItemDto {

    /**
     * 产品定义主键：与读模型 {@code product_definition_id} 一致，写入进货明细时映射为 {@code itemId}。
     */
    private Long productDefinitionId;

    /**
     * 物品来源物理表名：与供应/库存域 {@code item_table} 一致（白名单通常为药品定义表或耗材定义表）。
     */
    private String productTableName;

    /**
     * 产品业务流水号：读模型 {@code product_bus_no}，用于列表展示与对账检索，非技术主键。
     */
    private String productBusNo;

    /** 通用名/注册名等产品主展示名称。 */
    private String productName;

    /** 商品名或商用名；无单独商用名时可能与 {@link #productName} 同源或为空。 */
    private String merchandiseName;

    /**
     * 采购/业务默认计量单位编码：读模型采购单位列映射，与进货明细 {@code unitCode} 候选一致（字典持久化码）。
     */
    private String unitCode;

    /**
     * 最小包装（拆零）单位编码：与库存账面拆零单位及 {@link #unitBands} 中拆零槽 {@code bandKind=SPLIT} 一致（字典持久化码）。
     */
    private String minUnitCode;

    /** 物资分类编码：字典持久化码，用于选品侧分类筛选与展示。 */
    private String categoryCode;

    /**
     * 物资分类展示名（读模型 {@code category_code_dict_text}）：选品下拉「项目种类」等与编码并列的展示字段。
     */
    private String categoryCodeDictText;

    /**
     * 基础标价：读模型直接给出的参考售价口径（与选品列表 {@link #purchasePrice} 采购进价列来源不同）。
     */
    private BigDecimal price;

    /** 采购进价。 */
    private BigDecimal purchasePrice;

    /** 可用库存量。 */
    private BigDecimal availableQuantity;

    /**
     * 进货选品单位槽：采购、包装（{@code bandKind=PACKAGE}，读模型 {@code package_unit_code}）、拆零、库存；
     * 定价表 {@code PURCHASE}（每包装进价）与批号 {@code LOT_NUMBER_COST} 归一为包装锚后写各槽 {@code unitPrice}。
     */
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;

    /**
     * 生产厂家及产地合一展示：与产品定义读模型 {@code manufacturer} 列对齐；未拆分独立产地列时厂商与产地语义均落此字段。
     */
    private String manufacturer;

    /**
     * 包装规格或总量描述：读模型 {@code total_volume}，常用于列表「规格」列展示。
     */
    private String totalVolume;

    /** 拼音检索码：与读模型检索列一致，供前端本地或服务端关键字辅助。 */
    private String pyStr;

    /** 五笔检索码：与读模型检索列一致。 */
    private String wbStr;
}
