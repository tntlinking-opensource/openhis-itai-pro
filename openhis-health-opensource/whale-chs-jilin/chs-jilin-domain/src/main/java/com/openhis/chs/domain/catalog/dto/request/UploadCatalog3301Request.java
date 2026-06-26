/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 【3301】目录对照上传请求（多行 data 数组）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "3301 目录对照上传")
public class UploadCatalog3301Request {

    @Schema(description = "契约 bus_no", requiredMode = Schema.RequiredMode.REQUIRED)
    private String contractBusNo;

    @Schema(description = "上传行")
    private List<UploadCatalog3301RowRequest> rows;
}
