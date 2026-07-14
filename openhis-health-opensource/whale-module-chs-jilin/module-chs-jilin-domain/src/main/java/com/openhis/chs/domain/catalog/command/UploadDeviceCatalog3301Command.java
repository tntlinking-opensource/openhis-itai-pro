/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.command;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 单条耗材 3301 上传（bridge 编排：加载院内耗材 →3301 →回写 yb_match_flag） */
@Data
@Accessors(chain = true)
@Schema(description = "耗材 3301 上传")
public class UploadDeviceCatalog3301Command {

    @Schema(description = "契约 bus_no", requiredMode = Schema.RequiredMode.REQUIRED)
    private String contractBusNo;

    @Schema(description = "耗材定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long deviceDefId;

    @Schema(description = "list_type，空301")
    private String listType;
}
