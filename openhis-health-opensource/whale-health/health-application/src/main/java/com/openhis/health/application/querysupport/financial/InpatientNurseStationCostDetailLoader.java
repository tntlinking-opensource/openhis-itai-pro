/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.querysupport.financial;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.query.InpatientNurseStationCostDetailChargeRow;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 住院护士站费用明细读组装：收费明细 + 执行记录业务时间（B.6.1 跨 clinical 编排）。
 */
@Component
@RequiredArgsConstructor
public class InpatientNurseStationCostDetailLoader {

    private final ChargeItemQueryService chargeItemQueryService;
    private final ProcedureQueryService procedureQueryService;

    /**
     * 住院护士站费用明细候选行（DM-ADM-011 §6.2.15；AS-IP-NUR-006 IF-IP-CDT-02）。
     *
     * @param cmd 查询命令（{@code encounterId}、{@code businessDateFrom}、{@code businessDateTo} 必填）
     * @return 费用明细行；区间内无命中时返回空列表
     */
    public List<InpatientNurseStationCostDetailChargeRow> listInpatientNurseStationCostDetailChargeRows(
        ChargeItemQueryCommand cmd) {
        if (cmd == null || cmd.getEncounterId() == null) {
            throw new IllegalArgumentException("inpatient nurse station cost detail requires encounterId");
        }
        if (cmd.getBusinessDateFrom() == null || cmd.getBusinessDateTo() == null) {
            throw new IllegalArgumentException("inpatient nurse station cost detail requires businessDateFrom and businessDateTo");
        }
        if (cmd.getBusinessDateFrom().isAfter(cmd.getBusinessDateTo())) {
            throw new IllegalArgumentException("businessDateFrom must not be after businessDateTo");
        }
        if (cmd.getStatusEnumIn() == null || cmd.getStatusEnumIn().isEmpty()) {
            cmd.setStatusEnumIn(this.inpatientNurseStationCostDetailStatusCodes());
        }
        if (cmd.getExcludedServiceTable() == null || StringUtils.isBlank(cmd.getExcludedServiceTable())) {
            cmd.setExcludedServiceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        }
        List<ChargeItem> candidates = this.chargeItemQueryService.listChargeItemsByEncounterId(cmd);
        Map<Long, LocalDateTime> netProcedureOccurrenceById =
            this.procedureQueryService.mapNetCompletedOccurrenceTimeByEncounterId(cmd.getEncounterId());
        List<InpatientNurseStationCostDetailChargeRow> rows = new ArrayList<>();
        for (ChargeItem chargeItem : candidates) {
            if (chargeItem == null) {
                continue;
            }
            Optional<LocalDateTime> businessDateTimeOpt =
                this.resolveInpatientNurseStationCostDetailBusinessDateTimeForDateRange(
                    chargeItem,
                    netProcedureOccurrenceById,
                    cmd.getBusinessDateFrom(),
                    cmd.getBusinessDateTo());
            if (businessDateTimeOpt.isEmpty()) {
                continue;
            }
            LocalDateTime businessDateTime = businessDateTimeOpt.get();
            LocalDate businessDate = businessDateTime.toLocalDate();
            if (businessDate.isBefore(cmd.getBusinessDateFrom())) {
                continue;
            }
            if (businessDate.isAfter(cmd.getBusinessDateTo())) {
                continue;
            }
            rows.add(new InpatientNurseStationCostDetailChargeRow(chargeItem, businessDateTime));
        }
        rows.sort(Comparator.comparing(InpatientNurseStationCostDetailChargeRow::getBusinessDateTime).reversed());
        return rows;
    }

    /**
     * 护士站费用明细默认状态子集（待结算/已结算/拟退/已退）。
     */
    private List<String> inpatientNurseStationCostDetailStatusCodes() {
        return List.of(
            ChargeItemStatus.PENDING_SETTLEMENT.getCode(),
            ChargeItemStatus.SETTLED.getCode(),
            ChargeItemStatus.REFUNDING.getCode(),
            ChargeItemStatus.REFUNDED.getCode());
    }

    /**
     * 按 AS-IP-NUR-006 规则解析单行业务发生时刻（护士划价取 occurrence_time，医嘱取净完成执行时刻）。
     *
     * @param chargeItem                 收费明细
     * @param netProcedureOccurrenceById 就诊下净完成 Procedure 发生时刻
     * @param businessDateFrom           业务日下限（含）
     * @param businessDateTo             业务日上限（含）
     * @return 命中区间内的业务时刻；区间外或无法解析时 empty
     */
    private Optional<LocalDateTime> resolveInpatientNurseStationCostDetailBusinessDateTimeForDateRange(
        ChargeItem chargeItem,
        Map<Long, LocalDateTime> netProcedureOccurrenceById,
        LocalDate businessDateFrom,
        LocalDate businessDateTo) {
        if (GenerateSource.NURSE_PRICING.getCode().equals(chargeItem.getGenerateSourceEnum())) {
            if (chargeItem.getOccurrenceTime() == null) {
                throw new IllegalArgumentException(
                    "nurse pricing charge missing occurrence_time, chargeItemId=" + chargeItem.getId());
            }
            return Optional.of(chargeItem.getOccurrenceTime());
        }
        Long procedureId = chargeItem.getProcedureId();
        if (procedureId == null) {
            throw new IllegalArgumentException(
                "order charge missing procedure_id, chargeItemId=" + chargeItem.getId());
        }
        LocalDateTime occurrenceTime = netProcedureOccurrenceById.get(procedureId);
        if (occurrenceTime != null) {
            return Optional.of(occurrenceTime);
        }
        LocalDateTime billOccurrenceTime = chargeItem.getOccurrenceTime();
        if (billOccurrenceTime != null) {
            LocalDate billDate = billOccurrenceTime.toLocalDate();
            if (!billDate.isBefore(businessDateFrom) && !billDate.isAfter(businessDateTo)) {
                throw new IllegalArgumentException(
                    "charge procedure is not net completed, chargeItemId=" + chargeItem.getId()
                        + ", procedureId=" + procedureId);
            }
        }
        return Optional.empty();
    }
}
