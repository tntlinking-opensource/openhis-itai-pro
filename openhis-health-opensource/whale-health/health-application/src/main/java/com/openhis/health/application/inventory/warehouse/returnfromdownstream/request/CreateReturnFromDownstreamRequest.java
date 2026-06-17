/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.warehouse.returnfromdownstream.request;

import com.openhis.health.application.inventory.warehouse.returnfromdownstream.dto.ReturnFromDownstreamLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class CreateReturnFromDownstreamRequest {

    /** 关联来源业务单号。 */
    @Schema(description = "来源单号 originalBusNo")
    private String originalBusNo;
    /** 源药库位置主键。 */
    @Schema(description = "源药库 sourceLocationId")
    private Long sourceLocationId;
    /** 目的位置主键。 */
    @Schema(description = "目的位置 purposeLocationId")
    private Long purposeLocationId;
    /** 源位置类型持久化码。 */
    @Schema(description = "源位置类型 sourceTypeEnum")
    private Integer sourceTypeEnum;
    /** 目的位置类型持久化码。 */
    @Schema(description = "目的位置类型 purposeTypeEnum")
    private Integer purposeTypeEnum;
    /** 供应类别持久化码。 */
    @Schema(description = "供应类别 categoryEnum")
    private Integer categoryEnum;
    /** 供应商主键。 */
    @Schema(description = "供应商 supplierId")
    private Long supplierId;
    /** 明细行。 */
    @Schema(description = "明细行 lines")
    private List<ReturnFromDownstreamLineDto> lines;
}
