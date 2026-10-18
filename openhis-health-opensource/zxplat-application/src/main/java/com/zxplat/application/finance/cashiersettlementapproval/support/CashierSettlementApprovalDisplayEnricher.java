/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.cashiersettlementapproval.support;

import com.zxplat.application.finance.cashiersettlementapproval.dto.CashierSettlementApprovalHeaderDto;
import com.zxplat.application.finance.cashiersettlementapproval.dto.CashierSettlementApprovalListItemDto;
import com.zxplat.application.shared.pharmacydailysettlement.PharmacyDailySettlementPricingSupport;
import com.zxplat.application.shared.financial.cashiersettlement.CashierSettlementPartyNamesEnricher;
import com.zxplat.domain.medication.medicationdispense.entity.MedicationDispense;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.zxplat.domain.share.enums.CashierSettlementType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 交账审批列表/详情头展示 enrich（展示名 + 详情药房金额展示口径）。
 *
 * <p>列表 {@code totalIncome} 一律保留头表快照（收款=总收入；药房=售价合计），禁止按挂载逐条现算。
 * 详情头对药房日结覆盖为挂载发药 <strong>售价合计</strong>（与大类「售价总额」之和同口径），
 * 与新提交头表快照同口径；历史利润快照行亦以现算售价展示。</p>
 */
@Component
@RequiredArgsConstructor
public class CashierSettlementApprovalDisplayEnricher {

    /** 共享交账人/审批人/药房/科室名 enrich。 */
    private final CashierSettlementPartyNamesEnricher cashierSettlementPartyNamesEnricher;

    /** 发药挂载读侧（详情药房金额现算）。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** 药房进/售汇总（售价合计）。 */
    private final PharmacyDailySettlementPricingSupport pharmacyDailySettlementPricingSupport;

    /**
     * 批量回填列表行展示名；金额保持头表快照（禁 N+1 现算）。
     *
     * @param rows 列表行；可空
     */
    public void enrichListItems(List<CashierSettlementApprovalListItemDto> rows) {
        this.cashierSettlementPartyNamesEnricher.enrich(rows);
    }

    /**
     * 回填详情头展示名，并将药房日结金额覆盖为售价合计。
     *
     * @param header 详情头；可空
     */
    public void enrichHeader(CashierSettlementApprovalHeaderDto header) {
        if (header == null) {
            return;
        }
        this.cashierSettlementPartyNamesEnricher.enrich(List.of(header));
        // Asm：详情头药房金额现算为售价合计（列表仍用头表快照）
        BigDecimal saleTotal =
            this.resolvePharmacySaleTotal(header.getSettlementId(), header.getTypeEnum());
        if (saleTotal == null) {
            return;
        }
        header.setTotalIncome(saleTotal);
    }

    /**
     * 现算药房售价合计；非药房或无挂载时返回 null（表示不覆盖头表快照）。
     *
     * @param settlementId 交账头主键
     * @param typeEnum     交账类型 code
     * @return 售价合计；不覆盖时 null
     */
    private BigDecimal resolvePharmacySaleTotal(Long settlementId, String typeEnum) {
        if (settlementId == null || !CashierSettlementType.isPharmacyType(typeEnum)) {
            return null;
        }
        boolean inpatientMode = CashierSettlementType.IP_PHARMACY.getCode().equals(typeEnum);
        List<MedicationDispense> dispenses =
            this.medicationDispenseQueryService.findDispensesByCashierSettlementId(settlementId);
        // 无挂载（如已驳回清挂载）：保留头表快照，避免金额被盖成 0
        if (dispenses == null || dispenses.isEmpty()) {
            return null;
        }
        return this.pharmacyDailySettlementPricingSupport.sumSaleAmountFromDispenses(
            dispenses,
            inpatientMode);
    }
}
