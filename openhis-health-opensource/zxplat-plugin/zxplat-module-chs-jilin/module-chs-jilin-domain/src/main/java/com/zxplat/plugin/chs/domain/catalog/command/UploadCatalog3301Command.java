/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.command;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 【2301】目录对照上传请求（多行 data 数组） */
@Data
@Accessors(chain = true)
@Schema(description = "3301 目录对照上传")
public class UploadCatalog3301Command {

    @Schema(description = "契约 bus_no", requiredMode = Schema.RequiredMode.REQUIRED)
    private String contractBusNo;

    @Schema(description = "上传行")
    private List<UploadCatalog3301RowCommand> rows;
}
