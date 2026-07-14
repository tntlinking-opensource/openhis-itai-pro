/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returntosupplier.response;
import com.openhis.health.application.inventory.warehouse.returntosupplier.dto.ReturnableInboundLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

/**
 * 从已同意进货单拉取的退货新建/编辑预填数据（IF-WHS-RT-13）。
 *
 * <p>{@code originalBusNo} 与 {@code goodsReceiptBusNo} 均指向来源进货单号。</p>
 */
@Getter
@Setter
@Schema(description = "进货单退货预填 IF-WHS-RT-13")
public class GetLinkableGoodsReceiptDetailResponse {

    /** 来源单号 originalBusNo。 */
    @Schema(description = "来源单号 originalBusNo")
    private String originalBusNo;

    /** 进货单号 goodsReceiptBusNo。 */
    @Schema(description = "进货单号 goodsReceiptBusNo")
    private String goodsReceiptBusNo;

    /** 源药库 sourceLocationId。 */
    @Schema(description = "源药库 sourceLocationId")
    private Long sourceLocationId;

    /** 目的位置 purposeLocationId。 */
    @Schema(description = "目的位置 purposeLocationId")
    private Long purposeLocationId;

    /** 供应商 supplierId。 */
    @Schema(description = "供应商 supplierId")
    private Long supplierId;

    /** 供应商名称 supplierName。 */
    @Schema(description = "供应商名称 supplierName")
    private String supplierName;

    /** 供应类别 categoryEnum。 */
    @Schema(description = "供应类别 categoryEnum")
    private String categoryEnum;

    /** 经手人 practitionerId。 */
    @Schema(description = "经手人 practitionerId")
    private Long practitionerId;

    /** 可退明细 lines。 */
    @Schema(description = "可退明细 lines")
    private List<ReturnableInboundLineDto> lines;
}
