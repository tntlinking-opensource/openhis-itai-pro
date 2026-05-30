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

import com.openhis.health.application.common.dto.product.ProductDefinitionCatalogEditorHintsDto;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 *
 * <p>写模型字段来自 {@code wkf_supply_request} 行，含效期区间 **{@code startTime}** / **{@code endTime}**（库列
 * {@code start_time}/{@code end_time}）；{@code productName}/{@code specification}/{@code manufacturer}、
 * {@code unitCodeDictText}、限制用药与范围相关读模型列等仅在应用层批量命中产品定义读模型时由 Mapper 填充，未命中时为
 * null（B.2 分层：不在 Mapper 内注入查询服务）。{@link #productCatalog} 与选品列表在<strong>单位槽与参考价</strong>维度对齐，
 */
@Getter
@Setter
public class InboundReceiptDetailLineDto {

    /** 供应请求行技术主键。 */
    private Long id;

    /** 物品来源表名。 */
    private String itemTable;

    /** 物品定义主键。 */
    private Long itemId;

    /** 数量。 */
    private BigDecimal itemQuantity;

    /** 计量单位编码（字典持久化码，B.10.8.1）。 */
    private String unitCode;

    /**
     * 计量单位展示名：与 {@link #unitCode} 命中产品读模型采购/拆零/库存等单位槽时，取自读模型对应
     * {@code *_unit_code_dict_text}；未命中或无法对齐时为 {@code null}。
     */
    private String unitCodeDictText;

    /** 批号。 */
    private String lotNumber;

    /** 追溯码（可能为逗号分隔多段）。 */
    private String traceNo;

    /** 单价。 */
    private BigDecimal price;

    /** 行总价。 */
    private BigDecimal totalPrice;

    /** 期望或业务发生时间。 */
    private LocalDateTime occurrenceTime;

    /** 效期区间起点（与库 {@code start_time} 一致）；可空。 */
    private LocalDateTime startTime;

    /** 效期区间止点（与库 {@code end_time} 一致）；可空。 */
    private LocalDateTime endTime;

    /** 行备注。 */
    private String remake;

    /**
     * 物品名称：来自产品定义读模型，仅展示用。
     */
    private String productName;

    /**
     * 规格展示：读模型 {@code total_volume} 映射，非独立「规格码」列。
     */
    private String specification;

    /**
     * 生产厂家及产地合一展示：读模型 {@code manufacturer}。
     */
    private String manufacturer;

    private ProductDefinitionCatalogEditorHintsDto productCatalog;
}
