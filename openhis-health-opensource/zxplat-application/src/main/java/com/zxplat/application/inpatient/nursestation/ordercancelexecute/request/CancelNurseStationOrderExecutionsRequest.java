/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.ordercancelexecute.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量取消已执行医嘱计划点请求（IF-IP-CXL-01）。
 *
 * <p>procedureIds 为已执行 Tab 勾选槽位对应的净剩 COMPLETED 执行记录主键；同批禁止重复。</p>
 */
@Data
@Accessors(chain = true)
@Schema(
    description = "批量取消已执行医嘱计划点请求",
    requiredProperties = {"encounterIds", "procedureIds", "statusReasonText"})
public class CancelNurseStationOrderExecutionsRequest {

    /** 已选就诊主键列表。 */
    @NotEmpty(message = "请先选择患者")
    @Schema(description = "已选就诊主键列表", required = true)
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> encounterIds;

    /** 已执行 Tab 勾选槽位的净剩 COMPLETED procedureId（1～50，同批禁止重复，BR-IP-CXL-002）。 */
    @NotEmpty(message = "请选择已执行且未取消的计划点")
    /** 净剩 COMPLETED 执行记录主键列表。 */
    @Schema(description = "净剩 COMPLETED 执行记录主键列表", required = true)
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> procedureIds;

    /** 取消原因（BR-IP-CXL-004 必填）。 */
    @NotBlank(message = "取消原因不能为空")
    /** 取消原因。 */
    @Schema(description = "取消原因", required = true)
    private String statusReasonText;
}
