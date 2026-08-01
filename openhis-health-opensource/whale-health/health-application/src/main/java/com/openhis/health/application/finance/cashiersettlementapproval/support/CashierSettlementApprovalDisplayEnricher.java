/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.cashiersettlementapproval.support;

import com.openhis.health.application.finance.cashiersettlementapproval.dto.CashierSettlementApprovalHeaderDto;
import com.openhis.health.application.finance.cashiersettlementapproval.dto.CashierSettlementApprovalListItemDto;
import com.openhis.health.application.shared.financial.cashiersettlement.CashierSettlementPartyNamesEnricher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 交账审批列表/详情头展示名 enrich（委托共享批量 enrich）。
 */
@Component
@RequiredArgsConstructor
public class CashierSettlementApprovalDisplayEnricher {

    /** 共享交账人/审批人/药房/科室名 enrich。 */
    private final CashierSettlementPartyNamesEnricher cashierSettlementPartyNamesEnricher;

    /**
     * 批量回填列表行展示名。
     *
     * @param rows 列表行；可空
     */
    public void enrichListItems(List<CashierSettlementApprovalListItemDto> rows) {
        this.cashierSettlementPartyNamesEnricher.enrich(rows);
    }

    /**
     * 回填详情头展示名。
     *
     * @param header 详情头；可空
     */
    public void enrichHeader(CashierSettlementApprovalHeaderDto header) {
        if (header == null) {
            return;
        }
        this.cashierSettlementPartyNamesEnricher.enrich(List.of(header));
    }
}
