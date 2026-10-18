/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.charging.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 取消医技划价行键（AS-MT-CHG-001 IF-MT-CHG-04；耗材/诊疗共用）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "取消医技划价行")
public class MedicalTechDevicePricingCancelItemDto {

    /**
     * 请求主键（耗材=DeviceRequest；诊疗=ServiceRequest）。
     */
    @Schema(description = "请求主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestId;

    /**
     * 行类型：{@code DEVICE_REQUEST}（默认）或 {@code SERVICE_REQUEST}。
     *
     * <p>空值按耗材轨兼容历史前端。</p>
     */
    @Schema(description = "行类型 DEVICE_REQUEST|SERVICE_REQUEST")
    private String orderType;
}
