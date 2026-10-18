/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returnstockin.request;

import com.zxplat.application.inventory.warehouse.returnstockin.dto.ReturnStockInLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 修订退库入库壳单请求（IF-WHS-RSI-05，壳单只读：接口固定拒绝 WHS_RSI_006）。
 */
@Getter
@Setter
@Schema(description = "修订退库入库壳单 IF-WHS-RSI-05（已拒绝）")
public class UpdateReturnStockInRequest {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

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

    /** 供应商 supplierId。 */
    @Schema(description = "供应商 supplierId")
    private Long supplierId;

    /** 明细行 lines。 */
    @Schema(description = "明细行 lines")
    private List<ReturnStockInLineDto> lines;

    /** 行版本 rowVersion（禁止传入）。 */
    @Schema(description = "行版本 rowVersion（禁止传入）")
    private Integer rowVersion;
}
