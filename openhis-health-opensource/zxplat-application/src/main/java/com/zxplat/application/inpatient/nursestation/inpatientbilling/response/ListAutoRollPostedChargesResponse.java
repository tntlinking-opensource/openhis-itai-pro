/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.response;

import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationAutoRollPostedChargeRowDto;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-IP-ARG-09：自动计费配置已滚费用记录列表响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "自动计费已滚费用记录列表响应")
public class ListAutoRollPostedChargesResponse {

    /** 配置主键（回显）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "自动计费配置主键")
    private Long ruleId;

    /** 床位滚费长期 SR 主键（基于 based_on=ruleId）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "床位滚费医嘱主键")
    private Long serviceRequestId;

    /** 已滚费用行（occurrence 倒序）。 */
    @Schema(description = "已滚费用行")
    private List<NurseStationAutoRollPostedChargeRowDto> charges = new ArrayList<>();
}
