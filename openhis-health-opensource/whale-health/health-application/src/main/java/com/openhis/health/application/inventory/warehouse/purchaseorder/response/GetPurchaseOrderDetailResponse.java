/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.purchaseorder.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderDetailLineDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 *
 * <p>头字段来自同号写模型<strong>首行</strong>（与 Mapper
 * {@link com.openhis.health.application.inventory.warehouse.purchaseorder.mapper.InventoryWarehousePurchaseOrderMapper#toGetPurchaseOrderDetailResponse}
 * 装配一致）。凡 {@code *Enum} 或类型/状态/类别语义且为 {@link String} 的字段，均为各业务枚举或字典的
 * {@link com.openhis.health.domain.share.enums.EnumInterface#getCode()} 持久化值（B.10.8.1）。</p>
 *
 * <p>{@link #lines} 为明细行列表（{@link PurchaseOrderDetailLineDto}），含写模型字段及可选读模型展示扩展；
 * {@link #rowVersion} 使用 {@link JsonInclude.Include#ALWAYS}，保证 JSON 中显式出现 {@code 0}，
 * 须原样带入 {@link com.openhis.health.application.inventory.warehouse.purchaseorder.request.UpdatePurchaseOrderRequest}。</p>
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.warehouse.purchaseorder.service.InventoryWarehousePurchaseOrderAppService#getPurchaseOrderDetail(com.openhis.health.application.inventory.warehouse.purchaseorder.request.GetPurchaseOrderDetailRequest)}。</p>
 */
@Getter
@Setter
public class GetPurchaseOrderDetailResponse {

    /** 业务单号（{@code bus_no}，聚合键）。 */
    private String busNo;

    /** 单据类型持久化码（{@link com.openhis.health.domain.share.enums.SupplyType#getCode()}，固定药库订货类型）。 */
    private String typeEnum;

    /** 单据状态持久化码（{@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()}）。 */
    private String statusEnum;

    /** 供应商主键；头行口径，可为 null。 */
    private Long supplierId;

    /** 源位置主键；订货场景通常为 null。 */
    private Long sourceLocationId;

    /** 目的药库主键（{@code purpose_location_id}，列表/门禁锚点）。 */
    private Long purposeLocationId;

    /** 源位置类型持久化码（{@link com.openhis.health.domain.share.enums.SupplyRequestType#getCode()}），订货场景多为 null。 */
    private String sourceTypeEnum;

    /** 目的位置类型持久化码：由目的药库 form 推导（{@link com.openhis.health.domain.share.enums.SupplyRequestType#getCode()}）。 */
    private String purposeTypeEnum;

    /** 供应类别持久化码（{@link com.openhis.health.domain.share.enums.SupplyCategory#getCode()}）。 */
    private String categoryEnum;

    /** 经手人医务人员主键（头行 {@code practitioner_id}，前端库管员下拉）。 */
    private Long practitionerId;

    /**
     * 乐观锁版本：与同号明细行 {@code version} 对齐（Mapper 优先首行，否则回退至任一行非空版本，仍空则 0）。
     * 须原样带入 {@link com.openhis.health.application.inventory.warehouse.purchaseorder.request.UpdatePurchaseOrderRequest}。
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Integer rowVersion;

    /** 同号全部明细行（写模型 + 可选产品读模型展示字段；AppService enrich 单位槽与 catalog 价）。 */
    private List<PurchaseOrderDetailLineDto> lines;
}
