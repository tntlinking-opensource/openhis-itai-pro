/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.config.device.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存设备台账请求。
 */
@Getter
@Setter
@Schema(description = "设备台账-保存请求")
public class SaveDeviceRequest {

    /** 主键；空表示新建。 */
    @Schema(description = "主键；空=新建")
    private Long id;

    /** 设备编码。 */
    @NotBlank
    @Schema(description = "设备编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceCode;

    /** 设备名称。 */
    @NotBlank
    @Schema(description = "设备名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    /** 模态码。 */
    @NotBlank
    @Schema(description = "模态码", requiredMode = Schema.RequiredMode.REQUIRED)
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

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;
}
