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

package com.openhis.health.application.inpatient.nursestation.orderexecute.assembler;

import com.openhis.health.application.common.assembler.order.OrderCatalogDisplayAssembler;
import com.openhis.health.application.inpatient.nursestation.orderview.assembler.NurseStationOrderViewListAssembler;
import com.openhis.health.application.inpatient.nursestation.orderview.dto.NurseStationOrderViewDisplayContext;
import com.openhis.health.application.inpatient.nursestation.orderview.dto.NurseStationOrderViewDisplayCore;
import com.openhis.health.application.inpatient.nursestation.orderview.dto.NurseStationOrderViewListDetailIndex;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceOrderBindRowDto;
import com.openhis.health.application.inpatient.doctorstation.advice.query.InpatientDoctorStationAdviceOrderBindQueryService;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteBindItemDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecutePatientGroupDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteRowDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteSlotDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.mapper.NurseStationOrderExecuteMapper;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.custom.frequency.queries.model.FrequencyExecutionTimePointQueryModel;
import com.openhis.health.domain.custom.frequency.service.FrequencyQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.application.querysupport.order.dto.InpatientOrderListSkeleton;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.EventStatus;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院护士站医嘱执行列表装配（IF-IP-EXE-01 Asm，BR-IP-EXE-005～013）。
 *
 * <p>核心编排步骤：</p>
 * <ol>
 *   <li>剔除无 rateCode 长期医嘱；转科/出院/护理类诊疗整单排除（与 ATD-03 同源，不经执行槽位）</li>
 *   <li>停嘱（STOPPED）仍展示，状态「已停嘱」（BR-IP-EXE-002/003）</li>
 *   <li>长期：{@code FrequencyQueryService#computeExecutionTimePoints} 展开计划点（BR-IP-EXE-005）；临时：整单按净 COMPLETED 归并（BR-IP-EXE-006）</li>
 *   <li>长期 Procedure 按 occurrence 归并；临时不按 occurrence 与开立时刻并列 pending → pending/executed/skipped/cancelled 四类槽位（BR-IP-EXE-007～009）</li>
 *   <li>批量挂载 bindItems：药嘱按用法→耗材，诊嘱按 activity→子项（BR-IP-EXE-013）</li>
 *   <li>executeTab 收窄当前 Tab 槽位（医嘱行不变，BR-IP-EXE-010/012）</li>
 * </ol>
 *
 * <p>医嘱行展示字段委托 {@link NurseStationOrderViewListAssembler}。</p>
 *
 * <p>写路径 Gate（{@link #isPlanTimePending} / {@link #isPlanTimeAfterStopTime}）：
 * occurrence 防重 + 晚于停嘱拒绝；<b>不</b>用 executionDeadline、<b>不</b>约束 actualTime 与 planTime 关系。</p>
 */
@Component
@RequiredArgsConstructor
public class NurseStationOrderExecuteListAssembler {

    /** 医嘱查看列表行展示内核。 */
    private final NurseStationOrderViewListAssembler orderViewListAssembler;
    /** 频次计划点展开。 */
    private final FrequencyQueryService frequencyQueryService;
    /** Procedure 批量加载。 */
    private final ProcedureQueryService procedureQueryService;
    /** 绑定项模板查询。 */
    private final InpatientDoctorStationAdviceOrderBindQueryService orderBindQueryService;
    /** Tab 过滤辅助。 */
    private final NurseStationOrderExecuteMapper nurseStationOrderExecuteMapper;

    /**
     * OrderInfo 骨架 + 域明细 → 按就诊分组且组内已排序的列表（含槽位与 bindItems）。
     *
     * @param skeleton           过滤后的读模型行
     * @param medByEncounter     就诊 → 药品明细
     * @param svcByEncounter     就诊 → 诊疗明细
     * @param executionDeadline  截止时间
     * @param executeTab         执行 Tab
     * @param encounterOrgIds    就诊 → 机构主键（绑定模板预留）
     * @return 患者分组列表
     */
    public List<NurseStationOrderExecutePatientGroupDto> assemblePatientGroups(
        List<InpatientOrderListSkeleton> skeleton,
        Map<Long, List<MedicationRequestListQueryModel>> medByEncounter,
        Map<Long, List<ServiceRequestListModel>> svcByEncounter,
        LocalDateTime executionDeadline,
        String executeTab,
        Map<Long, Long> encounterOrgIds) {
        if (skeleton == null || skeleton.isEmpty()) {
            return new ArrayList<>();
        }
        NurseStationOrderViewListDetailIndex index =
            orderViewListAssembler.indexEncounterDetails(skeleton, medByEncounter, svcByEncounter);
        NurseStationOrderViewDisplayContext displayContext =
            orderViewListAssembler.prepareDisplayContext(index);
        Map<Long, List<NurseStationOrderExecuteRowDto>> rowsByEncounter = new LinkedHashMap<>();
        // 逐 OrderInfo 骨架行装配：展示字段 + 四类槽位 + bindItems + Tab 过滤
        for (InpatientOrderListSkeleton order : skeleton) {
            if (order == null || order.getEncounterId() == null) {
                continue;
            }
            // 剔除长期无频次、转科/出院/护理等特殊诊疗等不可执行行
            if (this.shouldExcludeExecuteRow(order, index.getMedDetailById(), index.getSvcDetailById())) {
                continue;
            }
            Long requestId = orderViewListAssembler.resolveRequestId(order);
            String requestTable = orderViewListAssembler.resolveRequestTable(order);
            // 按 request 锚点加载全部 Procedure，供槽位终态归并
            List<ProcedureRecordQueryModel> procedures = procedureQueryService.getProcedureRecords(requestId, requestTable);
            NurseStationOrderExecuteRowDto line = this.mapOrderRow(
                order,
                index.getMedDetailById().get(requestId),
                index.getSvcDetailById().get(requestId),
                displayContext,
                procedures,
                executionDeadline,
                encounterOrgIds);
            if (line == null) {
                continue;
            }
            // Tab 过滤：医嘱行始终保留，仅回传当前 Tab 且 planTime ≤ deadline 的计划点（BR-IP-EXE-010）
            nurseStationOrderExecuteMapper.applyExecuteTabSlotFilter(line, executeTab, executionDeadline);
            List<NurseStationOrderExecuteRowDto> bucket = rowsByEncounter.get(order.getEncounterId());
            if (bucket == null) {
                bucket = new ArrayList<>();
                rowsByEncounter.put(order.getEncounterId(), bucket);
            }
            bucket.add(line);
        }
        // 按就诊分组，组内长临+开立时间排序
        // 输出：每个 encounterId 一组，组内 orders 已排序
        List<NurseStationOrderExecutePatientGroupDto> groups = new ArrayList<>();
        for (Map.Entry<Long, List<NurseStationOrderExecuteRowDto>> entry : rowsByEncounter.entrySet()) {
            List<NurseStationOrderExecuteRowDto> sorted = this.sortRows(entry.getValue());
            NurseStationOrderExecutePatientGroupDto group = new NurseStationOrderExecutePatientGroupDto();
            group.setEncounterId(entry.getKey());
            group.setOrders(sorted);
            groups.add(group);
        }
        return groups;
    }

    /**
     * 写路径 occurrence 防重（BR-IP-EXE-008 / EX-IP-EXE-301）。
     *
     * <p>{@code planTime} 即 Procedure {@code occurrence_time}（如 22 日 8:00、16:00），仅标识「哪一次计划」；
     * 护士何时点击（{@code recorded_time} / actualTime）与此无关。**临时嘱**整单判定：存在净 COMPLETED 或末态 STOPPED/CANCEL 则不可写。</p>
     *
     * @param order       医嘱读模型
     * @param medDetail   药品明细；非药嘱可 null
     * @param svcDetail   诊疗明细；非诊嘱可 null
     * @param planTime    occurrence 锚点
     * @param procedures  已加载 Procedure 行
     * @return true 表示该 occurrence 尚未被终态占用
     */
    public boolean isPlanTimePending(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        LocalDateTime planTime,
        List<ProcedureRecordQueryModel> procedures) {
        if (order == null || planTime == null) {
            return false;
        }
        if (this.shouldExcludeExecuteRowForWrite(order, medDetail, svcDetail)) {
            return false;
        }
        // 临时嘱：整单维度判定是否仍有可写槽位，不按 occurrence 与开立时刻对齐（BR-IP-EXE-006）
        String therapy = this.resolveTherapyEnum(order, medDetail, svcDetail);
        if (TherapyType.TEMPORARY.getCode().equals(therapy)) {
            ProcedureRecordQueryModel lastNetCompleted = this.pickLastNetCompletedProcedure(procedures);
            if (lastNetCompleted != null) {
                return false;
            }
            ProcedureRecordQueryModel netFinal = this.pickNetFinalProcedureRecord(procedures);
            if (netFinal == null) {
                return true;
            }
            String status = netFinal.getStatusEnum();
            if (EventStatus.STOPPED.getCode().equals(status)) {
                return false;
            }
            if (EventStatus.isCancelExecutionStatusCode(status)) {
                return false;
            }
            if (EventStatus.COMPLETED.getCode().equals(status)) {
                return false;
            }
            return true;
        }
        Map<LocalDateTime, List<ProcedureRecordQueryModel>> byOccurrence = this.groupProceduresByOccurrence(procedures);
     List<ProcedureRecordQueryModel> group = null;
        for (Map.Entry<LocalDateTime, List<ProcedureRecordQueryModel>> entry : byOccurrence.entrySet()) {
            if (entry.getKey() != null && this.planTimesEqual(entry.getKey(), planTime)) {
                group = entry.getValue();
                break;
            }
        }
        if (group == null || group.isEmpty()) {
            // 该 occurrence 尚无 Procedure → 可写（含补录 21 日、提前 23 日等）
            return true;
        }
           ProcedureRecordQueryModel finalRecord = this.pickFinalProcedureRecord(group);
        if (finalRecord == null) {
            return true;
        }
        String status = finalRecord.getStatusEnum();
        if (EventStatus.COMPLETED.getCode().equals(status)) {
            if (this.isProcedureOffsetByCancel(procedures, finalRecord.getId())) {
                // 已执行但被取消对冲 → 不可再次执行
                return false;
            }
            // 已执行完成 → EX-IP-EXE-301
            return false;
        }
        if (EventStatus.STOPPED.getCode().equals(status)) {
            // 已登记不执行
            return false;
        }
        if (EventStatus.isCancelExecutionStatusCode(status)) {
            return false;
        }
        // 进行中等非终态 → 仍允许写路径继续（领域层 skipDuplicate 幂等）
        return true;
    }

    /**
     * 写 Gate：planTime（occurrence）是否晚于停嘱时刻。
     *
     * <p>写路径唯一的时间边界校验；与 actualTime、executionDeadline、当前日期无关。</p>
     */
    public boolean isPlanTimeAfterStopTime(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        LocalDateTime planTime) {
        if (planTime == null) {
            return false;
        }
        LocalDateTime stopTime = this.resolveOrderStopTime(order, medDetail, svcDetail);
        return stopTime != null && planTime.isAfter(stopTime);
    }

    /**
     * 解析停嘱右边界；未停嘱返回 null。
     */
    private LocalDateTime resolveOrderStopTime(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail) {
        if (order == null) {
            return null;
        }
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            LocalDateTime stopTime = null;
            if (medDetail != null
                && RequestStatus.STOPPED.getCode().equals(medDetail.getStatusEnum())
                && medDetail.getEffectiveDoseEnd() != null) {
                stopTime = medDetail.getEffectiveDoseEnd();
            }
            if (RequestStatus.STOPPED.getCode().equals(order.getStatusEnum())
                && order.getEffectiveDoseEnd() != null) {
                if (stopTime == null || order.getEffectiveDoseEnd().isBefore(stopTime)) {
                    stopTime = order.getEffectiveDoseEnd();
                }
            }
            return stopTime;
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())
            && svcDetail != null
            && RequestStatus.STOPPED.getCode().equals(svcDetail.getStatusEnum())
            && svcDetail.getOccurrenceEndTime() != null) {
            return svcDetail.getOccurrenceEndTime();
        }
        return null;
    }

    /**
     * 写 Gate 用：剔除长期无频次等不可执行医嘱（不停嘱整单拒绝，停嘱边界见 {@link #isPlanTimeAfterStopTime}）。
     */
    private boolean shouldExcludeExecuteRowForWrite(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail) {
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            String serviceCategory = orderViewListAssembler.resolveServiceCategoryEnum(order, svcDetail);
            if (orderViewListAssembler.isExcludedFromNurseStationExecuteList(serviceCategory)) {
                return true;
            }
        }
        String therapy = order.getTherapyEnum();
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            if (medDetail != null && StringUtils.isNotBlank(medDetail.getTherapyEnum())) {
                therapy = medDetail.getTherapyEnum();
            }
            if (TherapyType.LONG_TERM.getCode().equals(therapy)) {
                String rateCode = null;
                if (medDetail != null && StringUtils.isNotBlank(medDetail.getRateCode())) {
                    rateCode = medDetail.getRateCode();
                }
                if (StringUtils.isBlank(rateCode)) {
                    return true;
                }
            }
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            ServiceRequestListModel svc = svcDetail;
            if (svc != null && StringUtils.isNotBlank(svc.getTherapyEnum())) {
                therapy = svc.getTherapyEnum();
            }
            if (TherapyType.LONG_TERM.getCode().equals(therapy)) {
                String rateCode = null;
                if (svc != null && StringUtils.isNotBlank(svc.getRateCode())) {
                    rateCode = svc.getRateCode();
                }
                if (StringUtils.isBlank(rateCode)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * planTime 比对（秒级，避免 JSON 序列化丢纳秒导致 Gate 误判）。
     */
    private boolean planTimesEqual(LocalDateTime left, LocalDateTime right) {
        if (left == null || right == null) {
            return false;
        }
        LocalDateTime normalizedLeft = left.withNano(0);
        LocalDateTime normalizedRight = right.withNano(0);
        return normalizedLeft.equals(normalizedRight);
    }

    /**
     * 剔除无 rateCode 长期、转科/出院/护理等特殊诊疗等（BR-IP-EXE-003）；已停嘱医嘱仍保留在执行列表展示。
     */
    private boolean shouldExcludeExecuteRow(
        InpatientOrderListSkeleton order,
        Map<Long, MedicationRequestListQueryModel> medDetailById,
        Map<Long, ServiceRequestListModel> svcDetailById) {
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            ServiceRequestListModel svc = svcDetailById.get(order.getOrderId());
            String serviceCategory = orderViewListAssembler.resolveServiceCategoryEnum(order, svc);
            if (orderViewListAssembler.isExcludedFromNurseStationExecuteList(serviceCategory)) {
                return true;
            }
        }
        String therapy = order.getTherapyEnum();
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            MedicationRequestListQueryModel med = medDetailById.get(order.getMedReqId());
            if (med != null && StringUtils.isNotBlank(med.getTherapyEnum())) {
                therapy = med.getTherapyEnum();
            }
            if (TherapyType.LONG_TERM.getCode().equals(therapy)) {
                String rateCode = null;
                if (med != null && StringUtils.isNotBlank(med.getRateCode())) {
                    rateCode = med.getRateCode();
                }
                if (StringUtils.isBlank(rateCode)) {
                    // 长期药嘱无频次无法展开计划点（BR-IP-EXE-002）
                    return true;
                }
            }
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            ServiceRequestListModel svc = svcDetailById.get(order.getOrderId());
            if (svc != null && StringUtils.isNotBlank(svc.getTherapyEnum())) {
                therapy = svc.getTherapyEnum();
            }
            if (TherapyType.LONG_TERM.getCode().equals(therapy)) {
                String rateCode = null;
                if (svc != null && StringUtils.isNotBlank(svc.getRateCode())) {
                    rateCode = svc.getRateCode();
                }
                if (StringUtils.isBlank(rateCode)) {
                    // 长期诊嘱无频次同理剔除（BR-IP-EXE-003）
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 单条 OrderInfo → 执行列表行（含四类槽位 + bindItems）。
     *
     * @param encounterOrgIds 就诊→机构，供 bindItems 模板 org 收窄
     */
    private NurseStationOrderExecuteRowDto mapOrderRow(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        NurseStationOrderViewDisplayContext displayContext,
        List<ProcedureRecordQueryModel> procedures,
        LocalDateTime executionDeadline,
        Map<Long, Long> encounterOrgIds) {
        NurseStationOrderViewDisplayCore core;
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            core = orderViewListAssembler.buildMedicationDisplayCore(order, medDetail, displayContext);
        } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            core = orderViewListAssembler.buildServiceDisplayCore(order, svcDetail, displayContext);
        } else {
            // 本期列表仅药/诊，其他 orderType 跳过
            return null;
        }
        if (core == null) {
            return null;
        }
        NurseStationOrderExecuteRowDto line = new NurseStationOrderExecuteRowDto();
        this.applyViewCoreToExecuteRow(core, line);
        // 频次计划点 + Procedure 归并 → 四类槽位
        SlotBuckets buckets = this.buildSlotBuckets(order, medDetail, svcDetail, procedures, executionDeadline);
        line.setPendingSlots(buckets.pendingSlots);
        line.setExecutedSlots(buckets.executedSlots);
        line.setSkippedSlots(buckets.skippedSlots);
        line.setCancelledSlots(buckets.cancelledSlots);
        Long orgId = null;
        if (encounterOrgIds != null) {
            orgId = encounterOrgIds.get(order.getEncounterId());
        }
        // bindItems 供前端勾选；写路径按勾选项派生
        line.setBindItems(this.loadBindItems(order, medDetail, svcDetail, orgId, displayContext.getUnitLabelByCode()));
        return line;
    }

    /**
     * 医嘱查看展示内核 → 执行列表行 DTO（不含槽位与 bindItems）。
     */
    private void applyViewCoreToExecuteRow(NurseStationOrderViewDisplayCore core, NurseStationOrderExecuteRowDto line) {
        line.setListRowKey(core.getListRowKey());
        line.setOrderId(core.getRequestId());
        line.setOrderType(core.getOrderType());
        line.setEncounterId(core.getEncounterId());
        line.setPatientId(core.getPatientId());
        line.setStatusEnum(core.getStatusEnum());
        line.setStatusEnumText(core.getStatusDisplay());
        line.setTherapyEnum(core.getTherapyEnum());
        line.setTherapyEnumText(core.getTherapyDisplay());
        line.setAdviceDisplayName(core.getAdviceDisplayName());
        line.setSpecificationText(core.getSpecificationText());
        line.setDosageDisplay(core.getDosageDisplay());
        line.setUsageDisplay(core.getUsageDisplay());
        line.setFrequencyDisplay(core.getFrequencyDisplay());
        line.setAuthoredTime(core.getAuthoredTime());
        line.setStopTime(core.getStopTime());
        line.setRequesterName(core.getRequesterName());
        line.setGroupId(core.getGroupId());
    }

    /**
     * 槽位四类分桶（BR-IP-EXE-007～009）。
     *
     * <p>计划点集合 = 频次展开 ∪ Procedure 已有 occurrence；无 Procedure 覆盖的计划点 → pending；
     * 有 Procedure 时取组内最后一条为终态，COMPLETED 可能被 CANCEL 对冲归入 cancelled。</p>
     */
    private SlotBuckets buildSlotBuckets(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        List<ProcedureRecordQueryModel> procedures,
        LocalDateTime executionDeadline) {
        String therapy = this.resolveTherapyEnum(order, medDetail, svcDetail);
        LocalDateTime authoredTime = this.resolveAuthoredTime(order, medDetail, svcDetail);
        if (TherapyType.TEMPORARY.getCode().equals(therapy)) {
            // 临时嘱整单归并：有净执行记录即已执行，不再按 occurrence 与开立时刻并列 pending（BR-IP-EXE-006）
            return this.buildTemporarySlotBuckets(procedures, authoredTime);
        }
        SlotBuckets buckets = new SlotBuckets();
        buckets.pendingSlots = new ArrayList<>();
        buckets.executedSlots = new ArrayList<>();
        buckets.skippedSlots = new ArrayList<>();
        buckets.cancelledSlots = new ArrayList<>();
        // 长期频次展开
        Set<LocalDateTime> plannedTimes = this.resolvePlannedOccurrenceTimes(order, medDetail, svcDetail, executionDeadline);
        Map<LocalDateTime, List<ProcedureRecordQueryModel>> byOccurrence = this.groupProceduresByOccurrence(procedures);
        Set<LocalDateTime> allOccurrences = new LinkedHashSet<>(plannedTimes);
        allOccurrences.addAll(byOccurrence.keySet());
        for (LocalDateTime occurrence : allOccurrences) {
            if (occurrence == null) {
                continue;
            }
            List<ProcedureRecordQueryModel> group = byOccurrence.get(occurrence);
            if (group == null || group.isEmpty()) {
                // 频次计划点尚无 Procedure 记录 → 待执行
                if (plannedTimes.contains(occurrence)) {
                    NurseStationOrderExecuteSlotDto pending = this.buildPendingSlot(occurrence);
                    buckets.pendingSlots.add(pending);
                }
                continue;
            }
            // 同一 occurrence 可能有多条 Procedure（重试/取消），取 id 最大为终态
            ProcedureRecordQueryModel finalRecord = this.pickFinalProcedureRecord(group);
            NurseStationOrderExecuteSlotDto slot = this.toSlotDto(finalRecord, occurrence);
            if (slot == null) {
                continue;
            }
            String status = finalRecord.getStatusEnum();
            if (EventStatus.COMPLETED.getCode().equals(status)) {
                // COMPLETED 若被后续 CANCEL 对冲，展示为 cancelled 而非 executed
                if (this.isProcedureOffsetByCancel(procedures, finalRecord.getId())) {
                    buckets.cancelledSlots.add(slot);
                } else {
                    buckets.executedSlots.add(slot);
                }
            } else if (EventStatus.STOPPED.getCode().equals(status)) {
                buckets.skippedSlots.add(slot);
            } else if (EventStatus.isCancelExecutionStatusCode(status)) {
                buckets.cancelledSlots.add(slot);
            } else if (plannedTimes.contains(occurrence)) {
                // 进行中/其他非终态且仍在计划集合内 → 仍视为 pending
                buckets.pendingSlots.add(this.buildPendingSlot(occurrence));
            }
        }
        // 各桶内按 planTime 升序，便于护士按时间顺序操作
        this.sortSlots(buckets.pendingSlots);
        this.sortSlots(buckets.executedSlots);
        this.sortSlots(buckets.skippedSlots);
        this.sortSlots(buckets.cancelledSlots);
        return buckets;
    }

    /**
     * 临时嘱槽位分桶（BR-IP-EXE-006～008）。
     *
     * <p>整单仅一个逻辑槽位：存在净 COMPLETED → executed（展示最后一次净执行的 {@code recorded_time}）；
     * 否则末态 STOPPED/CANCEL 归入对应桶；无终态 Procedure 时 pending 仍为开立时刻。</p>
     *
     * @param procedures   该 request 下全部 Procedure
     * @param authoredTime 开立时刻，待执行 planTime
     * @return 四类槽位分桶
     */
    private SlotBuckets buildTemporarySlotBuckets(
        List<ProcedureRecordQueryModel> procedures,
        LocalDateTime authoredTime) {
        SlotBuckets buckets = new SlotBuckets();
        buckets.pendingSlots = new ArrayList<>();
        buckets.executedSlots = new ArrayList<>();
        buckets.skippedSlots = new ArrayList<>();
        buckets.cancelledSlots = new ArrayList<>();
        ProcedureRecordQueryModel lastNetCompleted = this.pickLastNetCompletedProcedure(procedures);
        if (lastNetCompleted != null) {
            // 有净执行记录 → 已执行 Tab 展示最后一次净执行时刻
            buckets.executedSlots.add(this.toTemporaryExecutedSlotDto(lastNetCompleted));
            return buckets;
        }
        ProcedureRecordQueryModel netFinal = this.pickNetFinalProcedureRecord(procedures);
        if (netFinal == null) {
            if (authoredTime != null) {
                buckets.pendingSlots.add(this.buildPendingSlot(authoredTime));
            }
            return buckets;
        }
        String status = netFinal.getStatusEnum();
        if (EventStatus.STOPPED.getCode().equals(status)) {
            buckets.skippedSlots.add(this.toTemporaryExecutedSlotDto(netFinal));
            return buckets;
        }
        if (EventStatus.isCancelExecutionStatusCode(status)) {
            buckets.cancelledSlots.add(this.toTemporaryExecutedSlotDto(netFinal));
            return buckets;
        }
        if (EventStatus.COMPLETED.getCode().equals(status)) {
            if (this.isProcedureOffsetByCancel(procedures, netFinal.getId())) {
                buckets.cancelledSlots.add(this.toTemporaryExecutedSlotDto(netFinal));
            } else {
                buckets.executedSlots.add(this.toTemporaryExecutedSlotDto(netFinal));
            }
            return buckets;
        }
        // 进行中/其他非终态 → 仍展示为待执行（开立时刻）
        if (authoredTime != null) {
            buckets.pendingSlots.add(this.buildPendingSlot(authoredTime));
        }
        return buckets;
    }

    /**
     * 长期频次展开 / 临时单槽（BR-IP-EXE-004/005/006）。
     *
     * <p>列表窗口：左边界 = 开立时间 {@code authored_time} 所在自然日 00:00:00（不按开立时分裁剪当日计划点）；
     * 右边界 = {@code min(停嘱时刻, executionDeadline)}；长期计划点仅委托
     * {@link FrequencyQueryService#computeExecutionTimePoints}。</p>
     */
    private Set<LocalDateTime> resolvePlannedOccurrenceTimes(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        LocalDateTime executionDeadline) {
        Set<LocalDateTime> planned = new LinkedHashSet<>();
        if (executionDeadline == null) {
            return planned;
        }
        LocalDateTime authoredTime = this.resolveAuthoredTime(order, medDetail, svcDetail);
        // 默认取自 OrderInfo 骨架，域明细非空时覆盖长临/停嘱/频次
        String therapy = this.resolveTherapyEnum(order, medDetail, svcDetail);
        String statusEnum = order.getStatusEnum();
        LocalDateTime end = executionDeadline;
        String rateCode = null;
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            if (medDetail != null) {
                if (StringUtils.isNotBlank(medDetail.getStatusEnum())) {
                    statusEnum = medDetail.getStatusEnum();
                }
                // 右边界：仅停嘱（STOPPED）时取 effective_dose_end，禁止与 check_time 混用
                if (RequestStatus.STOPPED.getCode().equals(medDetail.getStatusEnum())
                    && medDetail.getEffectiveDoseEnd() != null) {
                    end = medDetail.getEffectiveDoseEnd();
                }
                if (StringUtils.isNotBlank(medDetail.getRateCode())) {
                    rateCode = medDetail.getRateCode();
                }
            }
            if (RequestStatus.STOPPED.getCode().equals(statusEnum)
                && order.getEffectiveDoseEnd() != null
                && order.getEffectiveDoseEnd().isBefore(end)) {
                // 读模型骨架上的停嘱时间进一步收窄展开上界
                end = order.getEffectiveDoseEnd();
            }
        } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            if (svcDetail != null) {
                if (StringUtils.isNotBlank(svcDetail.getStatusEnum())) {
                    statusEnum = svcDetail.getStatusEnum();
                }
                // 诊嘱右边界：仅 STOPPED 时取 occurrence_end_time
                if (RequestStatus.STOPPED.getCode().equals(svcDetail.getStatusEnum())
                    && svcDetail.getOccurrenceEndTime() != null) {
                    end = svcDetail.getOccurrenceEndTime();
                }
                if (StringUtils.isNotBlank(svcDetail.getRateCode())) {
                    rateCode = svcDetail.getRateCode();
                }
            }
        }
        if (end.isAfter(executionDeadline)) {
            // 列表窗口上界不超过用户选择的 executionDeadline
            end = executionDeadline;
        }
        if (TherapyType.TEMPORARY.getCode().equals(therapy)) {
            // 临时医嘱：仅一个计划点 = 开立时刻（完整时分秒）
            if (authoredTime != null) {
                planned.add(authoredTime);
            }
            return planned;
        }
        if (authoredTime == null) {
            return planned;
        }
        // 长期：左边界取开立自然日 00:00:00，使开立当日频次时点（如 08:00）不因开立时分晚于该点而被裁掉
        LocalDateTime expandStart = authoredTime.toLocalDate().atStartOfDay();
        if (StringUtils.isBlank(rateCode)) {
            return planned;
        }
        if (!end.isAfter(expandStart)) {
            // 无效时间窗：截止不晚于开立日零点
            return planned;
        }
        try {
            // 长期：唯一入口 FrequencyQueryService#computeExecutionTimePoints（字段驱动）
            List<FrequencyExecutionTimePointQueryModel> points =
                frequencyQueryService.computeExecutionTimePoints(rateCode, expandStart, end);
            for (FrequencyExecutionTimePointQueryModel point : points) {
                if (point != null && point.getExecutionAt() != null) {
                    planned.add(point.getExecutionAt());
                }
            }
        } catch (BusinessException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_EXE_201,
                "医嘱引用频次不可用");
        }
        return planned;
    }

    /**
     * 解析医嘱开立时刻，供列表频次展开窗口左边界使用。
     *
     * <p>药嘱优先 {@code req_authored_time}（域明细 / OrderInfo {@code med_authored_time}）；
     * 诊嘱优先 {@code authored_time}（域明细 / OrderInfo 骨架）。</p>
     *
     * @param order     OrderInfo 骨架行
     * @param medDetail 药品明细，可空
     * @param svcDetail 诊疗明细，可空
     * @return 开立时刻；无法解析时返回 null
     */
    private LocalDateTime resolveAuthoredTime(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail) {
        LocalDateTime authoredTime = null;
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            if (medDetail != null && medDetail.getReqAuthoredTime() != null) {
                authoredTime = medDetail.getReqAuthoredTime();
            }
            if (authoredTime == null && order.getMedAuthoredTime() != null) {
                authoredTime = order.getMedAuthoredTime();
            }
            return authoredTime;
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            if (svcDetail != null && svcDetail.getReqAuthoredTime() != null) {
                authoredTime = svcDetail.getReqAuthoredTime();
            }
            if (authoredTime == null && order.getAuthoredTime() != null) {
                authoredTime = order.getAuthoredTime();
            }
            return authoredTime;
        }
        return order.getAuthoredTime();
    }

    /**
     * 解析医嘱长临 code；域明细优先，回落 OrderInfo 骨架。
     *
     * @param order     OrderInfo 骨架行
     * @param medDetail 药品明细，可空
     * @param svcDetail 诊疗明细，可空
     * @return TherapyType code；无法解析时返回 null
     */
    private String resolveTherapyEnum(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail) {
        if (order == null) {
            return null;
        }
        String therapy = order.getTherapyEnum();
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            if (medDetail != null && StringUtils.isNotBlank(medDetail.getTherapyEnum())) {
                therapy = medDetail.getTherapyEnum();
            }
            return therapy;
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            if (svcDetail != null && StringUtils.isNotBlank(svcDetail.getTherapyEnum())) {
                therapy = svcDetail.getTherapyEnum();
            }
            return therapy;
        }
        return therapy;
    }

    /**
     * 临时嘱：取 id 最大的一条 Procedure 作为整单末态（BR-IP-EXE-007 临时口径）。
     *
     * @param procedures 该 request 下全部 Procedure
     * @return 末态记录；无记录时 null
     */
    private ProcedureRecordQueryModel pickNetFinalProcedureRecord(List<ProcedureRecordQueryModel> procedures) {
        if (procedures == null || procedures.isEmpty()) {
            return null;
        }
        ProcedureRecordQueryModel netFinal = null;
        for (ProcedureRecordQueryModel row : procedures) {
            if (row == null) {
                continue;
            }
            if (netFinal == null) {
                netFinal = row;
                continue;
            }
            Long rowId = row.getId();
            Long finalId = netFinal.getId();
            if (rowId != null && (finalId == null || rowId.compareTo(finalId) > 0)) {
                netFinal = row;
            }
        }
        return netFinal;
    }

    /**
     * 临时嘱：取最后一条未被 CANCEL 对冲的 COMPLETED 记录（净执行）。
     *
     * @param procedures 该 request 下全部 Procedure
     * @return 净 COMPLETED；不存在时 null
     */
    private ProcedureRecordQueryModel pickLastNetCompletedProcedure(List<ProcedureRecordQueryModel> procedures) {
        if (procedures == null || procedures.isEmpty()) {
            return null;
        }
        ProcedureRecordQueryModel lastNet = null;
        for (ProcedureRecordQueryModel row : procedures) {
            if (row == null) {
                continue;
            }
            if (!EventStatus.COMPLETED.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            if (this.isProcedureOffsetByCancel(procedures, row.getId())) {
                continue;
            }
            if (lastNet == null) {
                lastNet = row;
                continue;
            }
            Long rowId = row.getId();
            Long lastId = lastNet.getId();
            if (rowId != null && (lastId == null || rowId.compareTo(lastId) > 0)) {
                lastNet = row;
            }
        }
        return lastNet;
    }

    /**
     * 临时嘱已执行/不执行/取消槽位：planTime 取净执行 {@code recorded_time}（回落 occurrence）。
     *
     * @param record 净末态 Procedure
     * @return 槽位 DTO
     */
    private NurseStationOrderExecuteSlotDto toTemporaryExecutedSlotDto(ProcedureRecordQueryModel record) {
        LocalDateTime displayTime = record.getRecordedTime();
        if (displayTime == null) {
            displayTime = record.getOccurrenceTime();
        }
        return this.toSlotDto(record, displayTime);
    }

    /**
     * Procedure 按 occurrence_time 分组（BR-IP-EXE-007，仅长期）。
     */
    private Map<LocalDateTime, List<ProcedureRecordQueryModel>> groupProceduresByOccurrence(
        List<ProcedureRecordQueryModel> procedures) {
        Map<LocalDateTime, List<ProcedureRecordQueryModel>> grouped = new HashMap<>();
        if (procedures == null) {
            return grouped;
        }
        for (ProcedureRecordQueryModel row : procedures) {
            if (row == null || row.getOccurrenceTime() == null) {
                // 无 occurrence 的 Procedure 不参与槽位归并
                continue;
            }
            List<ProcedureRecordQueryModel> bucket = grouped.get(row.getOccurrenceTime());
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(row.getOccurrenceTime(), bucket);
            }
            bucket.add(row);
        }
        return grouped;
    }

    /**
     * 组内取最后一条为槽位终态（BR-IP-EXE-007）。
     */
    private ProcedureRecordQueryModel pickFinalProcedureRecord(List<ProcedureRecordQueryModel> group) {
        List<ProcedureRecordQueryModel> copy = new ArrayList<>(group);
        // id 最大 = 时间上最后写入的记录，作为该 planTime 的展示终态
        copy.sort(Comparator.comparing(ProcedureRecordQueryModel::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return copy.get(copy.size() - 1);
    }

    /**
     * COMPLETED 是否已被 CANCEL 对冲（BR-NUR-005）。
     */
    private boolean isProcedureOffsetByCancel(List<ProcedureRecordQueryModel> procedures, Long completedProcedureId) {
        if (procedures == null || completedProcedureId == null) {
            return false;
        }
        for (ProcedureRecordQueryModel sibling : procedures) {
            if (sibling == null) {
                continue;
            }
            if (!EventStatus.isCancelExecutionStatusCode(sibling.getStatusEnum())) {
                continue;
            }
            if (completedProcedureId.equals(sibling.getRefundId())) {
                // 存在 CANCEL 记录指向该 COMPLETED → UI 展示为 cancelled
                return true;
            }
        }
        return false;
    }

    /**
     * 构建 PREPARATION 态 pending 槽位 DTO（无 procedureId）。
     */
    private NurseStationOrderExecuteSlotDto buildPendingSlot(LocalDateTime planTime) {
        NurseStationOrderExecuteSlotDto slot = new NurseStationOrderExecuteSlotDto();
        slot.setPlanTime(planTime);
        slot.setStatusEnum(EventStatus.PREPARATION.getCode());
        EventStatus status = EnumUtil.fromCode(EventStatus.class, slot.getStatusEnum());
        if (status != null) {
            // 待执行槽位展示「准备中」等人读文案
            slot.setStatusEnumText(status.getDisplay());
        }
        return slot;
    }

    /**
     * Procedure 记录 → 槽位 DTO（含 procedureId、status、recordedTime）。
     */
    private NurseStationOrderExecuteSlotDto toSlotDto(ProcedureRecordQueryModel record, LocalDateTime planTime) {
        if (record == null) {
            return null;
        }
        NurseStationOrderExecuteSlotDto slot = new NurseStationOrderExecuteSlotDto();
        slot.setPlanTime(planTime);
        slot.setProcedureId(record.getId());
        slot.setStatusEnum(record.getStatusEnum());
        EventStatus status = EnumUtil.fromCode(EventStatus.class, record.getStatusEnum());
        if (status != null) {
            slot.setStatusEnumText(status.getDisplay());
        }
        // recordedTime 供已执行/不执行 Tab 展示护士实际操作时刻
        slot.setRecordedTime(record.getRecordedTime());
        return slot;
    }

    /**
     * 槽位列表按 planTime 升序排序。
     */
    private void sortSlots(List<NurseStationOrderExecuteSlotDto> slots) {
        if (slots == null || slots.isEmpty()) {
            return;
        }
        slots.sort(Comparator.comparing(NurseStationOrderExecuteSlotDto::getPlanTime,
            Comparator.nullsLast(Comparator.naturalOrder())));
    }

    /**
     * 批量挂载 bindItems（BR-IP-EXE-013）。
     *
     * <p>药嘱：按用法 methodCode 查 USAGE_TO_DEVICE 绑定模板；
     * 诊嘱：按 activityId 查 ACTIVITY_CHILD 子项（按 targetItemTable 区分诊疗/耗材）。</p>
     *
     * @param organizationUnitId 机构主键，模板查询 org 收窄；可 null
     */
    private List<NurseStationOrderExecuteBindItemDto> loadBindItems(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        Long organizationUnitId,
        Map<String, String> unitLabelByCode) {
        List<NurseStationOrderExecuteBindItemDto> bindItems = new ArrayList<>();
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            // 药嘱：用法 → 绑定耗材/诊疗（methodCode 须为数字概念 id，非数字术语 code 暂无法解析）
            Long usageConceptId = this.resolveUsageConceptId(medDetail);
            if (usageConceptId != null) {
                List<InpatientDoctorStationAdviceOrderBindRowDto> rows = orderBindQueryService.listBindings(
                    DoctorStationAdviceConstants.BIND_TYPE_USAGE_TO_DEVICE, usageConceptId, organizationUnitId);
                bindItems.addAll(this.mapBindRows(rows, unitLabelByCode));
            }
        } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            // 诊嘱：活动定义 → 子绑定项
            Long activityId = order.getActivityId();
            if (svcDetail != null && svcDetail.getActivityId() != null) {
                activityId = svcDetail.getActivityId();
            }
            if (activityId != null) {
                List<InpatientDoctorStationAdviceOrderBindRowDto> rows = orderBindQueryService.listBindings(
                    DoctorStationAdviceConstants.BIND_TYPE_ACTIVITY_CHILD, activityId, organizationUnitId);
                bindItems.addAll(this.mapBindRows(rows, unitLabelByCode));
            }
        }
        return bindItems;
    }

    /**
     * 绑定模板行 → 执行 bindItem DTO；按 bindType/targetItemTable 回填 activityId 或 deviceId。
     */
    private List<NurseStationOrderExecuteBindItemDto> mapBindRows(
        List<InpatientDoctorStationAdviceOrderBindRowDto> rows,
        Map<String, String> unitLabelByCode) {
        List<NurseStationOrderExecuteBindItemDto> mapped = new ArrayList<>();
        if (rows == null) {
            return mapped;
        }
        for (InpatientDoctorStationAdviceOrderBindRowDto row : rows) {
            if (row == null) {
                continue;
            }
            NurseStationOrderExecuteBindItemDto item = new NurseStationOrderExecuteBindItemDto();
            item.setBindType(row.getBindType());
            item.setDisplayName(row.getTargetItemName());
            item.setQuantityNumerator(row.getQuantityNumerator());
            item.setQuantityDenominator(row.getQuantityDenominator());
            item.setUnitCode(row.getUnitCode());
            if (StringUtils.isNotBlank(row.getUnitName())) {
                item.setUnitCode_dictText(row.getUnitName());
            } else {
                item.setUnitCode_dictText(
                    OrderCatalogDisplayAssembler.resolveCategoryDisplay(unitLabelByCode, row.getUnitCode()));
            }
            item.setPriority(row.getPriority());
            if (DoctorStationAdviceConstants.BIND_TYPE_USAGE_TO_DEVICE.equals(row.getBindType())) {
                // 用法绑定模板：目标一般为耗材
                item.setDeviceId(row.getTargetItemId());
            } else if (DoctorStationAdviceConstants.BIND_TYPE_ACTIVITY_CHILD.equals(row.getBindType())) {
                // ACTIVITY_CHILD 按目标表区分：耗材定义 → deviceId，否则 → activityId
                String targetTable = StringUtils.trimToEmpty(row.getTargetItemTable());
                if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(targetTable)) {
                    item.setDeviceId(row.getTargetItemId());
                } else {
                    item.setActivityId(row.getTargetItemId());
                }
            } else {
                item.setDeviceId(row.getTargetItemId());
            }
            mapped.add(item);
        }
        return mapped;
    }

    /**
     * 用法编码解析为术语概念主键。
     *
     * <p>当前实现：methodCode 为纯数字串时直接 parse 为 conceptId；
     * 非数字术语 code 返回 null（bindItems 为空，待产品/后端增强）。</p>
     */
    private Long resolveUsageConceptId(MedicationRequestListQueryModel medDetail) {
        if (medDetail == null || StringUtils.isBlank(medDetail.getMethodCode())) {
            return null;
        }
        String methodCode = medDetail.getMethodCode().trim();
        if (StringUtils.isNumeric(methodCode)) {
            return Long.valueOf(methodCode);
        }
        return null;
    }

    /**
     * 组内医嘱行排序：长临 code 升序，同长临下开立时间降序（BR-IP-EXE-012）。
     */
    private List<NurseStationOrderExecuteRowDto> sortRows(List<NurseStationOrderExecuteRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<NurseStationOrderExecuteRowDto> copy = new ArrayList<>(rows);
        copy.sort(Comparator
            .comparing(NurseStationOrderExecuteRowDto::getTherapyEnum, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(NurseStationOrderExecuteRowDto::getAuthoredTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return copy;
    }

    /**
     * 四类槽位分桶容器（pending/executed/skipped/cancelled）。
     */
    private static class SlotBuckets {
        /** 待执行槽位。 */
        private List<NurseStationOrderExecuteSlotDto> pendingSlots;
        /** 已执行槽位。 */
        private List<NurseStationOrderExecuteSlotDto> executedSlots;
        /** 不执行槽位。 */
        private List<NurseStationOrderExecuteSlotDto> skippedSlots;
        /** 已取消执行槽位。 */
        private List<NurseStationOrderExecuteSlotDto> cancelledSlots;
    }
}
