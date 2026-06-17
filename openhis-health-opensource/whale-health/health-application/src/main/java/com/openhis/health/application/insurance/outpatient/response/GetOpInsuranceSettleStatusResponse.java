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
/** 门诊医保结算协同单状态响应。 */
@Schema(description = "门诊医保结算协同单状态响应")
public class GetOpInsuranceSettleStatusResponse {

    /** 协同单主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long coordinationId;

    /** 支付草稿主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentId;

    /** 协同单状态 code（{@link com.openhis.health.domain.share.enums.YbCoordinationState}）。 */
    private String state;

    /** 支付单当前状态 code。 */
    private String paymentStatus;

    /** 本地落账完成后生成的发票 id（协同单未完成时为空）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long invoiceId;

    /** 医保平台结算 id（平台步骤成功后回写）。 */
    private String ybSetlId;

    /** 协同单失败时的错误信息。 */
    private String errorMsg;

    /** 协同单是否已进入 COMPLETED 终态。 */
    private Boolean completed;
}
