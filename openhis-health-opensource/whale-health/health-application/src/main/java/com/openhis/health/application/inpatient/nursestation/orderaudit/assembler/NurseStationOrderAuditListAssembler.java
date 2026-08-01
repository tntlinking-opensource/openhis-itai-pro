/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderaudit.assembler;

import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderAuditPatientGroupDto;
import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderAuditRowDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.support.InpatientNurseStationTcmOrchestrator;
import com.openhis.health.application.inpatient.nursestation.orderaudit.support.NurseStationOrderViewListAssembler;
import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderViewDisplayContext;
import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderViewDisplayCore;
import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderViewListDetailIndex;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.application.shared.advice.InpatientOrderListSkeleton;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
        Map<Long, List<NurseStationOrderAuditRowDto>> rowsByEncounter = new LinkedHashMap<>();
        for (InpatientOrderListSkeleton order : skeleton) {
            if (order == null || order.getEncounterId() == null) {
                continue;
            }
            NurseStationOrderAuditRowDto line = this.mapOrderRow(order, index, displayContext);
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
     * 将单条 OrderInfo 映射为校对列表行。
     *
     * @return 列表行；非药/诊类型返回 null
     */
    private NurseStationOrderAuditRowDto mapOrderRow(
        InpatientOrderListSkeleton order,
        NurseStationOrderViewListDetailIndex index,
        NurseStationOrderViewDisplayContext displayContext) {
        Long requestId = orderViewListAssembler.resolveRequestId(order);
        NurseStationOrderViewDisplayCore core;
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            core = orderViewListAssembler.buildMedicationDisplayCore(
                order, index.getMedDetailById().get(requestId), displayContext);
        } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            core = orderViewListAssembler.buildServiceDisplayCore(
                order, index.getSvcDetailById().get(requestId), displayContext);
        } else {
            return null;
        }
        if (core == null) {
            return null;
        }
        NurseStationOrderAuditRowDto line = new NurseStationOrderAuditRowDto();
        this.applyViewCoreToAuditRow(core, line);
        return line;
    }

    /**
     * 医嘱查看展示内核 → 校对列表行 DTO。
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
        line.setDosageDisplay(core.getDosageDisplay());
        line.setUsageDisplay(core.getUsageDisplay());
        line.setFrequencyDisplay(core.getFrequencyDisplay());
        line.setReqOpenedTime(core.getReqOpenedTime());
        line.setReqAuthoredTime(core.getReqAuthoredTime());
        line.setStopTime(core.getStopTime());
        line.setRequesterName(core.getRequesterName());
        line.setStopperId(core.getStopperId());
        line.setStopperName(core.getStopperName());
        line.setPerformerCheckName(core.getPerformerCheckName());
        line.setPerformerLocationDisplay(core.getPerformerLocationDisplay());
        line.setGroupId(core.getGroupId());
        line.setGroupNo(core.getGroupNo());
        line.setSortNumber(core.getSortNumber());
        line.setSkinTestFlag(core.getSkinTestFlag());
        line.setBasedOnMedicationRequestId(core.getBasedOnMedicationRequestId());
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
