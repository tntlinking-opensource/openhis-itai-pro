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

package com.openhis.health.application.inventory.warehouse.inboundreceipt.request;

import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptLineDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 *
 * <p>凡表示位置类型、供应类别、单据类型等枚举语义者，须传各业务枚举的
 * {@link com.openhis.health.domain.share.enums.EnumInterface#getCode()} 字符串（B.10.8.1），禁止用排序号、整型或数字串冒充。</p>
 *
 * <p>{@link #lines} 须非空且至少一行；源/目的位置须通过应用层药库白名单门禁。
 * {@link #originalBusNo} 非空时须引用已同意的药库订货单（由 {@code validateOriginalPurchaseOrderForInboundReceipt} 校验）。</p>
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.service.InventoryWarehouseInboundReceiptAppService#createInboundReceipt(CreateInboundReceiptRequest)}。</p>
 */
@Getter
@Setter
public class CreateInboundReceiptRequest {

    /**
     * 来源订货业务单号；空或仅空白表示不引用订货、直建进货单。
     */
    private String originalBusNo;

    /**
     * 药库（源位置）主键：与 {@code wkf_supply_request.source_location_id} 一致，须属当前用户可见药库。
     */
    private Long sourceLocationId;

    /**
     * 入库目的位置主键：与 {@code purpose_location_id} 一致，通常为本机构目标药库库位。
     */
    private Long purposeLocationId;

    /**
     * 源位置类型持久化码（{@link com.openhis.health.domain.share.enums.SupplyRequestType#getCode()}）；
     * 可空或空白，装配层可不落库该字段并由领域按默认补齐。
     */
    private String sourceTypeEnum;

    /**
     * 目标位置类型持久化码（{@link com.openhis.health.domain.share.enums.SupplyRequestType#getCode()}），必填非空白。
     */
    private String purposeTypeEnum;

    /**
     * 供应类别持久化码（{@link com.openhis.health.domain.share.enums.SupplyCategory#getCode()}）；
     * 可空或空白，空时按库存供应类别默认落库（见 Mapper {@code resolveSupplyCategoryCode}）。
     */
    private String categoryEnum;

    /**
     * 供应商主键；可空。非空时须为启用态供应商（应用层 {@code validateSupplierOptional}）。
     */
    private Long supplierId;

    /**
     * 经手人（医务人员主键，{@code wkf_supply_request.practitioner_id}）；须由前端显式选择，不经会话代填。
     */
    private Long practitionerId;

    /**
     * 进货明细行全量集合（至少一行）；每行映射为一条 {@code CreateInboundReceiptItemCommand}。
     */
    private List<InboundReceiptLineDto> lines;
}
