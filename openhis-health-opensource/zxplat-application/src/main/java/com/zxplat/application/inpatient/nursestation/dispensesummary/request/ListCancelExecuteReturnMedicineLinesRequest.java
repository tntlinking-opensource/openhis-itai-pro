/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.dispensesummary.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 按患者查询取消执行退药清单请求（IF-IP-SUM-10）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "取消执行退药清单 IF-IP-SUM-10", requiredProperties = {
    "encounterIds", "medicineCategoryTab"
})
public class ListCancelExecuteReturnMedicineLinesRequest {

    /**
     * 左侧已选就诊主键（BR-IP-SUM-001）；按就诊分组回传，支持多选但行归属单患者。
     */
    @NotEmpty(message = "请选择至少一名患者")
    @Schema(description = "左侧已选就诊主键", required = true)
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> encounterIds;

    /**
     * 中西药 Tab：{@code west} / {@code tcm}；与待汇总同口径按药嘱 {@code tcmFlag} 收窄。
     */
    @NotNull(message = "药品分类 Tab 不能为空")
    @Schema(description = "中西药 Tab：west / tcm", required = true)
    private String medicineCategoryTab;
}
