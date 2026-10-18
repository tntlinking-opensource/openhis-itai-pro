/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.refundapplication.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DRA-04：撤销门诊退费申请响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站-撤销退费响应")
public class CancelOutpatientRefundApplicationResponse {

    /** 撤销受理成功。 */
    @Schema(description = "撤销受理成功")
    private boolean success;

    /** 回已结算的收费行数。 */
    @Schema(description = "回已结算的收费行数")
    private int settledChargeItemCount;
}
