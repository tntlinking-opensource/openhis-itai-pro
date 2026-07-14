/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.transferin.request;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 审批同意药房调入（手工单）请求（IF-PHA-TI-08）。
 *
 * <p>本服务为只读台账侧，接口统一拒绝手工同意；请求体保留契约兼容。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "审批同意药房调入 IF-PHA-TI-08", requiredProperties = {"busNo"})
public class AgreeTransferInRequest {

    /** 业务单号。 */
    @NotBlank(message = "busNo 不能为空")
    @Schema(description = "业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /**
     * 已废弃：领域 agreeRequest 内以服务端当前时间写入 approval_time，请求体若仍带此字段则忽略。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "已废弃：审批时间由服务端写入，可忽略", deprecated = true)
    /** 已废弃：审批时间由服务端写入，可忽略。 */
    private LocalDateTime approvalTime;

    /**
     * 已废弃：同意命令装配固定以当前操作人 operatorId 写入 approver_id，请求体若仍带此字段则忽略。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "已废弃：审批人取当前登录用户，可忽略", deprecated = true)
    /** 已废弃：审批人取当前登录用户，可忽略。 */
    private Long approverId;

    /** 同意后是否上传医保进销存。 */
    @Deprecated
    @Schema(description = "同意后是否上传医保进销存")
    private Boolean medicareUploadEnabled;

    /** 医保契约 busNo。 */
    @Schema(description = "医保契约 busNo")
    private String contractBusNo;
}
