/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.history.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

/**
 * 取消未收费体检响应。
 */
@Getter
@Setter
public class CancelPhysicalExamResponse {

    /** 就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 是否已取消。 */
    private Boolean cancelled;
}
