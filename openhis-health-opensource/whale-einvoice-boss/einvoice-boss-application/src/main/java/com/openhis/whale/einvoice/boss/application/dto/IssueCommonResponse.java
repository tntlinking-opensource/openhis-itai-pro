package com.openhis.whale.einvoice.boss.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 通用票开具结果（P3）。
 */
@Data
public class IssueCommonResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;

    private String busNo;
    private String billNo;
    private String billBatchCode;
    private String pictureUrl;
    private String pictureNetUrl;
    private boolean success;
    private String message;
}
