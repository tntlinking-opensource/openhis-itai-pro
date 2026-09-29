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
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 住院护士站费用明细读组装：收费明细 + 客户可见业务时间（B.6.1 跨 clinical 编排）。
 *
 * <p>用户轴（进窗 / 排序 / 列展示同一时刻）：有 Procedure → 净剩 {@code recorded_time}；
 * 护士划价耗材 → 发放展示时刻；滚费无 Procedure 展示轴 → {@code occurrence_time}。
 * {@code occurrence_time} 仅后端逻辑/排障，不对用户过滤或展示（AS-IP-NUR-006 / BR-IP-PRC-030）。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientNurseStationCostDetailLoader {

    private final ChargeItemQueryService chargeItemQueryService;
    private final ProcedureQueryService procedureQueryService;
    private final DeviceDispenseQueryService deviceDispenseQueryService;

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
        Map<Long, LocalDateTime> netProcedureRecordedById =
            this.procedureQueryService.mapNetCompletedRecordedTimeByEncounterId(cmd.getEncounterId());
        // Pre：护士/医技划价诊疗按 Request 取 recorded；耗材按发放展示时刻（与 IF-IP-PRC-05/07 同轴）
        Set<Long> nursePricingServiceReqIds = new HashSet<>();
        Set<Long> nursePricingDeviceReqIds = new HashSet<>();
        for (ChargeItem chargeItem : candidates) {
            if (chargeItem == null) {
                continue;
            }
            if (!GenerateSource.NURSE_PRICING.getCode().equals(chargeItem.getGenerateSourceEnum())
                && !GenerateSource.MEDICAL_TECH_PRICING.getCode().equals(chargeItem.getGenerateSourceEnum())) {
                continue;
            }
            if (chargeItem.getServiceId() == null) {
                continue;
            }
            if (TableNameConstants.WKF_SERVICE_REQUEST.equals(chargeItem.getServiceTable())) {
                nursePricingServiceReqIds.add(chargeItem.getServiceId());
            } else if (TableNameConstants.WKF_DEVICE_REQUEST.equals(chargeItem.getServiceTable())) {
                nursePricingDeviceReqIds.add(chargeItem.getServiceId());
            }
        }
        Map<Long, LocalDateTime> nursePricingRecordedByServiceReqId =
            this.procedureQueryService.mapNetCompletedRecordedTimeByServiceRequestIds(
                List.of(cmd.getEncounterId()), nursePricingServiceReqIds);
        Map<Long, LocalDateTime> deviceDisplayTimeByReqId =
            this.deviceDispenseQueryService.mapPrimaryDisplayTimeByDeviceReqIds(nursePricingDeviceReqIds);
        List<InpatientNurseStationCostDetailChargeRow> rows = new ArrayList<>();
        for (ChargeItem chargeItem : candidates) {
            if (chargeItem == null) {
                continue;
            }
            Optional<LocalDateTime> customerTimeOpt = this.resolveCustomerVisibleBusinessTime(
                chargeItem,
                netProcedureRecordedById,
                nursePricingRecordedByServiceReqId,
                deviceDisplayTimeByReqId);
            if (customerTimeOpt.isEmpty()) {
                continue;
            }
            LocalDateTime customerTime = customerTimeOpt.get();
            LocalDate customerDate = customerTime.toLocalDate();
            // 进窗：用户轴日期 ∈ [from, to]
            if (customerDate.isBefore(cmd.getBusinessDateFrom())) {
                continue;
            }
            if (customerDate.isAfter(cmd.getBusinessDateTo())) {
                continue;
            }
            // 列与排序同源：businessDateTime / displayDateTime 均为客户可见时刻
            rows.add(new InpatientNurseStationCostDetailChargeRow(
                chargeItem, customerTime, customerTime));
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
     * 解析客户可见业务时刻（进窗 / 排序 / 列展示同一值）。
     *
     * <p>有 Procedure → 净剩 {@code recorded_time}；护士划价耗材 → 发放展示；滚费 → occurrence。
     * 无法解析时 empty（静默跳过，不对用户回落 occurrence）。</p>
     *
     * @param chargeItem                         收费明细
     * @param netProcedureRecordedById           就诊净剩 Procedure → recorded
     * @param nursePricingRecordedByServiceReqId 护士划价诊疗 Request → recorded
     * @param deviceDisplayTimeByReqId           护士划价耗材 Request → 发放展示
     * @return 客户可见时刻；无法解析时 empty
     */
    private Optional<LocalDateTime> resolveCustomerVisibleBusinessTime(
        ChargeItem chargeItem,
        Map<Long, LocalDateTime> netProcedureRecordedById,
        Map<Long, LocalDateTime> nursePricingRecordedByServiceReqId,
        Map<Long, LocalDateTime> deviceDisplayTimeByReqId) {
        // 滚费：无 Procedure 客户轴，用账单 occurrence（落账日 00:00）
        if (GenerateSource.AUTO_ROLL_FEES.getCode().equals(chargeItem.getGenerateSourceEnum())) {
            if (chargeItem.getOccurrenceTime() == null) {
                throw new IllegalArgumentException(
                    "bill occurrence_time missing, chargeItemId=" + chargeItem.getId()
                        + ", generateSource=" + chargeItem.getGenerateSourceEnum());
            }
            return Optional.of(chargeItem.getOccurrenceTime());
        }
        // 护士/医技划价耗材：无 Procedure，客户轴 = 发放/拣配展示时刻
        if ((GenerateSource.NURSE_PRICING.getCode().equals(chargeItem.getGenerateSourceEnum())
                || GenerateSource.MEDICAL_TECH_PRICING.getCode().equals(chargeItem.getGenerateSourceEnum()))
            && TableNameConstants.WKF_DEVICE_REQUEST.equals(chargeItem.getServiceTable())) {
            if (deviceDisplayTimeByReqId != null && chargeItem.getServiceId() != null) {
                LocalDateTime dispenseDisplay = deviceDisplayTimeByReqId.get(chargeItem.getServiceId());
                if (dispenseDisplay != null) {
                    return Optional.of(dispenseDisplay);
                }
            }
            return Optional.empty();
        }
        // 优先本行 Procedure.recorded（禁止同 Request 兄弟行串日）
        Long procedureId = chargeItem.getProcedureId();
        if (procedureId != null && netProcedureRecordedById != null) {
            LocalDateTime recordedTime = netProcedureRecordedById.get(procedureId);
            if (recordedTime != null) {
                return Optional.of(recordedTime);
            }
            // 已对冲或无 recorded：不对用户回落 occurrence，静默跳过
            return Optional.empty();
        }
        // 护士划价诊疗缺 procedure_id：按 ServiceRequest 取净剩 recorded
        if (GenerateSource.NURSE_PRICING.getCode().equals(chargeItem.getGenerateSourceEnum())
            && TableNameConstants.WKF_SERVICE_REQUEST.equals(chargeItem.getServiceTable())
            && nursePricingRecordedByServiceReqId != null
            && chargeItem.getServiceId() != null) {
            LocalDateTime recordedByRequest = nursePricingRecordedByServiceReqId.get(chargeItem.getServiceId());
            if (recordedByRequest != null) {
                return Optional.of(recordedByRequest);
            }
        }
        // 医嘱路径缺 procedure_id：数据不完整
        if (!GenerateSource.NURSE_PRICING.getCode().equals(chargeItem.getGenerateSourceEnum())) {
            throw new IllegalArgumentException(
                "order charge missing procedure_id, chargeItemId=" + chargeItem.getId());
        }
        return Optional.empty();
    }
}
