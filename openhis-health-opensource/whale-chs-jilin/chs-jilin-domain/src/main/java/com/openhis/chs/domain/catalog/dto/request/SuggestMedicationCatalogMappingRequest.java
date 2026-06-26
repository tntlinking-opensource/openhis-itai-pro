/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 药品维护页：按院内药品信息推荐国家医保目录编码。
 */
@Data
@Accessors(chain = true)
@Schema(description = "药品医保目录推荐请求")
public class SuggestMedicationCatalogMappingRequest {

    @Schema(description = "契约 bus_no", requiredMode = Schema.RequiredMode.REQUIRED)
    private String contractBusNo;

    @Schema(description = "目录类别 list_type，空则默认 101")
    private String listType;

    @Schema(description = "药品名称（模糊）")
    private String name;

    @Schema(description = "批准文号")
    private String approvalNumber;

    @Schema(description = "贯标国家编码")
    private String nationalDrugCode;

    @Schema(description = "规格（院内）")
    private String totalVolume;

    @Schema(description = "剂型编码（院内 dose_form_code）")
    private String doseFormCode;

    @Schema(description = "本地无候选时是否调 1312 补库后再推荐")
    private boolean queryPlatformIfMiss = true;

    @Schema(description = "返回候选条数上限，默认 20")
    private Integer maxCandidates;
}
