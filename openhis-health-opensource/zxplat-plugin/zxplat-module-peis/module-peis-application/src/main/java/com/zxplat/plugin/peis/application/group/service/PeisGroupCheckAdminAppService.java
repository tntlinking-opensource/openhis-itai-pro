/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.service;

import com.zxplat.plugin.peis.application.group.dto.BatchRowDto;
import com.zxplat.plugin.peis.application.group.dto.CompanyRowDto;
import com.zxplat.plugin.peis.application.group.dto.ContractRowDto;
import com.zxplat.plugin.peis.application.group.dto.ContractTypeRowDto;
import com.zxplat.plugin.peis.application.group.dto.EnrollmentFailureRowDto;
import com.zxplat.plugin.peis.application.group.dto.ContractReconciliationRowDto;
import com.zxplat.plugin.peis.application.group.dto.GroupRosterDeskContextDto;
import com.zxplat.plugin.peis.application.group.request.LookupGroupRosterForDeskRequest;
import com.zxplat.plugin.peis.application.group.response.LookupGroupRosterForDeskResponse;
import com.zxplat.plugin.peis.application.group.dto.GroupRosterImportFileResponse;
import com.zxplat.plugin.peis.application.group.dto.GroupRosterRowDto;
import com.zxplat.plugin.peis.application.group.request.ConfirmBatchEnrollmentRequest;
import com.zxplat.plugin.peis.application.group.request.LoadRosterSummaryRequest;
import com.zxplat.plugin.peis.application.group.response.ConfirmBatchEnrollmentResponse;
import com.zxplat.plugin.peis.application.group.response.LoadRosterSummaryResponse;
import com.zxplat.plugin.peis.application.group.response.MarkContractPaidResponse;
import com.zxplat.plugin.peis.application.group.support.GroupRosterImportMemberKind;
import com.zxplat.plugin.peis.application.group.support.GroupRosterImportConstants;
import com.zxplat.plugin.peis.application.group.support.GroupRosterImportTemplateBuilder;
import com.zxplat.plugin.peis.application.group.support.GroupRosterImportWorkbookParser;
import com.zxplat.plugin.peis.application.group.support.GroupRosterCampusPullSupport;
import com.zxplat.plugin.peis.application.group.support.GroupRosterCampusStaffSyncSupport;
import com.zxplat.plugin.peis.application.group.support.GroupRosterPatientResolver;
import com.zxplat.plugin.peis.application.group.support.GroupRosterReversalExecutor;
import com.zxplat.plugin.peis.application.group.support.PeisGroupContractReconciliationSupport;
import com.zxplat.plugin.peis.application.group.dto.RosterImportRowDto;
import com.zxplat.plugin.peis.application.group.request.CancelGroupArrivalRequest;
import com.zxplat.plugin.peis.application.group.request.CancelGroupEnrollmentRequest;
import com.zxplat.plugin.peis.application.group.request.ConfirmGroupArrivalRequest;
import com.zxplat.plugin.peis.application.group.request.DownloadRosterImportTemplateRequest;
import com.zxplat.plugin.peis.application.group.request.ImportRosterFromCampusRequest;
import com.zxplat.plugin.peis.application.group.request.ImportRosterFromTemplateRequest;
import com.zxplat.plugin.peis.application.group.request.ImportRosterRequest;
import com.zxplat.plugin.peis.application.group.request.ListBatchesRequest;
import com.zxplat.plugin.peis.application.group.request.ListValidContractsForPatientRequest;
import com.zxplat.plugin.peis.application.group.request.ListCampusRosterClassNamesRequest;
import com.zxplat.plugin.peis.application.group.request.ListCampusRosterClassNamesResponse;
import com.zxplat.plugin.peis.application.group.request.ListCampusRosterDepartmentsRequest;
import com.zxplat.plugin.peis.application.group.request.ListCampusRosterDepartmentsResponse;
import com.zxplat.plugin.peis.application.group.request.AdjustGroupRosterServiceRequestsRequest;
import com.zxplat.plugin.peis.application.group.request.MarkContractPaidRequest;
import com.zxplat.plugin.peis.application.group.dto.PackageOptionDto;
import com.zxplat.plugin.peis.application.group.dto.RosterEnrollmentItemDto;
import com.zxplat.plugin.peis.application.group.request.ChangeRosterSelectedPackageRequest;
import com.zxplat.plugin.peis.application.group.request.DeleteCompanyRequest;
import com.zxplat.plugin.peis.application.group.request.ImportRosterByContractRequest;
import com.zxplat.plugin.peis.application.group.request.ListCompanyTreeRequest;
import com.zxplat.plugin.peis.application.group.request.ListCompanyTreeResponse;
import com.zxplat.plugin.peis.application.group.request.SelectRosterPackageRequest;
import com.zxplat.plugin.peis.application.group.request.UpdateRosterBatchRequest;
import com.zxplat.plugin.peis.application.group.request.UpdateRosterOptionalPackagesRequest;
import com.zxplat.plugin.peis.application.group.response.ChangeRosterSelectedPackageResponse;
import com.zxplat.plugin.peis.application.group.response.SelectRosterPackageResponse;
import com.zxplat.plugin.peis.application.group.support.GroupRosterImportPackageResolver;
import com.zxplat.plugin.peis.application.group.support.GroupRosterPackageSelectionSupport;
import com.zxplat.plugin.peis.application.group.support.PeisGroupCompanyTreeLoader;
import com.zxplat.plugin.peis.domain.group.service.PeisGroupCheckDomainService.PeisGroupRosterImportRow;
import com.zxplat.domain.workflow.activitydefinition.queries.model.ActivityDefinitionBaseQueryModel;
import com.zxplat.domain.workflow.activitydefinition.queries.model.ActivityDefinitionDetailQueryModel;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.plugin.peis.application.group.request.ListContractReconciliationRequest;
import com.zxplat.plugin.peis.application.group.request.ListContractsRequest;
import com.zxplat.plugin.peis.application.orchestration.PeisRegistrationOrchestrator;
import com.zxplat.plugin.peis.application.orchestration.command.AdjustPeServiceRequestsCommand;
import com.zxplat.plugin.peis.application.group.request.PageGroupRosterRequest;
import com.zxplat.plugin.peis.application.group.request.PreviewCampusRosterImportRequest;
import com.zxplat.plugin.peis.application.group.request.PreviewCampusRosterImportResponse;
import com.zxplat.plugin.peis.application.group.request.SaveCompanyRequest;
import com.zxplat.plugin.peis.application.group.request.SaveContractRequest;
import com.zxplat.plugin.peis.application.group.request.SaveGroupBatchRequest;
import com.zxplat.plugin.peis.application.group.response.ConfirmGroupArrivalResponse;
import com.zxplat.plugin.peis.application.group.response.ImportRosterResponse;
import com.zxplat.plugin.peis.application.group.response.SaveCompanyResponse;
import com.zxplat.plugin.peis.application.group.response.SaveContractResponse;
import com.zxplat.plugin.peis.application.group.response.SaveGroupBatchResponse;
import com.zxplat.plugin.peis.application.share.PeisCurrentPractitionerResolver;
import com.zxplat.domain.administration.patient.queries.model.BatchPatientDetailQueryModel;
import com.zxplat.domain.administration.patient.queries.model.PatientCoreQueryModel;
import com.zxplat.domain.administration.patient.queries.model.PatientDetailQueryModel;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.plugin.peis.domain.group.entity.PeisGroupBatch;
import com.zxplat.plugin.peis.domain.group.entity.PeisGroupCompany;
import com.zxplat.plugin.peis.domain.group.entity.PeisGroupContract;
import com.zxplat.plugin.peis.domain.group.entity.PeisGroupRoster;
import com.zxplat.plugin.peis.domain.group.service.PeisGroupCheckDomainService;
import com.zxplat.plugin.peis.domain.group.service.PeisGroupCheckQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.PeisGroupRosterConstants;
import com.zxplat.framework.core.application.dto.PagedResult;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.application.paging.PageWindowGate;
import com.zxplat.framework.core.application.paging.PagedResultBuilder;
import com.zxplat.framework.core.application.util.StringUtils;
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
 * 团检筹备应用服务（AS-PEIS-GRP-001 · PEIS-GRP-TREE-PKG-001）。
 *
 * <p>单位树/合同/体检分组/名册维护、选定套餐建单与到检前换套餐。</p>
 */
@Service
@Validated
@RequiredArgsConstructor
public class PeisGroupCheckAdminAppService {

    private final PeisGroupCheckDomainService peisGroupCheckDomainService;
    private final PeisGroupCheckQueryService peisGroupCheckQueryService;
    private final GroupRosterPatientResolver groupRosterPatientResolver;
    private final GroupRosterReversalExecutor groupRosterReversalExecutor;
    private final GroupRosterImportTemplateBuilder groupRosterImportTemplateBuilder;
    private final GroupRosterImportWorkbookParser groupRosterImportWorkbookParser;
    private final GroupRosterCampusStaffSyncSupport groupRosterCampusStaffSyncSupport;
    private final GroupRosterCampusPullSupport groupRosterCampusPullSupport;
    private final GroupRosterImportPackageResolver groupRosterImportPackageResolver;
    private final GroupRosterPackageSelectionSupport groupRosterPackageSelectionSupport;
    private final PeisGroupCompanyTreeLoader peisGroupCompanyTreeLoader;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final PatientQueryService patientQueryService;
    private final PeisCurrentPractitionerResolver currentPractitionerResolver;
    private final PeisRegistrationOrchestrator peisRegistrationOrchestrator;
    private final PeisGroupContractReconciliationSupport peisGroupContractReconciliationSupport;
    /** 分页窗口入站门控（B.10.11）。 */
    private final PageWindowGate pageWindowGate;

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
        entity.setParentId(request.getParentId());
        entity.setCampusCollegeId(request.getCampusCollegeId());
        entity.setContactName(request.getContactName());
        entity.setContactPhone(request.getContactPhone());
        PeisGroupCompany saved = peisGroupCheckDomainService.saveCompany(entity, operatorId);
        SaveCompanyResponse response = new SaveCompanyResponse();
        response.setCompanyId(saved.getId());
        return response;
    }

    /**
     * 团检单位树（GRP-01）。
     */
    public ListCompanyTreeResponse listCompanyTree(ListCompanyTreeRequest request) {
        Long orgId = null;
        if (request != null) {
            orgId = request.getOrgId();
        }
        ListCompanyTreeResponse response = new ListCompanyTreeResponse();
        response.setRoots(peisGroupCompanyTreeLoader.loadTree(orgId));
        return response;
    }

    /**
     * 删除团检单位（BR-GRP-TREE-01）。
     */
    public void deleteCompany(DeleteCompanyRequest request) {
        if (request == null || request.getCompanyId() == null) {
            throw new ValidationException("删除单位失败：单位不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再删除单位");
        peisGroupCheckDomainService.deleteCompany(request.getCompanyId(), operatorId);
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
        // Pre：合同类型为号源分池必填维度
        if (request.getContractTypeCode() == null || request.getContractTypeCode().isBlank()) {
            throw new ValidationException("保存合同失败：合同类型不能为空");
        }
        entity.setContractTypeCode(request.getContractTypeCode().trim());
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
     * 保存体检分组（GRP-03；原批次，多套餐）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SaveGroupBatchResponse saveGroupBatch(SaveGroupBatchRequest request) {
        if (request == null || request.getContractId() == null || StringUtils.isEmpty(request.getBatchName())) {
            throw new ValidationException("保存分组失败：合同与分组名称不能为空");
        }
        List<Long> packageActivityIds = resolveBatchPackageIds(request);
        if (packageActivityIds.isEmpty()) {
            throw new ValidationException("保存分组失败：须至少选择一个套餐");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再维护分组");
        PeisGroupContract contract = peisGroupCheckQueryService.requireContract(request.getContractId());
        Long companyId = request.getCompanyId();
        if (companyId == null) {
            companyId = contract.getCompanyId();
        }
        PeisGroupBatch entity = new PeisGroupBatch();
        entity.setId(request.getBatchId());
        entity.setCompanyId(companyId);
        entity.setContractId(request.getContractId());
        entity.setBatchName(request.getBatchName().trim());
        entity.setPackageActivityId(packageActivityIds.get(0));
        entity.setBookingDate(request.getBookingDate());
        Long batchOrgId = request.getOrgId();
        if (batchOrgId == null) {
            batchOrgId = contract.getOrgId();
        }
        entity.setOrgId(batchOrgId);
        PeisGroupBatch saved = peisGroupCheckDomainService.saveBatch(entity, packageActivityIds, operatorId);
        SaveGroupBatchResponse response = new SaveGroupBatchResponse();
        response.setBatchId(saved.getId());
        return response;
    }

    /**
     * 导入团检名册（GRP-04 · 兼容旧 batchId 入口）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportRosterResponse importRoster(ImportRosterRequest request) {
        if (request == null || request.getBatchId() == null) {
            throw new ValidationException("导入名册失败：分组不能为空");
        }
        if (request.getRows() == null || request.getRows().isEmpty()) {
            throw new ValidationException("导入名册失败：名册行不能为空");
        }
        PeisGroupBatch batch = peisGroupCheckQueryService.requireBatch(request.getBatchId());
        ImportRosterByContractRequest contractRequest = new ImportRosterByContractRequest();
        contractRequest.setContractId(batch.getContractId());
        contractRequest.setDefaultBatchId(batch.getId());
        contractRequest.setSelectedCompanyId(batch.getCompanyId());
        contractRequest.setRows(request.getRows());
        contractRequest.setMemberKind(request.getMemberKind());
        contractRequest.setSyncCampus(request.getSyncCampus());
        return importRosterByContract(contractRequest);
    }

    /**
     * 按合同导入团检名册（GRP-04 主路径）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportRosterResponse importRosterByContract(ImportRosterByContractRequest request) {
        if (request == null || request.getContractId() == null || request.getDefaultBatchId() == null) {
            throw new ValidationException("导入名册失败：合同与默认分组不能为空");
        }
        if (request.getSelectedCompanyId() == null) {
            throw new ValidationException("导入名册失败：请选择单位树节点");
        }
        if (request.getRows() == null || request.getRows().isEmpty()) {
            throw new ValidationException("导入名册失败：名册行不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再导入名册");
        GroupRosterImportMemberKind memberKind =
            resolveImportMemberKind(request.getMemberKind(), GroupRosterImportMemberKind.ENTERPRISE);
        PeisGroupBatch defaultBatch = peisGroupCheckQueryService.requireBatch(request.getDefaultBatchId());
        if (!request.getContractId().equals(defaultBatch.getContractId())) {
            throw new ValidationException("导入名册失败：默认分组不属于所选合同");
        }
        PeisGroupCompany company = peisGroupCheckQueryService.requireCompany(request.getSelectedCompanyId());
        Long orgId = defaultBatch.getOrgId();
        if (orgId == null) {
            orgId = company.getOrgId();
        }
        boolean shouldSyncCampus = memberKind == GroupRosterImportMemberKind.STAFF
            && !Boolean.FALSE.equals(request.getSyncCampus());
        List<PeisGroupRosterImportRow> importRows = new ArrayList<>();
        int campusSyncedCount = 0;
        List<EnrollmentFailureRowDto> failures = new ArrayList<>();
        for (RosterImportRowDto row : request.getRows()) {
            if (row == null || StringUtils.isEmpty(row.getFullName())) {
                continue;
            }
            Long targetBatchId = request.getDefaultBatchId();
            try {
                if (!StringUtils.isEmpty(row.getBatchNameOverride())) {
                    targetBatchId = peisGroupCheckQueryService.requireBatchByNameUnderContract(
                        request.getContractId(), row.getBatchNameOverride()).getId();
                }
                List<Long> optionalPackageIds = resolveOptionalPackageIdsForImport(targetBatchId, row);
                Long patientId = groupRosterPatientResolver.resolveOrCreatePatient(row, orgId);
                if (shouldSyncCampus) {
                    groupRosterCampusStaffSyncSupport.syncStaffForRosterImport(
                        memberKind, request.getSyncCampus(), row, company, patientId, operatorId);
                    campusSyncedCount++;
                }
                PeisGroupRoster entity = new PeisGroupRoster();
                entity.setPatientId(patientId);
                entity.setCompanyId(request.getSelectedCompanyId());
                entity.setFullName(row.getFullName().trim());
                entity.setStudentNo(trimToNull(row.getStudentNo()));
                entity.setEmployeeNo(trimToNull(row.getEmployeeNo()));
                entity.setCollegeId(row.getCollegeId());
                entity.setClassName(trimToNull(row.getClassName()));
                entity.setIdCardNo(trimToNull(row.getIdCardNo()));
                entity.setMobilePhone(trimToNull(row.getMobilePhone()));
                entity.setBatchId(targetBatchId);
                PeisGroupRosterImportRow importRow = new PeisGroupRosterImportRow();
                importRow.setRoster(entity);
                importRow.setOptionalPackageIds(optionalPackageIds);
                importRows.add(importRow);
            } catch (RuntimeException ex) {
                failures.add(buildImportFailureRow(row.getFullName(), ex));
            }
        }
        if (importRows.isEmpty()) {
            if (!failures.isEmpty()) {
                throw new ValidationException("导入名册失败：" + failures.get(0).getMessage());
            }
            throw new ValidationException("导入名册失败：有效姓名行不能为空");
        }
        int importedCount = 0;
        for (PeisGroupRosterImportRow importRow : importRows) {
            try {
                peisGroupCheckDomainService.saveRosterWithOptionalPackages(
                    importRow.getRoster(), importRow.getOptionalPackageIds(), operatorId);
                importedCount++;
            } catch (RuntimeException ex) {
                failures.add(buildImportFailureRow(importRow.getRoster().getFullName(), ex));
            }
        }
        ImportRosterResponse response = new ImportRosterResponse();
        response.setImportedCount(importedCount);
        response.setCampusSyncedCount(campusSyncedCount);
        response.setCampusFailures(failures);
        return response;
    }

    /**
     * 按 Excel 模板批量导入团检名册（GRP-04）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportRosterResponse importRosterFromTemplate(ImportRosterFromTemplateRequest request) {
        if (request == null) {
            throw new ValidationException("模板导入失败：请求不能为空");
        }
        Long defaultBatchId = request.getDefaultBatchId();
        if (defaultBatchId == null) {
            defaultBatchId = request.getBatchId();
        }
        if (defaultBatchId == null || request.getContractId() == null || request.getSelectedCompanyId() == null) {
            throw new ValidationException("模板导入失败：合同、默认分组与选中单位不能为空");
        }
        GroupRosterImportMemberKind memberKind = resolveImportMemberKind(request.getMemberKind());
        List<RosterImportRowDto> rows =
            groupRosterImportWorkbookParser.parse(request.getFileBytes(), memberKind);
        ImportRosterByContractRequest contractRequest = new ImportRosterByContractRequest();
        contractRequest.setContractId(request.getContractId());
        contractRequest.setDefaultBatchId(defaultBatchId);
        contractRequest.setSelectedCompanyId(request.getSelectedCompanyId());
        contractRequest.setRows(rows);
        contractRequest.setMemberKind(request.getMemberKind());
        contractRequest.setSyncCampus(request.getSyncCampus());
        return importRosterByContract(contractRequest);
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
            throw new ValidationException("从 Campus 导入失败：分组不能为空");
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
        List<Long> optionalPackageIds = peisGroupCheckQueryService.listBatchPackageIds(batch.getId());
        List<PeisGroupRosterImportRow> importRows = new ArrayList<>();
        for (PeisGroupRoster row : rows) {
            row.setBatchId(batch.getId());
            row.setCompanyId(request.getCompanyId());
            PeisGroupRosterImportRow importRow = new PeisGroupRosterImportRow();
            importRow.setRoster(row);
            importRow.setOptionalPackageIds(optionalPackageIds);
            importRows.add(importRow);
        }
        List<Long> importedRosterIds = peisGroupCheckDomainService.importRosterRows(
            request.getBatchId(), importRows, operatorId);
        ImportRosterResponse response = new ImportRosterResponse();
        response.setImportedCount(importedRosterIds.size());
        return response;
    }

    /**
     * 维护名册可选套餐。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateRosterOptionalPackages(UpdateRosterOptionalPackagesRequest request) {
        if (request == null || request.getRosterId() == null) {
            throw new ValidationException("更新可选套餐失败：名册不能为空");
        }
        if (request.getPackageActivityIds() == null || request.getPackageActivityIds().isEmpty()) {
            throw new ValidationException("更新可选套餐失败：须至少选择一个套餐");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再维护名册");
        peisGroupCheckDomainService.replaceRosterOptionalPackages(
            request.getRosterId(), request.getPackageActivityIds(), operatorId);
    }

    /**
     * 变更名册所属体检分组。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateRosterBatch(UpdateRosterBatchRequest request) {
        if (request == null || request.getRosterId() == null || request.getTargetBatchId() == null) {
            throw new ValidationException("换组失败：名册与目标分组不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再维护名册");
        peisGroupCheckDomainService.changeRosterBatch(
            request.getRosterId(), request.getTargetBatchId(), operatorId);
    }

    /**
     * 选定套餐并建单（BR-GRP-SEL-02）。
     */
    public SelectRosterPackageResponse selectRosterPackage(SelectRosterPackageRequest request) {
        if (request == null || request.getRosterId() == null || request.getPackageActivityId() == null) {
            throw new ValidationException("选定建单失败：名册与套餐不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再建单");
        com.zxplat.plugin.peis.application.orchestration.result.RegisterPersonalCheckInResult result =
            groupRosterPackageSelectionSupport.selectAndEnroll(
                request.getRosterId(), request.getPackageActivityId(), request.getOrgId(), operatorId);
        SelectRosterPackageResponse response = new SelectRosterPackageResponse();
        response.setRosterId(request.getRosterId());
        response.setEncounterId(result.getEncounterId());
        response.setBusNo(result.getBusNo());
        response.setSelectedPackageActivityId(request.getPackageActivityId());
        return response;
    }

    /**
     * 到检前更换选定套餐（BR-GRP-SEL-03）。
     */
    public ChangeRosterSelectedPackageResponse changeRosterSelectedPackage(
            ChangeRosterSelectedPackageRequest request) {
        if (request == null || request.getRosterId() == null || request.getPackageActivityId() == null) {
            throw new ValidationException("换套餐失败：名册与套餐不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再换套餐");
        groupRosterPackageSelectionSupport.changeSelectedPackageBeforeArrival(
            request.getRosterId(), request.getPackageActivityId(), operatorId);
        PeisGroupRoster roster = peisGroupCheckQueryService.requireRoster(request.getRosterId());
        ChangeRosterSelectedPackageResponse response = new ChangeRosterSelectedPackageResponse();
        response.setRosterId(roster.getId());
        response.setEncounterId(roster.getEncounterId());
        response.setSelectedPackageActivityId(request.getPackageActivityId());
        return response;
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
     * 团检批量选定建单（须 rosterId + packageActivityId 成对）。
     */
    public ConfirmBatchEnrollmentResponse confirmBatchEnrollment(ConfirmBatchEnrollmentRequest request) {
        List<RosterEnrollmentItemDto> items = resolveEnrollmentItems(request);
        if (items.isEmpty()) {
            throw new ValidationException("批量建单失败：请勾选名册并选定套餐");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再建单");
        ConfirmBatchEnrollmentResponse response = new ConfirmBatchEnrollmentResponse();
        int successCount = 0;
        List<EnrollmentFailureRowDto> failures = new ArrayList<>();
        for (RosterEnrollmentItemDto item : items) {
            if (item == null || item.getRosterId() == null || item.getPackageActivityId() == null) {
                continue;
            }
            PeisGroupRoster roster = null;
            try {
                roster = peisGroupCheckQueryService.requireRoster(item.getRosterId());
                groupRosterPackageSelectionSupport.selectAndEnroll(
                    item.getRosterId(), item.getPackageActivityId(), request.getOrgId(), operatorId);
                successCount++;
            } catch (RuntimeException ex) {
                EnrollmentFailureRowDto failure = new EnrollmentFailureRowDto();
                failure.setRosterId(item.getRosterId());
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
        if (request == null) {
            throw new ValidationException("查询名册失败：请求不能为空");
        }
        if (request.getBatchId() == null && request.getContractId() == null) {
            throw new ValidationException("查询名册失败：合同或分组不能为空");
        }
        // Gate：分页窗口合法（B.10.11）
        pageWindowGate.requirePositivePageWindow(
            request, ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "团检名册分页参数非法");
        PageRequest pageable = PageRequest.of(request.springPageIndex(), request.getBoundedPageSize());
        Page<PeisGroupRoster> page;
        if (request.getContractId() != null) {
            page = peisGroupCheckQueryService.pageRosterByContract(
                request.getContractId(),
                request.getBatchId(),
                request.getPayStatusCode(),
                request.getEnrollmentFilter(),
                request.getArrivalStatusCode(),
                request.getCollegeId(),
                request.getClassName(),
                pageable);
        } else {
            page = peisGroupCheckQueryService.pageRoster(
                request.getBatchId(),
                request.getPayStatusCode(),
                request.getEnrollmentFilter(),
                request.getArrivalStatusCode(),
                request.getCollegeId(),
                request.getClassName(),
                pageable);
        }
        List<Long> patientIds = page.getContent().stream()
            .map(PeisGroupRoster::getPatientId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, BatchPatientDetailQueryModel> patientMap =
            patientQueryService.batchLoadPatientDetails(patientIds);
        List<Long> rosterIds = page.getContent().stream().map(PeisGroupRoster::getId).toList();
        Map<Long, List<Long>> optionalPackageMap =
            peisGroupCheckQueryService.mapRosterOptionalPackageIds(rosterIds);
        List<GroupRosterRowDto> rows = new ArrayList<>();
        for (PeisGroupRoster entity : page.getContent()) {
            rows.add(toRosterRowDto(
                entity, patientMap.get(entity.getPatientId()), optionalPackageMap.get(entity.getId())));
        }
        // Asm：页码换算仅走 Builder（B.10.11）
        return PagedResultBuilder.build(new PagedResult<>(), rows, page);
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
            throw new ValidationException("查询分组失败：单位不能为空");
        }
        List<PeisGroupBatch> entities = peisGroupCheckQueryService.listBatchesCoveringCompany(request.getCompanyId());
        List<Long> batchIds = entities.stream().map(PeisGroupBatch::getId).toList();
        Map<Long, List<Long>> packageMap = peisGroupCheckQueryService.mapBatchPackageIds(batchIds);
        List<BatchRowDto> rows = new ArrayList<>();
        for (PeisGroupBatch entity : entities) {
            rows.add(toBatchRowDto(entity, packageMap.get(entity.getId())));
        }
        return rows;
    }

    /**
     * 按单位（含下级）列出团检合同。
     */
    public List<ContractRowDto> listContracts(ListContractsRequest request) {
        if (request == null || request.getCompanyId() == null) {
            throw new ValidationException("查询合同失败：单位不能为空");
        }
        List<ContractRowDto> rows = new ArrayList<>();
        List<PeisGroupContract> entities =
            peisGroupCheckQueryService.listContractsCoveringCompany(request.getCompanyId());
        List<Long> contractIds = entities.stream().map(PeisGroupContract::getId).toList();
        Map<Long, List<Long>> packageMap = peisGroupCheckQueryService.mapContractPackageIds(contractIds);
        for (PeisGroupContract entity : entities) {
            rows.add(toContractRowDto(entity, packageMap.get(entity.getId())));
        }
        return rows;
    }

    /**
     * 列出已启用的合同类型字典（booking/窗口/小程序只读）。
     *
     * @return 类型行列表
     */
    public List<ContractTypeRowDto> listContractTypes() {
        // Pre：读 peis 种子字典启用项
        List<ContractTypeRowDto> rows = new ArrayList<>();
        for (var entity : peisGroupCheckQueryService.listEnabledContractTypes()) {
            ContractTypeRowDto dto = new ContractTypeRowDto();
            dto.setTypeCode(entity.getTypeCode());
            dto.setTypeName(entity.getTypeName());
            dto.setSortOrder(entity.getSortOrder());
            rows.add(dto);
        }
        // Asm：返回结构化字典行
        return rows;
    }

    /**
     * 查询患者当日有效团检合同（多合同由前端手选；无合同则散客池）。
     *
     * @param request 患者主键
     * @return 有效合同行（含类型编码）
     */
    public List<ContractRowDto> listValidContractsForPatient(ListValidContractsForPatientRequest request) {
        // Gate：患者主键必填
        if (request == null || request.getPatientId() == null) {
            throw new ValidationException("查询有效合同失败：患者不能为空");
        }
        // Pre：名册→批次→合同并过滤有效期
        List<PeisGroupContract> entities =
            peisGroupCheckQueryService.listValidContractsForPatient(request.getPatientId(), java.time.LocalDate.now());
        List<Long> contractIds = entities.stream().map(PeisGroupContract::getId).toList();
        Map<Long, List<Long>> packageMap = peisGroupCheckQueryService.mapContractPackageIds(contractIds);
        List<ContractRowDto> rows = new ArrayList<>();
        for (PeisGroupContract entity : entities) {
            rows.add(toContractRowDto(entity, packageMap.get(entity.getId())));
        }
        // Asm：返回供窗口/小程序手选
        return rows;
    }

    private static List<RosterEnrollmentItemDto> resolveEnrollmentItems(ConfirmBatchEnrollmentRequest request) {
        List<RosterEnrollmentItemDto> items = new ArrayList<>();
        if (request == null) {
            return items;
        }
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            items.addAll(request.getItems());
            return items;
        }
        if (request.getRosterIds() != null) {
            for (Long rosterId : request.getRosterIds()) {
                if (rosterId == null) {
                    continue;
                }
                RosterEnrollmentItemDto item = new RosterEnrollmentItemDto();
                item.setRosterId(rosterId);
                items.add(item);
            }
        }
        return items;
    }

    private List<Long> resolveBatchPackageIds(SaveGroupBatchRequest request) {
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

    private List<Long> resolveOptionalPackageIdsForImport(Long batchId, RosterImportRowDto row) {
        if (!StringUtils.isEmpty(row.getPackageNames())) {
            List<String> names = splitPackageNames(row.getPackageNames());
            return groupRosterImportPackageResolver.resolvePackageIdsByNames(batchId, names);
        }
        return peisGroupCheckQueryService.listBatchPackageIds(batchId);
    }

    private static List<String> splitPackageNames(String raw) {
        List<String> names = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return names;
        }
        String normalized = raw.replace('，', ',').replace('、', ',');
        String[] parts = normalized.split(",");
        for (String part : parts) {
            if (part != null && !part.trim().isEmpty()) {
                names.add(part.trim());
            }
        }
        return names;
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
        dto.setParentId(entity.getParentId());
        dto.setOrgId(entity.getOrgId());
        dto.setCampusCollegeId(entity.getCampusCollegeId());
        dto.setContactName(entity.getContactName());
        dto.setContactPhone(entity.getContactPhone());
        return dto;
    }

    /**
     * 合同实体转行 DTO；类型名由字典补齐。
     *
     * @param entity 合同实体
     * @param packageActivityIds 合同绑定套餐主键列表
     * @return 合同行
     */
    private ContractRowDto toContractRowDto(PeisGroupContract entity, List<Long> packageActivityIds) {
        ContractRowDto dto = new ContractRowDto();
        dto.setContractId(entity.getId());
        dto.setCompanyId(entity.getCompanyId());
        dto.setPackageActivityId(entity.getPackageActivityId());
        // 多套餐时列表优先；否则回落合同头上的单套餐
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
        dto.setContractTypeCode(entity.getContractTypeCode());
        dto.setContractTypeName(
            peisGroupCheckQueryService.findContractTypeName(entity.getContractTypeCode()).orElse(null));
        dto.setOrgId(entity.getOrgId());
        return dto;
    }

    private static BatchRowDto toBatchRowDto(PeisGroupBatch entity, List<Long> packageActivityIds) {
        BatchRowDto dto = new BatchRowDto();
        dto.setBatchId(entity.getId());
        dto.setCompanyId(entity.getCompanyId());
        dto.setContractId(entity.getContractId());
        dto.setBatchName(entity.getBatchName());
        dto.setPackageActivityId(entity.getPackageActivityId());
        if (packageActivityIds != null && !packageActivityIds.isEmpty()) {
            dto.setPackageActivityIds(packageActivityIds);
            dto.setPackageActivityId(packageActivityIds.get(0));
        } else if (entity.getPackageActivityId() != null) {
            dto.setPackageActivityIds(List.of(entity.getPackageActivityId()));
        }
        dto.setBookingDate(entity.getBookingDate());
        dto.setStatusCode(entity.getStatusCode());
        dto.setOrgId(entity.getOrgId());
        return dto;
    }

    private List<PackageOptionDto> buildPackageOptions(List<Long> packageActivityIds) {
        List<PackageOptionDto> options = new ArrayList<>();
        if (packageActivityIds == null) {
            return options;
        }
        for (Long packageActivityId : packageActivityIds) {
            if (packageActivityId == null) {
                continue;
            }
            PackageOptionDto option = new PackageOptionDto();
            option.setPackageActivityId(packageActivityId);
            option.setPackageName(resolvePackageName(packageActivityId));
            options.add(option);
        }
        return options;
    }

    private String resolvePackageName(Long packageActivityId) {
        ActivityDefinitionDetailQueryModel detail = activityDefinitionQueryService.getServiceCatalog(packageActivityId);
        ActivityDefinitionBaseQueryModel base = detail == null ? null : detail.getBaseInfo();
        if (base == null) {
            return null;
        }
        return base.getName();
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
        dto.setSelectedPackageActivityId(entity.getSelectedPackageActivityId());
        dto.setPackageSelected(entity.getSelectedPackageActivityId() != null);
        dto.setCanChangePackage(dto.getEnrolled() && !dto.getArrived());
        if (entity.getBatchId() != null) {
            PeisGroupBatch batch = peisGroupCheckQueryService.requireBatch(entity.getBatchId());
            dto.setBatchName(batch.getBatchName());
            dto.setContractId(batch.getContractId());
            dto.setPackageActivityId(entity.getSelectedPackageActivityId());
            if (entity.getSelectedPackageActivityId() != null) {
                dto.setPackageName(resolvePackageName(entity.getSelectedPackageActivityId()));
            }
            Long companyId = entity.getCompanyId();
            if (companyId == null) {
                companyId = batch.getCompanyId();
            }
            dto.setCompanyId(companyId);
            if (companyId != null) {
                try {
                    PeisGroupCompany company = peisGroupCheckQueryService.requireCompany(companyId);
                    dto.setCompanyName(company.getName());
                } catch (RuntimeException ignored) {
                    // 单位缺失时不阻断登记台查询
                }
            }
            List<Long> optionalIds = peisGroupCheckQueryService.listRosterOptionalPackageIds(entity.getId());
            dto.setOptionalPackages(buildPackageOptions(optionalIds));
        }
        return dto;
    }

    private static GroupRosterRowDto toRosterRowDto(
            PeisGroupRoster entity,
            BatchPatientDetailQueryModel patientDetail,
            List<Long> optionalPackageIds) {
        GroupRosterRowDto dto = new GroupRosterRowDto();
        dto.setRosterId(entity.getId());
        dto.setBatchId(entity.getBatchId());
        dto.setCompanyId(entity.getCompanyId());
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
        dto.setSelectedPackageActivityId(entity.getSelectedPackageActivityId());
        if (optionalPackageIds != null) {
            dto.setOptionalPackageActivityIds(new ArrayList<>(optionalPackageIds));
        }
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
