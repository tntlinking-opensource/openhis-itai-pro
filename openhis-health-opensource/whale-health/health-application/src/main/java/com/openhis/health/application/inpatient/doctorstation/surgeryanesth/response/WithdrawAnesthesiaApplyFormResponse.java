/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.surgeryanesth.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * AS-IP-DOR-012 响应：WithdrawAnesthesiaApplyForm。
 */
@Data
@Accessors(chain = true)
@Schema(description = "WithdrawAnesthesiaApplyFormResponse")
public class WithdrawAnesthesiaApplyFormResponse {

    /** 申请单号。 */
    @Schema(description = "申请单号")
    private String prescriptionNo;

    /** 已撤回行主键。 */
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    @Schema(description = "已撤回行主键")
    private List<Long> withdrawnServiceRequestIds;
}
