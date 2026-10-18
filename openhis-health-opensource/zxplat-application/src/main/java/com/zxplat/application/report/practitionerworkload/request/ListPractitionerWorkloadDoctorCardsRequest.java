/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.practitionerworkload.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 科室/医生工作量医生卡片批量查询入参（卡片墙；与 IF-02 同筛选）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "医生工作量卡片批量查询")
public class ListPractitionerWorkloadDoctorCardsRequest {

    /** 就诊范围。 */
    @NotBlank(message = "请选择门诊或住院")
    @Schema(description = "就诊范围", requiredMode = Schema.RequiredMode.REQUIRED)
    private String encounterScope;

    /** 查询日起 yyyy-MM-dd。 */
    @NotBlank(message = "请选择查询开始日期")
    @Schema(description = "查询日起 yyyy-MM-dd", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateFrom;

    /** 查询日止 yyyy-MM-dd。 */
    @NotBlank(message = "请选择查询结束日期")
    @Schema(description = "查询日止 yyyy-MM-dd", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateTo;

    /** 开立科室主键（可选；{@code ChargeItem.requesting_org_id}）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "开立科室主键（可选）")
    private Long requestingOrgId;

    /** 科室主键（遗留；报表中心请用 {@link #requestingOrgId}）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "科室主键（遗留；请用 requestingOrgId）")
    private Long organizationId;

    /**
     * 医生主键列表（前端下拉全量；须非空）。
     *
     * <p>一次扫库后按人分桶，避免卡片墙 N 次 IF-02。</p>
     */
    @NotEmpty(message = "请选择门诊或住院及有效的查询日期")
    @Schema(description = "医生主键列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> practitionerIds;
}
