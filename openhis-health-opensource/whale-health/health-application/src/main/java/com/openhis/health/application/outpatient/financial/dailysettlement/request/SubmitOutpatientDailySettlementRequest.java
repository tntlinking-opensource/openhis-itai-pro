/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.financial.dailysettlement.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 确认门诊交账请求（IF-OP-DCL-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "确认门诊交账请求", requiredProperties = {"dateFrom", "dateTo", "orgId"})
public class SubmitOutpatientDailySettlementRequest {

    /** 业务日起（与预览相同）。 */
    @NotNull(message = "业务日起不能为空")
    @Schema(description = "业务日起", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate dateFrom;

    /** 业务日止（与预览相同）。 */
    @NotNull(message = "业务日止不能为空")
    @Schema(description = "业务日止", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate dateTo;

    /** 经办人 Practitioner 主键；缺省为当前操作员。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "经办人 Practitioner 主键")
    private Long cashierId;

    /** 交账科室。 */
    @NotNull(message = "交账科室不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "交账科室", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long orgId;

    /** 收费窗口，可选。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费窗口")
    private Long locationId;
}
