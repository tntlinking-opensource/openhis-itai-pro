/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.returnmedicine.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 退药失败项（IF-PHA-02-04 预留；当前全成功路径返回空列表）。
 */
@Data
/** 退药失败项。 */
@Schema(description = "退药失败项")
public class FailedReturnItemDto {

    @Schema(description = "发放单标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    @Schema(description = "错误码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String errorCode;

    /** 错误信息。 */
    @Schema(description = "错误信息", requiredMode = Schema.RequiredMode.REQUIRED)
    private String errorMessage;
}
