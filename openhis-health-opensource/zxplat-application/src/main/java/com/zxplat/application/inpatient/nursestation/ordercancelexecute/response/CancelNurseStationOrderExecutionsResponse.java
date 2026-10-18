/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.ordercancelexecute.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量取消已执行医嘱计划点响应（IF-IP-CXL-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量取消已执行医嘱计划点响应")
public class CancelNurseStationOrderExecutionsResponse {

    @Schema(description = "整批是否成功")
    /** 整批是否成功。 */
    private Boolean success;

    @Schema(description = "成功对冲的原 COMPLETED 执行记录主键列表")
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    /** 成功对冲的原 COMPLETED 执行记录主键列表。 */
    private List<Long> cancelledProcedureIds;

    /** 过滤重建后的新汇总单号列表。 */
    @Schema(description = "过滤重建后的新汇总单号列表")
    private List<String> rebuiltSummaryNos;

    /** 成功提示文案。 */
    @Schema(description = "成功提示文案")
    private String message;
}
