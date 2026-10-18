/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 待挂号（PLANNED）撤销请求：作废支付草稿并可选撤销医保 2206/2202。
 */
@Data
@Accessors(chain = true)
@Schema(description = "待挂号撤销请求")
public class CancelPlannedRegistrationRequest {

    /** 待撤销的门诊就诊 ID。 */
    @Schema(description = "就诊ID")
    private Long encounterId;

    /** 撤销原因（展示与审计用）。 */
    @Schema(description = "撤销原因")
    private String cancelReason;
}
