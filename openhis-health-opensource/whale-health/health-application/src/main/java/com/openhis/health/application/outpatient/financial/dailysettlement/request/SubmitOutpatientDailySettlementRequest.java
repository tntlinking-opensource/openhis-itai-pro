/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.dailysettlement.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.util.List;

/**
 * 确认门诊交账请求（IF-OP-DCL-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "确认门诊交账请求", requiredProperties = {"dateFrom", "dateTo", "orgId", "paymentIds"})
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

    /** 本次勾选挂载的 payment 主键（须 ⊆ 未交账候选；BR-OP-DCL-018）。 */
    @NotEmpty(message = "请至少选择一笔待交账流水")
    @Schema(description = "勾选 payment 主键列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<@NotNull Long> paymentIds;
}
