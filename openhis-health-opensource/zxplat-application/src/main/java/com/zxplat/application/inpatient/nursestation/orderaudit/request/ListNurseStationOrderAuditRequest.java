/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.orderaudit.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 医嘱校对列表查询请求（IF-IP-AUD-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医嘱校对列表查询请求", requiredProperties = {"encounterIds", "auditTab"})
public class ListNurseStationOrderAuditRequest {

    /** 左侧多选就诊主键列表。 */
    @NotEmpty(message = "就诊标识列表不能为空")
    @Schema(description = "左侧多选就诊主键列表", required = true)
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> encounterIds;

    /** 校对 Tab：pending / passed / stopped / suspended（BR-IP-AUD-003）。 */
    @NotBlank(message = "校对 Tab 不能为空")
    /** 校对 Tab：pending / passed / stopped / suspended。 */
    @Schema(description = "校对 Tab：pending / passed / stopped / suspended", required = true)
    private String auditTab;

    /** 长临筛选：all / long / temp；默认 all。 */
    @Schema(description = "长临筛选：all / long / temp；默认 all")
    private String therapyType;

    /** 中西药 Tab：west / tcm；默认 west。 */
    @Schema(description = "中西药 Tab：west / tcm；默认 west")
    private String medicineCategoryTab;

    /**
     * 麻醉申请范围（BR-IP-NUR-003-ANES-001）。
     *
     * <p>{@code true}=仅保留申请单号 AA 前缀的麻醉申请（诊疗与药品同口径）；
     * {@code false}/空=排除麻醉申请（普通护士默认）。无类目 25 / 同单号池兼容。</p>
     */
    @Schema(description = "麻醉申请范围：true=仅麻醉申请（AA）；false/空=排除麻醉申请")
    private Boolean anesthesiaApplyOnly;
}
