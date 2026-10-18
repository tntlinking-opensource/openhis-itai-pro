/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returntosupplier.response;

import com.zxplat.application.inventory.warehouse.returntosupplier.dto.ReturnToSupplierDetailLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 药库退供应商单详情响应（IF-WHS-RT-03：头 + 明细行，含产品名称富化）。
 */
@Getter
@Setter
@Schema(description = "退供应商单详情 IF-WHS-RT-03")
public class GetReturnToSupplierDetailResponse {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

    /** 来源单号 originalBusNo。 */
    @Schema(description = "来源单号 originalBusNo")
    private String originalBusNo;

    /** 供应类型 typeEnum。 */
    @Schema(description = "供应类型 typeEnum")
    private Integer typeEnum;

    /** 单据状态 statusEnum。 */
    @Schema(description = "单据状态 statusEnum")
    private Integer statusEnum;

    /** 供应商 supplierId。 */
    @Schema(description = "供应商 supplierId")
    private Long supplierId;

    /** 源药库 sourceLocationId。 */
    @Schema(description = "源药库 sourceLocationId")
    private Long sourceLocationId;

    /** 目的位置 purposeLocationId。 */
    @Schema(description = "目的位置 purposeLocationId")
    private Long purposeLocationId;

    /** 源位置类型 sourceTypeEnum。 */
    @Schema(description = "源位置类型 sourceTypeEnum")
    private Integer sourceTypeEnum;

    /** 目的位置类型 purposeTypeEnum。 */
    @Schema(description = "目的位置类型 purposeTypeEnum")
    private Integer purposeTypeEnum;

    /** 供应类别 categoryEnum。 */
    @Schema(description = "供应类别 categoryEnum")
    private Integer categoryEnum;

    /**
     * 采购发票号（同单共享；进销存 {@code purc_invo_no}）。
     */
    @Schema(description = "采购发票号 invoiceNo")
    private String invoiceNo;

    /**
     * 整单退货金额合计：明细行 {@code totalPrice} 之和。
     */
    @Schema(description = "整单退货金额合计 totalAmount")
    private BigDecimal totalAmount;

    /** 明细行 lines。 */
    @Schema(description = "明细行 lines")
    private List<ReturnToSupplierDetailLineDto> lines;
}
