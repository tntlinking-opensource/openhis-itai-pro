package com.openhis.whale.einvoice.boss.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量补开结果。
 */
@Data
public class BatchCompensateResultDto {

    private int successCount;
    private int failCount;
    private List<BatchCompensateItemDto> items = new ArrayList<>();

    @Data
    public static class BatchCompensateItemDto {

        @JsonSerialize(using = ToStringSerializer.class)
        private Long invoiceId;

        private boolean success;
        private String message;
        private String billNo;
    }
}
