/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.medicationdispense.outpatient.service;

import com.openhis.health.application.report.medicationdispense.outpatient.dto.OutpatientMedicationDispenseDetailRowDto;
import com.openhis.health.application.report.medicationdispense.outpatient.dto.OutpatientMedicationDispenseExportRowDto;
import com.openhis.health.application.report.medicationdispense.outpatient.dto.OutpatientMedicationDispenseSummaryRowDto;
import com.openhis.health.application.report.medicationdispense.outpatient.mapper.OutpatientMedicationDispenseReportMapper;
import com.openhis.health.application.report.medicationdispense.outpatient.request.ExportOutpatientMedicationDispenseReportRequest;
import com.openhis.health.application.report.medicationdispense.outpatient.request.LoadOutpatientMedicationDispenseReportPageInitRequest;
import com.openhis.health.application.report.medicationdispense.outpatient.request.PageOutpatientMedicationDispenseReportDetailRequest;
import com.openhis.health.application.report.medicationdispense.outpatient.request.SummarizeOutpatientMedicationDispenseReportRequest;
import com.openhis.health.application.report.medicationdispense.outpatient.response.ExportOutpatientMedicationDispenseReportResponse;
import com.openhis.health.application.report.medicationdispense.outpatient.response.LoadOutpatientMedicationDispenseReportPageInitResponse;
import com.openhis.health.application.report.medicationdispense.outpatient.response.PageOutpatientMedicationDispenseReportDetailResponse;
import com.openhis.health.application.report.medicationdispense.outpatient.response.SummarizeOutpatientMedicationDispenseReportResponse;
import com.openhis.health.application.report.medicationdispense.outpatient.support.OutpatientMedicationDispenseReportQuerySupport;
import com.openhis.health.application.shared.report.ReportPatientSettlementContextAssembler;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseReportSummaryRow;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseRecordPageQuery;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseReportSummaryQuery;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.MedicationDispenseReportConstants;
import com.openhis.health.domain.share.constants.OutpatientMedicationDispenseReportConstants;
import com.openhis.health.domain.share.constants.PharmacyDispenseRecordConstants;
import com.openhis.health.domain.share.constants.PharmacyDispenseRecordExportConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.DeptIssueReportLineQueryModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.DeptIssueReportSummaryRow;
import com.openhis.health.domain.workflow.supplyrequest.query.DeptIssueReportPageQuery;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.util.AnnotatedExcelExporter;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 门诊发药报表应用服务（AS-RPT-003，IF-RPT-OMD-01～04）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：登录、日期窗、分页窗口。</li>
 *   <li>Pre：{@link OutpatientMedicationDispenseReportQuerySupport} 两路预解析与装配。</li>
 *   <li>Asm：Mapper + Excel 导出。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/outpatientMedicationDispenseReport/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutpatientMedicationDispenseReportAppService {

    /** 读路径登录门禁（报表只读亦须会话）。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 明细分页 pageNo/pageSize 正数门禁。 */
    private final PageWindowGate pageWindowGate;

    /** 启用药房下拉（报表不按管辖库房收窄）。 */
    private final LocationQueryService locationQueryService;

    /** 开立/领用科室下拉。 */
    private final OrganizationQueryService organizationQueryService;

    /** medication_category_code 术语下拉。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 发药人（药师岗）下拉主档。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 患者发放 ∪ 科室领用两路预解析与装配（B.6.4）。 */
    private final OutpatientMedicationDispenseReportQuerySupport outpatientMedicationDispenseReportQuerySupport;

    /** Request/Response 与 Option/导出映射。 */
    private final OutpatientMedicationDispenseReportMapper outpatientMedicationDispenseReportMapper;

    /** 费用性质 / medType 上下文装配。 */
    private final ReportPatientSettlementContextAssembler reportPatientSettlementContextAssembler;

    /**
     * IF-RPT-OMD-01：门诊发药报表页初始化。
     *
     * @param request 占位请求
     * @return 筛选项与默认日期
     * @throws BusinessException EX-RPT-OMD-101
     */
    public LoadOutpatientMedicationDispenseReportPageInitResponse loadOutpatientMedicationDispenseReportPageInit(
        LoadOutpatientMedicationDispenseReportPageInitRequest request) {
        // Gate：须已登录方可加载报表元数据
        this.currentOperatorResolver.requireOperatorId(
            OutpatientMedicationDispenseReportConstants.EX_OMD_101,
            "页面初始化失败，请稍后重试");
        try {
            // Pre：启用药房、药品类型、发药人与固定筛选项
            List<Location> pharmacies = this.locationQueryService.listAllActivePharmacies();
            List<OptionDto> categoryOptions =
                this.terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.MEDICATION_CATEGORY_CODE);
            Page<Practitioner> dispenserPage = this.practitionerQueryService.pageActivePractitionersForPosition(
                null,
                null,
                PageRequest.of(0, PharmacyDispenseRecordConstants.DISPENSER_OPTION_PAGE_SIZE),
                false);
            List<OptionDto> practitionerOptions = new ArrayList<>();
            // 发药人下拉：跳过无主键脏行，避免前端 Option 值为空
            for (Practitioner practitioner : dispenserPage.getContent()) {
                // 无主键的主档行无法回传筛选，直接丢弃
                if (practitioner == null || practitioner.getId() == null) {
                    continue;
                }
                OptionDto option = new OptionDto();
                option.setValue(String.valueOf(practitioner.getId()));
                option.setLabel(practitioner.getName());
                practitionerOptions.add(option);
            }
            LocalDate today = LocalDate.now();
            String defaultFrom = today.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            String defaultTo = today.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            List<OptionDto> feeNatureOptions = this.reportPatientSettlementContextAssembler.loadFeeNatureOptions();
            List<OptionDto> organizationOptions = this.buildOrganizationOptions();
            // Asm：装配初始化响应
            LoadOutpatientMedicationDispenseReportPageInitResponse response =
                this.outpatientMedicationDispenseReportMapper.toLoadPageInitResponse(
                pharmacies,
                categoryOptions,
                practitionerOptions,
                this.outpatientMedicationDispenseReportMapper.buildRecordTypeOptions(),
                this.outpatientMedicationDispenseReportMapper.buildSummaryDimensionOptions(),
                this.outpatientMedicationDispenseReportMapper.buildSourceOptions(),
                feeNatureOptions,
                defaultFrom,
                defaultTo);
            response.setOrganizationOptions(organizationOptions);
            return response;
        } catch (RuntimeException ex) {
            log.warn("门诊发药报表页初始化失败", ex);
            throw new BusinessException(
                OutpatientMedicationDispenseReportConstants.EX_OMD_101,
                "页面初始化失败，请稍后重试");
        }
    }

    /**
     * IF-RPT-OMD-02：门诊发药报表净汇总。
     *
     * @param request 筛选条件
     * @return 汇总行与页脚合计
     * @throws BusinessException EX-RPT-OMD-102 / 103
     */
    public SummarizeOutpatientMedicationDispenseReportResponse summarizeOutpatientMedicationDispenseReport(
        SummarizeOutpatientMedicationDispenseReportRequest request) {
        // Gate：登录与日期窗
        this.currentOperatorResolver.requireOperatorId(
            OutpatientMedicationDispenseReportConstants.EX_OMD_103,
            "查询失败，请稍后重试");
        this.validateDispenseDateWindow(request.getDispenseDateFrom(), request.getDispenseDateTo());
        try {
            String sourceCode =
                this.outpatientMedicationDispenseReportQuerySupport.normalizeSourceCode(request.getSourceCode());
            String summaryDimension =
                this.outpatientMedicationDispenseReportQuerySupport.normalizeSummaryDimension(
                    request.getSummaryDimension());
            List<MedicationDispenseReportSummaryRow> patientRows = List.of();
            List<DeptIssueReportSummaryRow> deptRows = List.of();
            MedicationDispenseRecordPageQuery recordQuery = null;
            DeptIssueReportPageQuery deptQuery = null;
            Map<Long, String> categoryCodeByMedicationId = null;
            // Pre：科室维仅领用侧有组织主键；类型维仅患者侧有 category；按来源+维度决定查哪一路
            boolean queryPatient = this.outpatientMedicationDispenseReportQuerySupport.includePatientDispense(sourceCode)
                && !MedicationDispenseReportConstants.SUMMARY_DIMENSION_DEPARTMENT.equals(summaryDimension);
            boolean queryDept = this.outpatientMedicationDispenseReportQuerySupport.includeDeptIssue(sourceCode)
                && !MedicationDispenseReportConstants.SUMMARY_DIMENSION_CATEGORY.equals(summaryDimension);
            // 患者侧：发放净数量分桶（金额由明细 enrich 后汇总）
            if (queryPatient) {
                recordQuery =
                    this.outpatientMedicationDispenseReportQuerySupport.buildPatientRecordQuery(
                        request.getDispenseDateFrom(),
                        request.getDispenseDateTo(),
                        request.getRecordTypeCode(),
                        request.getPharmacyLocationId(),
                        request.getDispenserPractitionerId(),
                        request.getMedicationCategoryCodes(),
                        request.getDrugKeyword(),
                        request.getPatientKeyword(),
                        request.getFeeNatureContractNo(),
                        request.getOrganizationId(),
                        1,
                        PharmacyDispenseRecordExportConstants.EXPORT_MAX_ROWS);
                MedicationDispenseReportSummaryQuery summaryQuery =
                    this.outpatientMedicationDispenseReportQuerySupport.buildPatientSummaryQuery(
                        recordQuery, summaryDimension);
                patientRows = this.outpatientMedicationDispenseReportQuerySupport.summarizePatient(summaryQuery);
                if (MedicationDispenseReportConstants.SUMMARY_DIMENSION_CATEGORY.equals(summaryDimension)) {
                    categoryCodeByMedicationId = summaryQuery.getCategoryCodeByMedicationId();
                }
            }
            // 领用侧：已同意 DEPT_ISSUE/RETURN 药品行净数量（与患者侧同维键可合并）
            if (queryDept) {
                deptQuery =
                    this.outpatientMedicationDispenseReportQuerySupport.buildDeptIssueQuery(
                        request.getDispenseDateFrom(),
                        request.getDispenseDateTo(),
                        request.getPharmacyLocationId(),
                        request.getMedicationCategoryCodes(),
                        request.getDrugKeyword(),
                        request.getOrganizationId(),
                        summaryDimension,
                        1,
                        PharmacyDispenseRecordExportConstants.EXPORT_MAX_ROWS);
                deptRows = this.outpatientMedicationDispenseReportQuerySupport.summarizeDept(deptQuery);
            }
            // Asm：同 dimensionKey 净数量相加并回填维度展示名
            List<OutpatientMedicationDispenseSummaryRowDto> rows =
                this.outpatientMedicationDispenseReportQuerySupport.mergeSummaryRows(
                    patientRows, deptRows, summaryDimension);
            this.outpatientMedicationDispenseReportQuerySupport.enrichSummaryPurchaseSaleAmounts(
                rows,
                recordQuery,
                deptQuery,
                queryPatient,
                queryDept,
                summaryDimension,
                categoryCodeByMedicationId);
            BigDecimal totalQuantity = BigDecimal.ZERO;
            BigDecimal purchaseTotalAmount = null;
            BigDecimal saleTotalAmount = null;
            // 页脚：净数量必有；进价/售价净额仅累加非空行
            for (OutpatientMedicationDispenseSummaryRowDto row : rows) {
                if (row.getNetQuantity() != null) {
                    totalQuantity = totalQuantity.add(row.getNetQuantity());
                }
                if (row.getPurchaseNetAmount() != null) {
                    if (purchaseTotalAmount == null) {
                        purchaseTotalAmount = BigDecimal.ZERO;
                    }
                    purchaseTotalAmount = purchaseTotalAmount.add(row.getPurchaseNetAmount());
                }
                BigDecimal saleNet = row.getSaleNetAmount();
                if (saleNet == null) {
                    saleNet = row.getNetAmount();
                }
                if (saleNet != null) {
                    if (saleTotalAmount == null) {
                        saleTotalAmount = BigDecimal.ZERO;
                    }
                    saleTotalAmount = saleTotalAmount.add(saleNet);
                }
            }
            return this.outpatientMedicationDispenseReportMapper.toSummarizeResponse(
                rows, totalQuantity, purchaseTotalAmount, saleTotalAmount);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("门诊发药报表汇总失败", ex);
            throw new BusinessException(
                OutpatientMedicationDispenseReportConstants.EX_OMD_103,
                "查询失败，请稍后重试");
        }
    }

    /**
     * IF-RPT-OMD-03：门诊发药报表明细分页（单源领域真分页：患者或领用）。
     *
     * @param request 筛选与分页
     * @return 平铺明细分页
     * @throws BusinessException EX-RPT-OMD-102 / 103；来源 ALL 时「禁止全部来源混查」
     */
    public PageOutpatientMedicationDispenseReportDetailResponse pageOutpatientMedicationDispenseReportDetail(
        PageOutpatientMedicationDispenseReportDetailRequest request) {
        // Gate：登录、分页、日期窗
        this.currentOperatorResolver.requireOperatorId(
            OutpatientMedicationDispenseReportConstants.EX_OMD_103,
            "查询失败，请稍后重试");
        this.pageWindowGate.requirePositivePageWindow(
            request,
            OutpatientMedicationDispenseReportConstants.EX_OMD_103,
            "分页参数无效");
        this.validateDispenseDateWindow(request.getDispenseDateFrom(), request.getDispenseDateTo());
        try {
            // Gate：规范化来源；ALL/未知码在 support 内拒绝
            String sourceCode =
                this.outpatientMedicationDispenseReportQuerySupport.normalizeSourceCode(request.getSourceCode());
            boolean queryPatient =
                this.outpatientMedicationDispenseReportQuerySupport.includePatientDispense(sourceCode);
            boolean queryDept = this.outpatientMedicationDispenseReportQuerySupport.includeDeptIssue(sourceCode);
            int pageNo = request.getPageNo();
            int pageSize = request.getPageSize();
            // Pre：患者单源 → slip 领域分页，禁止 pageNo*pageSize 前缀预取
            if (queryPatient && !queryDept) {
                MedicationDispenseRecordPageQuery patientQuery =
                    this.outpatientMedicationDispenseReportQuerySupport.buildPatientRecordQuery(
                        request.getDispenseDateFrom(),
                        request.getDispenseDateTo(),
                        request.getRecordTypeCode(),
                        request.getPharmacyLocationId(),
                        request.getDispenserPractitionerId(),
                        request.getMedicationCategoryCodes(),
                        request.getDrugKeyword(),
                        request.getPatientKeyword(),
                        request.getFeeNatureContractNo(),
                        request.getOrganizationId(),
                        pageNo,
                        pageSize);
                Page<MedicationDispenseSlipQueryModel> slipPage =
                    this.outpatientMedicationDispenseReportQuerySupport.pagePatientSlips(patientQuery);
                List<OutpatientMedicationDispenseDetailRowDto> rows =
                    this.outpatientMedicationDispenseReportQuerySupport.assemblePatientDetailRows(
                        slipPage.getContent());
                // Asm：total 用领域分页元数据
                return this.outpatientMedicationDispenseReportMapper.toPageDetailResponse(
                    rows, slipPage.getTotalElements(), pageNo, pageSize);
            }
            // Pre：领用单源 → 领域分页
            if (queryDept && !queryPatient) {
                DeptIssueReportPageQuery deptQuery =
                    this.outpatientMedicationDispenseReportQuerySupport.buildDeptIssueQuery(
                        request.getDispenseDateFrom(),
                        request.getDispenseDateTo(),
                        request.getPharmacyLocationId(),
                        request.getMedicationCategoryCodes(),
                        request.getDrugKeyword(),
                        request.getOrganizationId(),
                        null,
                        pageNo,
                        pageSize);
                Page<DeptIssueReportLineQueryModel> deptPage =
                    this.outpatientMedicationDispenseReportQuerySupport.pageDeptLines(deptQuery);
                List<OutpatientMedicationDispenseDetailRowDto> rows =
                    this.outpatientMedicationDispenseReportQuerySupport.assembleDeptDetailRows(deptPage.getContent());
                return this.outpatientMedicationDispenseReportMapper.toPageDetailResponse(
                    rows, deptPage.getTotalElements(), pageNo, pageSize);
            }
            // Gate：防御——规范化后仍非单源则拒绝混查
            throw new BusinessException(
                OutpatientMedicationDispenseReportConstants.EX_OMD_103,
                "禁止全部来源混查");
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("门诊发药报表明细分页失败", ex);
            throw new BusinessException(
                OutpatientMedicationDispenseReportConstants.EX_OMD_103,
                "查询失败，请稍后重试");
        }
    }

    /**
     * IF-RPT-OMD-04：导出当前 Tab（汇总或明细）。
     *
     * @param request 筛选 + presentationMode
     * @return xlsx 字节流
     * @throws BusinessException EX-RPT-OMD-102 / 104
     */
    public ExportOutpatientMedicationDispenseReportResponse exportOutpatientMedicationDispenseReport(
        ExportOutpatientMedicationDispenseReportRequest request) {
        // Gate：登录与日期窗
        this.currentOperatorResolver.requireOperatorId(
            OutpatientMedicationDispenseReportConstants.EX_OMD_104,
            "导出失败，请稍后重试");
        this.validateDispenseDateWindow(request.getDispenseDateFrom(), request.getDispenseDateTo());
        try {
            String presentationMode = StringUtils.trimToNull(request.getPresentationMode());
            // 未传呈现模式时默认明细 Tab，与页面默认「当前 Tab」导出一致
            if (presentationMode == null) {
                presentationMode = MedicationDispenseReportConstants.PRESENTATION_MODE_DETAIL;
            }
            List<OutpatientMedicationDispenseExportRowDto> exportRows = new ArrayList<>();
            boolean truncated = false;
            // Pre：按当前 Tab 走汇总全量或明细上限列表（BR-RPT-OMD-008）
            if (MedicationDispenseReportConstants.PRESENTATION_MODE_SUMMARY.equals(presentationMode)) {
                SummarizeOutpatientMedicationDispenseReportRequest summarizeRequest =
                    new SummarizeOutpatientMedicationDispenseReportRequest();
                summarizeRequest.setDispenseDateFrom(request.getDispenseDateFrom());
                summarizeRequest.setDispenseDateTo(request.getDispenseDateTo());
                summarizeRequest.setRecordTypeCode(request.getRecordTypeCode());
                summarizeRequest.setPharmacyLocationId(request.getPharmacyLocationId());
                summarizeRequest.setMedicationCategoryCodes(request.getMedicationCategoryCodes());
                summarizeRequest.setDispenserPractitionerId(request.getDispenserPractitionerId());
                summarizeRequest.setDrugKeyword(request.getDrugKeyword());
                summarizeRequest.setPatientKeyword(request.getPatientKeyword());
                summarizeRequest.setSummaryDimension(request.getSummaryDimension());
                summarizeRequest.setSourceCode(request.getSourceCode());
                SummarizeOutpatientMedicationDispenseReportResponse summaryResponse =
                    this.summarizeOutpatientMedicationDispenseReport(summarizeRequest);
                // 汇总导出行：维度名 + 净量净额
                for (OutpatientMedicationDispenseSummaryRowDto row : summaryResponse.getRows()) {
                    exportRows.add(this.outpatientMedicationDispenseReportMapper.toExportRowFromSummary(row));
                }
            } else {
                // 明细导出：两路合并后截断至 EXPORT_MAX_ROWS
                List<OutpatientMedicationDispenseDetailRowDto> detailRows =
                    this.loadDetailRowsForExport(request);
                truncated = detailRows.size() >= PharmacyDispenseRecordExportConstants.EXPORT_MAX_ROWS;
                for (OutpatientMedicationDispenseDetailRowDto row : detailRows) {
                    exportRows.add(this.outpatientMedicationDispenseReportMapper.toExportRowFromDetail(row));
                }
            }
            // Asm：生成 xlsx
            byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                OutpatientMedicationDispenseReportConstants.EXPORT_SHEET_NAME,
                exportRows,
                OutpatientMedicationDispenseExportRowDto.class);
            return this.outpatientMedicationDispenseReportMapper.toExportResponse(
                body, exportRows.size(), truncated);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("门诊发药报表导出失败", ex);
            throw new BusinessException(
                OutpatientMedicationDispenseReportConstants.EX_OMD_104,
                "导出失败，请稍后重试");
        }
    }

    /**
     * 导出明细：按单源 list 至 EXPORT_MAX_ROWS；禁止两路并集。
     *
     * @param request 导出请求
     * @return 明细行
     */
    private List<OutpatientMedicationDispenseDetailRowDto> loadDetailRowsForExport(
        ExportOutpatientMedicationDispenseReportRequest request) {
        // Gate：与明细分页同来源口径
        String sourceCode =
            this.outpatientMedicationDispenseReportQuerySupport.normalizeSourceCode(request.getSourceCode());
        boolean queryPatient =
            this.outpatientMedicationDispenseReportQuerySupport.includePatientDispense(sourceCode);
        boolean queryDept = this.outpatientMedicationDispenseReportQuerySupport.includeDeptIssue(sourceCode);
        // 患者单源：有界 list
        if (queryPatient && !queryDept) {
            MedicationDispenseRecordPageQuery patientQuery =
                this.outpatientMedicationDispenseReportQuerySupport.buildPatientRecordQuery(
                    request.getDispenseDateFrom(),
                    request.getDispenseDateTo(),
                    request.getRecordTypeCode(),
                    request.getPharmacyLocationId(),
                    request.getDispenserPractitionerId(),
                    request.getMedicationCategoryCodes(),
                    request.getDrugKeyword(),
                    request.getPatientKeyword(),
                    request.getFeeNatureContractNo(),
                    request.getOrganizationId(),
                    1,
                    PharmacyDispenseRecordExportConstants.EXPORT_MAX_ROWS);
            List<MedicationDispenseSlipQueryModel> slips =
                this.outpatientMedicationDispenseReportQuerySupport.listPatientSlips(patientQuery);
            return this.outpatientMedicationDispenseReportQuerySupport.assemblePatientDetailRows(slips);
        }
        // 领用单源：同上限；退领数量/金额已取负
        if (queryDept && !queryPatient) {
            DeptIssueReportPageQuery deptQuery =
                this.outpatientMedicationDispenseReportQuerySupport.buildDeptIssueQuery(
                    request.getDispenseDateFrom(),
                    request.getDispenseDateTo(),
                    request.getPharmacyLocationId(),
                    request.getMedicationCategoryCodes(),
                    request.getDrugKeyword(),
                    request.getOrganizationId(),
                    null,
                    1,
                    PharmacyDispenseRecordExportConstants.EXPORT_MAX_ROWS);
            List<DeptIssueReportLineQueryModel> lines =
                this.outpatientMedicationDispenseReportQuerySupport.listDeptLines(deptQuery);
            return this.outpatientMedicationDispenseReportQuerySupport.assembleDeptDetailRows(lines);
        }
        throw new BusinessException(
            OutpatientMedicationDispenseReportConstants.EX_OMD_104,
            "禁止全部来源混查");
    }

    /**
     * 构造开立/领用科室下拉（门诊挂号组织树）。
     *
     * @return 科室选项
     */
    private List<OptionDto> buildOrganizationOptions() {
        List<OptionDto> options = new ArrayList<>();
        List<Organization> organizations =
            this.organizationQueryService.listOrganizationsForOutpatientRegistration();
        for (Organization organization : organizations) {
            // 缺主键的脏数据不入下拉
            if (organization == null || organization.getId() == null) {
                continue;
            }
            OptionDto option = new OptionDto();
            option.setValue(String.valueOf(organization.getId()));
            option.setLabel(organization.getName());
            options.add(option);
        }
        return options;
    }

    /**
     * Gate：业务日闭区间合法且起止不颠倒。
     *
     * @param dispenseDateFrom 日起
     * @param dispenseDateTo   日止
     */
    private void validateDispenseDateWindow(String dispenseDateFrom, String dispenseDateTo) {
        // Gate：缺日起或日止直接拒绝（BR-RPT-OMD-001）
        if (StringUtils.isEmpty(dispenseDateFrom) || StringUtils.isEmpty(dispenseDateTo)) {
            throw new BusinessException(
                OutpatientMedicationDispenseReportConstants.EX_OMD_102,
                "请选择有效时间范围");
        }
        LocalDate from = LocalDate.parse(dispenseDateFrom.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        LocalDate to = LocalDate.parse(dispenseDateTo.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        // Gate：结束日早于起始日时拒绝，避免空结果误导
        if (from.isAfter(to)) {
            throw new BusinessException(
                OutpatientMedicationDispenseReportConstants.EX_OMD_102,
                "请选择有效时间范围");
        }
    }
}
