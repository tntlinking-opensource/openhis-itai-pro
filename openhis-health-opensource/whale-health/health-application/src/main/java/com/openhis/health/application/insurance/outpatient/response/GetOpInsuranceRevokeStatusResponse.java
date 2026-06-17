/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.outpatient.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
/** 门诊医保撤销协同单状态响应。 */
@Schema(description = "门诊医保撤销协同单状态响应")
public class GetOpInsuranceRevokeStatusResponse {

    /** 协同单主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long coordinationId;

    /** 原支付单主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long originalPaymentId;

    /** 协同单状态 code。 */
    private String state;

    /** 医保平台结算 id（撤销成功后回写）。 */
    private String ybSetlId;

    /** 协同单失败时的错误信息。 */
    private String errorMsg;

    /** 协同单是否已进入 COMPLETED 终态。 */
    private Boolean completed;

    /** 医保侧是否已完成撤销（含同步 NO_OP 场景）。 */
    private Boolean insuranceRevoked;
}
