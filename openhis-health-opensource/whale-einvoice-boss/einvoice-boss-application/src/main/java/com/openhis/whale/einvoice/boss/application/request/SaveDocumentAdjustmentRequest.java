package com.openhis.whale.einvoice.boss.application.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 保存金额调整请求。
 */
@Data
public class SaveDocumentAdjustmentRequest {

    /** 单据主键。 */
    private Long documentId;

    /** 开票总额。 */
    private BigDecimal totalAmt;

    /** 调整原因（≥5 字）。 */
    private String reason;

    /** 明细行更新。 */
    private List<LineItem> lines = new ArrayList<>();

    @Data
    public static class LineItem {
        /** 已有行主键（新增行可空）。 */
        private Long lineId;
        private String chargeCode;
        private String chargeName;
        private BigDecimal stdAmount;
        private BigDecimal amount;
        private String unit;
        private BigDecimal quantity;
        private boolean newLine;
    }
}
