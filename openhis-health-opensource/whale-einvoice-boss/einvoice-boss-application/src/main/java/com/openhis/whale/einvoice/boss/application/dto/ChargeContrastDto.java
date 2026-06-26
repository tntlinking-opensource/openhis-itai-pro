package com.openhis.whale.einvoice.boss.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收费对照行。
 */
@Data
public class ChargeContrastDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;

    private String internalCode;
    private String bossChargeCode;
    private String bossChargeName;
    private LocalDateTime updatedAt;
}
