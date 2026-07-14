/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderexecute.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.application.shared.advice.InpatientOrderListMergeAssembler;
import com.openhis.health.application.shared.validator.CurrentManagedWardLocationIdsResolver;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecutePatientGroupDto;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.application.shared.advice.InpatientOrderListSkeleton;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院护士站医嘱执行列表读路径（IF-IP-EXE-01），供 AppService 与 report-adapter 共用。
 */
@Component
@RequiredArgsConstructor
public class NurseStationOrderExecuteListLoader {

    private final CurrentManagedWardLocationIdsResolver managedWardLocationIdsResolver;
    private final EncounterContextReader EncounterContextReader;
    private final NurseStationOrderExecuteListReadAssembler nurseStationOrderExecuteListReadAssembler;
    private final InpatientOrderListMergeAssembler inpatientOrderListMergeAssembler;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;

    private final InpatientNurseStationTcmOrchestrator inpatientNurseStationTcmOrchestrator;

    /**
     * 加载住院护士站医嘱执行列表（IF-IP-EXE-01）。
     *
     * @param query 须含 encounterIds、executionDeadline、executeTab
     * @return 按就诊分组的执行列表
     */
    public NurseStationOrderExecuteListResult listNurseStationOrderExecute(NurseStationOrderExecuteListQuery query) {
        try {
            assertListExecuteQueryValid(query);
            // Gate：解析管辖病区并校验所选就诊可见（BR-IP-TRC-001）
            List<Long> managedWardIds = managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty();
            List<Long> requestedEncounterIds = distinctEncounterIds(query.getEncounterIds());
            InpatientEncounterPageQuery visibilityQuery =
                nurseStationOrderExecuteListReadAssembler.toInpatientEncounterPageQuery(requestedEncounterIds, managedWardIds);
            List<InpatientEncounterContextQueryModel> visibleRows =
                EncounterContextReader.listInpatientEncounterContexts(visibilityQuery);
            List<Long> visibleEncounterIds = retainRequestedVisibleEncounterIds(requestedEncounterIds, visibleRows);
            if (visibleEncounterIds.isEmpty()) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_EXE_102,
                    "所选患者不在护士站可见范围");
            }
            Set<Long> visibleEncounterIdSet = new HashSet<>(visibleEncounterIds);
            List<InpatientEncounterContextQueryModel> matchedVisibleRows = new ArrayList<>();
            for (InpatientEncounterContextQueryModel row : visibleRows) {
                if (row == null || row.getEncounterId() == null) {
                    continue;
                }
                if (visibleEncounterIdSet.contains(row.getEncounterId())) {
                    matchedVisibleRows.add(row);
                }
            }
            Map<Long, Long> encounterOrgIds = indexEncounterOrgIds(matchedVisibleRows);
            String therapyEnum = nurseStationOrderExecuteListReadAssembler.resolveTherapyEnum(query.getTherapyType());
            String medicineCategoryTab = inpatientNurseStationTcmOrchestrator.resolveMedicineCategoryTabOrDefault(
                query.getMedicineCategoryTab());
            // Pre：合并 nurse 站可见医嘱骨架（药/诊/材）
            List<InpatientOrderListSkeleton> skeleton = inpatientOrderListMergeAssembler.mergeNurseStationExecuteOrders(
                visibleEncounterIds, therapyEnum);
            String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
            Map<Long, List<MedicationRequestListQueryModel>> medByEncounter = new HashMap<>();
            Map<Long, List<ServiceRequestListModel>> svcByEncounter = new HashMap<>();
            // Pre：逐就诊加载药/诊明细（B.6.4 分聚合 QS；tcm Tab 时收窄 SERVICE_REQUEST）
            for (Long encounterId : visibleEncounterIds) {
                List<MedicationRequestListQueryModel> medRows =
                    medicationRequestQueryService.listMedicationRequestsByEncounterForDoctorStation(
                        encounterId, doctorSource);
                medRows = inpatientNurseStationTcmOrchestrator.filterMedRowsByMedicineCategory(medRows, medicineCategoryTab);
                medByEncounter.put(encounterId, medRows);
                if (inpatientNurseStationTcmOrchestrator.includeServiceRequestOnTab(medicineCategoryTab)) {
                    svcByEncounter.put(encounterId,
                        serviceRequestQueryService.listServiceRequestsByGenerateSource(encounterId, doctorSource));
                } else {
                    // 中药 Tab 不展示诊疗嘱执行槽位
                    svcByEncounter.put(encounterId, new ArrayList<>());
                }
            }
            Map<Long, MedicationRequestListQueryModel> medDetailById =
                inpatientNurseStationTcmOrchestrator.indexMedDetailById(medByEncounter);
            skeleton = inpatientNurseStationTcmOrchestrator.filterSkeletonByMedicineCategory(
                skeleton, medDetailById, medicineCategoryTab);
            // Asm：展开执行槽位并按 executeTab 收窄
            List<NurseStationOrderExecutePatientGroupDto> groups =
                nurseStationOrderExecuteListReadAssembler.assemblePatientGroups(
                    skeleton,
                    medByEncounter,
                    svcByEncounter,
                    query.getExecutionDeadline(),
                    query.getExecuteTab(),
                    encounterOrgIds,
                    medicineCategoryTab);
            return nurseStationOrderExecuteListReadAssembler.toListResult(groups);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_EXE_101,
                "医嘱执行列表加载失败，请稍后重试");
        }
    }

    /**
     * 医嘱执行列表查询入参门禁：就诊、截止时间、Tab 与长临筛选合法。
     *
     * @param query 列表查询
     * @throws BusinessException 必填项缺失或 Tab/长临/中药分类不合法
     */
    private void assertListExecuteQueryValid(NurseStationOrderExecuteListQuery query) {
        if (query == null || query.getEncounterIds() == null || query.getEncounterIds().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请选择至少一名患者");
        }
        if (query.getExecutionDeadline() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "截止时间不能为空");
        }
        String tab = trimToEmpty(query.getExecuteTab());
        if (StringUtils.isEmpty(tab) || !isAllowedExecuteTab(tab)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "执行 Tab 不合法");
        }
        String therapy = query.getTherapyType();
        if (StringUtils.isEmpty(therapy)) {
            return;
        }
        if (!isAllowedTherapyFilter(trimToEmpty(therapy))) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "长临筛选不合法");
        }
        inpatientNurseStationTcmOrchestrator.resolveMedicineCategoryTabOrDefault(query.getMedicineCategoryTab());
    }

    /**
     * 就诊主键去重并保持请求顺序。
     *
     * @param encounterIds 原始就诊 ID 列表
     * @return 去重后的列表
     */
    private static List<Long> distinctEncounterIds(List<Long> encounterIds) {
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        if (encounterIds != null) {
            for (Long id : encounterIds) {
                if (id != null) {
                    set.add(id);
                }
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * 将请求的就诊列表收窄为护士可见就诊子集（保持原顺序）。
     *
     * @param requestedEncounterIds 前端多选就诊
     * @param visibleRows           护士管理病区可见就诊
     * @return 可见就诊 ID 列表
     */
    private static List<Long> retainRequestedVisibleEncounterIds(
        List<Long> requestedEncounterIds,
        List<InpatientEncounterContextQueryModel> visibleRows) {
        if (requestedEncounterIds == null || requestedEncounterIds.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> visibleIdSet = new HashSet<>();
        if (visibleRows != null) {
            for (InpatientEncounterContextQueryModel row : visibleRows) {
                if (row != null && row.getEncounterId() != null) {
                    visibleIdSet.add(row.getEncounterId());
                }
            }
        }
        List<Long> result = new ArrayList<>();
        for (Long encounterId : requestedEncounterIds) {
            if (encounterId != null && visibleIdSet.contains(encounterId)) {
                result.add(encounterId);
            }
        }
        return result;
    }

    /**
     * 可见就诊行 → encounterId → organizationId 索引。
     *
     * @param rows 住院就诊上下文行
     * @return 就诊机构映射
     */
    private static Map<Long, Long> indexEncounterOrgIds(List<InpatientEncounterContextQueryModel> rows) {
        Map<Long, Long> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (InpatientEncounterContextQueryModel row : rows) {
            if (row != null && row.getEncounterId() != null) {
                map.put(row.getEncounterId(), row.getOrganizationId());
            }
        }
        return map;
    }

    /**
     * 执行列表 Tab code 是否合法（待执行/已执行/跳过/已取消）。
     *
     * @param tab Tab code
     * @return 合法为 true
     */
    private static boolean isAllowedExecuteTab(String tab) {
        return InpatientNurseStationConstants.EXECUTE_TAB_PENDING.equals(tab)
            || InpatientNurseStationConstants.EXECUTE_TAB_EXECUTED.equals(tab)
            || InpatientNurseStationConstants.EXECUTE_TAB_SKIPPED.equals(tab)
            || InpatientNurseStationConstants.EXECUTE_TAB_CANCELLED.equals(tab);
    }

    /**
     * 长临筛选 code 是否合法（全部/长期/临时）。
     *
     * @param therapy 长临筛选 code
     * @return 合法为 true
     */
    private static boolean isAllowedTherapyFilter(String therapy) {
        return InpatientNurseStationConstants.THERAPY_FILTER_ALL.equals(therapy)
            || InpatientNurseStationConstants.THERAPY_FILTER_LONG.equals(therapy)
            || InpatientNurseStationConstants.THERAPY_FILTER_TEMP.equals(therapy);
    }
}
