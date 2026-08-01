/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.dispensesummary.mapper;

import com.openhis.health.application.inpatient.nursestation.dispensesummary.assembler.NurseStationDispenseSummaryWriteAssembler.SummaryMergeGroup;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummaryPatientGroupDto;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummaryPatientPickupGroupDto;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummarySlipHeaderDto;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummarySlipLineDto;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.request.PageMedicationSummarySlipHeadersRequest;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.request.PagePendingDispenseSummaryLinesRequest;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.response.CancelMedicationSummaryResponse;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.response.CreateMedicationSummaryResponse;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.response.GetMedicationSummarySlipDetailResponse;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.response.GetMedicationSummarySlipPatientDetailResponse;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.response.PageMedicationSummarySlipHeadersResponse;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.response.PagePendingDispenseSummaryLinesResponse;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.response.SubmitMedicationSummaryResponse;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.medication.medicationdispense.command.CancelMedicationDispenseSummaryCommand;
import com.openhis.health.domain.medication.medicationdispense.command.SummarizeMedicationDispensesCommand;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispensePickupPendingQueryModel;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispensePickupPendingPageQuery;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateSummarySupplyRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.MedicationSummarySlipQueryModel;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 住院护士站汇总领药 Request/Query/Command/Response 映射（AS-IP-NUR-010）。
 */
public interface NurseStationDispenseSummaryMapper {

    /**
     * 可见性查询入参（BR-IP-SUM-001）。
     */
    InpatientEncounterPageQuery toInpatientEncounterPageQuery(List<Long> encounterIds, List<Long> managedWardIds);

    /**
     * therapyFilter → TherapyType code；all 返回 null。
     */
    String resolveTherapyEnum(String therapyFilter);

    /**
     * IF-IP-SUM-01 域分页查询入参。
     */
    MedicationDispensePickupPendingPageQuery toPickupPendingPageQuery(
        PagePendingDispenseSummaryLinesRequest request,
        List<Long> visibleEncounterIds,
        List<Long> resolvedMedReqIds);

    /**
     * IF-IP-SUM-01 分页响应。
     */
    PagePendingDispenseSummaryLinesResponse toPagePendingDispenseSummaryLinesResponse(
        Page<MedicationDispensePickupPendingQueryModel> page,
        List<NurseStationDispenseSummaryPatientGroupDto> groups);

    /**
     * IF-IP-SUM-02 汇总单头列表查询入参。
     */
    SupplyRequestDocumentListQuery toSupplyRequestDocumentListQuery(
        PageMedicationSummarySlipHeadersRequest request,
        List<Long> purposeLocationIds);

    /**
     * 域汇总单头 → DTO（含展示字段）。
     */
    List<NurseStationDispenseSummarySlipHeaderDto> toSlipHeaderDtos(Page<MedicationSummarySlipQueryModel> page);

    /**
     * IF-IP-SUM-02 分页响应。
     */
    PageMedicationSummarySlipHeadersResponse toPageMedicationSummarySlipHeadersResponse(
        Page<MedicationSummarySlipQueryModel> page,
        List<NurseStationDispenseSummarySlipHeaderDto> rows);

    /**
     * IF-IP-SUM-03 详情响应。
     */
    GetMedicationSummarySlipDetailResponse toGetMedicationSummarySlipDetailResponse(
        String summaryNo,
        NurseStationDispenseSummarySlipHeaderDto header,
        List<NurseStationDispenseSummarySlipLineDto> lines);

    /**
     * IF-IP-SUM-09 摆药单查询响应（按患者分组、同 medReqId 合并）。
     */
    GetMedicationSummarySlipPatientDetailResponse toGetMedicationSummarySlipPatientDetailResponse(
        String summaryNo,
        NurseStationDispenseSummarySlipHeaderDto header,
        List<NurseStationDispenseSummaryPatientPickupGroupDto> patientGroups);

    /**
     * 发放汇总命令。
     */
    SummarizeMedicationDispensesCommand toSummarizeMedicationDispensesCommand(
        Long locationId,
        List<Long> dispenseIds,
        Long operatorId,
        String summaryNo);

    /**
     * 汇总供应请求命令（初态 PENDING_APPROVAL，不写 applyTime）。
     */
    CreateSummarySupplyRequestCommand toCreateSummarySupplyRequestCommand(
        String summaryNo,
        List<SummaryMergeGroup> groups,
        Long applicantId,
        Long operatorId);

    /**
     * IF-IP-SUM-04 写响应。
     */
    CreateMedicationSummaryResponse toCreateMedicationSummaryResponse(List<String> summaryNos);

    /**
     * IF-IP-SUM-05 提交审批命令。
     */
    SubmitApprovalCommand toSubmitApprovalCommand(String summaryNo, Long applicantId, Long operatorId);

    /**
     * IF-IP-SUM-05 写响应。
     */
    SubmitMedicationSummaryResponse toSubmitMedicationSummaryResponse(List<String> summaryNos);

    /**
     * IF-IP-SUM-08 取消发放汇总命令。
     */
    CancelMedicationDispenseSummaryCommand toCancelMedicationDispenseSummaryCommand(
        String summaryNo,
        Long operatorId);

    /**
     * IF-IP-SUM-08 逻辑删除汇总供应单命令。
     */
    RemoveByBusNoCommand toRemoveByBusNoCommand(String summaryNo, Long operatorId);

    /**
     * IF-IP-SUM-08 写响应。
     */
    CancelMedicationSummaryResponse toCancelMedicationSummaryResponse(List<String> summaryNos);
}
