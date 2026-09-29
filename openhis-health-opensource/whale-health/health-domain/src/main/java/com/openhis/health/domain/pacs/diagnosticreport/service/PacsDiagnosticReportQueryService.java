/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.diagnosticreport.service;

import com.openhis.health.domain.pacs.diagnosticreport.entity.PacsDiagnosticReport;
import com.openhis.health.domain.pacs.diagnosticreport.queries.model.PacsDiagnosticReportDetailQueryModel;
import com.openhis.health.domain.pacs.diagnosticreport.repository.PacsDiagnosticReportRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PacsReportStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.SpecificationUtils;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 检查报告读侧查询服务。
 */
@Service
@RequiredArgsConstructor
public class PacsDiagnosticReportQueryService {

    private final PacsDiagnosticReportRepository reportRepository;

    /**
     * 待审分页列表。
     *
     * @param organizationId 科室过滤（可空）
     * @param pageable 分页
     * @return 详情读模型页
     */
    public Page<PacsDiagnosticReportDetailQueryModel> searchPendingAudit(Long organizationId, Pageable pageable) {
        Specification<PacsDiagnosticReport> specification = SpecificationUtils.<PacsDiagnosticReport>tenantFilter()
            .and(SpecificationUtils.notDeleted())
            .and((root, cq, cb) -> cb.equal(root.get("statusEnum"), PacsReportStatus.PRELIMINARY.getCode()))
            .and((root, cq, cb) -> cb.isNotNull(root.get("submittedAt")));
        if (organizationId != null) {
            specification = specification.and(
                (root, cq, cb) -> cb.equal(root.get("organizationId"), organizationId));
        }
        Pageable resolved;
        if (pageable == null) {
            resolved = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "submittedAt"));
        } else {
            Sort sort = pageable.getSort();
            if (!sort.isSorted()) {
                sort = Sort.by(Sort.Direction.DESC, "submittedAt");
            }
            resolved = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        }
        return reportRepository.findAll(specification, resolved).map(this::toDetail);
    }

    /**
     * 按主键取详情。
     *
     * @param reportId 报告主键
     * @return 详情
     */
    public PacsDiagnosticReportDetailQueryModel getById(Long reportId) {
        PacsDiagnosticReport report = reportRepository.findById(reportId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.PACS_REPORT_NOT_FOUND, "检查报告不存在"));
        return this.toDetail(report);
    }

    /**
     * 按工单取主报告（可空）。
     *
     * @param workItemId 工单主键
     * @return 详情 Optional
     */
    public Optional<PacsDiagnosticReportDetailQueryModel> findByWorkItemId(Long workItemId) {
        return reportRepository.findByWorkItemIdAndAmendedFromIdIsNull(workItemId)
            .map(this::toDetail);
    }

    /**
     * 待审详情（须已提交）。
     *
     * @param reportId 报告主键
     * @return 详情
     */
    public PacsDiagnosticReportDetailQueryModel getPendingDetail(Long reportId) {
        PacsDiagnosticReportDetailQueryModel detail = this.getById(reportId);
        if (!Boolean.TRUE.equals(detail.getPendingAudit())) {
            throw new BusinessException(ErrorCodeConstants.PACS_REPORT_INVALID_STATUS, "报告非待审状态");
        }
        return detail;
    }

    /**
     * 报告列表分页（主报告；可选状态/科室/患者/签发时间窗）。
     *
     * @param statusEnum 状态（可空）
     * @param organizationId 科室（可空）
     * @param patientId 患者（可空）
     * @param issuedFrom 签发起（含，可空）
     * @param issuedTo 签发止（含，可空）
     * @param pageable 分页
     * @return 详情读模型页
     */
    public Page<PacsDiagnosticReportDetailQueryModel> searchReports(
        String statusEnum,
        Long organizationId,
        Long patientId,
        LocalDateTime issuedFrom,
        LocalDateTime issuedTo,
        Pageable pageable) {
        Specification<PacsDiagnosticReport> specification = this.buildMainReportSpecification();
        if (!StringUtils.isEmpty(statusEnum)) {
            specification = specification.and(
                (root, cq, cb) -> cb.equal(root.get("statusEnum"), statusEnum));
        }
        if (organizationId != null) {
            specification = specification.and(
                (root, cq, cb) -> cb.equal(root.get("organizationId"), organizationId));
        }
        if (patientId != null) {
            specification = specification.and(
                (root, cq, cb) -> cb.equal(root.get("patientId"), patientId));
        }
        if (issuedFrom != null) {
            specification = specification.and(
                (root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("issuedAt"), issuedFrom));
        }
        if (issuedTo != null) {
            specification = specification.and(
                (root, cq, cb) -> cb.lessThanOrEqualTo(root.get("issuedAt"), issuedTo));
        }
        Pageable resolved = this.resolveUpdatedAtDescPageable(pageable);
        return reportRepository.findAll(specification, resolved).map(this::toDetail);
    }

    /**
     * 同患者主报告历史分页。
     *
     * @param patientId 患者主键（必填）
     * @param excludeReportId 排除报告主键（可空）
     * @param pageable 分页
     * @return 详情读模型页
     */
    public Page<PacsDiagnosticReportDetailQueryModel> searchHistoryByPatient(
        Long patientId, Long excludeReportId, Pageable pageable) {
        if (patientId == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "患者主键不能为空");
        }
        Specification<PacsDiagnosticReport> specification = this.buildMainReportSpecification()
            .and((root, cq, cb) -> cb.equal(root.get("patientId"), patientId));
        if (excludeReportId != null) {
            specification = specification.and(
                (root, cq, cb) -> cb.notEqual(root.get("id"), excludeReportId));
        }
        Pageable resolved = this.resolveUpdatedAtDescPageable(pageable);
        return reportRepository.findAll(specification, resolved).map(this::toDetail);
    }

    private Specification<PacsDiagnosticReport> buildMainReportSpecification() {
        return SpecificationUtils.<PacsDiagnosticReport>tenantFilter()
            .and(SpecificationUtils.notDeleted())
            .and((root, cq, cb) -> cb.isNull(root.get("amendedFromId")));
    }

    private Pageable resolveUpdatedAtDescPageable(Pageable pageable) {
        Sort defaultSort = Sort.by(Sort.Direction.DESC, "updatedAt");
        if (pageable == null) {
            return PageRequest.of(0, 20, defaultSort);
        }
        if (pageable.getSort().isSorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
    }

    private PacsDiagnosticReportDetailQueryModel toDetail(PacsDiagnosticReport report) {
        PacsDiagnosticReportDetailQueryModel model = new PacsDiagnosticReportDetailQueryModel();
        model.setId(report.getId());
        model.setWorkItemId(report.getWorkItemId());
        model.setPatientId(report.getPatientId());
        model.setEncounterId(report.getEncounterId());
        model.setOrganizationId(report.getOrganizationId());
        model.setServiceRequestId(report.getServiceRequestId());
        model.setStatusEnum(report.getStatusEnum());
        model.setFindings(report.getFindings());
        model.setConclusion(report.getConclusion());
        model.setPdfStorageKey(report.getPdfStorageKey());
        model.setAuthorPractitionerId(report.getAuthorPractitionerId());
        model.setSubmittedAt(report.getSubmittedAt());
        model.setSubmittedBy(report.getSubmittedBy());
        model.setIssuedAt(report.getIssuedAt());
        model.setAuditorPractitionerId(report.getAuditorPractitionerId());
        model.setPendingAudit(report.isPendingAudit());
        return model;
    }
}
