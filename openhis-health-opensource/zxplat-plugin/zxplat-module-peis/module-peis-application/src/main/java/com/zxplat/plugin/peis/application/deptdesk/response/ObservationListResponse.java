/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.deptdesk.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.plugin.peis.application.deptdesk.dto.ObservationRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 观察实例列表响应。
 */
@Data
public class ObservationListResponse {

    /** 服务申请主键。 */
    @Schema(description = "服务申请主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long serviceRequestId;

    /** 观察实例行列表。 */
    @Schema(description = "观察实例列表")
    private List<ObservationRowDto> observations;
}
