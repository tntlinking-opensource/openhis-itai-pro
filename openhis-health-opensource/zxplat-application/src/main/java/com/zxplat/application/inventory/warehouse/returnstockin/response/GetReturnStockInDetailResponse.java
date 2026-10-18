/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returnstockin.response;

import com.zxplat.application.inventory.warehouse.returnstockin.dto.ReturnStockInDetailLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 退库入库壳单详情响应（IF-WHS-RSI-03：只读头 + 明细行，含产品名称富化）。
 *
 * <p>{@code originalBusNo} 指向下游退库单号；壳单 type 5 生成即为已同意态。</p>
 */
@Getter
@Setter
@Schema(description = "退库入库壳单详情 IF-WHS-RSI-03")
public class GetReturnStockInDetailResponse {

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

    /** 源位置 sourceLocationId。 */
    @Schema(description = "源位置 sourceLocationId")
    private Long sourceLocationId;

    /** 目的药库 purposeLocationId。 */
    @Schema(description = "目的药库 purposeLocationId")
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

    /** 明细行 lines。 */
    @Schema(description = "明细行 lines")
    private List<ReturnStockInDetailLineDto> lines;
}
