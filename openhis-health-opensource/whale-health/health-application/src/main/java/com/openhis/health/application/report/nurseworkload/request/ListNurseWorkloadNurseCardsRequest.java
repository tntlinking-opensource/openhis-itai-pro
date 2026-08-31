/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.nurseworkload.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 护士工作量卡片墙批量查询入参（与汇总同筛选；不含单护士收窄）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "护士工作量卡片批量查询")
public class ListNurseWorkloadNurseCardsRequest {

    /** 门诊/住院。 */
    @Schema(description = "门诊/住院", requiredMode = Schema.RequiredMode.REQUIRED)
    private String encounterScope;

    /** 统计日起。 */
    @NotNull(message = "请选择门诊或住院及有效的查询日期")
    @Schema(description = "统计日起", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate dateFrom;

    /** 统计日止。 */
    @NotNull(message = "请选择门诊或住院及有效的查询日期")
    @Schema(description = "统计日止", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate dateTo;

    /**
     * 开立科室主键（可选；仅金额按 requesting_org_id 二次收窄）。
     *
     * <p>科室工作量请传 {@link #organizationId}。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "开立科室主键（可选；金额二次收窄）")
    private Long requestingOrgId;

    /**
     * 默认登录科室主键（可选；{@code Practitioner.org_id}）。
     *
     * <p>已选时仅汇总该科下护士岗人员卡片。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "默认登录科室主键（可选；Practitioner.org_id）")
    private Long organizationId;
}
