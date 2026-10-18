/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.support;

import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.chargeitem.query.InpatientNurseStationCostDetailChargeRow;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.clinical.procedure.entity.Procedure;
import com.zxplat.domain.clinical.procedure.service.ProcedureQueryService;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 住院护士站费用明细读组装：收费明细 + 客户可见业务时间（B.6.1 跨 clinical 编排）。
 *
 * <p>用户轴（进窗 / 排序 / 列展示同一时刻）：有 Procedure → 净剩 {@code recorded_time}；
 * 护士/医技划价诊疗冲销留痕（Procedure 已对冲）→ 仍用原 COMPLETED {@code recorded_time}
 *（与耗材划价成对可见同构，AS-IP-NUR-006 **BR-IP-CDT-016** 例外 / **BR-IP-CDT-018**）；
 * 护士划价耗材 → 发放展示时刻；滚费无 Procedure 展示轴 → {@code occurrence_time}。
 * {@code occurrence_time} 仅后端逻辑/排障，不对用户过滤或展示（AS-IP-NUR-006）。</p>
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
     * <p>步骤：状态下推拉收费 → 预取净剩/划价冲销 recorded 与耗材发放时刻 → 按客户可见日闭区间进窗 → 倒序。</p>
     *
     * @param cmd 查询命令（{@code encounterId}、{@code businessDateFrom}、{@code businessDateTo} 必填）
     * @return 费用明细行；区间内无命中时返回空列表
     */
    public List<InpatientNurseStationCostDetailChargeRow> listInpatientNurseStationCostDetailChargeRows(
        ChargeItemQueryCommand cmd) {
        // Gate：缺就诊则无法按单就诊拉费用
        if (cmd == null || cmd.getEncounterId() == null) {
            throw new IllegalArgumentException("inpatient nurse station cost detail requires encounterId");
        }
        // Gate：业务日期闭区间必填（BR-IP-CDT-002）
        if (cmd.getBusinessDateFrom() == null || cmd.getBusinessDateTo() == null) {
            throw new IllegalArgumentException("inpatient nurse station cost detail requires businessDateFrom and businessDateTo");
        }
        // Gate：起日不得晚于止日
        if (cmd.getBusinessDateFrom().isAfter(cmd.getBusinessDateTo())) {
            throw new IllegalArgumentException("businessDateFrom must not be after businessDateTo");
        }
        // Pre：默认状态白名单（待结算/已结算/拟退/已退，BR-IP-CDT-004）
        if (cmd.getStatusEnumIn() == null || cmd.getStatusEnumIn().isEmpty()) {
            cmd.setStatusEnumIn(this.inpatientNurseStationCostDetailStatusCodes());
        }
        // Pre：排除挂号锚点账单（BR-IP-CDT-004）
        if (cmd.getExcludedServiceTable() == null || StringUtils.isEmpty(cmd.getExcludedServiceTable())) {
            cmd.setExcludedServiceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        }
        // Pre：已结算被 supersede 的行不下推（BR-IP-CDT-017）
        cmd.setExcludeSupersededSettledLines(true);
        // Pre：状态下推拉取候选 Charge
        List<ChargeItem> candidates = this.chargeItemQueryService.listChargeItemsByEncounterId(cmd);
        // Pre：就诊净剩 COMPLETED.recorded（医嘱执行 / 净剩划价诊疗主轴）
        Map<Long, LocalDateTime> netProcedureRecordedById =
            this.procedureQueryService.mapNetCompletedRecordedTimeByEncounterId(cmd.getEncounterId());
        // Pre：护士/医技划价按 Request 取 recorded；耗材按发放展示时刻（与 IF-IP-PRC-05/07 同轴）
        Set<Long> nursePricingServiceReqIds = new HashSet<>();
        Set<Long> nursePricingDeviceReqIds = new HashSet<>();
        // Pre：划价诊疗冲销后 Procedure 不在净剩 Map，收集 procedure_id 批量回查原 recorded
        Set<Long> hedgedPricingProcedureIds = new HashSet<>();
        for (ChargeItem chargeItem : candidates) {
            // 空行跳过，避免 NPE
            if (chargeItem == null) {
                continue;
            }
            // 仅划价来源需要按 Request / 对冲 Procedure 预取
            if (!this.isNurseOrMedicalTechPricingCharge(chargeItem)) {
                continue;
            }
            // 无服务锚点则无法按 Request 取展示轴
            if (chargeItem.getServiceId() == null) {
                continue;
            }
            // 诊疗划价：收集 SR 与已对冲 procedure_id
            if (TableNameConstants.WKF_SERVICE_REQUEST.equals(chargeItem.getServiceTable())) {
                nursePricingServiceReqIds.add(chargeItem.getServiceId());
                // 已对冲 COMPLETED：净剩索引无 recorded，须按 id 回查供冲销成对进窗
                if (chargeItem.getProcedureId() != null
                    && !netProcedureRecordedById.containsKey(chargeItem.getProcedureId())) {
                    hedgedPricingProcedureIds.add(chargeItem.getProcedureId());
                }
            } else if (TableNameConstants.WKF_DEVICE_REQUEST.equals(chargeItem.getServiceTable())) {
                // 耗材划价：收集 DeviceRequest 供发放展示时刻
                nursePricingDeviceReqIds.add(chargeItem.getServiceId());
            }
        }
        // Pre：划价诊疗缺 procedure_id 时按 SR 净剩 recorded 回落
        Map<Long, LocalDateTime> nursePricingRecordedByServiceReqId =
            this.procedureQueryService.mapNetCompletedRecordedTimeByServiceRequestIds(
                List.of(cmd.getEncounterId()), nursePricingServiceReqIds);
        // Pre：划价冲销留痕 procedure_id → 原 COMPLETED.recorded（含已对冲）
        Map<Long, LocalDateTime> pricingProcedureRecordedById =
            this.mapRecordedTimeByProcedureIds(hedgedPricingProcedureIds);
        // Pre：耗材发放/拣配展示时刻
        Map<Long, LocalDateTime> deviceDisplayTimeByReqId =
            this.deviceDispenseQueryService.mapPrimaryDisplayTimeByDeviceReqIds(nursePricingDeviceReqIds);
        List<InpatientNurseStationCostDetailChargeRow> rows = new ArrayList<>();
        for (ChargeItem chargeItem : candidates) {
            // 空行跳过
            if (chargeItem == null) {
                continue;
            }
            // Pre：解析客户可见业务时刻（进窗/排序/列同源）
            Optional<LocalDateTime> customerTimeOpt = this.resolveCustomerVisibleBusinessTime(
                chargeItem,
                netProcedureRecordedById,
                pricingProcedureRecordedById,
                nursePricingRecordedByServiceReqId,
                deviceDisplayTimeByReqId);
            // 无法解析客户轴：医嘱执行已对冲等静默跳过（BR-IP-CDT-016）
            if (customerTimeOpt.isEmpty()) {
                continue;
            }
            LocalDateTime customerTime = customerTimeOpt.get();
            LocalDate customerDate = customerTime.toLocalDate();
            // 进窗：用户轴日期早于区间起日 → 排除
            if (customerDate.isBefore(cmd.getBusinessDateFrom())) {
                continue;
            }
            // 进窗：用户轴日期晚于区间止日 → 排除
            if (customerDate.isAfter(cmd.getBusinessDateTo())) {
                continue;
            }
            // Asm：列与排序同源——businessDateTime / displayDateTime 均为客户可见时刻
            rows.add(new InpatientNurseStationCostDetailChargeRow(
                chargeItem, customerTime, customerTime));
        }
        // Asm：业务时间倒序（BR-IP-CDT-008）
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
     * <p>有 Procedure → 净剩 {@code recorded_time}；护士/医技划价诊疗冲销留痕 → 原 COMPLETED
     * {@code recorded_time}；护士划价耗材 → 发放展示；滚费 → occurrence。
     * 医嘱执行已对冲仍 empty（静默跳过，不对用户回落 occurrence）。</p>
     *
     * @param chargeItem                         收费明细
     * @param netProcedureRecordedById           就诊净剩 Procedure → recorded
     * @param pricingProcedureRecordedById       划价诊疗已对冲 Procedure → recorded（可空）
     * @param nursePricingRecordedByServiceReqId 护士划价诊疗 Request → recorded
     * @param deviceDisplayTimeByReqId           护士划价耗材 Request → 发放展示
     * @return 客户可见时刻；无法解析时 empty
     */
    private Optional<LocalDateTime> resolveCustomerVisibleBusinessTime(
        ChargeItem chargeItem,
        Map<Long, LocalDateTime> netProcedureRecordedById,
        Map<Long, LocalDateTime> pricingProcedureRecordedById,
        Map<Long, LocalDateTime> nursePricingRecordedByServiceReqId,
        Map<Long, LocalDateTime> deviceDisplayTimeByReqId) {
        // 滚费：无 Procedure 客户轴，用账单 occurrence（落账日 00:00）
        if (GenerateSource.AUTO_ROLL_FEES.getCode().equals(chargeItem.getGenerateSourceEnum())) {
            // 滚费缺 occurrence 无法进窗
            if (chargeItem.getOccurrenceTime() == null) {
                throw new IllegalArgumentException(
                    "bill occurrence_time missing, chargeItemId=" + chargeItem.getId()
                        + ", generateSource=" + chargeItem.getGenerateSourceEnum());
            }
            return Optional.of(chargeItem.getOccurrenceTime());
        }
        // 护士/医技划价耗材：无 Procedure，客户轴 = 发放/拣配展示时刻
        if (this.isNurseOrMedicalTechPricingCharge(chargeItem)
            && TableNameConstants.WKF_DEVICE_REQUEST.equals(chargeItem.getServiceTable())) {
            // 有 DeviceRequest 锚点时取发放展示时刻
            if (deviceDisplayTimeByReqId != null && chargeItem.getServiceId() != null) {
                LocalDateTime dispenseDisplay = deviceDisplayTimeByReqId.get(chargeItem.getServiceId());
                // 发放单已回写展示时刻 → 进窗
                if (dispenseDisplay != null) {
                    return Optional.of(dispenseDisplay);
                }
            }
            // 无发放展示轴：耗材划价行静默跳过
            return Optional.empty();
        }
        // 优先本行 Procedure.recorded（禁止同 Request 兄弟行串日）
        Long procedureId = chargeItem.getProcedureId();
        if (procedureId != null && netProcedureRecordedById != null) {
            LocalDateTime recordedTime = netProcedureRecordedById.get(procedureId);
            // 净剩 COMPLETED 命中：主轴进窗
            if (recordedTime != null) {
                return Optional.of(recordedTime);
            }
            // 划价诊疗冲销留痕：净剩已对冲，仍用原 COMPLETED.recorded 成对进窗（BR-IP-CDT-016/018）
            if (this.isNurseOrMedicalTechPricingCharge(chargeItem)
                && TableNameConstants.WKF_SERVICE_REQUEST.equals(chargeItem.getServiceTable())
                && pricingProcedureRecordedById != null) {
                LocalDateTime hedgedRecorded = pricingProcedureRecordedById.get(procedureId);
                // 回查到原划价 recorded → 正负成对同日可见
                if (hedgedRecorded != null) {
                    return Optional.of(hedgedRecorded);
                }
            }
            // 滚费 order-execute / 麻醉执行：recorded 缺失时回落 occurrence，避免有账却列表空白
            if (GenerateSource.isOrderExecuteFamilyCode(chargeItem.getGenerateSourceEnum())
                && chargeItem.getOccurrenceTime() != null) {
                return Optional.of(chargeItem.getOccurrenceTime());
            }
            // 医嘱执行已对冲或无 recorded：不对用户回落 occurrence，静默跳过
            return Optional.empty();
        }
        // 护士/医技划价诊疗缺 procedure_id：按 ServiceRequest 取净剩 recorded
        if (this.isNurseOrMedicalTechPricingCharge(chargeItem)
            && TableNameConstants.WKF_SERVICE_REQUEST.equals(chargeItem.getServiceTable())
            && nursePricingRecordedByServiceReqId != null
            && chargeItem.getServiceId() != null) {
            LocalDateTime recordedByRequest = nursePricingRecordedByServiceReqId.get(chargeItem.getServiceId());
            // SR 净剩 recorded 命中 → 进窗
            if (recordedByRequest != null) {
                return Optional.of(recordedByRequest);
            }
        }
        // 医嘱路径缺 procedure_id：数据不完整，整次失败由上层感知
        if (!this.isNurseOrMedicalTechPricingCharge(chargeItem)) {
            throw new IllegalArgumentException(
                "order charge missing procedure_id, chargeItemId=" + chargeItem.getId());
        }
        // 划价路径仍无法解析：静默跳过
        return Optional.empty();
    }

    /**
     * 是否护士/医技划价来源费用（冲销留痕行 generate_source 仍为本码）。
     *
     * @param chargeItem 收费明细
     * @return true 表示护士划价或医技划价
     */
    private boolean isNurseOrMedicalTechPricingCharge(ChargeItem chargeItem) {
        // 空实体不当划价来源
        if (chargeItem == null) {
            return false;
        }
        // 护士划价 / 麻醉划价（含冲销负向行）
        if (GenerateSource.isNurseStationPricingFamilyCode(chargeItem.getGenerateSourceEnum())) {
            return true;
        }
        // 医技划价同族冲销留痕
        return GenerateSource.MEDICAL_TECH_PRICING.getCode().equals(chargeItem.getGenerateSourceEnum());
    }

    /**
     * 按 Procedure 主键批量取 recorded（含已对冲 COMPLETED，供划价冲销成对进窗）。
     *
     * @param procedureIds 待回查主键；可空
     * @return procedureId → recorded_time；无 recorded 的行不入 Map
     */
    private Map<Long, LocalDateTime> mapRecordedTimeByProcedureIds(Set<Long> procedureIds) {
        // 空集：无对冲回查需求
        if (procedureIds == null || procedureIds.isEmpty()) {
            return Map.of();
        }
        // Pre：按 id 批量加载（含已对冲 COMPLETED，非净剩过滤）
        List<Procedure> procedures = this.procedureQueryService.listByIds(procedureIds);
        Map<Long, LocalDateTime> recordedById = new HashMap<>();
        for (Procedure procedure : procedures) {
            // 缺主键或 recorded 的行不入索引
            if (procedure == null || procedure.getId() == null || procedure.getRecordedTime() == null) {
                continue;
            }
            recordedById.put(procedure.getId(), procedure.getRecordedTime());
        }
        return recordedById;
    }
}
