/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.document.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 调额审计记录。
 */
@Data
public class BossAdjustLog {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private BigDecimal beforeTotal;
    private BigDecimal afterTotal;
    private String reason;
    private LocalDateTime createdAt;
}
