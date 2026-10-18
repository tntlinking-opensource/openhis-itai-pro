/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.surgeryanesth.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * AS-IP-DOR-012 响应：DeleteAnesthesiaApplyForm。
 */
@Data
@Accessors(chain = true)
@Schema(description = "DeleteAnesthesiaApplyFormResponse")
public class DeleteAnesthesiaApplyFormResponse {

    /** 申请单号。 */
    @Schema(description = "申请单号")
    private String prescriptionNo;

    /** 已删除行主键。 */
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    @Schema(description = "已删除行主键")
    private List<Long> deletedServiceRequestIds;
}
