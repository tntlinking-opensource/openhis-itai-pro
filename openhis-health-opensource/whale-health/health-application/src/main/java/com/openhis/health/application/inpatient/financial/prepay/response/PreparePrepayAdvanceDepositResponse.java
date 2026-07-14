/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.prepay.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/** 预交金扫码预下单（落 DRAFT，供 prepareConsumerScan）。 */
@Data
@Accessors(chain = true)
@Schema(description = "预交金扫码预下单响应")
public class PreparePrepayAdvanceDepositResponse {

    /** 预交金支付草稿 ID（扫码预下单后由 IF-IP-PREP-03a 返回）。 */
    @Schema(description = "预交金支付草稿 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentDraftId;
}
