package com.openhis.whale.einvoice.boss.application.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 医疗通用票开具请求（P3）。
 */
@Data
public class IssueCommonRequest {

    /** 业务标识：09 往来 / 10 捐赠 / 11 非税通用。 */
    private String commonBusType;

    private String payer;
    private String payerPhone;
    private String payerIdCard;
    private String payerType = "1";
    private String payerSex = "男";
    private String payerAge = "0";
    private String remark;
    private String operatorName;
    private BigDecimal totalAmt;
    private List<CommonLineRequest> lines = new ArrayList<>();

    @Data
    public static class CommonLineRequest {
        private String chargeCode;
        private String chargeName;
        private BigDecimal amount;
        private BigDecimal quantity;
        private String unit;
    }
}
