/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderexecute.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.application.shared.advice.OrderCatalogDisplayAssembler;
import com.openhis.health.application.outpatient.nursestation.skintest.support.NurseStationSkinTestExecuteExcludeHelper;
import com.openhis.health.application.inpatient.nursestation.orderaudit.support.NurseStationOrderViewListAssembler;
import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderViewDisplayContext;
import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderViewDisplayCore;
import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderViewListDetailIndex;
import com.openhis.health.application.shared.advice.AdviceOrderBindQueryLoader;
import com.openhis.health.application.shared.advice.AdviceOrderBindRowDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteBindItemDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecutePatientGroupDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteRowDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteSlotDto;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.clinical.procedure.entity.Procedure;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.custom.frequency.queries.model.FrequencyExecutionTimePointQueryModel;
import com.openhis.health.domain.custom.frequency.service.FrequencyQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.application.shared.advice.InpatientOrderListSkeleton;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.EventStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
 *   <li>净态判定：occurrence 组内 id 最大 + {@code CANCEL.refund_id}；执行-取消对冲净值为 hedged pending，同时展示于待执行/取消 Tab（BR-IP-EXE-008/014）</li>
 *   <li>批量挂载 bindItems：药嘱按用法→耗材，诊嘱按 activity→子项（BR-IP-EXE-013）</li>
 *   <li>executeTab 收窄当前 Tab 槽位（医嘱行不变，BR-IP-EXE-010/012）</li>
 * </ol>
 *
 * <p>医嘱行展示字段委托 {@link NurseStationOrderViewListAssembler}。</p>
 *
 */
@Component
@RequiredArgsConstructor
public class NurseStationOrderExecuteListReadAssembler {

    /** 医嘱查看列表行展示内核。 */
    private final NurseStationOrderViewListAssembler orderViewListAssembler;
    /** 频次计划点展开。 */
    private final FrequencyQueryService frequencyQueryService;
    /** Procedure 批量加载。 */
    private final ProcedureQueryService procedureQueryService;
    /** 绑定项模板查询。 */
    private final AdviceOrderBindQueryLoader orderBindQueryLoader;

    /** 中药 Tab 方级折叠。 */
    private final InpatientNurseStationTcmOrchestrator inpatientNurseStationTcmOrchestrator;

    /** 皮试检查活动定义解析：执行 Tab 与皮试 Tab 互斥过滤。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

    /** 皮试检查活动 id 进程内缓存（主数据固定名，启动后不变）。 */
    private volatile Long cachedSkinTestActivityId;

    /**
     * OrderInfo 骨架 + 域明细 → 按就诊分组且组内已排序的列表（含槽位与 bindItems）。
     *
     * @param skeleton           过滤后的读模型行
     * @param medByEncounter     就诊 → 药品明细
     * @param svcByEncounter     就诊 → 诊疗明细
     * @param executionDeadline  截止时间
     * @param executeTab         执行 Tab
     * @param encounterOrgIds    就诊 → 机构主键（绑定模板预留）
     * @param medicineCategoryTab west / tcm
     * @return 患者分组列表
     */
    public List<NurseStationOrderExecutePatientGroupDto> assemblePatientGroups(
        List<InpatientOrderListSkeleton> skeleton,
        Map<Long, List<MedicationRequestListQueryModel>> medByEncounter,
        Map<Long, List<ServiceRequestListModel>> svcByEncounter,
        LocalDateTime executionDeadline,
        String executeTab,
        Map<Long, Long> encounterOrgIds,
        String medicineCategoryTab) {
        if (skeleton == null || skeleton.isEmpty()) {
            return new ArrayList<>();
        }
        NurseStationOrderViewListDetailIndex index =
            orderViewListAssembler.indexEncounterDetails(skeleton, medByEncounter, svcByEncounter);
        NurseStationOrderViewDisplayContext displayContext =
            orderViewListAssembler.prepareDisplayContext(index);
        Long skinTestActivityId = this.resolveSkinTestActivityId();
        Map<Long, List<NurseStationOrderExecuteRowDto>> rowsByEncounter = new LinkedHashMap<>();
        // 逐 OrderInfo 骨架行装配：展示字段 + 四类槽位 + bindItems + Tab 过滤
        for (InpatientOrderListSkeleton order : skeleton) {
            if (order == null || order.getEncounterId() == null) {
                continue;
            }
            // 剔除长期无频次、转科/出院/护理等特殊诊疗等不可执行行
            if (this.shouldExcludeExecuteRow(
                order, index.getMedDetailById(), index.getSvcDetailById(), skinTestActivityId)) {
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
            this.applyExecuteTabSlotFilter(line, executeTab, executionDeadline);
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
        Map<Long, MedicationRequestListQueryModel> medDetailById =
            inpatientNurseStationTcmOrchestrator.indexMedDetailById(medByEncounter);
        for (Map.Entry<Long, List<NurseStationOrderExecuteRowDto>> entry : rowsByEncounter.entrySet()) {
            List<NurseStationOrderExecuteRowDto> sorted = this.sortRows(entry.getValue());
            List<NurseStationOrderExecuteRowDto> collapsed =
                inpatientNurseStationTcmOrchestrator.collapseTcmPrescriptionDisplayRows(
                    sorted,
                    medDetailById,
                    medicineCategoryTab,
                    EXECUTE_TCM_ROW_ACCESSOR);
            NurseStationOrderExecutePatientGroupDto group = new NurseStationOrderExecutePatientGroupDto();
            group.setEncounterId(entry.getKey());
            group.setOrders(collapsed);
            groups.add(group);
        }
        return groups;
    }

    /**
     * 出院门禁/ATD-03：长期已停嘱是否仍有待执行槽位（BR-IP-ATD-011①′，与 Q-IP-EXE-001 槽位分桶同源）。
     *
     * @param medDetail           药品请求；非药嘱传 null
     * @param svcDetail           诊疗请求；非诊嘱传 null
     * @param procedures          该 request 下全部 Procedure 实体
     * @param executionDeadline   截止时刻（出院门禁通常为当前时刻）
     * @return true 表示仍有 pending 槽位，阻塞出院
     */
    public boolean hasLongTermStoppedPendingExecutionSlots(
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        List<Procedure> procedures,
        LocalDateTime executionDeadline) {
        return this.hasLongTermStoppedPendingExecutionSlotsFromRecords(
            medDetail,
            svcDetail,
            this.mapProceduresToRecordModels(procedures),
            executionDeadline);
    }

    /**
     * 出院门禁/ATD-03：临时嘱整单净态是否仍阻塞出院（BR-IP-ATD-011②，与 Q-IP-EXE-001 / BR-IP-EXE-006/008 同源）。
     *
     * <p>仅用于 {@code generate_source=doctor-prescription} 候选；护士划价（{@code nurse-pricing}）不在 ATD-03/05 范围。
     * 执行-取消对冲后净态为 hedged pending，须再次执行或登记不执行后方可出院。</p>
     *
     * @param procedures 该 request 下全部 Procedure 实体
     * @return true 表示净态仍为 pending 或 hedged pending
     */
    public boolean isTemporaryOrderNetPendingForDischarge(List<Procedure> procedures) {
        List<ProcedureRecordQueryModel> records = this.mapProceduresToRecordModels(procedures);
        OccurrenceNetSlotState state = this.resolveTemporaryNetSlotState(records);
        if (OccurrenceNetSlotState.PENDING.equals(state)) {
            return true;
        }
        if (OccurrenceNetSlotState.HEDGED_PENDING.equals(state)) {
            return true;
        }
        return false;
    }

    /**
     * 出院门禁/ATD-03：长期已停嘱是否仍有待执行槽位（Procedure 读模型入参，内部复用）。
     *
     * @param medDetail           药品请求；非药嘱传 null
     * @param svcDetail           诊疗请求；非诊嘱传 null
     * @param procedures          该 request 下全部 Procedure 读模型
     * @param executionDeadline   截止时刻
     * @return true 表示仍有 pending 槽位
     */
    private boolean hasLongTermStoppedPendingExecutionSlotsFromRecords(
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        List<ProcedureRecordQueryModel> procedures,
        LocalDateTime executionDeadline) {
        if (executionDeadline == null) {
            return false;
        }
        InpatientOrderListSkeleton order = null;
        if (medDetail != null) {
            order = InpatientOrderListSkeleton.fromMedicationListModel(medDetail);
        } else if (svcDetail != null) {
            order = InpatientOrderListSkeleton.fromServiceListModel(svcDetail);
        }
        if (order == null) {
            return false;
        }
        String therapy = this.resolveTherapyEnum(order, medDetail, svcDetail);
        String statusEnum = order.getStatusEnum();
        if (medDetail != null && StringUtils.isNotEmpty(medDetail.getStatusEnum())) {
            statusEnum = medDetail.getStatusEnum();
        }
        if (svcDetail != null && StringUtils.isNotEmpty(svcDetail.getStatusEnum())) {
            statusEnum = svcDetail.getStatusEnum();
        }
        if (!TherapyType.LONG_TERM.getCode().equals(therapy)) {
            return false;
        }
        if (!RequestStatus.STOPPED.getCode().equals(statusEnum)) {
            return false;
        }
        List<ProcedureRecordQueryModel> procedureRows = procedures;
        if (procedureRows == null) {
            procedureRows = Collections.emptyList();
        }
        SlotBuckets buckets = this.buildSlotBuckets(order, medDetail, svcDetail, procedureRows, executionDeadline);
        if (buckets.pendingSlots == null || buckets.pendingSlots.isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * Procedure 实体 → 执行列表槽位归并读模型（出院门禁批量 Procedure 复用）。
     *
     * @param procedures 同一 request 下 Procedure 实体列表
     * @return 读模型列表；入参为空时空列表
     */
    public List<ProcedureRecordQueryModel> mapProceduresToRecordModels(List<Procedure> procedures) {
        if (procedures == null || procedures.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProcedureRecordQueryModel> models = new ArrayList<>();
        for (Procedure row : procedures) {
            if (row == null) {
                continue;
            }
            ProcedureRecordQueryModel model = new ProcedureRecordQueryModel();
            model.setId(row.getId());
            model.setRequestId(row.getRequestId());
            model.setRequestTable(row.getRequestTable());
            model.setStatusEnum(row.getStatusEnum());
            model.setRecordedTime(row.getRecordedTime());
            model.setRefundId(row.getRefundId());
            model.setEncounterId(row.getEncounterId());
            model.setPatientId(row.getPatientId());
            model.setCategoryEnum(row.getCategoryEnum());
            model.setOccurrenceTime(row.getOccurrenceTime());
            model.setRecorderId(row.getRecorderId());
            model.setResultText(row.getResultText());
            models.add(model);
        }
        return models;
    }

    private static final InpatientNurseStationTcmOrchestrator.TcmPrescriptionRowAccessor<NurseStationOrderExecuteRowDto>
        EXECUTE_TCM_ROW_ACCESSOR = new InpatientNurseStationTcmOrchestrator.TcmPrescriptionRowAccessor<>() {
        @Override
        public Long getRequestId(NurseStationOrderExecuteRowDto row) {
            return row.getOrderId();
        }

        @Override
        public Long getEncounterId(NurseStationOrderExecuteRowDto row) {
            return row.getEncounterId();
        }

        @Override
        public String getOrderType(NurseStationOrderExecuteRowDto row) {
            return row.getOrderType();
        }

        @Override
        public Integer getSortNumber(NurseStationOrderExecuteRowDto row) {
            return null;
        }

        @Override
        public String getAdviceDisplayName(NurseStationOrderExecuteRowDto row) {
            return row.getAdviceDisplayName();
        }

        @Override
        public String getDosageDisplay(NurseStationOrderExecuteRowDto row) {
            return row.getDosageDisplay();
        }

        @Override
        public void applyTcmPrescriptionCollapse(
            NurseStationOrderExecuteRowDto representative,
            MedicationRequestListQueryModel repMed,
            List<Long> lineRequestIds,
            String adviceDisplayName,
            String dosageDisplay) {
            representative.setTcmFlag(Boolean.TRUE);
            if (repMed != null) {
                representative.setPrescriptionNo(repMed.getPrescriptionNo());
                representative.setSufferingFlag(repMed.getSufferingFlag());
                representative.setChineseHerbsDoseQuantity(repMed.getChineseHerbsDoseQuantity());
            }
            representative.setLineRequestIds(lineRequestIds);
            representative.setAdviceDisplayName(adviceDisplayName);
            representative.setSpecificationText(null);
            representative.setDosageDisplay(dosageDisplay);
        }
    };

    /**
     * 写路径「不执行」Gate：occurrence 净态须为 pending（BR-IP-EXE-021 / EX-IP-EXE-401）。
     *
     * <p>已执行、已不执行、已取消执行（含 CANCEL 对冲后）均不可再登记不执行。</p>
     *
     * @param order       医嘱读模型
     * @param medDetail   药品明细；非药嘱可 null
     * @param svcDetail   诊疗明细；非诊嘱可 null
     * @param planTime    occurrence 锚点
     * @param procedures  已加载 Procedure 行
     * @return true 表示该 occurrence 仍为待执行净态
     */
    public boolean isPlanTimePending(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        LocalDateTime planTime,
        List<ProcedureRecordQueryModel> procedures) {
        OccurrenceNetSlotState netState = this.resolveNetSlotState(order, medDetail, svcDetail, planTime, procedures);
        if (OccurrenceNetSlotState.PENDING.equals(netState)) {
            return true;
        }
        if (OccurrenceNetSlotState.HEDGED_PENDING.equals(netState)) {
            // 执行-取消对冲净值待执行，待执行 Tab 可再次执行
            return true;
        }
        return false;
    }

    /**
     * 写路径「执行」Gate：occurrence 净态为 pending 或 cancelled 时可写（BR-IP-EXE-014 / EX-IP-EXE-301）。
     *
     * <p>执行一次再取消一次视为对冲，净态归 hedged pending，待执行/取消 Tab 均可再次执行；无 refund 指向的孤立 CANCEL 仍为 cancelled 净态。
     * 多次执行/取消往返时以 occurrence 组内 id 最大记录 + CANCEL.refund_id 判定净态，避免误读历史行。</p>
     *
     * @param order       医嘱读模型
     * @param medDetail   药品明细；非药嘱可 null
     * @param svcDetail   诊疗明细；非诊嘱可 null
     * @param planTime    occurrence 锚点
     * @param procedures  已加载 Procedure 行
     * @return true 表示该 occurrence 允许正向执行
     */
    public boolean isPlanTimeExecutable(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        LocalDateTime planTime,
        List<ProcedureRecordQueryModel> procedures) {
        OccurrenceNetSlotState netState = this.resolveNetSlotState(order, medDetail, svcDetail, planTime, procedures);
        if (OccurrenceNetSlotState.PENDING.equals(netState)) {
            return true;
        }
        if (OccurrenceNetSlotState.HEDGED_PENDING.equals(netState)) {
            // 执行-取消对冲净值待执行，待执行/取消 Tab 均可再次执行
            return true;
        }
        if (OccurrenceNetSlotState.CANCELLED.equals(netState)) {
            // 孤立 CANCEL 净态：允许再次执行，写路径追加新 COMPLETED
            return true;
        }
        return false;
    }

    /**
     * 写 Gate：planTime（occurrence）是否晚于停嘱时刻。
     *
     * <p>写路径唯一的时间边界校验；与 actualTime、executionDeadline、当前日期无关。</p>
     *
     * @param order     医嘱骨架
     * @param medDetail 药品明细；非药嘱可 null
     * @param svcDetail 诊疗明细；非诊嘱可 null
     * @param planTime  计划执行时刻
     * @return {@code true} 表示 planTime 严格晚于停嘱时刻
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
     * 写 Gate：请求状态是否允许正向执行（与列表 Q-IP-EXE-001 对齐：VERIFIED + STOPPED）。
     *
     * @param order     医嘱骨架
     * @param medDetail 药品明细；非药嘱可 null
     * @param svcDetail 诊疗明细；非诊嘱可 null
     * @return true 表示已校对或在执停嘱态，可进入 planTime 边界校验
     */
    public boolean isRequestStatusExecutableForWrite(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail) {
        String status = this.resolveRequestStatusCode(order, medDetail, svcDetail);
        if (RequestStatus.VERIFIED.getCode().equals(status)) {
            return true;
        }
        if (RequestStatus.STOPPED.getCode().equals(status)) {
            return true;
        }
        return false;
    }

    /**
     * 请求状态：域明细优先于 OrderInfo 骨架（与列表展示、停嘱 Gate 同源）。
     */
    private String resolveRequestStatusCode(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail) {
        if (order == null) {
            return null;
        }
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            if (medDetail != null && StringUtils.isNotEmpty(medDetail.getStatusEnum())) {
                return medDetail.getStatusEnum();
            }
        } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            if (svcDetail != null && StringUtils.isNotEmpty(svcDetail.getStatusEnum())) {
                return svcDetail.getStatusEnum();
            }
        }
        return order.getStatusEnum();
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
        if (this.shouldExcludeSkinTestExecuteRow(order, medDetail, svcDetail, this.resolveSkinTestActivityId())) {
            return true;
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            String serviceCategory = orderViewListAssembler.resolveServiceCategoryEnum(order, svcDetail);
            if (orderViewListAssembler.isExcludedFromNurseStationExecuteList(serviceCategory)) {
                return true;
            }
            // 床位滚费配置长期 SR 禁止手动执行（BR-IP-ARG-019）
            if (this.shouldExcludeAutoRollBedServiceRequest(svcDetail)) {
                return true;
            }
        }
        String therapy = order.getTherapyEnum();
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            if (medDetail != null && StringUtils.isNotEmpty(medDetail.getTherapyEnum())) {
                therapy = medDetail.getTherapyEnum();
            }
            if (TherapyType.LONG_TERM.getCode().equals(therapy)) {
                String rateCode = null;
                if (medDetail != null && StringUtils.isNotEmpty(medDetail.getRateCode())) {
                    rateCode = medDetail.getRateCode();
                }
                if (StringUtils.isEmpty(rateCode)) {
                    return true;
                }
            }
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            ServiceRequestListModel svc = svcDetail;
            if (svc != null && StringUtils.isNotEmpty(svc.getTherapyEnum())) {
                therapy = svc.getTherapyEnum();
            }
            if (TherapyType.LONG_TERM.getCode().equals(therapy)) {
                String rateCode = null;
                if (svc != null && StringUtils.isNotEmpty(svc.getRateCode())) {
                    rateCode = svc.getRateCode();
                }
                if (StringUtils.isEmpty(rateCode)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 剔除床位滚费配置 ensure 的长期 SR（generate_source=auto-roll-fees；BR-IP-ARG-019）。
     *
     * @param svc 诊疗申请读模型
     * @return 应剔除时 true
     */
    private boolean shouldExcludeAutoRollBedServiceRequest(ServiceRequestListModel svc) {
        if (svc == null) {
            return false;
        }
        return GenerateSource.AUTO_ROLL_FEES.getCode().equals(svc.getGenerateSourceEnum());
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
        Map<Long, ServiceRequestListModel> svcDetailById,
        Long skinTestActivityId) {
        MedicationRequestListQueryModel med = null;
        ServiceRequestListModel svc = null;
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            med = medDetailById.get(order.getMedReqId());
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            svc = svcDetailById.get(order.getOrderId());
        }
        if (this.shouldExcludeSkinTestExecuteRow(order, med, svc, skinTestActivityId)) {
            return true;
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            String serviceCategory = orderViewListAssembler.resolveServiceCategoryEnum(order, svc);
            if (orderViewListAssembler.isExcludedFromNurseStationExecuteList(serviceCategory)) {
                return true;
            }
            // 床位滚费配置长期 SR 禁止手动执行（BR-IP-ARG-019）
            if (this.shouldExcludeAutoRollBedServiceRequest(svc)) {
                return true;
            }
        }
        String therapy = order.getTherapyEnum();
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            if (med != null && StringUtils.isNotEmpty(med.getTherapyEnum())) {
                therapy = med.getTherapyEnum();
            }
            if (TherapyType.LONG_TERM.getCode().equals(therapy)) {
                String rateCode = null;
                if (med != null && StringUtils.isNotEmpty(med.getRateCode())) {
                    rateCode = med.getRateCode();
                }
                if (StringUtils.isEmpty(rateCode)) {
                    // 长期药嘱无频次无法展开计划点（BR-IP-EXE-002）
                    return true;
                }
            }
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            if (svc != null && StringUtils.isNotEmpty(svc.getTherapyEnum())) {
                therapy = svc.getTherapyEnum();
            }
            if (TherapyType.LONG_TERM.getCode().equals(therapy)) {
                String rateCode = null;
                if (svc != null && StringUtils.isNotEmpty(svc.getRateCode())) {
                    rateCode = svc.getRateCode();
                }
                if (StringUtils.isEmpty(rateCode)) {
                    // 长期诊嘱无频次同理剔除（BR-IP-EXE-003）
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 皮试检查 SR 与皮试药不进执行 Tab（AS-IP-NUR-011 与执行 Tab 互斥）。
     */
    private boolean shouldExcludeSkinTestExecuteRow(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        Long skinTestActivityId) {
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            if (NurseStationSkinTestExecuteExcludeHelper.shouldExcludeServiceFromNurseStationExecuteList(
                skinTestActivityId, svcDetail)) {
                return true;
            }
        }
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            if (NurseStationSkinTestExecuteExcludeHelper.shouldExcludeMedicationFromNurseStationExecuteList(medDetail)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析皮试检查活动定义主键（进程内缓存）。
     */
    private Long resolveSkinTestActivityId() {
        if (this.cachedSkinTestActivityId == null) {
            this.cachedSkinTestActivityId = this.activityDefinitionQueryService.requireIdByExactName(
                GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME);
        }
        return this.cachedSkinTestActivityId;
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
        line.setCategoryEnum(core.getCategoryEnum());
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
     * 有 Procedure 时取组内最后一条为终态；执行-取消对冲净值 hedged pending，同时归入 pending 与 cancelled 桶。</p>
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
            // 同一 occurrence 可能有多条 Procedure（执行/取消往返），按净态分桶
            OccurrenceNetSlotState netState = this.resolveOccurrenceNetSlotState(group, procedures);
            if (OccurrenceNetSlotState.PENDING.equals(netState)) {
                if (plannedTimes.contains(occurrence)) {
                    buckets.pendingSlots.add(this.buildPendingSlot(occurrence));
                }
                continue;
            }
            if (OccurrenceNetSlotState.HEDGED_PENDING.equals(netState)) {
                // 执行-取消对冲：净值待执行，待执行/取消 Tab 双展示
                if (plannedTimes.contains(occurrence)) {
                    buckets.pendingSlots.add(this.buildPendingSlot(occurrence));
                }
                ProcedureRecordQueryModel hedgeFinalRecord = this.pickFinalProcedureRecord(group);
                NurseStationOrderExecuteSlotDto cancelledSlot = this.toSlotDto(hedgeFinalRecord, occurrence);
                if (cancelledSlot != null) {
                    buckets.cancelledSlots.add(cancelledSlot);
                }
                continue;
            }
            ProcedureRecordQueryModel finalRecord = this.pickFinalProcedureRecord(group);
            NurseStationOrderExecuteSlotDto slot = this.toSlotDto(finalRecord, occurrence);
            if (slot == null) {
                continue;
            }
            if (OccurrenceNetSlotState.EXECUTED.equals(netState)) {
                buckets.executedSlots.add(slot);
            } else if (OccurrenceNetSlotState.SKIPPED.equals(netState)) {
                buckets.skippedSlots.add(slot);
            } else if (OccurrenceNetSlotState.CANCELLED.equals(netState)) {
                buckets.cancelledSlots.add(slot);
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
        OccurrenceNetSlotState netState = this.resolveTemporaryNetSlotState(procedures);
        if (OccurrenceNetSlotState.EXECUTED.equals(netState)) {
            ProcedureRecordQueryModel lastNetCompleted = this.pickLastNetCompletedProcedure(procedures);
            if (lastNetCompleted != null) {
                buckets.executedSlots.add(this.toTemporaryExecutedSlotDto(lastNetCompleted));
            }
            return buckets;
        }
        if (OccurrenceNetSlotState.PENDING.equals(netState)) {
            if (authoredTime != null) {
                buckets.pendingSlots.add(this.buildPendingSlot(authoredTime));
            }
            return buckets;
        }
        if (OccurrenceNetSlotState.HEDGED_PENDING.equals(netState)) {
            // 临时嘱执行-取消对冲：待执行/取消 Tab 双展示
            if (authoredTime != null) {
                buckets.pendingSlots.add(this.buildPendingSlot(authoredTime));
            }
            ProcedureRecordQueryModel hedgeFinalRecord = this.pickNetFinalProcedureRecord(procedures);
            if (hedgeFinalRecord != null) {
                buckets.cancelledSlots.add(this.toTemporaryExecutedSlotDto(hedgeFinalRecord));
            }
            return buckets;
        }
        ProcedureRecordQueryModel netFinal = this.pickNetFinalProcedureRecord(procedures);
        if (netFinal == null) {
            if (authoredTime != null) {
                buckets.pendingSlots.add(this.buildPendingSlot(authoredTime));
            }
            return buckets;
        }
        if (OccurrenceNetSlotState.SKIPPED.equals(netState)) {
            buckets.skippedSlots.add(this.toTemporaryExecutedSlotDto(netFinal));
            return buckets;
        }
        if (OccurrenceNetSlotState.CANCELLED.equals(netState)) {
            buckets.cancelledSlots.add(this.toTemporaryExecutedSlotDto(netFinal));
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
                if (StringUtils.isNotEmpty(medDetail.getStatusEnum())) {
                    statusEnum = medDetail.getStatusEnum();
                }
                // 右边界：仅停嘱（STOPPED）时取 effective_dose_end，禁止与 check_time 混用
                if (RequestStatus.STOPPED.getCode().equals(medDetail.getStatusEnum())
                    && medDetail.getEffectiveDoseEnd() != null) {
                    end = medDetail.getEffectiveDoseEnd();
                }
                if (StringUtils.isNotEmpty(medDetail.getRateCode())) {
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
                if (StringUtils.isNotEmpty(svcDetail.getStatusEnum())) {
                    statusEnum = svcDetail.getStatusEnum();
                }
                // 诊嘱右边界：仅 STOPPED 时取 occurrence_end_time
                if (RequestStatus.STOPPED.getCode().equals(svcDetail.getStatusEnum())
                    && svcDetail.getOccurrenceEndTime() != null) {
                    end = svcDetail.getOccurrenceEndTime();
                }
                if (StringUtils.isNotEmpty(svcDetail.getRateCode())) {
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
        if (StringUtils.isEmpty(rateCode)) {
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
            if (medDetail != null && StringUtils.isNotEmpty(medDetail.getTherapyEnum())) {
                therapy = medDetail.getTherapyEnum();
            }
            return therapy;
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            if (svcDetail != null && StringUtils.isNotEmpty(svcDetail.getTherapyEnum())) {
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
     * 解析 occurrence 净态（长期嘱：按 occurrence 分组；写/读 Gate 共用）。
     *
     * @param order      医嘱读模型
     * @param medDetail  药品明细
     * @param svcDetail  诊疗明细
     * @param planTime   occurrence 锚点
     * @param procedures 该 request 下全部 Procedure
     * @return 净态枚举；不可写医嘱返回 null
     */
    private OccurrenceNetSlotState resolveNetSlotState(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        LocalDateTime planTime,
        List<ProcedureRecordQueryModel> procedures) {
        if (order == null || planTime == null) {
            return null;
        }
        if (this.shouldExcludeExecuteRowForWrite(order, medDetail, svcDetail)) {
            return null;
        }
        String therapy = this.resolveTherapyEnum(order, medDetail, svcDetail);
        if (TherapyType.TEMPORARY.getCode().equals(therapy)) {
            return this.resolveTemporaryNetSlotState(procedures);
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
            // 该 occurrence 尚无 Procedure → 待执行
            return OccurrenceNetSlotState.PENDING;
        }
        return this.resolveOccurrenceNetSlotState(group, procedures);
    }

    /**
     * 长期嘱：单 occurrence 组内净态（BR-IP-EXE-007/008）。
     *
     * <p>组内 id 最大为末态；COMPLETED 若存在 CANCEL.refund_id 指向则净态为 hedged pending（执行-取消对冲，BR-NUR-005 净值）。</p>
     */
    private OccurrenceNetSlotState resolveOccurrenceNetSlotState(
        List<ProcedureRecordQueryModel> occurrenceGroup,
        List<ProcedureRecordQueryModel> allProceduresAtRequest) {
        ProcedureRecordQueryModel finalRecord = this.pickFinalProcedureRecord(occurrenceGroup);
        if (finalRecord == null) {
            return OccurrenceNetSlotState.PENDING;
        }
        String status = finalRecord.getStatusEnum();
        if (EventStatus.COMPLETED.getCode().equals(status)) {
            if (this.isProcedureOffsetByCancel(allProceduresAtRequest, finalRecord.getId())) {
                // 末态 COMPLETED 已被 CANCEL 对冲 → 净值待执行，双 Tab 展示
                return OccurrenceNetSlotState.HEDGED_PENDING;
            }
            return OccurrenceNetSlotState.EXECUTED;
        }
        if (EventStatus.STOPPED.getCode().equals(status)) {
            return OccurrenceNetSlotState.SKIPPED;
        }
        if (EventStatus.isCancelExecutionStatusCode(status)) {
            if (finalRecord.getRefundId() != null) {
                // CANCEL 行指向被对冲 COMPLETED → 净值待执行，双 Tab 展示
                return OccurrenceNetSlotState.HEDGED_PENDING;
            }
            return OccurrenceNetSlotState.CANCELLED;
        }
        // 进行中等非终态 → 仍视为待执行
        return OccurrenceNetSlotState.PENDING;
    }

    /**
     * 临时嘱：整单净态（BR-IP-EXE-006/008）。
     *
     * <p>存在净 COMPLETED → executed；否则按 id 最大末态归类；无记录 → pending。</p>
     */
    private OccurrenceNetSlotState resolveTemporaryNetSlotState(List<ProcedureRecordQueryModel> procedures) {
        ProcedureRecordQueryModel lastNetCompleted = this.pickLastNetCompletedProcedure(procedures);
        if (lastNetCompleted != null) {
            return OccurrenceNetSlotState.EXECUTED;
        }
        ProcedureRecordQueryModel netFinal = this.pickNetFinalProcedureRecord(procedures);
        if (netFinal == null) {
            return OccurrenceNetSlotState.PENDING;
        }
        String status = netFinal.getStatusEnum();
        if (EventStatus.STOPPED.getCode().equals(status)) {
            return OccurrenceNetSlotState.SKIPPED;
        }
        if (EventStatus.isCancelExecutionStatusCode(status)) {
            if (netFinal.getRefundId() != null) {
                // 临时嘱：取消执行已对冲净 COMPLETED → 净值待执行，双 Tab 展示
                return OccurrenceNetSlotState.HEDGED_PENDING;
            }
            return OccurrenceNetSlotState.CANCELLED;
        }
        if (EventStatus.COMPLETED.getCode().equals(status)) {
            if (this.isProcedureOffsetByCancel(procedures, netFinal.getId())) {
                // 临时嘱：末态 COMPLETED 已被对冲 → 净值待执行，双 Tab 展示
                return OccurrenceNetSlotState.HEDGED_PENDING;
            }
            return OccurrenceNetSlotState.EXECUTED;
        }
        return OccurrenceNetSlotState.PENDING;
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
                // 存在 CANCEL 记录指向该 COMPLETED → 执行-取消对冲，读侧净值归 pending
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
     * <p>药嘱：用法 {@code method_code}（术语 code）→ {@code USAGE} 来源绑定模板；
     * 诊嘱：按 activityId 查 {@code ACTIVITY} 来源绑定（按 targetItemTable 区分诊疗/耗材）。</p>
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
            // 药嘱：用法 method_code（术语 code）→ USAGE 来源绑定模板
            String methodCode = null;
            if (medDetail != null && StringUtils.isNotEmpty(medDetail.getMethodCode())) {
                methodCode = medDetail.getMethodCode().trim();
            }
            if (StringUtils.isNotEmpty(methodCode)) {
                List<AdviceOrderBindRowDto> rows = orderBindQueryLoader.listBindingsBySourceItemKey(
                    DoctorStationAdviceConstants.BIND_TYPE_USAGE, methodCode, organizationUnitId);
                bindItems.addAll(this.mapBindRows(rows, unitLabelByCode));
            }
        } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            // 诊嘱：活动定义 → 子绑定项
            Long activityId = order.getActivityId();
            if (svcDetail != null && svcDetail.getActivityId() != null) {
                activityId = svcDetail.getActivityId();
            }
            if (activityId != null) {
                List<AdviceOrderBindRowDto> rows = orderBindQueryLoader.listBindingsBySourceItemKey(
                    DoctorStationAdviceConstants.BIND_TYPE_ACTIVITY,
                    String.valueOf(activityId),
                    organizationUnitId);
                bindItems.addAll(this.mapBindRows(rows, unitLabelByCode));
            }
        }
        return bindItems;
    }

    /**
     * 绑定模板行 → 执行 bindItem DTO；按 bindType/targetItemTable 回填 activityId 或 deviceId。
     */
    private List<NurseStationOrderExecuteBindItemDto> mapBindRows(
        List<AdviceOrderBindRowDto> rows,
        Map<String, String> unitLabelByCode) {
        List<NurseStationOrderExecuteBindItemDto> mapped = new ArrayList<>();
        if (rows == null) {
            return mapped;
        }
        for (AdviceOrderBindRowDto row : rows) {
            if (row == null) {
                continue;
            }
            NurseStationOrderExecuteBindItemDto item = new NurseStationOrderExecuteBindItemDto();
            item.setBindingId(row.getBindingId());
            item.setBindType(row.getBindType());
            item.setDisplayName(row.getTargetItemName());
            item.setBusNo(row.getBusNo());
            item.setSpecificationText(row.getSpecificationText());
            item.setUnitPrice(row.getUnitPrice());
            item.setChargeItemDefinitionId(row.getChargeItemDefinitionId());
            item.setQuantityNumerator(row.getQuantityNumerator());
            item.setQuantityDenominator(row.getQuantityDenominator());
            item.setUnitCode(row.getUnitCode());
            if (StringUtils.isNotEmpty(row.getUnitName())) {
                item.setUnitCode_dictText(row.getUnitName());
            } else {
                item.setUnitCode_dictText(
                    OrderCatalogDisplayAssembler.resolveCategoryDisplay(unitLabelByCode, row.getUnitCode()));
            }
            item.setPriority(row.getPriority());
            this.applyBindTargetIds(item, row);
            mapped.add(item);
        }
        return mapped;
    }

    /**
     * 按绑定类型与目标表回填 {@code activityId}/{@code deviceId}。
     */
    private void applyBindTargetIds(
        NurseStationOrderExecuteBindItemDto item,
        AdviceOrderBindRowDto row) {
        if (item == null || row == null || row.getTargetItemId() == null) {
            return;
        }
        String targetTable = trimToEmpty(row.getTargetItemTable());
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(targetTable)) {
            item.setDeviceId(row.getTargetItemId());
            return;
        }
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(targetTable)) {
            item.setActivityId(row.getTargetItemId());
            return;
        }
        if (DoctorStationAdviceConstants.BIND_TYPE_USAGE.equals(row.getBindType())) {
            item.setDeviceId(row.getTargetItemId());
            return;
        }
        if (DoctorStationAdviceConstants.BIND_TYPE_ACTIVITY.equals(row.getBindType())) {
            item.setActivityId(row.getTargetItemId());
        }
    }

    /**
     * 组内医嘱行排序：长临 code 升序，同长临下开立时间降序（BR-IP-EXE-012）。
     *
     * @param rows 待排序医嘱行
     * @return 排序后的新列表
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
     * occurrence 净态（读侧分桶与写 Gate 共用，避免多次往返误读历史 Procedure）。
     */
    private enum OccurrenceNetSlotState {
        /** 待执行（无终态或进行中）。 */
        PENDING,
        /** 执行-取消对冲后净值待执行（读侧 pending+cancelled 双 Tab 展示）。 */
        HEDGED_PENDING,
        /** 净已执行（COMPLETED 且未被 CANCEL 对冲）。 */
        EXECUTED,
        /** 已登记不执行（STOPPED）。 */
        SKIPPED,
        /** 已取消执行（孤立 CANCEL，无 refund 对冲 COMPLETED）。 */
        CANCELLED
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

    /**
     * 构造护士站可见性查询（启用 nurseStationQueue + encounterIdsIn + managedWardIds）。
     *
     * @param encounterIds   调用方请求的就诊主键；可空表示仅按管辖病区收窄
     * @param managedWardIds 当前操作者管辖病区；可空
     * @return 住院就诊分页查询入参
     */
    public InpatientEncounterPageQuery toInpatientEncounterPageQuery(List<Long> encounterIds, List<Long> managedWardIds) {
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        query.setNurseStationQueue(Boolean.TRUE);
        if (encounterIds != null && !encounterIds.isEmpty()) {
            query.setEncounterIdsIn(encounterIds);
        }
        if (managedWardIds != null && !managedWardIds.isEmpty()) {
            query.setManagedWardIds(managedWardIds);
        }
        return query;
    }

    /**
     * 长临筛选入参 → {@link TherapyType} code；{@code all} 或空时返回 null（不过滤）。
     *
     * @param therapyType {@link InpatientNurseStationConstants} 长临筛选码
     * @return {@code LONG_TERM}/{@code TEMPORARY} code，或 null
     */
    public String resolveTherapyEnum(String therapyType) {
        String normalized = trimToEmpty(therapyType);
        if (StringUtils.isEmpty(normalized)
            || InpatientNurseStationConstants.THERAPY_FILTER_ALL.equals(normalized)) {
            return null;
        }
        if (InpatientNurseStationConstants.THERAPY_FILTER_LONG.equals(normalized)) {
            return TherapyType.LONG_TERM.getCode();
        }
        if (InpatientNurseStationConstants.THERAPY_FILTER_TEMP.equals(normalized)) {
            return TherapyType.TEMPORARY.getCode();
        }
        return null;
    }

    /**
     * 患者分组列表 → IF-IP-EXE-01 读结果包装。
     *
     * @param groups 按就诊分组的执行行；null 时 groups 为空列表
     * @return 读结果
     */
    public NurseStationOrderExecuteListResult toListResult(List<NurseStationOrderExecutePatientGroupDto> groups) {
        NurseStationOrderExecuteListResult result = new NurseStationOrderExecuteListResult();
        if (groups == null) {
            result.setGroups(Collections.emptyList());
            return result;
        }
        result.setGroups(groups);
        return result;
    }

    /**
     * 按 executeTab 收窄槽位回传（BR-IP-EXE-010/012）：仅当前 Tab 对应槽位且 planTime ≤ deadline。
     *
     * @param row                医嘱执行行（原地修改四类槽位字段）
     * @param executeTab         pending / executed / skipped / cancelled
     * @param executionDeadline  计划点上界
     */
    public void applyExecuteTabSlotFilter(
        NurseStationOrderExecuteRowDto row,
        String executeTab,
        LocalDateTime executionDeadline) {
        if (row == null || executionDeadline == null) {
            return;
        }
        String tab = trimToEmpty(executeTab);
        List<NurseStationOrderExecuteSlotDto> pendingSource = row.getPendingSlots();
        List<NurseStationOrderExecuteSlotDto> executedSource = row.getExecutedSlots();
        List<NurseStationOrderExecuteSlotDto> skippedSource = row.getSkippedSlots();
        List<NurseStationOrderExecuteSlotDto> cancelledSource = row.getCancelledSlots();
        row.setPendingSlots(Collections.emptyList());
        row.setExecutedSlots(Collections.emptyList());
        row.setSkippedSlots(Collections.emptyList());
        row.setCancelledSlots(Collections.emptyList());
        if (InpatientNurseStationConstants.EXECUTE_TAB_PENDING.equals(tab)) {
            row.setPendingSlots(this.filterSlotsWithinDeadline(pendingSource, executionDeadline));
            return;
        }
        if (InpatientNurseStationConstants.EXECUTE_TAB_EXECUTED.equals(tab)) {
            row.setExecutedSlots(this.filterSlotsWithinDeadline(executedSource, executionDeadline));
            return;
        }
        if (InpatientNurseStationConstants.EXECUTE_TAB_SKIPPED.equals(tab)) {
            row.setSkippedSlots(this.filterSlotsWithinDeadline(skippedSource, executionDeadline));
            return;
        }
        if (InpatientNurseStationConstants.EXECUTE_TAB_CANCELLED.equals(tab)) {
            row.setCancelledSlots(this.filterSlotsWithinDeadline(cancelledSource, executionDeadline));
        }
    }

    /**
     * 执行槽过滤：仅保留 planTime ≤ executionDeadline 的槽位。
     *
     * @param slots              源槽列表
     * @param executionDeadline  执行截止时刻
     * @return 过滤后列表
     */
    private List<NurseStationOrderExecuteSlotDto> filterSlotsWithinDeadline(
        List<NurseStationOrderExecuteSlotDto> slots,
        LocalDateTime executionDeadline) {
        if (slots == null || slots.isEmpty()) {
            return Collections.emptyList();
        }
        List<NurseStationOrderExecuteSlotDto> filtered = new ArrayList<>();
        for (NurseStationOrderExecuteSlotDto slot : slots) {
            if (slot == null || slot.getPlanTime() == null) {
                continue;
            }
            if (!slot.getPlanTime().isAfter(executionDeadline)) {
                filtered.add(slot);
            }
        }
        return filtered;
    }
}
