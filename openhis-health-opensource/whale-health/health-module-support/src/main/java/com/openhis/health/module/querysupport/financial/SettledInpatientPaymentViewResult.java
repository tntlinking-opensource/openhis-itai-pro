/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.querysupport.financial;

import com.openhis.health.module.querysupport.financial.dto.InpatientSettleCategorySummaryDto;
import com.openhis.health.module.querysupport.financial.dto.InpatientSettleChargeLineDto;
import com.openhis.health.module.querysupport.financial.dto.SettledPaymentBriefDto;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 已结算单查阅读结果（IF-IP-STL-07）。
 */
@Data
@Accessors(chain = true)
public class SettledInpatientPaymentViewResult {

    private Long encounterId;
    private List<SettledPaymentBriefDto> payments;
    private Long activePaymentId;
    private LocalDateTime settleTime;
    private String operatorName;
    private BigDecimal receivableTotal;
    private String payMethodText;
    private List<InpatientSettleChargeLineDto> lines;
    private List<InpatientSettleCategorySummaryDto> categories;
}
