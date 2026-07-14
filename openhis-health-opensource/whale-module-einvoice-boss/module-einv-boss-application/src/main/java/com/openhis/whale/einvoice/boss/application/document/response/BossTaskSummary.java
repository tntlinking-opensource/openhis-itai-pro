/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.document.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开票任务摘要。
 */
@Data
public class BossTaskSummary {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    private String status;
    private Integer attemptCount;
    private String lastErrorCode;
    private String lastErrorMsg;
    private LocalDateTime nextRetryAt;
}
