/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.registration.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登记详情响应。
 */
@Getter
@Setter
@Schema(description = "检查登记-详情响应")
public class GetRegistrationDetailResponse {

    @Schema(description = "工单主键")
    private Long workItemId;

    @Schema(description = "检查号")
    private String accessionNumber;

    @Schema(description = "患者主键")
    private Long patientId;

    @Schema(description = "患者姓名")
    private String patientName;

    @Schema(description = "就诊主键")
    private Long encounterId;

    @Schema(description = "科室")
    private Long organizationId;

    @Schema(description = "状态")
    private String statusEnum;

    @Schema(description = "模态")
    private String modalityCode;

    @Schema(description = "部位")
    private String bodySiteCode;

    @Schema(description = "设备")
    private Long deviceId;

    @Schema(description = "执行人")
    private Long performerPractitionerId;

    @Schema(description = "计划时间")
    private LocalDateTime scheduledTime;

    @Schema(description = "是否有媒体")
    private Boolean hasMedia;

    @Schema(description = "关联医嘱数")
    private Integer orderCount;

    @Schema(description = "关联 ServiceRequest id 列表")
    private List<Long> serviceRequestIds;
}
