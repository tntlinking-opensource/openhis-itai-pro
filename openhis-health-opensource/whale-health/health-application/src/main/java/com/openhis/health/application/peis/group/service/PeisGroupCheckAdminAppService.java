/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.service;

import com.openhis.health.application.peis.group.dto.BatchRowDto;
import com.openhis.health.application.peis.group.dto.CompanyRowDto;
import com.openhis.health.application.peis.group.dto.ContractRowDto;
import com.openhis.health.application.peis.group.dto.EnrollmentFailureRowDto;
import com.openhis.health.application.peis.group.dto.ContractReconciliationRowDto;
import com.openhis.health.application.peis.group.dto.GroupRosterDeskContextDto;
import com.openhis.health.application.peis.group.request.LookupGroupRosterForDeskRequest;
import com.openhis.health.application.peis.group.response.LookupGroupRosterForDeskResponse;
import com.openhis.health.application.peis.group.dto.GroupRosterImportFileResponse;
import com.openhis.health.application.peis.group.dto.GroupRosterRowDto;
import com.openhis.health.application.peis.group.request.ConfirmBatchEnrollmentRequest;
import com.openhis.health.application.peis.group.request.LoadRosterSummaryRequest;
import com.openhis.health.application.peis.group.response.ConfirmBatchEnrollmentResponse;
import com.openhis.health.application.peis.group.response.LoadRosterSummaryResponse;
import com.openhis.health.application.peis.group.response.MarkContractPaidResponse;
import com.openhis.health.application.peis.group.support.GroupRosterImportMemberKind;
import com.openhis.health.application.peis.group.support.GroupRosterImportConstants;
import com.openhis.health.application.peis.group.support.GroupRosterImportTemplateBuilder;
import com.openhis.health.application.peis.group.support.GroupRosterImportWorkbookParser;
import com.openhis.health.application.peis.group.support.GroupRosterCampusPullSupport;
import com.openhis.health.application.peis.group.support.GroupRosterCampusStaffSyncSupport;
import com.openhis.health.application.peis.group.support.GroupRosterEnrollmentExecutor;
import com.openhis.health.application.peis.group.support.GroupRosterPatientResolver;
import com.openhis.health.application.peis.group.support.GroupRosterReversalExecutor;
import com.openhis.health.application.peis.group.support.PeisGroupContractReconciliationSupport;
import com.openhis.health.application.peis.group.dto.RosterImportRowDto;
import com.openhis.health.application.peis.group.request.CancelGroupArrivalRequest;
import com.openhis.health.application.peis.group.request.CancelGroupEnrollmentRequest;
import com.openhis.health.application.peis.group.request.ConfirmGroupArrivalRequest;
import com.openhis.health.application.peis.group.request.DownloadRosterImportTemplateRequest;
import com.openhis.health.application.peis.group.request.ImportRosterFromCampusRequest;
import com.openhis.health.application.peis.group.request.ImportRosterFromTemplateRequest;
import com.openhis.health.application.peis.group.request.ImportRosterRequest;
import com.openhis.health.application.peis.group.request.ListCampusRosterClassNamesRequest;
import com.openhis.health.application.peis.group.request.ListCampusRosterClassNamesResponse;
import com.openhis.health.application.peis.group.request.ListCampusRosterDepartmentsRequest;
import com.openhis.health.application.peis.group.request.ListCampusRosterDepartmentsResponse;
import com.openhis.health.application.peis.group.request.AdjustGroupRosterServiceRequestsRequest;
import com.openhis.health.application.peis.group.request.MarkContractPaidRequest;
import com.openhis.health.application.peis.group.request.ListBatchesRequest;
import com.openhis.health.application.peis.group.request.ListContractReconciliationRequest;
import com.openhis.health.application.peis.group.request.ListContractsRequest;
import com.openhis.health.application.peis.orchestration.PeisRegistrationOrchestrator;
import com.openhis.health.application.peis.orchestration.command.AdjustPeServiceRequestsCommand;
import com.openhis.health.application.peis.group.request.PageGroupRosterRequest;
import com.openhis.health.application.peis.group.request.PreviewCampusRosterImportRequest;
import com.openhis.health.application.peis.group.request.PreviewCampusRosterImportResponse;
import com.openhis.health.application.peis.group.request.SaveCompanyRequest;
import com.openhis.health.application.peis.group.request.SaveContractRequest;
import com.openhis.health.application.peis.group.request.SaveGroupBatchRequest;
import com.openhis.health.application.peis.group.response.ConfirmGroupArrivalResponse;
import com.openhis.health.application.peis.group.response.ImportRosterResponse;
import com.openhis.health.application.peis.group.response.SaveCompanyResponse;
import com.openhis.health.application.peis.group.response.SaveContractResponse;
import com.openhis.health.application.peis.group.response.SaveGroupBatchResponse;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.domain.administration.patient.queries.model.BatchPatientDetailQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientCoreQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientDetailQueryModel;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.peis.group.entity.PeisGroupBatch;
import com.openhis.health.domain.peis.group.entity.PeisGroupCompany;
import com.openhis.health.domain.peis.group.entity.PeisGroupContract;
import com.openhis.health.domain.peis.group.entity.PeisGroupRoster;
import com.openhis.health.domain.peis.group.service.PeisGroupCheckDomainService;
import com.openhis.health.domain.peis.group.service.PeisGroupCheckQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.PeisGroupRosterConstants;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 团检筹备应用服务（AS-PEIS-GRP-001 v1.3）。
 *
 * <p>单位/合同/批次/名册维护、免收费建单与到检确认；单位款项在合同上后结确认。</p>
 */
@Service
@Validated
@RequiredArgsConstructor
public class PeisGroupCheckAdminAppService {

    private final PeisGroupCheckDomainService peisGroupCheckDomainService;
    private final PeisGroupCheckQueryService peisGroupCheckQueryService;
    private final GroupRosterEnrollmentExecutor groupRosterEnrollmentExecutor;
    private final GroupRosterPatientResolver groupRosterPatientResolver;
    private final GroupRosterReversalExecutor groupRosterReversalExecutor;
    private final GroupRosterImportTemplateBuilder groupRosterImportTemplateBuilder;
    private final GroupRosterImportWorkbookParser groupRosterImportWorkbookParser;
    private final GroupRosterCampusStaffSyncSupport groupRosterCampusStaffSyncSupport;
    private final GroupRosterCampusPullSupport groupRosterCampusPullSupport;
    private final PatientQueryService patientQueryService;
    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final PeisRegistrationOrchestrator peisRegistrationOrchestrator;
    private final PeisGroupContractReconciliationSupport peisGroupContractReconciliationSupport;

    /**
     * 保存团检单位（GRP-01）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SaveCompanyResponse saveCompany(SaveCompanyRequest request) {
        if (request == null || StringUtils.isEmpty(request.getName()) || request.getOrgId() == null) {
            throw new ValidationException("保存单位失败：名称与机构不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再维护单位");
        PeisGroupCompany entity = new PeisGroupCompany();
        entity.setId(request.getCompanyId());
        entity.setName(request.getName().trim());
        entity.setOrgId(request.getOrgId());
        entity.setCampusCollegeId(request.getCampusCollegeId());
        entity.setContactName(request.getContactName());
        entity.setContactPhone(request.getContactPhone());
        PeisGroupCompany saved = peisGroupCheckDomainService.saveCompany(entity, operatorId);
        SaveCompanyResponse response = new SaveCompanyResponse();
        response.setCompanyId(saved.getId());
        return response;
    }

    /**
     * 保存团检合同（GRP-02）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SaveContractResponse saveContract(SaveContractRequest request) {
        if (request == null) {
            throw new ValidationException("保存合同失败：请求不能为空");
        }
        List<Long> packageActivityIds = resolveContractPackageIds(request);
        if (request.getCompanyId() == null || packageActivityIds.isEmpty()) {
            throw new ValidationException("保存合同失败：单位与套餐不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再维护合同");
        peisGroupCheckQueryService.requireCompany(request.getCompanyId());
        PeisGroupContract entity = new PeisGroupContract();
        entity.setId(request.getContractId());
        entity.setCompanyId(request.getCompanyId());
        entity.setPackageActivityId(packageActivityIds.get(0));
        entity.setContractName(request.getContractName());
        entity.setValidFrom(request.getValidFrom());
        entity.setValidTo(request.getValidTo());
        entity.setContractAmount(request.getContractAmount());
        entity.setOrgId(request.getOrgId());
        PeisGroupContract saved = peisGroupCheckDomainService.saveContract(
            entity, packageActivityIds, operatorId);
        SaveContractResponse response = new SaveContractResponse();
        response.setContractId(saved.getId());
        return response;
    }

    /**
     * 团检合同单位后结收款确认（合同 pay_status → PAID）。
     */
    @Transactional(rollbackFor = Exception.class)
    public MarkContractPaidResponse markContractPaid(MarkContractPaidRequest request) {
        if (request == null || request.getContractId() == null) {
            throw new ValidationException("单位后结确认失败：合同不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再确认收款");
        PeisGroupContract contract = peisGroupCheckDomainService.markContractPaid(
            request.getContractId(), operatorId);
        MarkContractPaidResponse response = new MarkContractPaidResponse();
        response.setContractId(contract.getId());
        response.setPayStatusCode(contract.getPayStatusCode());
        return response;
    }

    /**
     * 团检合同对账汇总（GRP-08）：按单位汇总合同金额、名册进度与加项收费。
     */
    @Transactional(readOnly = true)
    public List<ContractReconciliationRowDto> listContractReconciliation(
            ListContractReconciliationRequest request) {
        if (request == null || request.getCompanyId() == null) {
            throw new ValidationException("合同对账查询失败：单位不能为空");
        }
        return peisGroupContractReconciliationSupport.listByCompany(
            request.getCompanyId(), request.getPayStatusCode());
    }

    /**
     * 团检名册已建单后加/减项（复用登记编排 REG-04）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void adjustRosterServiceRequests(AdjustGroupRosterServiceRequestsRequest request) {
        if (request == null || request.getRosterId() == null) {
            throw new ValidationException("项目调整失败：名册不能为空");
        }
        boolean hasAdd = request.getAddActivityIds() != null && !request.getAddActivityIds().isEmpty();
        boolean hasRemove = request.getRemoveServiceRequestIds() != null
            && !request.getRemoveServiceRequestIds().isEmpty();
        if (!hasAdd && !hasRemove) {
            throw new ValidationException("项目调整失败：加项或减项至少一项");
        }
        PeisGroupRoster roster = peisGroupCheckQueryService.requireRoster(request.getRosterId());
        if (!PeisGroupRosterConstants.isEnrolled(roster.getPayStatusCode())) {
            throw new BusinessException("NOT_ENROLLED", "请先完成团检建单后再调整项目");
        }
        if (roster.getEncounterId() == null) {
            throw new BusinessException("NOT_ENROLLED", "名册未关联体检就诊，无法调整项目");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再调整项目");
        AdjustPeServiceRequestsCommand cmd = new AdjustPeServiceRequestsCommand();
        cmd.setEncounterId(roster.getEncounterId());
        cmd.setOperatorId(operatorId);
        cmd.setAddActivityIds(request.getAddActivityIds());
        cmd.setRemoveServiceRequestIds(request.getRemoveServiceRequestIds());
        peisRegistrationOrchestrator.adjustServiceRequests(cmd);
    }

    /**
     * 保存团检批次（GRP-03）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SaveGroupBatchResponse saveGroupBatch(SaveGroupBatchRequest request) {
        if (request == null || request.getContractId() == null || StringUtils.isEmpty(request.getBatchName())) {
            throw new ValidationException("保存批次失败：合同与批次名称不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再维护批次");
        PeisGroupContract contract = peisGroupCheckQueryService.requireContract(request.getContractId());
        Long companyId = request.getCompanyId();
        if (companyId == null) {
            companyId = contract.getCompanyId();
        }
        List<Long> contractPackageIds = peisGroupCheckQueryService.listContractPackageIds(contract.getId());
        Long packageActivityId = request.getPackageActivityId();
        if (packageActivityId == null) {
            if (contractPackageIds.isEmpty()) {
                packageActivityId = contract.getPackageActivityId();
            } else {
                packageActivityId = contractPackageIds.get(0);
            }
        }
        if (packageActivityId == null) {
            throw new ValidationException("保存批次失败：合同未配置套餐");
        }
        peisGroupCheckDomainService.assertPackageAllowedForContract(contract.getId(), packageActivityId);
        PeisGroupBatch entity = new PeisGroupBatch();
        entity.setId(request.getBatchId());
        entity.setCompanyId(companyId);
        entity.setContractId(request.getContractId());
        entity.setBatchName(request.getBatchName().trim());
        entity.setPackageActivityId(packageActivityId);
        entity.setBookingDate(request.getBookingDate());
        Long batchOrgId = request.getOrgId();
        if (batchOrgId == null) {
            batchOrgId = contract.getOrgId();
        }
        entity.setOrgId(batchOrgId);
        PeisGroupBatch saved = peisGroupCheckDomainService.saveBatch(entity, operatorId);
        SaveGroupBatchResponse response = new SaveGroupBatchResponse();
        response.setBatchId(saved.getId());
        return response;
    }

    /**
     * 导入团检名册（GRP-04）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportRosterResponse importRoster(ImportRosterRequest request) {
        if (request == null || request.getBatchId() == null) {
            throw new ValidationException("导入名册失败：批次不能为空");
        }
        if (request.getRows() == null || request.getRows().isEmpty()) {
            throw new ValidationException("导入名册失败：名册行不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再导入名册");
        return importRosterRows(
            request.getBatchId(),
            request.getRows(),
            resolveImportMemberKind(request.getMemberKind(), GroupRosterImportMemberKind.ENTERPRISE),
            request.getSyncCampus(),
            request.getAutoEnroll(),
            operatorId);
    }

    /**
     * 下载团检名册导入模板（学生 / 企事业）。
     */
    public GroupRosterImportFileResponse downloadRosterImportTemplate(DownloadRosterImportTemplateRequest request) {
        GroupRosterImportMemberKind memberKind = resolveImportMemberKind(request == null ? null : request.getMemberKind());
        byte[] bytes = groupRosterImportTemplateBuilder.build(memberKind);
        GroupRosterImportFileResponse response = new GroupRosterImportFileResponse();
        response.setContentType(GroupRosterImportConstants.XLSX_CONTENT_TYPE);
        response.setFileBytes(bytes);
        if (memberKind == GroupRosterImportMemberKind.STUDENT) {
            response.setFileName(GroupRosterImportConstants.STUDENT_TEMPLATE_FILE);
        } else if (memberKind == GroupRosterImportMemberKind.STAFF) {
            response.setFileName(GroupRosterImportConstants.STAFF_TEMPLATE_FILE);
        } else {
            response.setFileName(GroupRosterImportConstants.ENTERPRISE_TEMPLATE_FILE);
        }
        return response;
    }

    /**
     * 按 Excel 模板批量导入团检名册（GRP-04）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportRosterResponse importRosterFromTemplate(ImportRosterFromTemplateRequest request) {
        if (request == null || request.getBatchId() == null) {
            throw new ValidationException("模板导入失败：批次不能为空");
        }
        GroupRosterImportMemberKind memberKind = resolveImportMemberKind(request.getMemberKind());
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再导入名册");
        List<RosterImportRowDto> rows =
            groupRosterImportWorkbookParser.parse(request.getFileBytes(), memberKind);
        return importRosterRows(
            request.getBatchId(),
            rows,
            memberKind,
            request.getSyncCampus(),
            request.getAutoEnroll(),
            operatorId);
    }

    /**
     * 列出 Campus 部门（教职工拉取名册）。
     */
    public ListCampusRosterDepartmentsResponse listCampusRosterDepartments(
            ListCampusRosterDepartmentsRequest request) {
        if (request == null || request.getCompanyId() == null) {
            throw new ValidationException("请选择团检单位");
        }
        PeisGroupCompany company = peisGroupCheckQueryService.requireCompany(request.getCompanyId());
        ListCampusRosterDepartmentsResponse response = new ListCampusRosterDepartmentsResponse();
        response.setDepartments(groupRosterCampusPullSupport.listDepartments(company));
        return response;
    }

    /**
     * 列出 Campus 班级（学生拉取名册）。
     */
    public ListCampusRosterClassNamesResponse listCampusRosterClassNames(
            ListCampusRosterClassNamesRequest request) {
        if (request == null || request.getCompanyId() == null) {
            throw new ValidationException("请选择团检单位");
        }
        PeisGroupCompany company = peisGroupCheckQueryService.requireCompany(request.getCompanyId());
        ListCampusRosterClassNamesResponse response = new ListCampusRosterClassNamesResponse();
        response.setClassNames(groupRosterCampusPullSupport.listClassNames(company));
        return response;
    }

    /**
     * 预览从 Campus 拉取的团检名册候选。
     */
    public PreviewCampusRosterImportResponse previewCampusRosterImport(
            PreviewCampusRosterImportRequest request) {
        if (request == null || request.getCompanyId() == null) {
            throw new ValidationException("请选择团检单位");
        }
        GroupRosterImportMemberKind memberKind = resolveCampusPullMemberKind(request.getMemberKind());
        PeisGroupCompany company = peisGroupCheckQueryService.requireCompany(request.getCompanyId());
        PreviewCampusRosterImportResponse response = new PreviewCampusRosterImportResponse();
        response.setCandidates(groupRosterCampusPullSupport.previewCandidates(
            memberKind,
            company,
            trimToNull(request.getClassName()),
            request.getDepartmentId()));
        return response;
    }

    /**
     * 从 Campus 拉取学生/教职工名册并挂册（GRP-04）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportRosterResponse importRosterFromCampus(ImportRosterFromCampusRequest request) {
        if (request == null || request.getBatchId() == null) {
            throw new ValidationException("从 Campus 导入失败：批次不能为空");
        }
        if (request.getCompanyId() == null) {
            throw new ValidationException("从 Campus 导入失败：单位不能为空");
        }
        GroupRosterImportMemberKind memberKind = resolveCampusPullMemberKind(request.getMemberKind());
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再导入名册");
        PeisGroupCompany company = peisGroupCheckQueryService.requireCompany(request.getCompanyId());
        List<PeisGroupRoster> rows = groupRosterCampusPullSupport.buildRosterRows(
            memberKind,
            company,
            trimToNull(request.getClassName()),
            request.getDepartmentId(),
            request.getPatientIds());
        if (rows.isEmpty()) {
            throw new ValidationException("从 Campus 导入失败：未找到可挂册人员");
        }
        PeisGroupBatch batch = peisGroupCheckQueryService.requireBatch(request.getBatchId());
        boolean shouldAutoEnroll = request.getAutoEnroll() == null || Boolean.TRUE.equals(request.getAutoEnroll());
        if (shouldAutoEnroll && batch.getPackageActivityId() == null) {
            throw new ValidationException("导入并建单失败：请先在批次上绑定套餐");
        }
        List<Long> importedRosterIds =
            peisGroupCheckDomainService.importRosterRows(request.getBatchId(), rows, operatorId);
        ImportRosterResponse response = new ImportRosterResponse();
        response.setImportedCount(importedRosterIds.size());
        applyAutoEnroll(response, batch.getOrgId(), importedRosterIds, shouldAutoEnroll, operatorId);
        return response;
    }

    private ImportRosterResponse importRosterRows(
            Long batchId,
            List<RosterImportRowDto> rowDtos,
            GroupRosterImportMemberKind memberKind,
            Boolean syncCampus,
            Boolean autoEnroll,
            Long operatorId) {
        PeisGroupBatch batch = peisGroupCheckQueryService.requireBatch(batchId);
        PeisGroupCompany company = peisGroupCheckQueryService.requireCompany(batch.getCompanyId());
        Long orgId = batch.getOrgId();
        boolean shouldSyncCampus = memberKind == GroupRosterImportMemberKind.STAFF
            && !Boolean.FALSE.equals(syncCampus);
        List<PeisGroupRoster> rows = new ArrayList<>();
        int campusSyncedCount = 0;
        List<EnrollmentFailureRowDto> campusFailures = new ArrayList<>();
        for (RosterImportRowDto row : rowDtos) {
            if (row == null || StringUtils.isEmpty(row.getFullName())) {
                continue;
            }
            Long patientId;
            try {
                patientId = groupRosterPatientResolver.resolveOrCreatePatient(row, orgId);
            } catch (RuntimeException ex) {
                campusFailures.add(buildImportFailureRow(row.getFullName(), ex));
                continue;
            }
            if (shouldSyncCampus) {
                try {
                    groupRosterCampusStaffSyncSupport.syncStaffForRosterImport(
                        memberKind, syncCampus, row, company, patientId, operatorId);
                    campusSyncedCount++;
                } catch (RuntimeException ex) {
                    campusFailures.add(buildImportFailureRow(row.getFullName(), ex));
                    continue;
                }
            }
            PeisGroupRoster entity = new PeisGroupRoster();
            entity.setPatientId(patientId);
            entity.setFullName(row.getFullName().trim());
            entity.setStudentNo(trimToNull(row.getStudentNo()));
            entity.setEmployeeNo(trimToNull(row.getEmployeeNo()));
            entity.setCollegeId(row.getCollegeId());
            entity.setClassName(trimToNull(row.getClassName()));
            entity.setIdCardNo(trimToNull(row.getIdCardNo()));
            entity.setMobilePhone(trimToNull(row.getMobilePhone()));
            rows.add(entity);
        }
        if (rows.isEmpty()) {
            if (!campusFailures.isEmpty()) {
                throw new ValidationException("导入名册失败：" + campusFailures.get(0).getMessage());
            }
            throw new ValidationException("导入名册失败：有效姓名行不能为空");
        }
        boolean shouldAutoEnroll = autoEnroll == null || Boolean.TRUE.equals(autoEnroll);
        if (shouldAutoEnroll && batch.getPackageActivityId() == null) {
            throw new ValidationException("导入并建单失败：请先在批次上绑定套餐");
        }
        List<Long> importedRosterIds = peisGroupCheckDomainService.importRosterRows(batchId, rows, operatorId);
        ImportRosterResponse response = new ImportRosterResponse();
        response.setImportedCount(importedRosterIds.size());
        response.setCampusSyncedCount(campusSyncedCount);
        response.setCampusFailures(campusFailures);
        applyAutoEnroll(response, orgId, importedRosterIds, shouldAutoEnroll, operatorId);
        return response;
    }

    private void applyAutoEnroll(
            ImportRosterResponse response,
            Long orgId,
            List<Long> importedRosterIds,
            boolean shouldAutoEnroll,
            Long operatorId) {
        if (!shouldAutoEnroll || importedRosterIds.isEmpty()) {
            return;
        }
        int enrolledCount = 0;
        List<EnrollmentFailureRowDto> failures = new ArrayList<>();
        for (Long rosterId : importedRosterIds) {
            PeisGroupRoster roster = null;
            try {
                roster = peisGroupCheckQueryService.requireRoster(rosterId);
                groupRosterEnrollmentExecutor.enrollOneRoster(rosterId, orgId, operatorId);
                enrolledCount++;
            } catch (RuntimeException ex) {
                EnrollmentFailureRowDto failure = new EnrollmentFailureRowDto();
                failure.setRosterId(rosterId);
                failure.setFullName(resolveFailureFullName(roster));
                failure.setMessage(resolveFailureMessage(ex));
                failures.add(failure);
            }
        }
        response.setEnrolledCount(enrolledCount);
        response.setEnrollmentFailures(failures);
    }

    private static GroupRosterImportMemberKind resolveCampusPullMemberKind(String raw) {
        GroupRosterImportMemberKind memberKind = GroupRosterImportMemberKind.parse(raw);
        if (memberKind != GroupRosterImportMemberKind.STUDENT
            && memberKind != GroupRosterImportMemberKind.STAFF) {
            throw new ValidationException("从 Campus 拉取名册仅支持学生或教职工");
        }
        return memberKind;
    }

    private static GroupRosterImportMemberKind resolveImportMemberKind(String raw) {
        GroupRosterImportMemberKind memberKind = GroupRosterImportMemberKind.parse(raw);
        if (memberKind == null) {
            throw new ValidationException("模板导入失败：人员类型无效，请选择学生、教职工或企事业模板");
        }
        return memberKind;
    }

    private static GroupRosterImportMemberKind resolveImportMemberKind(
            String raw, GroupRosterImportMemberKind defaultKind) {
        GroupRosterImportMemberKind memberKind = GroupRosterImportMemberKind.parse(raw);
        return memberKind != null ? memberKind : defaultKind;
    }

    private static EnrollmentFailureRowDto buildImportFailureRow(String fullName, RuntimeException ex) {
        EnrollmentFailureRowDto failure = new EnrollmentFailureRowDto();
        failure.setFullName(fullName);
        failure.setMessage(resolveFailureMessage(ex));
        return failure;
    }

    /**
     * 团检到检确认（GRP-05）；不创建 Encounter。
     */
    @Transactional(rollbackFor = Exception.class)
    public ConfirmGroupArrivalResponse confirmGroupArrival(ConfirmGroupArrivalRequest request) {
        if (request == null || request.getRosterId() == null) {
            throw new ValidationException("到检确认失败：名册标识不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再确认到检");
        PeisGroupRoster roster = peisGroupCheckDomainService.confirmArrival(request.getRosterId(), operatorId);
        ConfirmGroupArrivalResponse response = new ConfirmGroupArrivalResponse();
        response.setRosterId(roster.getId());
        response.setArrivalStatusCode(roster.getArrivalStatusCode());
        return response;
    }

    /**
     * 撤销团检到检（GRP-05 逆向）。
     */
    public ConfirmGroupArrivalResponse cancelGroupArrival(CancelGroupArrivalRequest request) {
        if (request == null || request.getRosterId() == null) {
            throw new ValidationException("撤销到检失败：名册标识不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再撤销到检");
        PeisGroupRoster roster = groupRosterReversalExecutor.cancelArrival(request.getRosterId(), operatorId);
        ConfirmGroupArrivalResponse response = new ConfirmGroupArrivalResponse();
        response.setRosterId(roster.getId());
        response.setArrivalStatusCode(roster.getArrivalStatusCode());
        return response;
    }

    /**
     * 撤销团检免收费建单（P0-1 逆向）。
     */
    public ConfirmGroupArrivalResponse cancelGroupEnrollment(CancelGroupEnrollmentRequest request) {
        if (request == null || request.getRosterId() == null) {
            throw new ValidationException("撤销建单失败：名册标识不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再撤销建单");
        PeisGroupRoster roster = groupRosterReversalExecutor.revertEnrollment(request.getRosterId(), operatorId);
        ConfirmGroupArrivalResponse response = new ConfirmGroupArrivalResponse();
        response.setRosterId(roster.getId());
        response.setArrivalStatusCode(roster.getArrivalStatusCode());
        return response;
    }

    /**
     * 登记台按患者查询团检名册（GRP-04 读侧，供健康体检登记台按人操作）。
     */
    public LookupGroupRosterForDeskResponse lookupRosterForDesk(LookupGroupRosterForDeskRequest request) {
        if (request == null || request.getPatientId() == null) {
            throw new ValidationException("查询团检名册失败：患者不能为空");
        }
        List<PeisGroupRoster> rosters = peisGroupCheckQueryService.listRosterByPatientId(request.getPatientId());
        LookupGroupRosterForDeskResponse response = new LookupGroupRosterForDeskResponse();
        for (PeisGroupRoster roster : rosters) {
            response.getMatches().add(toDeskContextDto(roster));
        }
        return response;
    }

    /**
     * 团检批量免收费建单（P0-1）：创建 Encounter 并标记 ENROLLED，不经 Payment。
     */
    public ConfirmBatchEnrollmentResponse confirmBatchEnrollment(ConfirmBatchEnrollmentRequest request) {
        if (request == null || request.getRosterIds() == null || request.getRosterIds().isEmpty()) {
            throw new ValidationException("批量建单失败：请勾选名册");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再建单");
        ConfirmBatchEnrollmentResponse response = new ConfirmBatchEnrollmentResponse();
        int successCount = 0;
        List<EnrollmentFailureRowDto> failures = new ArrayList<>();
        for (Long rosterId : request.getRosterIds()) {
            if (rosterId == null) {
                continue;
            }
            PeisGroupRoster roster = null;
            try {
                roster = peisGroupCheckQueryService.requireRoster(rosterId);
                groupRosterEnrollmentExecutor.enrollOneRoster(rosterId, request.getOrgId(), operatorId);
                successCount++;
            } catch (RuntimeException ex) {
                EnrollmentFailureRowDto failure = new EnrollmentFailureRowDto();
                failure.setRosterId(rosterId);
                failure.setFullName(resolveFailureFullName(roster));
                failure.setMessage(resolveFailureMessage(ex));
                failures.add(failure);
            }
        }
        response.setSuccessCount(successCount);
        response.setFailures(failures);
        return response;
    }

    /**
     * 团检名册汇总（名册工作台统计条）。
     */
    public LoadRosterSummaryResponse loadRosterSummary(LoadRosterSummaryRequest request) {
        if (request == null || request.getBatchId() == null) {
            throw new ValidationException("查询汇总失败：批次不能为空");
        }
        List<PeisGroupRoster> rows = peisGroupCheckQueryService.listRosterByBatch(request.getBatchId());
        LoadRosterSummaryResponse response = new LoadRosterSummaryResponse();
        int total = rows.size();
        int enrolled = 0;
        int arrived = 0;
        for (PeisGroupRoster row : rows) {
            if (PeisGroupRosterConstants.isEnrolled(row.getPayStatusCode())) {
                enrolled++;
            }
            if (PeisGroupRosterConstants.ARRIVAL_ARRIVED.equals(row.getArrivalStatusCode())) {
                arrived++;
            }
        }
        response.setTotalCount(total);
        response.setEnrolledCount(enrolled);
        response.setUnenrolledCount(total - enrolled);
        response.setArrivedCount(arrived);
        response.setPendingArrivalCount(Math.max(0, enrolled - arrived));
        return response;
    }

    /**
     * 分页查询团检名册。
     */
    public PagedResult<GroupRosterRowDto> pageRoster(PageGroupRosterRequest request) {
        if (request == null || request.getBatchId() == null) {
            throw new ValidationException("查询名册失败：批次不能为空");
        }
        PageRequest pageable = PageRequest.of(request.springPageIndex(), request.getBoundedPageSize());
        Page<PeisGroupRoster> page = peisGroupCheckQueryService.pageRoster(
            request.getBatchId(),
            request.getPayStatusCode(),
            request.getEnrollmentFilter(),
            request.getArrivalStatusCode(),
            request.getCollegeId(),
            request.getClassName(),
            pageable);
        List<Long> patientIds = page.getContent().stream()
            .map(PeisGroupRoster::getPatientId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, BatchPatientDetailQueryModel> patientMap =
            patientQueryService.batchLoadPatientDetails(patientIds);
        List<GroupRosterRowDto> rows = new ArrayList<>();
        for (PeisGroupRoster entity : page.getContent()) {
            rows.add(toRosterRowDto(entity, patientMap.get(entity.getPatientId())));
        }
        return PagedResult.of(rows, page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }

    /**
     * 列出全部团检单位。
     */
    public List<CompanyRowDto> listCompanies() {
        List<CompanyRowDto> rows = new ArrayList<>();
        for (PeisGroupCompany entity : peisGroupCheckQueryService.listCompanies()) {
            rows.add(toCompanyRowDto(entity));
        }
        return rows;
    }

    /**
     * 按单位列出团检批次。
     */
    public List<BatchRowDto> listBatches(ListBatchesRequest request) {
        if (request == null || request.getCompanyId() == null) {
            throw new ValidationException("查询批次失败：单位不能为空");
        }
        List<BatchRowDto> rows = new ArrayList<>();
        for (PeisGroupBatch entity : peisGroupCheckQueryService.listBatches(request.getCompanyId())) {
            rows.add(toBatchRowDto(entity));
        }
        return rows;
    }

    /**
     * 按单位列出团检合同。
     */
    public List<ContractRowDto> listContracts(ListContractsRequest request) {
        if (request == null || request.getCompanyId() == null) {
            throw new ValidationException("查询合同失败：单位不能为空");
        }
        List<ContractRowDto> rows = new ArrayList<>();
        List<PeisGroupContract> entities = peisGroupCheckQueryService.listContracts(request.getCompanyId());
        List<Long> contractIds = entities.stream().map(PeisGroupContract::getId).toList();
        Map<Long, List<Long>> packageMap = peisGroupCheckQueryService.mapContractPackageIds(contractIds);
        for (PeisGroupContract entity : entities) {
            rows.add(toContractRowDto(entity, packageMap.get(entity.getId())));
        }
        return rows;
    }

    private static String resolveFailureFullName(PeisGroupRoster roster) {
        if (roster == null) {
            return null;
        }
        return roster.getFullName();
    }

    private static String resolveFailureMessage(RuntimeException ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        if (current.getMessage() != null && !current.getMessage().isBlank()) {
            return current.getMessage();
        }
        if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            return ex.getMessage();
        }
        return "建单失败";
    }

    private static List<Long> resolveContractPackageIds(SaveContractRequest request) {
        if (request.getPackageActivityIds() != null && !request.getPackageActivityIds().isEmpty()) {
            return request.getPackageActivityIds().stream()
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        }
        if (request.getPackageActivityId() != null) {
            return List.of(request.getPackageActivityId());
        }
        return List.of();
    }

    private static CompanyRowDto toCompanyRowDto(PeisGroupCompany entity) {
        CompanyRowDto dto = new CompanyRowDto();
        dto.setCompanyId(entity.getId());
        dto.setName(entity.getName());
        dto.setOrgId(entity.getOrgId());
        dto.setCampusCollegeId(entity.getCampusCollegeId());
        dto.setContactName(entity.getContactName());
        dto.setContactPhone(entity.getContactPhone());
        return dto;
    }

    private static ContractRowDto toContractRowDto(PeisGroupContract entity, List<Long> packageActivityIds) {
        ContractRowDto dto = new ContractRowDto();
        dto.setContractId(entity.getId());
        dto.setCompanyId(entity.getCompanyId());
        dto.setPackageActivityId(entity.getPackageActivityId());
        if (packageActivityIds != null && !packageActivityIds.isEmpty()) {
            dto.setPackageActivityIds(packageActivityIds);
            dto.setPackageActivityId(packageActivityIds.get(0));
        } else if (entity.getPackageActivityId() != null) {
            dto.setPackageActivityIds(List.of(entity.getPackageActivityId()));
        }
        dto.setContractName(entity.getContractName());
        dto.setValidFrom(entity.getValidFrom());
        dto.setValidTo(entity.getValidTo());
        dto.setContractAmount(entity.getContractAmount());
        dto.setPayStatusCode(entity.getPayStatusCode());
        dto.setOrgId(entity.getOrgId());
        return dto;
    }

    private static BatchRowDto toBatchRowDto(PeisGroupBatch entity) {
        BatchRowDto dto = new BatchRowDto();
        dto.setBatchId(entity.getId());
        dto.setCompanyId(entity.getCompanyId());
        dto.setContractId(entity.getContractId());
        dto.setBatchName(entity.getBatchName());
        dto.setPackageActivityId(entity.getPackageActivityId());
        dto.setBookingDate(entity.getBookingDate());
        dto.setStatusCode(entity.getStatusCode());
        dto.setOrgId(entity.getOrgId());
        return dto;
    }

    private GroupRosterDeskContextDto toDeskContextDto(PeisGroupRoster entity) {
        GroupRosterDeskContextDto dto = new GroupRosterDeskContextDto();
        dto.setRosterId(entity.getId());
        dto.setBatchId(entity.getBatchId());
        dto.setPatientId(entity.getPatientId());
        dto.setFullName(entity.getFullName());
        dto.setEncounterId(entity.getEncounterId());
        dto.setPayStatusCode(entity.getPayStatusCode());
        dto.setArrivalStatusCode(entity.getArrivalStatusCode());
        dto.setEnrolled(PeisGroupRosterConstants.isEnrolled(entity.getPayStatusCode()));
        dto.setArrived(PeisGroupRosterConstants.ARRIVAL_ARRIVED.equals(entity.getArrivalStatusCode()));
        if (entity.getBatchId() != null) {
            PeisGroupBatch batch = peisGroupCheckQueryService.requireBatch(entity.getBatchId());
            dto.setBatchName(batch.getBatchName());
            dto.setPackageActivityId(batch.getPackageActivityId());
            dto.setCompanyId(batch.getCompanyId());
            if (batch.getCompanyId() != null) {
                try {
                    PeisGroupCompany company = peisGroupCheckQueryService.requireCompany(batch.getCompanyId());
                    dto.setCompanyName(company.getName());
                } catch (RuntimeException ignored) {
                    // 单位缺失时不阻断登记台查询
                }
            }
        }
        return dto;
    }

    private static GroupRosterRowDto toRosterRowDto(
            PeisGroupRoster entity, BatchPatientDetailQueryModel patientDetail) {
        GroupRosterRowDto dto = new GroupRosterRowDto();
        dto.setRosterId(entity.getId());
        dto.setBatchId(entity.getBatchId());
        dto.setPatientId(entity.getPatientId());
        dto.setFullName(entity.getFullName());
        dto.setGenderEnum(resolvePatientGender(patientDetail));
        dto.setIdCardNo(entity.getIdCardNo());
        dto.setStudentNo(entity.getStudentNo());
        dto.setEmployeeNo(entity.getEmployeeNo());
        dto.setCollegeId(entity.getCollegeId());
        dto.setClassName(entity.getClassName());
        dto.setMobilePhone(entity.getMobilePhone());
        dto.setEncounterId(entity.getEncounterId());
        dto.setPayStatusCode(entity.getPayStatusCode());
        dto.setArrivalStatusCode(entity.getArrivalStatusCode());
        return dto;
    }

    private static String resolvePatientGender(BatchPatientDetailQueryModel patientDetail) {
        if (patientDetail == null) {
            return null;
        }
        PatientDetailQueryModel detail = patientDetail.getPatientDetail();
        if (detail == null) {
            return null;
        }
        PatientCoreQueryModel core = detail.getPatient();
        if (core == null) {
            return null;
        }
        return core.getGenderEnum();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }
}
