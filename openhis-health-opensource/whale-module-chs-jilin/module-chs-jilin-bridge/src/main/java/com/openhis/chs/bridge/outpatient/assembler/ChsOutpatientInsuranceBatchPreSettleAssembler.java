/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.outpatient.assembler;

import com.openhis.chs.domain.outpatient.command.OpPreSettleBatchCommand;
import com.openhis.chs.domain.outpatient.command.OpPreSettleCommand;
import com.openhis.chs.domain.outpatient.command.OpSelfPayRollup;
import com.openhis.chs.domain.outpatient.loader.OpPreSettleContextLoader;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.chs.bridge.outpatient.assembler.OutpatientInsuranceChargeGrouper.OutpatientInsuranceChargeSplitResult;
import com.openhis.chs.bridge.outpatient.assembler.OutpatientInsuranceChargeGrouper.OutpatientInsuranceSettleGroupPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 门诊混合预结算批次组装：收费分组 → 逐组 ContextLoader enrich → {@link OpPreSettleBatchCommand}。
 *
 * <p>调用 {@link OpPreSettleContextLoader} 补全 HIS 字段；本类无 gateway 出站。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsOutpatientInsuranceBatchPreSettleAssembler {

    private final OutpatientInsuranceChargeGrouper chargeGrouper;
    private final OpPreSettleContextLoader contextLoader;

    /** 分组 enrich 后组装批量预结算命令（含自费汇总）。 */
    public OpPreSettleBatchCommand enrichBatch(OpPreSettleBatchCommand partial) {
        OutpatientInsuranceChargeSplitResult split = chargeGrouper.split(
            partial.getEncounterId(), partial.getChargeItemIds());

        List<OpPreSettleCommand> enrichedGroups = new ArrayList<>();
        for (OutpatientInsuranceSettleGroupPlan plan : split.getInsuranceGroups()) {
            OpPreSettleCommand groupPartial = this.toGroupCommand(partial, plan);
            List<Long> groupChargeItemIds = new ArrayList<>();
            for (ChargeItem item : plan.getChargeItems()) {
                groupChargeItemIds.add(item.getId());
            }
            OpPreSettleCommand enriched = contextLoader.enrichPreSettleForGroup(
                groupPartial,
                groupChargeItemIds,
                plan.getMedType(),
                plan.getDiseCodg(),
                plan.getDiseName());
            enriched.setAccountId(plan.getAccountId());
            enriched.setGroupIndex(plan.getGroupIndex());
            enriched.setGroupDiseCodg(plan.getDiseCodg());
            enrichedGroups.add(enriched);
        }

        return partial
            .setEnrichedGroups(enrichedGroups)
            .setSelfPayRollup(this.buildSelfPayRollup(split));
    }

    /**
     * 从批量命令与分组计划组装单组预结算命令。
     *
     * @param batch 批量预结算命令
     * @param plan  医保结算分组计划
     * @return 单组预结算命令
     */
    private OpPreSettleCommand toGroupCommand(
        OpPreSettleBatchCommand batch,
        OutpatientInsuranceSettleGroupPlan plan) {
        List<Long> groupIds = new ArrayList<>();
        for (ChargeItem item : plan.getChargeItems()) {
            groupIds.add(item.getId());
        }
        return new OpPreSettleCommand()
            .setEncounterId(batch.getEncounterId())
            .setChargeItemIds(groupIds)
            .setContractId(batch.getContractId())
            .setPaymentDraftId(batch.getPaymentDraftId())
            .setMdtrtCertType(batch.getMdtrtCertType())
            .setMdtrtCertNo(batch.getMdtrtCertNo())
            .setPsnNo(batch.getPsnNo())
            .setInsuplcAdmdvs(batch.getInsuplcAdmdvs())
            .setInsutype(batch.getInsutype())
            .setAcctUsedFlag(batch.getAcctUsedFlag())
            .setMedType(plan.getMedType());
    }

    /**
     * 根据自费收费项构建自费汇总信息。
     *
     * @param split 收费项分组结果
     * @return 自费汇总；无自费项时返回 {@code null}
     */
    private OpSelfPayRollup buildSelfPayRollup(OutpatientInsuranceChargeSplitResult split) {
        if (split.getSelfPayItems() == null || split.getSelfPayItems().isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        List<Long> ids = new ArrayList<>();
        for (ChargeItem item : split.getSelfPayItems()) {
            ids.add(item.getId());
            if (item.getTotalPrice() != null) {
                total = total.add(item.getTotalPrice());
            }
        }
        return new OpSelfPayRollup()
            .setAccountId(split.getSelfPayAccountId())
            .setChargeItemIds(ids)
            .setTotalAmount(total);
    }
}
