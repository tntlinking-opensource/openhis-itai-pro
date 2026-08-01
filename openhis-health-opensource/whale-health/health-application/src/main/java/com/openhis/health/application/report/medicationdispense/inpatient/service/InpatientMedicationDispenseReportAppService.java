/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.medicationdispense.inpatient.service;

import com.openhis.health.application.report.medicationdispense.inpatient.dto.InpatientMedicationDispenseDetailRowDto;
import com.openhis.health.application.report.medicationdispense.inpatient.dto.InpatientMedicationDispenseExportRowDto;
import com.openhis.health.application.report.medicationdispense.inpatient.dto.InpatientMedicationDispenseSummaryRowDto;
import com.openhis.health.application.report.medicationdispense.inpatient.mapper.InpatientMedicationDispenseReportMapper;
import com.openhis.health.application.report.medicationdispense.inpatient.request.ExportInpatientMedicationDispenseReportRequest;
import com.openhis.health.application.report.medicationdispense.inpatient.request.LoadInpatientMedicationDispenseReportPageInitRequest;
import com.openhis.health.application.report.medicationdispense.inpatient.request.PageInpatientMedicationDispenseReportDetailRequest;
import com.openhis.health.application.report.medicationdispense.inpatient.request.SummarizeInpatientMedicationDispenseReportRequest;
import com.openhis.health.application.report.medicationdispense.inpatient.response.ExportInpatientMedicationDispenseReportResponse;
import com.openhis.health.application.report.medicationdispense.inpatient.response.LoadInpatientMedicationDispenseReportPageInitResponse;
import com.openhis.health.application.report.medicationdispense.inpatient.response.PageInpatientMedicationDispenseReportDetailResponse;
import com.openhis.health.application.report.medicationdispense.inpatient.response.SummarizeInpatientMedicationDispenseReportResponse;
import com.openhis.health.application.report.medicationdispense.inpatient.support.InpatientMedicationDispenseReportQuerySupport;
import com.openhis.health.application.shared.report.ReportPatientSettlementContextAssembler;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseReportSummaryRow;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseRecordPageQuery;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseReportSummaryQuery;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.MedicationDispenseReportConstants;
import com.openhis.health.domain.share.constants.InpatientMedicationDispenseReportConstants;
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
 * 住院发药报表应用服务（AS-RPT-004，IF-RPT-IMD-01～04）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：登录、日期窗、分页窗口。</li>
 *   <li>Pre：{@link InpatientMedicationDispenseReportQuerySupport} 两路预解析与装配。</li>
 *   <li>Asm：Mapper + Excel 导出。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/inpatientMedicationDispenseReport/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpatientMedicationDispenseReportAppService {

    /** 读路径登录门禁（报表只读亦须会话）。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 明细分页 pageNo/pageSize 正数门禁。 */
    private final PageWindowGate pageWindowGate;

    /** 启用药房下拉（报表不按管辖库房收窄）。 */
    private final LocationQueryService locationQueryService;

    /** medication_category_code 术语下拉。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 发药人（药师岗）下拉主档。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 患者发放 ∪ 科室领用两路预解析与装配（B.6.4）。 */
    private final InpatientMedicationDispenseReportQuerySupport inpatientMedicationDispenseReportQuerySupport;

    /** Request/Response 与 Option/导出映射。 */
    private final InpatientMedicationDispenseReportMapper inpatientMedicationDispenseReportMapper;

    /** 费用性质 / medType 上下文装配。 */
    private final ReportPatientSettlementContextAssembler reportPatientSettlementContextAssembler;

    /**
     * IF-RPT-IMD-01：住院发药报表页初始化。
     *
     * @param request 占位请求
     * @return 筛选项与默认日期
     * @throws BusinessException EX-RPT-IMD-101
     */
    public LoadInpatientMedicationDispenseReportPageInitResponse loadInpatientMedicationDispenseReportPageInit(
        LoadInpatientMedicationDispenseReportPageInitRequest request) {
        // Gate：须已登录方可加载报表元数据
        this.currentOperatorResolver.requireOperatorId(
            InpatientMedicationDispenseReportConstants.EX_IMD_101,
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
            // Asm：装配初始化响应
            return this.inpatientMedicationDispenseReportMapper.toLoadPageInitResponse(
                pharmacies,
                categoryOptions,
                practitionerOptions,
                this.inpatientMedicationDispenseReportMapper.buildRecordTypeOptions(),
                this.inpatientMedicationDispenseReportMapper.buildSummaryDimensionOptions(),
                this.inpatientMedicationDispenseReportMapper.buildSourceOptions(),
                feeNatureOptions,
                defaultFrom,
                defaultTo);
        } catch (RuntimeException ex) {
            log.warn("住院发药报表页初始化失败", ex);
            throw new BusinessException(
                InpatientMedicationDispenseReportConstants.EX_IMD_101,
                "页面初始化失败，请稍后重试");
        }
    }

    /**
     * IF-RPT-IMD-02：住院发药报表净汇总。
     *
     * @param request 筛选条件
     * @return 汇总行与页脚合计
     * @throws BusinessException EX-RPT-IMD-102 / 103
     */
    public SummarizeInpatientMedicationDispenseReportResponse summarizeInpatientMedicationDispenseReport(
        SummarizeInpatientMedicationDispenseReportRequest request) {
        // Gate：登录与日期窗
        this.currentOperatorResolver.requireOperatorId(
            InpatientMedicationDispenseReportConstants.EX_IMD_103,
            "查询失败，请稍后重试");
        this.validateDispenseDateWindow(request.getDispenseDateFrom(), request.getDispenseDateTo());
        try {
            String sourceCode =
                this.inpatientMedicationDispenseReportQuerySupport.normalizeSourceCode(request.getSourceCode());
            String summaryDimension =
                this.inpatientMedicationDispenseReportQuerySupport.normalizeSummaryDimension(
                    request.getSummaryDimension());
            List<MedicationDispenseReportSummaryRow> patientRows = List.of();
            List<DeptIssueReportSummaryRow> deptRows = List.of();
            MedicationDispenseRecordPageQuery recordQuery = null;
            DeptIssueReportPageQuery deptQuery = null;
            Map<Long, String> categoryCodeByMedicationId = null;
            // Pre：科室维仅领用侧有组织主键；类型维仅患者侧有 category；按来源+维度决定查哪一路
            boolean queryPatient = this.inpatientMedicationDispenseReportQuerySupport.includePatientDispense(sourceCode)
                && !MedicationDispenseReportConstants.SUMMARY_DIMENSION_DEPARTMENT.equals(summaryDimension);
            boolean queryDept = this.inpatientMedicationDispenseReportQuerySupport.includeDeptIssue(sourceCode)
                && !MedicationDispenseReportConstants.SUMMARY_DIMENSION_CATEGORY.equals(summaryDimension);
            // 患者侧：发放净数量分桶（金额由明细 enrich 后汇总）
            if (queryPatient) {
                recordQuery =
                    this.inpatientMedicationDispenseReportQuerySupport.buildPatientRecordQuery(
                        request.getDispenseDateFrom(),
                        request.getDispenseDateTo(),
                        request.getRecordTypeCode(),
                        request.getPharmacyLocationId(),
                        request.getDispenserPractitionerId(),
                        request.getMedicationCategoryCodes(),
                        request.getDrugKeyword(),
                        request.getPatientKeyword(),
                        request.getFeeNatureContractNo(),
                        1,
                        PharmacyDispenseRecordExportConstants.EXPORT_MAX_ROWS);
                MedicationDispenseReportSummaryQuery summaryQuery =
                    this.inpatientMedicationDispenseReportQuerySupport.buildPatientSummaryQuery(
                        recordQuery, summaryDimension);
                patientRows = this.inpatientMedicationDispenseReportQuerySupport.summarizePatient(summaryQuery);
                if (MedicationDispenseReportConstants.SUMMARY_DIMENSION_CATEGORY.equals(summaryDimension)) {
                    categoryCodeByMedicationId = summaryQuery.getCategoryCodeByMedicationId();
                }
            }
            // 领用侧：已同意 DEPT_ISSUE/RETURN 药品行净数量（与患者侧同维键可合并）
            if (queryDept) {
                deptQuery =
                    this.inpatientMedicationDispenseReportQuerySupport.buildDeptIssueQuery(
                        request.getDispenseDateFrom(),
                        request.getDispenseDateTo(),
                        request.getPharmacyLocationId(),
                        request.getMedicationCategoryCodes(),
                        request.getDrugKeyword(),
                        summaryDimension,
                        1,
                        PharmacyDispenseRecordExportConstants.EXPORT_MAX_ROWS);
                deptRows = this.inpatientMedicationDispenseReportQuerySupport.summarizeDept(deptQuery);
            }
            // Asm：同 dimensionKey 净数量相加并回填维度展示名
            List<InpatientMedicationDispenseSummaryRowDto> rows =
                this.inpatientMedicationDispenseReportQuerySupport.mergeSummaryRows(
                    patientRows, deptRows, summaryDimension);
            this.inpatientMedicationDispenseReportQuerySupport.enrichSummaryPurchaseSaleAmounts(
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
            for (InpatientMedicationDispenseSummaryRowDto row : rows) {
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
            return this.inpatientMedicationDispenseReportMapper.toSummarizeResponse(
                rows, totalQuantity, purchaseTotalAmount, saleTotalAmount);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("住院发药报表汇总失败", ex);
            throw new BusinessException(
                InpatientMedicationDispenseReportConstants.EX_IMD_103,
                "查询失败，请稍后重试");
        }
    }

    /**
     * IF-RPT-IMD-03：住院发药报表明细分页（患者发退药 ∪ 科室领用）。
     *
     * @param request 筛选与分页
     * @return 平铺明细分页
     * @throws BusinessException EX-RPT-IMD-102 / 103
     */
    public PageInpatientMedicationDispenseReportDetailResponse pageInpatientMedicationDispenseReportDetail(
        PageInpatientMedicationDispenseReportDetailRequest request) {
        // Gate：登录、分页、日期窗
        this.currentOperatorResolver.requireOperatorId(
            InpatientMedicationDispenseReportConstants.EX_IMD_103,
            "查询失败，请稍后重试");
        this.pageWindowGate.requirePositivePageWindow(
            request,
            InpatientMedicationDispenseReportConstants.EX_IMD_103,
            "分页参数无效");
        this.validateDispenseDateWindow(request.getDispenseDateFrom(), request.getDispenseDateTo());
        try {
            String sourceCode =
                this.inpatientMedicationDispenseReportQuerySupport.normalizeSourceCode(request.getSourceCode());
            boolean queryPatient =
                this.inpatientMedicationDispenseReportQuerySupport.includePatientDispense(sourceCode);
            boolean queryDept = this.inpatientMedicationDispenseReportQuerySupport.includeDeptIssue(sourceCode);
            int pageNo = request.getPageNo();
            int pageSize = request.getPageSize();
            // 仅患者侧：领域分页即可，避免无意义的两路 merge
            if (queryPatient && !queryDept) {
                MedicationDispenseRecordPageQuery patientQuery =
                    this.inpatientMedicationDispenseReportQuerySupport.buildPatientRecordQuery(
                        request.getDispenseDateFrom(),
                        request.getDispenseDateTo(),
                        request.getRecordTypeCode(),
                        request.getPharmacyLocationId(),
                        request.getDispenserPractitionerId(),
                        request.getMedicationCategoryCodes(),
                        request.getDrugKeyword(),
                    request.getPatientKeyword(),
                    request.getFeeNatureContractNo(),
                    pageNo,
                        pageSize);
                // Pre：发放事实分页 + ChargeItem 价装载
                Page<MedicationDispenseSlipQueryModel> slipPage =
                    this.inpatientMedicationDispenseReportQuerySupport.pagePatientSlips(patientQuery);
                List<InpatientMedicationDispenseDetailRowDto> rows =
                    this.inpatientMedicationDispenseReportQuerySupport.assemblePatientDetailRows(
                        slipPage.getContent());
                // Asm：单路 total 直接用领域分页元数据
                return this.inpatientMedicationDispenseReportMapper.toPageDetailResponse(
                    rows, slipPage.getTotalElements(), pageNo, pageSize);
            }
            // 仅科室领用侧：领域分页 + 落库价符号处理
            if (queryDept && !queryPatient) {
                DeptIssueReportPageQuery deptQuery =
                    this.inpatientMedicationDispenseReportQuerySupport.buildDeptIssueQuery(
                        request.getDispenseDateFrom(),
                        request.getDispenseDateTo(),
                        request.getPharmacyLocationId(),
                        request.getMedicationCategoryCodes(),
                        request.getDrugKeyword(),
                        null,
                        pageNo,
                        pageSize);
                Page<DeptIssueReportLineQueryModel> deptPage =
                    this.inpatientMedicationDispenseReportQuerySupport.pageDeptLines(deptQuery);
                List<InpatientMedicationDispenseDetailRowDto> rows =
                    this.inpatientMedicationDispenseReportQuerySupport.assembleDeptDetailRows(deptPage.getContent());
                return this.inpatientMedicationDispenseReportMapper.toPageDetailResponse(
                    rows, deptPage.getTotalElements(), pageNo, pageSize);
            }
            // 两路并集：各取前 pageNo*pageSize 条，内存按业务时间倒序后切片（BR-RPT-IMD-009）
            int fetchSize = pageNo * pageSize;
            // 防护：页码异常导致 fetchSize 小于 pageSize 时至少取一页
            if (fetchSize < pageSize) {
                fetchSize = pageSize;
            }
            long patientTotal = 0L;
            long deptTotal = 0L;
            List<InpatientMedicationDispenseDetailRowDto> patientRows = List.of();
            List<InpatientMedicationDispenseDetailRowDto> deptRows = List.of();
            // 患者侧前缀页（含 total 供并集基数）
            if (queryPatient) {
                MedicationDispenseRecordPageQuery patientQuery =
                    this.inpatientMedicationDispenseReportQuerySupport.buildPatientRecordQuery(
                        request.getDispenseDateFrom(),
                        request.getDispenseDateTo(),
                        request.getRecordTypeCode(),
                        request.getPharmacyLocationId(),
                        request.getDispenserPractitionerId(),
                        request.getMedicationCategoryCodes(),
                        request.getDrugKeyword(),
                        request.getPatientKeyword(),
                        request.getFeeNatureContractNo(),
                        1,
                        fetchSize);
                Page<MedicationDispenseSlipQueryModel> slipPage =
                    this.inpatientMedicationDispenseReportQuerySupport.pagePatientSlips(patientQuery);
                patientTotal = slipPage.getTotalElements();
                patientRows = this.inpatientMedicationDispenseReportQuerySupport.assemblePatientDetailRows(
                    slipPage.getContent());
            }
            // 领用侧前缀页
            if (queryDept) {
                DeptIssueReportPageQuery deptQuery =
                    this.inpatientMedicationDispenseReportQuerySupport.buildDeptIssueQuery(
                        request.getDispenseDateFrom(),
                        request.getDispenseDateTo(),
                        request.getPharmacyLocationId(),
                        request.getMedicationCategoryCodes(),
                        request.getDrugKeyword(),
                        null,
                        1,
                        fetchSize);
                Page<DeptIssueReportLineQueryModel> deptPage =
                    this.inpatientMedicationDispenseReportQuerySupport.pageDeptLines(deptQuery);
                deptTotal = deptPage.getTotalElements();
                deptRows = this.inpatientMedicationDispenseReportQuerySupport.assembleDeptDetailRows(
                    deptPage.getContent());
            }
            List<InpatientMedicationDispenseDetailRowDto> pageRows =
                this.inpatientMedicationDispenseReportQuerySupport.mergeAndSliceDetailRows(
                    patientRows, deptRows, pageNo, pageSize);
            // Asm：总条数取两路并集基数之和（来源互斥，无重复计）
            return this.inpatientMedicationDispenseReportMapper.toPageDetailResponse(
                pageRows, patientTotal + deptTotal, pageNo, pageSize);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("住院发药报表明细分页失败", ex);
            throw new BusinessException(
                InpatientMedicationDispenseReportConstants.EX_IMD_103,
                "查询失败，请稍后重试");
        }
    }

    /**
     * IF-RPT-IMD-04：导出当前 Tab（汇总或明细）。
     *
     * @param request 筛选 + presentationMode
     * @return xlsx 字节流
     * @throws BusinessException EX-RPT-IMD-102 / 104
     */
    public ExportInpatientMedicationDispenseReportResponse exportInpatientMedicationDispenseReport(
        ExportInpatientMedicationDispenseReportRequest request) {
        // Gate：登录与日期窗
        this.currentOperatorResolver.requireOperatorId(
            InpatientMedicationDispenseReportConstants.EX_IMD_104,
            "导出失败，请稍后重试");
        this.validateDispenseDateWindow(request.getDispenseDateFrom(), request.getDispenseDateTo());
        try {
            String presentationMode = StringUtils.trimToNull(request.getPresentationMode());
            // 未传呈现模式时默认明细 Tab，与页面默认「当前 Tab」导出一致
            if (presentationMode == null) {
                presentationMode = MedicationDispenseReportConstants.PRESENTATION_MODE_DETAIL;
            }
            List<InpatientMedicationDispenseExportRowDto> exportRows = new ArrayList<>();
            boolean truncated = false;
            // Pre：按当前 Tab 走汇总全量或明细上限列表（BR-RPT-IMD-008）
            if (MedicationDispenseReportConstants.PRESENTATION_MODE_SUMMARY.equals(presentationMode)) {
                SummarizeInpatientMedicationDispenseReportRequest summarizeRequest =
                    new SummarizeInpatientMedicationDispenseReportRequest();
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
                SummarizeInpatientMedicationDispenseReportResponse summaryResponse =
                    this.summarizeInpatientMedicationDispenseReport(summarizeRequest);
                // 汇总导出行：维度名 + 净量净额
                for (InpatientMedicationDispenseSummaryRowDto row : summaryResponse.getRows()) {
                    exportRows.add(this.inpatientMedicationDispenseReportMapper.toExportRowFromSummary(row));
                }
            } else {
                // 明细导出：两路合并后截断至 EXPORT_MAX_ROWS
                List<InpatientMedicationDispenseDetailRowDto> detailRows =
                    this.loadDetailRowsForExport(request);
                truncated = detailRows.size() >= PharmacyDispenseRecordExportConstants.EXPORT_MAX_ROWS;
                for (InpatientMedicationDispenseDetailRowDto row : detailRows) {
                    exportRows.add(this.inpatientMedicationDispenseReportMapper.toExportRowFromDetail(row));
                }
            }
            // Asm：生成 xlsx
            byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                InpatientMedicationDispenseReportConstants.EXPORT_SHEET_NAME,
                exportRows,
                InpatientMedicationDispenseExportRowDto.class);
            return this.inpatientMedicationDispenseReportMapper.toExportResponse(
                body, exportRows.size(), truncated);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("住院发药报表导出失败", ex);
            throw new BusinessException(
                InpatientMedicationDispenseReportConstants.EX_IMD_104,
                "导出失败，请稍后重试");
        }
    }

    /**
     * 导出明细：两路各取上限后合并，再截断至 EXPORT_MAX_ROWS。
     *
     * @param request 导出请求
     * @return 明细行
     */
    private List<InpatientMedicationDispenseDetailRowDto> loadDetailRowsForExport(
        ExportInpatientMedicationDispenseReportRequest request) {
        String sourceCode =
            this.inpatientMedicationDispenseReportQuerySupport.normalizeSourceCode(request.getSourceCode());
        boolean queryPatient =
            this.inpatientMedicationDispenseReportQuerySupport.includePatientDispense(sourceCode);
        boolean queryDept = this.inpatientMedicationDispenseReportQuerySupport.includeDeptIssue(sourceCode);
        List<InpatientMedicationDispenseDetailRowDto> patientRows = List.of();
        List<InpatientMedicationDispenseDetailRowDto> deptRows = List.of();
        // 患者侧：list 上限与发退药记录导出同常量
        if (queryPatient) {
            MedicationDispenseRecordPageQuery patientQuery =
                this.inpatientMedicationDispenseReportQuerySupport.buildPatientRecordQuery(
                    request.getDispenseDateFrom(),
                    request.getDispenseDateTo(),
                    request.getRecordTypeCode(),
                    request.getPharmacyLocationId(),
                    request.getDispenserPractitionerId(),
                    request.getMedicationCategoryCodes(),
                    request.getDrugKeyword(),
                    request.getPatientKeyword(),
                    request.getFeeNatureContractNo(),
                    1,
                    PharmacyDispenseRecordExportConstants.EXPORT_MAX_ROWS);
            List<MedicationDispenseSlipQueryModel> slips =
                this.inpatientMedicationDispenseReportQuerySupport.listPatientSlips(patientQuery);
            patientRows = this.inpatientMedicationDispenseReportQuerySupport.assemblePatientDetailRows(slips);
        }
        // 领用侧：同上限 list，退领数量/金额已取负
        if (queryDept) {
            DeptIssueReportPageQuery deptQuery =
                this.inpatientMedicationDispenseReportQuerySupport.buildDeptIssueQuery(
                    request.getDispenseDateFrom(),
                    request.getDispenseDateTo(),
                    request.getPharmacyLocationId(),
                    request.getMedicationCategoryCodes(),
                    request.getDrugKeyword(),
                    null,
                    1,
                    PharmacyDispenseRecordExportConstants.EXPORT_MAX_ROWS);
            List<DeptIssueReportLineQueryModel> lines =
                this.inpatientMedicationDispenseReportQuerySupport.listDeptLines(deptQuery);
            deptRows = this.inpatientMedicationDispenseReportQuerySupport.assembleDeptDetailRows(lines);
        }
        return this.inpatientMedicationDispenseReportQuerySupport.mergeAndSliceDetailRows(
            patientRows,
            deptRows,
            1,
            PharmacyDispenseRecordExportConstants.EXPORT_MAX_ROWS);
    }

    /**
     * Gate：业务日闭区间合法且起止不颠倒。
     *
     * @param dispenseDateFrom 日起
     * @param dispenseDateTo   日止
     */
    private void validateDispenseDateWindow(String dispenseDateFrom, String dispenseDateTo) {
        // Gate：缺日起或日止直接拒绝（BR-RPT-IMD-001）
        if (StringUtils.isEmpty(dispenseDateFrom) || StringUtils.isEmpty(dispenseDateTo)) {
            throw new BusinessException(
                InpatientMedicationDispenseReportConstants.EX_IMD_102,
                "请选择有效时间范围");
        }
        LocalDate from = LocalDate.parse(dispenseDateFrom.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        LocalDate to = LocalDate.parse(dispenseDateTo.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        // Gate：结束日早于起始日时拒绝，避免空结果误导
        if (from.isAfter(to)) {
            throw new BusinessException(
                InpatientMedicationDispenseReportConstants.EX_IMD_102,
                "请选择有效时间范围");
        }
    }
}
