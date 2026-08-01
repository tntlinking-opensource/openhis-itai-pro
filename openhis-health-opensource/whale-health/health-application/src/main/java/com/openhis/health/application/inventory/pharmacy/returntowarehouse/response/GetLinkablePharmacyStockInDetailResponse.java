/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.returntowarehouse.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inventory.pharmacy.returntowarehouse.dto.ReturnablePharmacyStockInLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 从已同意药房入库单拉取的退库新建/编辑预填数据（IF-PHA-RTW-13）。
 *
 * <p>{@code originalBusNo} 与 {@code stockInBusNo} 均指向来源入库单号。
 * 库位语义：{@code sourceLocationId}=入库目的药房；{@code purposeLocationId}=入库来源药库（退入药库）。</p>
 */
@Getter
@Setter
public class GetLinkablePharmacyStockInDetailResponse {

    /** 来源单号 originalBusNo。 */
    @Schema(description = "来源单号 originalBusNo")
    private String originalBusNo;

    /** 入库单号 stockInBusNo。 */
    @Schema(description = "入库单号 stockInBusNo")
    private String stockInBusNo;

    /**
     * 退库源药房：取入库单头行 {@code purposeLocationId}。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "源药房 sourceLocationId")
    private Long sourceLocationId;

    /**
     * 退入药库：取入库单头行 {@code sourceLocationId}（药库）；为空时前端留空由用户选择。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "目的药库 purposeLocationId")
    private Long purposeLocationId;

    /** 供应商 supplierId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "供应商 supplierId")
    private Long supplierId;

    /** 供应类别 categoryEnum。 */
    @Schema(description = "供应类别 categoryEnum")
    private String categoryEnum;

    /** 经手人 practitionerId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "经手人 practitionerId")
    private Long practitionerId;

    /** 可退明细 lines。 */
    @Schema(description = "可退明细 lines")
    private List<ReturnablePharmacyStockInLineDto> lines;
}
