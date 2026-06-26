/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.orderexecute.assembler;

import com.openhis.health.module.querysupport.nursestation.NurseStationOrderExecuteListReadAssembler;
import com.openhis.health.module.querysupport.nursestation.dto.NurseStationOrderExecutePatientGroupDto;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.module.querysupport.order.dto.InpatientOrderListSkeleton;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 住院护士站医嘱执行列表装配：读路径委托 {@link NurseStationOrderExecuteListReadAssembler}，写 Gate 保留在本类。
 */
@Component
@RequiredArgsConstructor
public class NurseStationOrderExecuteListAssembler {

    private final NurseStationOrderExecuteListReadAssembler readAssembler;

    public List<NurseStationOrderExecutePatientGroupDto> assemblePatientGroups(
        List<InpatientOrderListSkeleton> skeleton,
        Map<Long, List<MedicationRequestListQueryModel>> medByEncounter,
        Map<Long, List<ServiceRequestListModel>> svcByEncounter,
        LocalDateTime executionDeadline,
        String executeTab,
        Map<Long, Long> encounterOrgIds,
        String medicineCategoryTab) {
        return readAssembler.assemblePatientGroups(
            skeleton, medByEncounter, svcByEncounter, executionDeadline, executeTab, encounterOrgIds,
            medicineCategoryTab);
    }

    /**
     * 不执行写 Gate：occurrence 净态须为 pending。
     */
    public boolean isPlanTimePending(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        LocalDateTime planTime,
        List<ProcedureRecordQueryModel> procedures) {
        return readAssembler.isPlanTimePending(order, medDetail, svcDetail, planTime, procedures);
    }

    /**
     * 正向执行写 Gate：occurrence 净态为 pending 或 cancelled 时可写。
     */
    public boolean isPlanTimeExecutable(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        LocalDateTime planTime,
        List<ProcedureRecordQueryModel> procedures) {
        return readAssembler.isPlanTimeExecutable(order, medDetail, svcDetail, planTime, procedures);
    }

    public boolean isPlanTimeAfterStopTime(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail,
        LocalDateTime planTime) {
        return readAssembler.isPlanTimeAfterStopTime(order, medDetail, svcDetail, planTime);
    }
}
