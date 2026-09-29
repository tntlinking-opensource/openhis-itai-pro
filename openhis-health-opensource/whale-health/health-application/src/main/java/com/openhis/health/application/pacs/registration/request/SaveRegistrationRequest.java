/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.registration.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 保存/确认登记请求（IF-PACS-REG-02）。
 */
@Getter
@Setter
@Schema(description = "检查登记-保存请求")
public class SaveRegistrationRequest {

    @NotNull
    @Schema(description = "工单主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long workItemId;

    @Schema(description = "登记/执行科室")
    private Long organizationId;

    @Schema(description = "模态码")
    private String modalityCode;

    @Schema(description = "检查部位码")
    private String bodySiteCode;

    @Schema(description = "执行设备主键")
    private Long deviceId;

    @Schema(description = "执行人主键")
    private Long performerPractitionerId;

    @Schema(description = "计划检查时间")
    private LocalDateTime scheduledTime;

    @Schema(description = "检查号；确认时可空由系统生成")
    private String accessionNumber;

    @Schema(description = "true=确认进入待摄（scheduled）")
    private Boolean confirmSchedule;
}
