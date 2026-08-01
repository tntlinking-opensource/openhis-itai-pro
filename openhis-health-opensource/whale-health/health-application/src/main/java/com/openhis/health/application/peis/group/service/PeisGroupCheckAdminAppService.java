/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.service;

import com.openhis.health.application.peis.group.dto.BatchRowDto;
import com.openhis.health.application.peis.group.dto.CompanyRowDto;
import com.openhis.health.application.peis.group.dto.ContractRowDto;
import com.openhis.health.application.peis.group.dto.GroupRosterRowDto;
import com.openhis.health.application.peis.group.dto.RosterImportRowDto;
import com.openhis.health.application.peis.group.request.ConfirmGroupArrivalRequest;
import com.openhis.health.application.peis.group.request.ImportRosterRequest;
import com.openhis.health.application.peis.group.request.ListBatchesRequest;
import com.openhis.health.application.peis.group.request.ListContractsRequest;
import com.openhis.health.application.peis.group.request.PageGroupRosterRequest;
import com.openhis.health.application.peis.group.request.SaveCompanyRequest;
import com.openhis.health.application.peis.group.request.SaveContractRequest;
import com.openhis.health.application.peis.group.request.SaveGroupBatchRequest;
import com.openhis.health.application.peis.group.response.ConfirmGroupArrivalResponse;
import com.openhis.health.application.peis.group.response.ImportRosterResponse;
import com.openhis.health.application.peis.group.response.SaveCompanyResponse;
import com.openhis.health.application.peis.group.response.SaveContractResponse;
import com.openhis.health.application.peis.group.response.SaveGroupBatchResponse;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.domain.peis.group.entity.PeisGroupBatch;
import com.openhis.health.domain.peis.group.entity.PeisGroupCompany;
import com.openhis.health.domain.peis.group.entity.PeisGroupContract;
import com.openhis.health.domain.peis.group.entity.PeisGroupRoster;
import com.openhis.health.domain.peis.group.service.PeisGroupCheckDomainService;
import com.openhis.health.domain.peis.group.service.PeisGroupCheckQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.dto.PagedResult;
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

/**
 * 团检管理应用服务（AS-PEIS-GRP-001）。
 *
 * <p>单位/合同/批次/名册维护与到检确认；建 Encounter 由 AS-PE-FIN-002 负责。</p>
 */
@Service
@Validated
@RequiredArgsConstructor
public class PeisGroupCheckAdminAppService {

    private final PeisGroupCheckDomainService peisGroupCheckDomainService;
    private final PeisGroupCheckQueryService peisGroupCheckQueryService;
    private final CurrentPractitionerResolver currentPractitionerResolver;

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
        if (request == null || request.getCompanyId() == null || request.getPackageActivityId() == null) {
            throw new ValidationException("保存合同失败：单位与套餐不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再维护合同");
        peisGroupCheckQueryService.requireCompany(request.getCompanyId());
        PeisGroupContract entity = new PeisGroupContract();
        entity.setId(request.getContractId());
        entity.setCompanyId(request.getCompanyId());
        entity.setPackageActivityId(request.getPackageActivityId());
        entity.setContractName(request.getContractName());
        entity.setValidFrom(request.getValidFrom());
        entity.setValidTo(request.getValidTo());
        entity.setOrgId(request.getOrgId());
        PeisGroupContract saved = peisGroupCheckDomainService.saveContract(entity, operatorId);
        SaveContractResponse response = new SaveContractResponse();
        response.setContractId(saved.getId());
        return response;
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
        Long companyId = request.getCompanyId() != null ? request.getCompanyId() : contract.getCompanyId();
        Long packageActivityId = request.getPackageActivityId() != null
            ? request.getPackageActivityId() : contract.getPackageActivityId();
        PeisGroupBatch entity = new PeisGroupBatch();
        entity.setId(request.getBatchId());
        entity.setCompanyId(companyId);
        entity.setContractId(request.getContractId());
        entity.setBatchName(request.getBatchName().trim());
        entity.setPackageActivityId(packageActivityId);
        entity.setBookingDate(request.getBookingDate());
        entity.setOrgId(request.getOrgId() != null ? request.getOrgId() : contract.getOrgId());
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
        peisGroupCheckQueryService.requireBatch(request.getBatchId());
        List<PeisGroupRoster> rows = new ArrayList<>();
        for (RosterImportRowDto row : request.getRows()) {
            if (row == null || StringUtils.isEmpty(row.getFullName())) {
                continue;
            }
            PeisGroupRoster entity = new PeisGroupRoster();
            entity.setPatientId(row.getPatientId());
            entity.setFullName(row.getFullName().trim());
            entity.setStudentNo(row.getStudentNo());
            entity.setEmployeeNo(row.getEmployeeNo());
            entity.setCollegeId(row.getCollegeId());
            entity.setClassName(row.getClassName());
            entity.setIdCardNo(row.getIdCardNo());
            entity.setMobilePhone(row.getMobilePhone());
            rows.add(entity);
        }
        if (rows.isEmpty()) {
            throw new ValidationException("导入名册失败：有效姓名行不能为空");
        }
        int imported = peisGroupCheckDomainService.importRosterRows(request.getBatchId(), rows, operatorId);
        ImportRosterResponse response = new ImportRosterResponse();
        response.setImportedCount(imported);
        return response;
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
            request.getCollegeId(),
            request.getClassName(),
            pageable);
        List<GroupRosterRowDto> rows = new ArrayList<>();
        for (PeisGroupRoster entity : page.getContent()) {
            rows.add(toRosterRowDto(entity));
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
        for (PeisGroupContract entity : peisGroupCheckQueryService.listContracts(request.getCompanyId())) {
            rows.add(toContractRowDto(entity));
        }
        return rows;
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

    private static ContractRowDto toContractRowDto(PeisGroupContract entity) {
        ContractRowDto dto = new ContractRowDto();
        dto.setContractId(entity.getId());
        dto.setCompanyId(entity.getCompanyId());
        dto.setPackageActivityId(entity.getPackageActivityId());
        dto.setContractName(entity.getContractName());
        dto.setValidFrom(entity.getValidFrom());
        dto.setValidTo(entity.getValidTo());
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

    private static GroupRosterRowDto toRosterRowDto(PeisGroupRoster entity) {
        GroupRosterRowDto dto = new GroupRosterRowDto();
        dto.setRosterId(entity.getId());
        dto.setBatchId(entity.getBatchId());
        dto.setPatientId(entity.getPatientId());
        dto.setFullName(entity.getFullName());
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
}
