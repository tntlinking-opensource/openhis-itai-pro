package com.openhis.whale.einvoice.boss.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 调额审计记录。
 */
@Data
public class BossAdjustLogDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private BigDecimal beforeTotal;
    private BigDecimal afterTotal;
    private String reason;
    private LocalDateTime createdAt;
}
