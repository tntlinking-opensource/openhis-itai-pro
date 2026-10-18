/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.orderexecute.assembler;

import com.zxplat.application.inpatient.nursestation.orderexecute.support.NurseStationOrderExecuteListReadAssembler;
import com.zxplat.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecutePatientGroupDto;
import com.zxplat.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.application.shared.advice.InpatientOrderListSkeleton;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
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
            medicineCategoryTab, null, false, false);
    }

    /**
     * 医嘱执行列表装配（含计划点下界，供打印中心日期范围查询）。
     *
     * @param skeleton            过滤后的读模型行
     * @param medByEncounter      就诊 → 药品明细
     * @param svcByEncounter      就诊 → 诊疗明细
     * @param executionDeadline   计划点上界
     * @param executeTab          执行 Tab
     * @param encounterOrgIds     就诊 → 机构主键
     * @param medicineCategoryTab west / tcm
     * @param planTimeFrom        计划点下界（含）；可空
     * @return 患者分组列表
     */
    public List<NurseStationOrderExecutePatientGroupDto> assemblePatientGroups(
        List<InpatientOrderListSkeleton> skeleton,
        Map<Long, List<MedicationRequestListQueryModel>> medByEncounter,
        Map<Long, List<ServiceRequestListModel>> svcByEncounter,
        LocalDateTime executionDeadline,
        String executeTab,
        Map<Long, Long> encounterOrgIds,
        String medicineCategoryTab,
        LocalDateTime planTimeFrom) {
        return readAssembler.assemblePatientGroups(
            skeleton, medByEncounter, svcByEncounter, executionDeadline, executeTab, encounterOrgIds,
            medicineCategoryTab, planTimeFrom, false, true);
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

    /**
     * 正向执行写 Gate：请求状态须为 VERIFIED 或 STOPPED（与列表 Q-IP-EXE-001 一致）。
     */
    public boolean isRequestStatusExecutableForWrite(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail) {
        return readAssembler.isRequestStatusExecutableForWrite(order, medDetail, svcDetail);
    }
}
