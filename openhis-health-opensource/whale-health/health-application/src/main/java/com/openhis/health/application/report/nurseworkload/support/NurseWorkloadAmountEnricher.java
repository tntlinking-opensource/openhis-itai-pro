/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.nurseworkload.support;

import com.openhis.health.application.report.nurseworkload.dto.NurseConsumableDetailRowDto;
import com.openhis.health.application.report.nurseworkload.dto.NurseExecutionDetailRowDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 护士工作量报表金额 enrich（**BR-RPT-NWL-021**；金额刻度 **BR-MON-001**）。
 *
 * <p>按收费明细锚点汇总 {@code total_price}；明细行 {@link MonetaryAmountSupport#scaleCalc} 出参 6 位，
 * 页脚 {@link #sumDetailAmounts} 走 {@link MonetaryAmountSupport#sumThenSettle}。</p>
 */
@Component
@RequiredArgsConstructor
public class NurseWorkloadAmountEnricher {

    private final ChargeItemQueryService chargeItemQueryService;

    /**
     * 回填诊疗执行明细行金额。
     *
     * @param rows 待 enrich 明细行
     */
    public void enrichExecutionDetailAmounts(List<NurseExecutionDetailRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<ChargeItem> chargeItems = this.loadChargeItemsForExecutionRows(rows);
        for (NurseExecutionDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            // Asm：匹配 ChargeItem 合计后保持 6 位行金额（BR-MON-001）
            row.setAmount(MonetaryAmountSupport.scaleCalc(this.sumMatchedChargeAmount(row, chargeItems)));
        }
    }

    /**
     * 回填耗材使用明细行金额。
     *
     * @param rows                   待 enrich 明细行
     * @param deviceReqIdByDispenseId 发放 id → 耗材请求 id
     */
    public void enrichConsumableDetailAmounts(
        List<NurseConsumableDetailRowDto> rows,
        Map<Long, Long> deviceReqIdByDispenseId) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<ChargeItem> chargeItems = this.loadChargeItemsForConsumableRows(rows);
        for (NurseConsumableDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            Long deviceReqId = null;
            if (row.getDispenseId() != null && deviceReqIdByDispenseId != null) {
                deviceReqId = deviceReqIdByDispenseId.get(row.getDispenseId());
            }
            row.setAmount(MonetaryAmountSupport.scaleCalc(this.sumMatchedConsumableChargeAmount(row, deviceReqId, chargeItems)));
        }
    }

    /**
     * 汇总明细行金额合计。
     *
     * @param amounts 明细金额列表
     * @return 结算刻度合计（先 6 位加总再 2 位）
     */
    public BigDecimal sumDetailAmounts(List<BigDecimal> amounts) {
        return MonetaryAmountSupport.sumThenSettle(amounts);
    }

    /**
     * 按诊疗执行明细行收集 encounter/procedure/service 锚点并批量加载 ChargeItem。
     *
     * @param rows 诊疗执行明细行
     * @return 去重后的收费明细列表
     */
    private List<ChargeItem> loadChargeItemsForExecutionRows(List<NurseExecutionDetailRowDto> rows) {
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> procedureIds = new LinkedHashSet<>();
        LinkedHashSet<Long> serviceRequestIds = new LinkedHashSet<>();
        for (NurseExecutionDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
            if (row.getProcedureId() != null) {
                procedureIds.add(row.getProcedureId());
            }
            if (row.getServiceRequestId() != null) {
                serviceRequestIds.add(row.getServiceRequestId());
            }
        }
        Map<Long, ChargeItem> chargeItemById = new HashMap<>();
        this.mergeChargeItems(chargeItemById, this.listChargeItemsByEncounterIds(encounterIds));
        this.mergeChargeItems(chargeItemById, this.chargeItemQueryService.listChargeItemsByProcedureIds(procedureIds));
        ChargeItemQueryCommand serviceCmd = new ChargeItemQueryCommand();
        serviceCmd.setServiceIds(serviceRequestIds);
        serviceCmd.setExcludeSupersededSettledLines(true);
        this.mergeChargeItems(chargeItemById, this.chargeItemQueryService.listChargeItemsByServiceIds(serviceCmd));
        return new ArrayList<>(chargeItemQueryService.filterOutSupersededSettledLinesGroupedByEncounter(
            new ArrayList<>(chargeItemById.values())));
    }

    /**
     * 按耗材使用明细行收集 encounter/dispense 锚点并批量加载 ChargeItem。
     *
     * @param rows 耗材使用明细行
     * @return 去重后的收费明细列表
     */
    private List<ChargeItem> loadChargeItemsForConsumableRows(List<NurseConsumableDetailRowDto> rows) {
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> serviceIds = new LinkedHashSet<>();
        for (NurseConsumableDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
            if (row.getDispenseId() != null) {
                serviceIds.add(row.getDispenseId());
            }
        }
        Map<Long, ChargeItem> chargeItemById = new HashMap<>();
        this.mergeChargeItems(chargeItemById, this.listChargeItemsByEncounterIds(encounterIds));
        ChargeItemQueryCommand serviceCmd = new ChargeItemQueryCommand();
        serviceCmd.setServiceIds(serviceIds);
        serviceCmd.setExcludeSupersededSettledLines(true);
        this.mergeChargeItems(chargeItemById, this.chargeItemQueryService.listChargeItemsByServiceIds(serviceCmd));
        return new ArrayList<>(chargeItemQueryService.filterOutSupersededSettledLinesGroupedByEncounter(
            new ArrayList<>(chargeItemById.values())));
    }

    /**
     * 按就诊 id 集合查询收费明细；空集合返回空列表。
     *
     * @param encounterIds 就诊主键集合
     * @return 收费明细列表
     */
    private List<ChargeItem> listChargeItemsByEncounterIds(Set<Long> encounterIds) {
        if (encounterIds.isEmpty()) {
            return List.of();
        }
        ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
        cmd.setEncounterIds(encounterIds);
        cmd.setExcludeSupersededSettledLines(true);
        return this.chargeItemQueryService.listChargeItemsByEncounterIds(cmd);
    }

    /**
     * 将收费明细合并到 id → 实体 Map（跳过已软删或无 id 行）。
     *
     * @param chargeItemById 目标 Map
     * @param chargeItems    待合并列表
     */
    private void mergeChargeItems(Map<Long, ChargeItem> chargeItemById, List<ChargeItem> chargeItems) {
        if (chargeItems == null) {
            return;
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null || Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
                continue;
            }
            chargeItemById.put(chargeItem.getId(), chargeItem);
        }
    }

    /**
     * 汇总与诊疗执行行匹配的 ChargeItem 金额（procedure 或 serviceRequest 锚点）。
     *
     * @param row         执行明细行
     * @param chargeItems 候选收费明细
     * @return 匹配行 total_price 合计（未结算刻度）
     */
    private BigDecimal sumMatchedChargeAmount(NurseExecutionDetailRowDto row, List<ChargeItem> chargeItems) {
        Set<Long> usedChargeItemIds = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ChargeItem chargeItem : chargeItems) {
            if (!this.matchesExecutionRow(row, chargeItem)) {
                continue;
            }
            if (!usedChargeItemIds.add(chargeItem.getId())) {
                continue;
            }
            total = total.add(this.priceOf(chargeItem));
        }
        return total;
    }

    /**
     * 收费明细是否与诊疗执行行锚点匹配。
     *
     * @param row        执行明细行
     * @param chargeItem 收费明细
     * @return 匹配为 true
     */
    private boolean matchesExecutionRow(NurseExecutionDetailRowDto row, ChargeItem chargeItem) {
        if (row.getProcedureId() != null
            && row.getProcedureId().equals(chargeItem.getProcedureId())) {
            return true;
        }
        if (row.getServiceRequestId() != null
            && TableNameConstants.WKF_SERVICE_REQUEST.equals(chargeItem.getServiceTable())
            && row.getServiceRequestId().equals(chargeItem.getServiceId())) {
            return true;
        }
        return false;
    }

    /**
     * 汇总与耗材使用行匹配的 ChargeItem 金额（dispense 或 deviceRequest 锚点）。
     *
     * @param row         耗材明细行
     * @param deviceReqId 发放 id 反查的耗材请求 id；可空
     * @param chargeItems 候选收费明细
     * @return 匹配行 total_price 合计（未结算刻度）
     */
    private BigDecimal sumMatchedConsumableChargeAmount(
        NurseConsumableDetailRowDto row,
        Long deviceReqId,
        List<ChargeItem> chargeItems) {
        Set<Long> usedChargeItemIds = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ChargeItem chargeItem : chargeItems) {
            if (!this.matchesConsumableRow(row, deviceReqId, chargeItem)) {
                continue;
            }
            if (!usedChargeItemIds.add(chargeItem.getId())) {
                continue;
            }
            total = total.add(this.priceOf(chargeItem));
        }
        return total;
    }

    /**
     * 收费明细是否与耗材使用行锚点匹配。
     *
     * @param row         耗材明细行
     * @param deviceReqId 发放反查的耗材请求 id；可空
     * @param chargeItem  收费明细
     * @return 匹配为 true
     */
    private boolean matchesConsumableRow(
        NurseConsumableDetailRowDto row,
        Long deviceReqId,
        ChargeItem chargeItem) {
        if (row.getDispenseId() != null) {
            if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(chargeItem.getServiceTable())
                && row.getDispenseId().equals(chargeItem.getServiceId())) {
                return true;
            }
            if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(chargeItem.getDispenseTable())
                && row.getDispenseId().equals(chargeItem.getDispenseId())) {
                return true;
            }
        }
        if (deviceReqId != null
            && TableNameConstants.WKF_DEVICE_REQUEST.equals(chargeItem.getServiceTable())
            && deviceReqId.equals(chargeItem.getServiceId())) {
            return true;
        }
        return false;
    }

    /**
     * 取收费明细行金额；null 按零处理。
     *
     * @param chargeItem 收费明细
     * @return total_price 或 ZERO
     */
    private BigDecimal priceOf(ChargeItem chargeItem) {
        if (chargeItem.getTotalPrice() == null) {
            return BigDecimal.ZERO;
        }
        return MonetaryAmountSupport.nullToZero(chargeItem.getTotalPrice());
    }
}
