package com.openhis.whale.einvoice.boss.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开票任务摘要。
 */
@Data
public class BossTaskSummaryDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    private String status;
    private Integer attemptCount;
    private String lastErrorCode;
    private String lastErrorMsg;
    private LocalDateTime nextRetryAt;
}
