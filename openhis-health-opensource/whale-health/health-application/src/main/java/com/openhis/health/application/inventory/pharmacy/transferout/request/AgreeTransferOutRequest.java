/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.transferout.request;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 同意药房调出单请求（IF-PHA-TR-08）。
 *
 * <p>触发同意链：领域同意、发放、源库扣减、目的入账、对侧调入壳单与追溯写入由应用服务编排，单事务边界见 AS-SDD。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class AgreeTransferOutRequest {

    /** 调出业务单号。 */
    @Schema(description = "调出业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /**
     * 已废弃：领域 {@code agreeRequest} 内以服务端当前时间写入 {@code approval_time}（WHS-IN-SDD-R-013 / WHS-SO-SDD-R-009），请求体若仍带此字段则忽略。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "已废弃：审批时间由服务端写入，可忽略", deprecated = true)
    /** 已废弃：审批时间由服务端写入，可忽略。 */
    private LocalDateTime approvalTime;

    /**
     * 已废弃：Mapper 固定以当前操作人 {@code operatorId} 写入 {@code approver_id}，请求体若仍带此字段则忽略。
     */
    @Schema(description = "已废弃：审批人取当前登录用户，可忽略", deprecated = true)
    private Long approverId;

    /** 同意后是否上传医保进销存。 */
    @Deprecated
    @Schema(description = "同意后是否上传医保进销存")
    private Boolean medicareUploadEnabled;

    /** 医保契约 busNo。 */
    @Schema(description = "医保契约 busNo")
    private String contractBusNo;
}
