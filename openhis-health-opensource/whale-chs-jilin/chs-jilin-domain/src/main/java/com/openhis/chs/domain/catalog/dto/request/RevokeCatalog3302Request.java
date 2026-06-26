/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 【3302】目录对照撤销请求（平台单次仅支持单条，多行时分多次 invoke）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "3302 目录对照撤销")
public class RevokeCatalog3302Request {

    @Schema(description = "契约 bus_no", requiredMode = Schema.RequiredMode.REQUIRED)
    private String contractBusNo;

    @Schema(description = "待撤销行")
    private List<RevokeCatalog3302RowRequest> rows;
}
