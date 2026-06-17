/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.querysupport.procedure;

import com.openhis.health.module.common.assembler.order.InpatientOrderListMergeAssembler;
import com.openhis.health.module.common.validator.CurrentManagedWardLocationIdsResolver;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.module.querysupport.procedure.dto.NurseStationOrderExecutePatientGroupDto;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.module.querysupport.order.dto.InpatientOrderListSkeleton;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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
    private final EncounterContextFacade encounterContextFacade;
    private final NurseStationOrderExecuteListReadAssembler nurseStationOrderExecuteListReadAssembler;
    private final InpatientOrderListMergeAssembler inpatientOrderListMergeAssembler;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;

    public NurseStationOrderExecuteListResult listNurseStationOrderExecute(NurseStationOrderExecuteListQuery query) {
        try {
            assertListExecuteQueryValid(query);
            List<Long> managedWardIds = managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty();
            List<Long> requestedEncounterIds = distinctEncounterIds(query.getEncounterIds());
            InpatientEncounterPageQuery visibilityQuery =
                nurseStationOrderExecuteListReadAssembler.toInpatientEncounterPageQuery(requestedEncounterIds, managedWardIds);
            List<InpatientEncounterContextQueryModel> visibleRows =
                encounterContextFacade.listInpatientEncounterContexts(visibilityQuery);
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
            List<InpatientOrderListSkeleton> skeleton = inpatientOrderListMergeAssembler.mergeNurseStationExecuteOrders(
                visibleEncounterIds, therapyEnum);
            String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
            Map<Long, List<MedicationRequestListQueryModel>> medByEncounter = new HashMap<>();
            Map<Long, List<ServiceRequestListModel>> svcByEncounter = new HashMap<>();
            for (Long encounterId : visibleEncounterIds) {
                medByEncounter.put(encounterId,
                    medicationRequestQueryService.listMedicationRequestsByEncounterForDoctorStation(
                        encounterId, doctorSource));
                svcByEncounter.put(encounterId,
                    serviceRequestQueryService.listServiceRequestsByGenerateSource(encounterId, doctorSource));
            }
            List<NurseStationOrderExecutePatientGroupDto> groups =
                nurseStationOrderExecuteListReadAssembler.assemblePatientGroups(
                    skeleton,
                    medByEncounter,
                    svcByEncounter,
                    query.getExecutionDeadline(),
                    query.getExecuteTab(),
                    encounterOrgIds);
            return nurseStationOrderExecuteListReadAssembler.toListResult(groups);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_EXE_101,
                "医嘱执行列表加载失败，请稍后重试");
        }
    }

    private void assertListExecuteQueryValid(NurseStationOrderExecuteListQuery query) {
        if (query == null || query.getEncounterIds() == null || query.getEncounterIds().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请选择至少一名患者");
        }
        if (query.getExecutionDeadline() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "截止时间不能为空");
        }
        String tab = StringUtils.trimToEmpty(query.getExecuteTab());
        if (StringUtils.isBlank(tab) || !isAllowedExecuteTab(tab)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "执行 Tab 不合法");
        }
        String therapy = query.getTherapyType();
        if (StringUtils.isBlank(therapy)) {
            return;
        }
        if (!isAllowedTherapyFilter(StringUtils.trimToEmpty(therapy))) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "长临筛选不合法");
        }
    }

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

    private static boolean isAllowedExecuteTab(String tab) {
        return InpatientNurseStationConstants.EXECUTE_TAB_PENDING.equals(tab)
            || InpatientNurseStationConstants.EXECUTE_TAB_EXECUTED.equals(tab)
            || InpatientNurseStationConstants.EXECUTE_TAB_SKIPPED.equals(tab)
            || InpatientNurseStationConstants.EXECUTE_TAB_CANCELLED.equals(tab);
    }

    private static boolean isAllowedTherapyFilter(String therapy) {
        return InpatientNurseStationConstants.THERAPY_FILTER_ALL.equals(therapy)
            || InpatientNurseStationConstants.THERAPY_FILTER_LONG.equals(therapy)
            || InpatientNurseStationConstants.THERAPY_FILTER_TEMP.equals(therapy);
    }
}
