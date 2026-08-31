/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.support;

import com.openhis.health.application.peis.group.dto.ContractReconciliationBatchRowDto;
import com.openhis.health.application.peis.group.dto.ContractReconciliationRowDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.peis.group.entity.PeisGroupBatch;
import com.openhis.health.domain.peis.group.entity.PeisGroupCompany;
import com.openhis.health.domain.peis.group.entity.PeisGroupContract;
import com.openhis.health.domain.peis.group.entity.PeisGroupRoster;
import com.openhis.health.domain.peis.group.service.PeisGroupCheckQueryService;
import com.openhis.health.domain.share.constants.PeisGroupRosterConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 团检合同对账汇总（名册进度 + 加项收费）。
 */
@Component
@RequiredArgsConstructor
public class PeisGroupContractReconciliationSupport {

    private final PeisGroupCheckQueryService peisGroupCheckQueryService;
    private final ChargeItemQueryService chargeItemQueryService;

    public List<ContractReconciliationRowDto> listByCompany(Long companyId, String payStatusFilter) {
        PeisGroupCompany company = peisGroupCheckQueryService.requireCompany(companyId);
        List<PeisGroupContract> contracts = peisGroupCheckQueryService.listContracts(companyId);
        List<PeisGroupBatch> batches = peisGroupCheckQueryService.listBatches(companyId);
        Map<Long, List<PeisGroupBatch>> batchesByContract = new HashMap<>();
        for (PeisGroupBatch batch : batches) {
            if (batch == null || batch.getContractId() == null) {
                continue;
            }
            batchesByContract.computeIfAbsent(batch.getContractId(), key -> new ArrayList<>()).add(batch);
        }
        List<ContractReconciliationRowDto> rows = new ArrayList<>();
        for (PeisGroupContract contract : contracts) {
            if (contract == null || Boolean.TRUE.equals(contract.getIsDeleted())) {
                continue;
            }
            if (payStatusFilter != null && !payStatusFilter.isBlank()
                && !payStatusFilter.equals(contract.getPayStatusCode())) {
                continue;
            }
            rows.add(buildContractRow(company, contract, batchesByContract.get(contract.getId())));
        }
        return rows;
    }

    private ContractReconciliationRowDto buildContractRow(
            PeisGroupCompany company,
            PeisGroupContract contract,
            List<PeisGroupBatch> contractBatches) {
        ContractReconciliationRowDto row = new ContractReconciliationRowDto();
        row.setContractId(contract.getId());
        row.setContractName(contract.getContractName());
        row.setCompanyId(company.getId());
        row.setCompanyName(company.getName());
        row.setContractAmount(contract.getContractAmount());
        row.setPayStatusCode(contract.getPayStatusCode());
        row.setValidFrom(contract.getValidFrom());
        row.setValidTo(contract.getValidTo());
        if (contractBatches == null) {
            contractBatches = List.of();
        }
        row.setBatchCount(contractBatches.size());
        int rosterTotal = 0;
        int enrolled = 0;
        int arrived = 0;
        Set<Long> encounterIds = new HashSet<>();
        List<ContractReconciliationBatchRowDto> batchRows = new ArrayList<>();
        for (PeisGroupBatch batch : contractBatches) {
            List<PeisGroupRoster> rosterRows = peisGroupCheckQueryService.listRosterByBatch(batch.getId());
            ContractReconciliationBatchRowDto batchRow = buildBatchRow(batch, rosterRows);
            batchRows.add(batchRow);
            rosterTotal += safeInt(batchRow.getRosterTotal());
            enrolled += safeInt(batchRow.getEnrolledCount());
            arrived += safeInt(batchRow.getArrivedCount());
            for (PeisGroupRoster roster : rosterRows) {
                if (roster != null && roster.getEncounterId() != null
                    && PeisGroupRosterConstants.isEnrolled(roster.getPayStatusCode())) {
                    encounterIds.add(roster.getEncounterId());
                }
            }
        }
        row.setBatches(batchRows);
        row.setRosterTotal(rosterTotal);
        row.setEnrolledCount(enrolled);
        row.setUnenrolledCount(Math.max(0, rosterTotal - enrolled));
        row.setArrivedCount(arrived);
        row.setPendingArrivalCount(Math.max(0, enrolled - arrived));
        applyAddOnChargeSummary(row, encounterIds);
        return row;
    }

    private ContractReconciliationBatchRowDto buildBatchRow(
            PeisGroupBatch batch, List<PeisGroupRoster> rosterRows) {
        ContractReconciliationBatchRowDto batchRow = new ContractReconciliationBatchRowDto();
        batchRow.setBatchId(batch.getId());
        batchRow.setBatchName(batch.getBatchName());
        batchRow.setPackageActivityId(batch.getPackageActivityId());
        if (batch.getBookingDate() != null) {
            batchRow.setBookingDate(batch.getBookingDate().toString());
        }
        if (rosterRows == null) {
            rosterRows = List.of();
        }
        int total = rosterRows.size();
        int enrolled = 0;
        int arrived = 0;
        for (PeisGroupRoster roster : rosterRows) {
            if (roster == null) {
                continue;
            }
            if (PeisGroupRosterConstants.isEnrolled(roster.getPayStatusCode())) {
                enrolled++;
            }
            if (PeisGroupRosterConstants.ARRIVAL_ARRIVED.equals(roster.getArrivalStatusCode())) {
                arrived++;
            }
        }
        batchRow.setRosterTotal(total);
        batchRow.setEnrolledCount(enrolled);
        batchRow.setUnenrolledCount(Math.max(0, total - enrolled));
        batchRow.setArrivedCount(arrived);
        batchRow.setPendingArrivalCount(Math.max(0, enrolled - arrived));
        return batchRow;
    }

    private void applyAddOnChargeSummary(ContractReconciliationRowDto row, Set<Long> encounterIds) {
        row.setAddOnPendingCount(0);
        row.setAddOnPendingAmount(BigDecimal.ZERO);
        row.setAddOnSettledAmount(BigDecimal.ZERO);
        if (encounterIds.isEmpty()) {
            return;
        }
        ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
        cmd.setEncounterIds(new ArrayList<>(encounterIds));
        List<ChargeItem> chargeItems = chargeItemQueryService.listChargeItemsByEncounterIds(cmd);
        int pendingCount = 0;
        BigDecimal pendingAmount = BigDecimal.ZERO;
        BigDecimal settledAmount = BigDecimal.ZERO;
        String planned = ChargeItemStatus.PLANNED.getCode();
        String settled = ChargeItemStatus.SETTLED.getCode();
        for (ChargeItem item : chargeItems) {
            if (item == null || !TableNameConstants.WKF_SERVICE_REQUEST.equals(item.getServiceTable())) {
                continue;
            }
            BigDecimal amount = item.getTotalPrice() != null ? item.getTotalPrice() : item.getUnitPrice();
            if (amount == null) {
                continue;
            }
            amount = MonetaryAmountSupport.scaleCalc(amount);
            if (planned.equals(item.getStatusEnum())) {
                pendingCount++;
                pendingAmount = pendingAmount.add(amount);
            } else if (settled.equals(item.getStatusEnum())) {
                settledAmount = settledAmount.add(amount);
            }
        }
        row.setAddOnPendingCount(pendingCount);
        row.setAddOnPendingAmount(pendingAmount);
        row.setAddOnSettledAmount(settledAmount);
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
