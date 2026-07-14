/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.ATDManager.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-NUR-ATD-02：分床/换床/互换提交响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "分床换床互换响应")
public class ExecuteBedOperationResponse {

    @Schema(description = "发起方就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 发起方就诊主键。 */
    private Long encounterId;

    /** 操作类型回显。 */
    @Schema(description = "操作类型回显")
    private String operationType;

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean succeeded;
}
