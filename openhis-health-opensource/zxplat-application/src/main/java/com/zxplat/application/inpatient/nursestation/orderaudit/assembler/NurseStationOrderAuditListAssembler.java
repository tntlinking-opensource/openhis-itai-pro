/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.orderaudit.assembler;

import com.zxplat.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderAuditPatientGroupDto;
import com.zxplat.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderAuditRowDto;
import com.zxplat.application.inpatient.nursestation.orderexecute.support.InpatientNurseStationTcmOrchestrator;
import com.zxplat.application.inpatient.nursestation.orderaudit.support.NurseStationOrderViewListAssembler;
import com.zxplat.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderViewDisplayContext;
import com.zxplat.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderViewDisplayCore;
import com.zxplat.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderViewListDetailIndex;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.application.shared.advice.AdviceExtensionJsonParser;
import com.zxplat.application.shared.advice.AdviceExtensionJsonParser.AdvicePackageChildItemDto;
import com.zxplat.application.shared.advice.InpatientOrderListSkeleton;
import com.zxplat.domain.share.constants.ReadModelOrderTypeConstants;
import com.zxplat.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 住院护士站医嘱校对列表装配（IF-IP-AUD-01 Asm）。
 *
 * <p>在 OrderInfo 骨架上叠加药/诊明细与展示名；行展示委托
 * {@link NurseStationOrderViewListAssembler}；组内按开立时间倒序；
 * 不返回余额/费别（BR-IP-AUD-007）。</p>
 */
@Component
@RequiredArgsConstructor
public class NurseStationOrderAuditListAssembler {

    /** 医嘱查看列表行展示内核。 */
    private final NurseStationOrderViewListAssembler orderViewListAssembler;

    /** 中药 Tab 筛选与方级折叠。 */
    private final InpatientNurseStationTcmOrchestrator inpatientNurseStationTcmOrchestrator;

    /** 诊疗活动定义只读：校对行挂载带子项目录（列表不展示，供医嘱单打印）。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

    /**
     * OrderInfo 骨架 + 域明细 → 按就诊分组且组内已排序的列表。
     *
     * @param skeleton       过滤后的读模型行
     * @param medByEncounter       就诊 → 药品明细列表
     * @param svcByEncounter       就诊 → 诊疗明细列表
     * @param medicineCategoryTab  west / tcm
     * @return 患者分组列表（BR-IP-AUD-005）
     */
    public List<NurseStationOrderAuditPatientGroupDto> assemblePatientGroups(
        List<InpatientOrderListSkeleton> skeleton,
        Map<Long, List<MedicationRequestListQueryModel>> medByEncounter,
        Map<Long, List<ServiceRequestListModel>> svcByEncounter,
        String medicineCategoryTab) {
        if (skeleton == null || skeleton.isEmpty()) {
            return new ArrayList<>();
        }
        NurseStationOrderViewListDetailIndex index =
            orderViewListAssembler.indexEncounterDetails(skeleton, medByEncounter, svcByEncounter);
        NurseStationOrderViewDisplayContext displayContext =
            orderViewListAssembler.prepareDisplayContext(index);
        // Pre：皮试药最新登记结果（列表药名后展示阴/阳性，与医生站 skinTestOutcome* 同源）
        orderViewListAssembler.enrichSkinTestOutcomes(displayContext, medByEncounter);
        Map<Long, List<AdvicePackageChildItemDto>> packageChildrenByActivityId =
            this.loadPackageChildrenByActivityId(index.getSvcDetailById().values());
        Map<Long, List<NurseStationOrderAuditRowDto>> rowsByEncounter = new LinkedHashMap<>();
        for (InpatientOrderListSkeleton order : skeleton) {
            if (order == null || order.getEncounterId() == null) {
                continue;
            }
            NurseStationOrderAuditRowDto line = this.mapOrderRow(
                order, index, displayContext, packageChildrenByActivityId);
            if (line == null) {
                continue;
            }
            List<NurseStationOrderAuditRowDto> bucket = rowsByEncounter.get(order.getEncounterId());
            if (bucket == null) {
                bucket = new ArrayList<>();
                rowsByEncounter.put(order.getEncounterId(), bucket);
            }
            bucket.add(line);
        }
        List<NurseStationOrderAuditPatientGroupDto> groups = new ArrayList<>();
        Map<Long, MedicationRequestListQueryModel> medDetailById =
            inpatientNurseStationTcmOrchestrator.indexMedDetailById(medByEncounter);
        for (Map.Entry<Long, List<NurseStationOrderAuditRowDto>> entry : rowsByEncounter.entrySet()) {
            List<NurseStationOrderAuditRowDto> sorted = this.sortRows(entry.getValue());
            // Asm：中药 Tab 按 prescription_no 折叠为一方一行
            List<NurseStationOrderAuditRowDto> collapsed =
                inpatientNurseStationTcmOrchestrator.collapseTcmPrescriptionDisplayRows(
                    sorted,
                    medDetailById,
                    medicineCategoryTab,
                    AUDIT_TCM_ROW_ACCESSOR);
            NurseStationOrderAuditPatientGroupDto group = new NurseStationOrderAuditPatientGroupDto();
            group.setEncounterId(entry.getKey());
            group.setOrders(collapsed);
            groups.add(group);
        }
        return groups;
    }

    private static final InpatientNurseStationTcmOrchestrator.TcmPrescriptionRowAccessor<NurseStationOrderAuditRowDto>
        AUDIT_TCM_ROW_ACCESSOR = new InpatientNurseStationTcmOrchestrator.TcmPrescriptionRowAccessor<>() {
        @Override
        public Long getRequestId(NurseStationOrderAuditRowDto row) {
            return row.getRequestId();
        }

        @Override
        public Long getEncounterId(NurseStationOrderAuditRowDto row) {
            return row.getEncounterId();
        }

        @Override
        public String getOrderType(NurseStationOrderAuditRowDto row) {
            return row.getOrderType();
        }

        @Override
        public Integer getSortNumber(NurseStationOrderAuditRowDto row) {
            return row.getSortNumber();
        }

        @Override
        public String getAdviceDisplayName(NurseStationOrderAuditRowDto row) {
            return row.getAdviceDisplayName();
        }

        @Override
        public String getDosageDisplay(NurseStationOrderAuditRowDto row) {
            return row.getDosageDisplay();
        }

        @Override
        public void applyTcmPrescriptionCollapse(
            NurseStationOrderAuditRowDto representative,
            MedicationRequestListQueryModel repMed,
            List<Long> lineRequestIds,
            String adviceDisplayName,
            String dosageDisplay,
            LocalDateTime prescriptionOpenedTime) {
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
            // 方级开立时间覆盖为代表行展示用最晚时刻（BR-IP-NUR-003-TCM-002）
            if (prescriptionOpenedTime != null) {
                representative.setReqOpenedTime(prescriptionOpenedTime);
            }
        }
    };

    /**
     * 将单条 OrderInfo 映射为校对列表行。
     *
     * @param order                        医嘱骨架行
     * @param index                        药/诊明细索引
     * @param displayContext               展示预热上下文
     * @param packageChildrenByActivityId  活动定义 → 目录子项（诊嘱打印用）
     * @return 列表行；非药/诊类型返回 null
     */
    private NurseStationOrderAuditRowDto mapOrderRow(
        InpatientOrderListSkeleton order,
        NurseStationOrderViewListDetailIndex index,
        NurseStationOrderViewDisplayContext displayContext,
        Map<Long, List<AdvicePackageChildItemDto>> packageChildrenByActivityId) {
        Long requestId = orderViewListAssembler.resolveRequestId(order);
        NurseStationOrderViewDisplayCore core;
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            // 药嘱：剂量/皮试等展示走药品内核
            core = orderViewListAssembler.buildMedicationDisplayCore(
                order, index.getMedDetailById().get(requestId), displayContext);
        } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            // 诊嘱：活动名/长临等展示走诊疗内核
            core = orderViewListAssembler.buildServiceDisplayCore(
                order, index.getSvcDetailById().get(requestId), displayContext);
        } else {
            // 转科/出院等非药诊不进校对列表
            return null;
        }
        if (core == null) {
            // 明细缺失则本行无法展示
            return null;
        }
        NurseStationOrderAuditRowDto line = new NurseStationOrderAuditRowDto();
        this.applyViewCoreToAuditRow(core, line);
        // 方号：临时医嘱打印把同申请单的麻醉药品收进「麻醉」筛选（中药折叠会再覆盖方号）
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            MedicationRequestListQueryModel med = index.getMedDetailById().get(requestId);
            // 药嘱方号：麻醉药品与麻醉诊疗同号，供打印筛选收口
            if (med != null) {
                line.setPrescriptionNo(med.getPrescriptionNo());
            }
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            ServiceRequestListModel svc = index.getSvcDetailById().get(requestId);
            // 诊疗申请号：手术/麻醉行带出方号，与同单药品对齐
            if (svc != null) {
                line.setPrescriptionNo(svc.getPrescriptionNo());
            }
            List<AdvicePackageChildItemDto> found = null;
            if (svc != null && svc.getActivityId() != null && packageChildrenByActivityId != null) {
                found = packageChildrenByActivityId.get(svc.getActivityId());
            }
            if (found != null) {
                // 诊嘱挂目录子项供医嘱单打印；校对表格不渲染
                line.setPackageChildren(found);
            }
        }
        return line;
    }

    /**
     * Pre：按本批诊嘱 activityId 批量加载目录子项（禁逐行查库）。
     *
     * @param svcDetails 本批诊疗明细
     * @return activityId → 子项；无子项时对应键可缺省
     */
    private Map<Long, List<AdvicePackageChildItemDto>> loadPackageChildrenByActivityId(
        Collection<ServiceRequestListModel> svcDetails) {
        if (svcDetails == null || svcDetails.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> activityIds = new LinkedHashSet<>();
        for (ServiceRequestListModel svc : svcDetails) {
            if (svc != null && svc.getActivityId() != null) {
                activityIds.add(svc.getActivityId());
            }
        }
        if (activityIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<ActivityDefinitionChildQueryModel>> modelsByActivityId =
            activityDefinitionQueryService.listChildrenByActivityIds(activityIds);
        Map<Long, List<AdvicePackageChildItemDto>> result = new HashMap<>();
        for (Map.Entry<Long, List<ActivityDefinitionChildQueryModel>> entry : modelsByActivityId.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result.put(entry.getKey(), AdviceExtensionJsonParser.toPackageChildItems(entry.getValue()));
        }
        return result;
    }

    /**
     * 医嘱查看展示内核 → 校对列表行 DTO（含停嘱校对痕迹与待校标记）。
     *
     * @param core 展示内核；非空
     * @param line 校对列表行；非空，由调用方新建
     */
    private void applyViewCoreToAuditRow(NurseStationOrderViewDisplayCore core, NurseStationOrderAuditRowDto line) {
        line.setListRowKey(core.getListRowKey());
        line.setRequestId(core.getRequestId());
        line.setOrderType(core.getOrderType());
        line.setEncounterId(core.getEncounterId());
        line.setPatientId(core.getPatientId());
        line.setStatusEnum(core.getStatusEnum());
        line.setStatusDisplay(core.getStatusDisplay());
        line.setTherapyEnum(core.getTherapyEnum());
        line.setTherapyDisplay(core.getTherapyDisplay());
        line.setCategoryEnum(core.getCategoryEnum());
        line.setOrderKindDisplay(core.getOrderKindDisplay());
        line.setAdviceDisplayName(core.getAdviceDisplayName());
        line.setRemark(core.getRemark());
        line.setSpecificationText(core.getSpecificationText());
        line.setPartPercent(core.getPartPercent());
        line.setPackageSplitRatioText(core.getPackageSplitRatioText());
        line.setDosageDisplay(core.getDosageDisplay());
        line.setDailyDoseDisplay(core.getDailyDoseDisplay());
        // 首日用量：与 OrderView 内核同字段拷贝，供校对列表剂量列展示
        line.setFirstDose(core.getFirstDose());
        line.setFirstDoseUnitCode(core.getFirstDoseUnitCode());
        line.setFirstDoseUnitName(core.getFirstDoseUnitName());
        line.setDoseSchedule(core.getDoseSchedule());
        line.setQuantitySchedule(core.getQuantitySchedule());
        line.setUsageDisplay(core.getUsageDisplay());
        line.setFrequencyDisplay(core.getFrequencyDisplay());
        line.setSpeedCode(core.getSpeedCode());
        line.setSpeedDisplay(core.getSpeedDisplay());
        line.setReqOpenedTime(core.getReqOpenedTime());
        line.setReqAuthoredTime(core.getReqAuthoredTime());
        line.setStopTime(core.getStopTime());
        line.setRequesterName(core.getRequesterName());
        line.setStopperId(core.getStopperId());
        line.setStopperName(core.getStopperName());
        line.setPerformerCheckName(core.getPerformerCheckName());
        line.setStopPerformerCheckId(core.getStopPerformerCheckId());
        line.setStopCheckTime(core.getStopCheckTime());
        line.setStopCheckerName(core.getStopCheckerName());
        line.setStopAuditPending(core.getStopAuditPending());
        line.setPerformerLocationDisplay(core.getPerformerLocationDisplay());
        line.setGroupId(core.getGroupId());
        line.setGroupNo(core.getGroupNo());
        line.setSortNumber(core.getSortNumber());
        line.setSkinTestFlag(core.getSkinTestFlag());
        line.setBasedOnMedicationRequestId(core.getBasedOnMedicationRequestId());
        line.setSkinTestOutcomeCode(core.getSkinTestOutcomeCode());
        line.setSkinTestOutcomeText(core.getSkinTestOutcomeText());
        // 出院医嘱：计划出院时间与原因（医生填写；校对列表内容扩展行展示）
        line.setPlannedDischargeTime(core.getPlannedDischargeTime());
        line.setReasonText(core.getReasonText());
    }

    /**
     * 组内排序：开立时间（{@code reqOpenedTime}，空回落签发）倒序；成组药按组内最晚开立时刻聚邻；
     * 皮试检查 companion 与 based_on 皮试药同簇（药在前）。
     *
     * @param rows 未排序行
     * @return 排序后列表
     */
    private List<NurseStationOrderAuditRowDto> sortRows(List<NurseStationOrderAuditRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<NurseStationOrderAuditRowDto> copy = new ArrayList<>(rows);
        Map<Long, LocalDateTime> groupMaxOpenedTime = new HashMap<>();
        Map<Long, LocalDateTime> medicationOpenedTimeByRequestId = new HashMap<>();
        for (NurseStationOrderAuditRowDto row : copy) {
            LocalDateTime sortTime = this.resolveRowOpenedTimeForSort(row);
            Long groupId = row.getGroupId();
            if (groupId != null && sortTime != null) {
                LocalDateTime existing = groupMaxOpenedTime.get(groupId);
                // 组内取最晚开立时刻，保证同组校对行连续展示。
                if (existing == null || sortTime.isAfter(existing)) {
                    groupMaxOpenedTime.put(groupId, sortTime);
                }
            }
            if (ReadModelOrderTypeConstants.MED_REQUEST.equals(row.getOrderType())
                && row.getRequestId() != null
                && sortTime != null) {
                medicationOpenedTimeByRequestId.put(row.getRequestId(), sortTime);
            }
        }
        copy.sort(Comparator
            .comparing((NurseStationOrderAuditRowDto row) ->
                    this.resolveAuditClusterOpenedTime(row, groupMaxOpenedTime, medicationOpenedTimeByRequestId),
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(NurseStationOrderAuditRowDto::getGroupId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(this::resolveAuditSkinTestClusterKey, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(this::resolveAuditSkinTestCompanionRank)
            .thenComparing(NurseStationOrderAuditRowDto::getRequestId, Comparator.nullsLast(Comparator.naturalOrder())));
        return copy;
    }

    /**
     * 校对列表簇排序键：成组药用组内最晚开立时刻；皮试 companion 用锚定皮试药开立时刻。
     *
     * @param row                              校对行
     * @param groupMaxOpenedTime               成组 groupId → 最晚开立时刻
     * @param medicationOpenedTimeByRequestId  药品 requestId → 开立时刻
     * @return 簇排序时刻；无时刻时为 null
     */
    private LocalDateTime resolveAuditClusterOpenedTime(
        NurseStationOrderAuditRowDto row,
        Map<Long, LocalDateTime> groupMaxOpenedTime,
        Map<Long, LocalDateTime> medicationOpenedTimeByRequestId) {
        // companion：跟锚皮试药同簇
        if (row != null && row.getBasedOnMedicationRequestId() != null
            && medicationOpenedTimeByRequestId != null) {
            LocalDateTime linked = medicationOpenedTimeByRequestId.get(row.getBasedOnMedicationRequestId());
            if (linked != null) {
                return linked;
            }
        }
        if (row != null && row.getGroupId() != null) {
            LocalDateTime groupOpened = groupMaxOpenedTime.get(row.getGroupId());
            if (groupOpened != null) {
                return groupOpened;
            }
        }
        return this.resolveRowOpenedTimeForSort(row);
    }

    /**
     * 皮试聚邻键：皮试药=自身；companion=basedOn；其它 null。
     *
     * @param row 校对行
     * @return 聚邻键
     */
    private Long resolveAuditSkinTestClusterKey(NurseStationOrderAuditRowDto row) {
        if (row == null) {
            return null;
        }
        if (row.getBasedOnMedicationRequestId() != null) {
            return row.getBasedOnMedicationRequestId();
        }
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(row.getOrderType())
            && Boolean.TRUE.equals(row.getSkinTestFlag())) {
            return row.getRequestId();
        }
        return null;
    }

    /**
     * 同皮试簇内次序：皮试药 0，companion 1，其它 2。
     *
     * @param row 校对行
     * @return 次序
     */
    private int resolveAuditSkinTestCompanionRank(NurseStationOrderAuditRowDto row) {
        if (row == null) {
            return 2;
        }
        if (row.getBasedOnMedicationRequestId() != null) {
            return 1;
        }
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(row.getOrderType())
            && Boolean.TRUE.equals(row.getSkinTestFlag())) {
            return 0;
        }
        return 2;
    }

    /**
     * 行排序时刻：优先 {@code reqOpenedTime}，空回落 {@code reqAuthoredTime}。
     *
     * @param row 校对行
     * @return 排序时刻；均空时 null
     */
    private LocalDateTime resolveRowOpenedTimeForSort(NurseStationOrderAuditRowDto row) {
        if (row == null) {
            return null;
        }
        if (row.getReqOpenedTime() != null) {
            return row.getReqOpenedTime();
        }
        return row.getReqAuthoredTime();
    }
}
