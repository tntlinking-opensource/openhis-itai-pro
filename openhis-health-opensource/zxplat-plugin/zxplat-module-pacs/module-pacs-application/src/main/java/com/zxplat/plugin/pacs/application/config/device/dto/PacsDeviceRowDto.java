/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.config.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 设备台账列表行。
 */
@Getter
@Setter
@Schema(description = "设备台账列表行")
public class PacsDeviceRowDto {

    /** 主键。 */
    @Schema(description = "主键")
    private Long id;

    /** 设备编码。 */
    @Schema(description = "设备编码")
    private String deviceCode;

    /** 设备名称。 */
    @Schema(description = "设备名称")
    private String name;

    /** 模态码。 */
    @Schema(description = "模态码")
    private String modalityCode;

    /** DICOM AE Title。 */
    @Schema(description = "DICOM AE Title")
    private String aeTitle;

    /** 机房/室名。 */
    @Schema(description = "机房/室名")
    private String stationName;

    /** 是否启用。 */
    @Schema(description = "是否启用")
    private Boolean enabledFlag;

    /** 列表排序。 */
    @Schema(description = "列表排序")
    private Integer sortOrder;
}
