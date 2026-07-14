/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.gainloss.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 审批同意损益单入参（IF-DEPT-GL-08）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "审批同意损益 IF-DEPT-GL-08")
public class AgreeGainLossRequest {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /**
     * 已废弃：审批时间由服务端写入。
     *
     * @deprecated 客户端勿传；服务端以操作时刻为准
     */
    @Deprecated
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "已废弃：审批时间由服务端写入", deprecated = true)
    /** 已废弃：审批时间由服务端写入。 */
    private LocalDateTime approvalTime;

    /**
     * 已废弃：审批人取当前操作人。
     *
     * @deprecated 客户端勿传；服务端取 {@code operatorId}
     */
    @Deprecated
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "已废弃：审批人取当前登录用户", deprecated = true)
    /** 已废弃：审批人取当前登录用户。 */
    private Long approverId;

    /**
     * 已废弃：CHS 模块启用即上传，此开关不再生效。
     *
     * @deprecated 见 {@link com.openhis.health.infrastructure.integration.insurance.inventory.InventoryThirdPartyUploadExtension#runUploadIfEnabled(Runnable)}
     */
    @Deprecated
    @Schema(description = "同意后是否上传医保进销存", deprecated = true)
    /** 同意后是否上传医保进销存。 */
    private Boolean medicareUploadEnabled;

    /** 医保契约 busNo。 */
    @Schema(description = "医保契约 busNo")
    private String contractBusNo;
}
