/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.stocktake.request;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 审批同意盘点单入参（IF-PHA-ST-08）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "审批同意盘点 IF-PHA-ST-08")
public class AgreePharmacyStocktakeRequest {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /** 已废弃：审批时间由服务端写入。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 已废弃：审批时间由服务端写入。 */
    @Schema(description = "已废弃：审批时间由服务端写入", deprecated = true)
    private LocalDateTime approvalTime;

    /** 已废弃：审批人取当前操作人。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 已废弃：审批人取当前登录用户。 */
    @Schema(description = "已废弃：审批人取当前登录用户", deprecated = true)
    private Long approverId;

    /** @deprecated CHS 模块启用即上传，此开关不再生效。 */
    @Deprecated
    /** 同意后是否上传医保进销存。 */
    @Schema(description = "同意后是否上传医保进销存")
    private Boolean medicareUploadEnabled;

    /** 医保契约 busNo。 */
    @Schema(description = "医保契约 busNo")
    private String contractBusNo;
}
