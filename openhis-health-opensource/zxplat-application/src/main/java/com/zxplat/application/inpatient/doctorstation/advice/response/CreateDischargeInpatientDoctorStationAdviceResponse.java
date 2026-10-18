/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.advice.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-21：开立出院医嘱响应（出院服务申请与离院里程碑同事务写入并签发后的回传）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医生站-开立出院医嘱响应")
public class CreateDischargeInpatientDoctorStationAdviceResponse {

    /** 出院 ServiceRequest 主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "出院 ServiceRequest 主键")
    private Long serviceRequestId;

    /** 同事务写入的 {@code EncounterHistory} 离院里程碑记录主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 离院里程碑主键。 */
    @Schema(description = "离院里程碑主键")
    private Long encounterHistoryId;

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 提示文案。 */
    @Schema(description = "提示文案")
    private String message;
}
