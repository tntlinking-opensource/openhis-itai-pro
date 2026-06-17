/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.outpatient.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
/** 查询门诊医保撤销协同单状态请求。 */
@Schema(description = "查询门诊医保撤销协同单状态请求")
public class GetOpInsuranceRevokeStatusRequest {

    /** 原支付单主键，用于定位 OP_SETTLE_REVERSE 协同单。 */
    @NotNull
    @JsonSerialize(using = ToStringSerializer.class)
    private Long originalPaymentId;
}
