/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.charging.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-MT-CHG-07：确认医技诊疗划价响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "确认医技诊疗划价响应")
public class ConfirmMedicalTechServicePricingResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean succeeded;

    /** 提示文案。 */
    @Schema(description = "提示文案")
    private String message;

    /** 新建 ServiceRequest 主键列表。 */
    @Schema(description = "新建服务申请主键列表")
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> createdRequestIds;
}
