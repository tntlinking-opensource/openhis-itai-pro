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

package com.openhis.health.application.inventory.warehouse.inboundreceipt.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptDetailLineDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 *
 * <p>头字段来自同号写模型<strong>首行</strong>（与 Mapper
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapper#toGetInboundReceiptDetailResponse}
 * 装配一致）。凡 {@code *Enum} 或类型/状态/类别语义且为 {@link String} 的字段，均为各业务枚举或字典的
 * {@link com.openhis.health.domain.share.enums.EnumInterface#getCode()} 持久化值（B.10.8.1）。</p>
 *
 * <p>{@link #lines} 为明细行列表（{@link InboundReceiptDetailLineDto}），含写模型字段及可选读模型展示扩展；
 * {@link #rowVersion} 使用 {@link JsonInclude.Include#ALWAYS}，保证 JSON 中显式出现 {@code 0}，便于前端与编辑请求对齐。</p>
 */
@Getter
@Setter
public class GetInboundReceiptDetailResponse {

    /** 业务单号（{@code bus_no}）。 */
    private String busNo;

    /** 来源订货业务单号；无引用订货时为 null。 */
    private String originalBusNo;

    /** 单据类型持久化码（{@link com.openhis.health.domain.share.enums.SupplyType#getCode()}，药库进货场景为固定进货类型）。 */
    private String typeEnum;

    /** 单据状态持久化码（{@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()}）。 */
    private String statusEnum;

    /** 供应商主键；头行口径。 */
    private Long supplierId;

    /** 源位置主键（药库）。 */
    private Long sourceLocationId;

    /** 目的位置主键（入库去向）。 */
    private Long purposeLocationId;

    /** 源位置类型持久化码（{@link com.openhis.health.domain.share.enums.SupplyRequestType#getCode()}）。 */
    private String sourceTypeEnum;

    /** 目标位置类型持久化码（{@link com.openhis.health.domain.share.enums.SupplyRequestType#getCode()}）。 */
    private String purposeTypeEnum;

    /** 供应类别持久化码（{@link com.openhis.health.domain.share.enums.SupplyCategory#getCode()}）。 */
    private String categoryEnum;

    /**
     * 乐观锁版本：与同号明细行 {@code version} 对齐（Mapper 优先首行，否则回退至任一行非空版本，仍空则 0）。
     * 须原样带入 {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.request.UpdateInboundReceiptRequest}。
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Integer rowVersion;

    /** 同号全部明细行（写模型 + 可选产品读模型展示字段）。 */
    private List<InboundReceiptDetailLineDto> lines;
}
