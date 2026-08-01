/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.support;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.query.InpatientNurseStationCostDetailChargeRow;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
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
 *
 * <p>双轨：过滤/倒序锚定 Procedure/账单 {@code occurrence_time}；页面展示优先
 * {@code recorded_time}（AS-IP-NUR-006 / BR-IP-ORD-030⑥）。</p>
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
        if (cmd.getExcludedServiceTable() == null || StringUtils.isEmpty(cmd.getExcludedServiceTable())) {
            cmd.setExcludedServiceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        }
        cmd.setExcludeSupersededSettledLines(true);
        List<ChargeItem> candidates = this.chargeItemQueryService.listChargeItemsByEncounterId(cmd);
        Map<Long, LocalDateTime> netProcedureOccurrenceById =
            this.procedureQueryService.mapNetCompletedOccurrenceTimeByEncounterId(cmd.getEncounterId());
        Map<Long, LocalDateTime> netProcedureRecordedById =
            this.procedureQueryService.mapNetCompletedRecordedTimeByEncounterId(cmd.getEncounterId());
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
            // 过滤/倒序：逻辑轴 = occurrence（计划点）
            LocalDateTime businessDateTime = businessDateTimeOpt.get();
            LocalDate businessDate = businessDateTime.toLocalDate();
            if (businessDate.isBefore(cmd.getBusinessDateFrom())) {
                continue;
            }
            if (businessDate.isAfter(cmd.getBusinessDateTo())) {
                continue;
            }
            // 展示轴：医嘱执行优先 recorded；划价/滚费与逻辑轴相同
            LocalDateTime displayDateTime = this.resolveDisplayDateTime(
                chargeItem, businessDateTime, netProcedureRecordedById);
            rows.add(new InpatientNurseStationCostDetailChargeRow(
                chargeItem, businessDateTime, displayDateTime));
        }
        rows.sort(Comparator.comparing(InpatientNurseStationCostDetailChargeRow::getBusinessDateTime).reversed());
        return rows;
    }

    /**
     * 护士站费用明细默认状态子集（待结算/已结算/拟退/已退）。
     *
     * @return 状态 code 列表
     */
    private List<String> inpatientNurseStationCostDetailStatusCodes() {
        return List.of(
            ChargeItemStatus.PENDING_SETTLEMENT.getCode(),
            ChargeItemStatus.SETTLED.getCode(),
            ChargeItemStatus.REFUNDING.getCode(),
            ChargeItemStatus.REFUNDED.getCode());
    }

    /**
     * 解析页面展示时刻：医嘱执行路径取净剩 Procedure.recorded_time，否则回落逻辑业务时间。
     *
     * @param chargeItem              收费明细
     * @param businessDateTime        逻辑业务时间（occurrence）
     * @param netProcedureRecordedById 就诊下净完成 Procedure 登记时刻
     * @return 展示时刻（非空）
     */
    private LocalDateTime resolveDisplayDateTime(
        ChargeItem chargeItem,
        LocalDateTime businessDateTime,
        Map<Long, LocalDateTime> netProcedureRecordedById) {
        // 划价/滚费无 Procedure 双轨，展示与逻辑同源
        if (GenerateSource.NURSE_PRICING.getCode().equals(chargeItem.getGenerateSourceEnum())
            || GenerateSource.AUTO_ROLL_FEES.getCode().equals(chargeItem.getGenerateSourceEnum())) {
            return businessDateTime;
        }
        Long procedureId = chargeItem.getProcedureId();
        if (procedureId == null || netProcedureRecordedById == null) {
            return businessDateTime;
        }
        LocalDateTime recordedTime = netProcedureRecordedById.get(procedureId);
        if (recordedTime != null) {
            return recordedTime;
        }
        return businessDateTime;
    }

    /**
     * 按 AS-IP-NUR-006 规则解析单行<strong>逻辑</strong>业务发生时刻（过滤轴：划价/滚费 occurrence；医嘱净完成 occurrence）。
     *
     * @param chargeItem                 收费明细
     * @param netProcedureOccurrenceById 就诊下净完成 Procedure 发生时刻
     * @param businessDateFrom           业务日下限（含）
     * @param businessDateTo             业务日上限（含）
     * @return 命中区间内的逻辑业务时刻；区间外或无法解析时 empty
     */
    private Optional<LocalDateTime> resolveInpatientNurseStationCostDetailBusinessDateTimeForDateRange(
        ChargeItem chargeItem,
        Map<Long, LocalDateTime> netProcedureOccurrenceById,
        LocalDate businessDateFrom,
        LocalDate businessDateTo) {
        // 护士划价与住院滚费均锚定账单 occurrence_time（BR-IP-CDT-003；无 procedure_id）
        if (GenerateSource.NURSE_PRICING.getCode().equals(chargeItem.getGenerateSourceEnum())
            || GenerateSource.AUTO_ROLL_FEES.getCode().equals(chargeItem.getGenerateSourceEnum())) {
            if (chargeItem.getOccurrenceTime() == null) {
                throw new IllegalArgumentException(
                    "bill occurrence_time missing, chargeItemId=" + chargeItem.getId()
                        + ", generateSource=" + chargeItem.getGenerateSourceEnum());
            }
            LocalDate billDate = chargeItem.getOccurrenceTime().toLocalDate();
            if (billDate.isBefore(businessDateFrom) || billDate.isAfter(businessDateTo)) {
                return Optional.empty();
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
