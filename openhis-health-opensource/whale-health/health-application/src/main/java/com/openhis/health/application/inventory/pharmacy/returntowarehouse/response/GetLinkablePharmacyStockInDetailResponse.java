/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.returntowarehouse.response;

import com.openhis.health.application.inventory.pharmacy.returntowarehouse.dto.ReturnablePharmacyStockInLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 从已同意药房入库单拉取的退库新建/编辑预填数据。
 *
 * <p>{@code originalBusNo} 与 {@code stockInBusNo} 均指向来源入库单号。</p>
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

    /** 源药房 sourceLocationId。 */
    @Schema(description = "源药房 sourceLocationId")
    private Long sourceLocationId;

    /** 目的药库 purposeLocationId。 */
    @Schema(description = "目的药库 purposeLocationId")
    private Long purposeLocationId;

    /** 供应商 supplierId。 */
    @Schema(description = "供应商 supplierId")
    private Long supplierId;

    /** 供应类别 categoryEnum。 */
    @Schema(description = "供应类别 categoryEnum")
    private String categoryEnum;

    /** 经手人 practitionerId。 */
    @Schema(description = "经手人 practitionerId")
    private Long practitionerId;

    /** 可退明细 lines。 */
    @Schema(description = "可退明细 lines")
    private List<ReturnablePharmacyStockInLineDto> lines;
}
