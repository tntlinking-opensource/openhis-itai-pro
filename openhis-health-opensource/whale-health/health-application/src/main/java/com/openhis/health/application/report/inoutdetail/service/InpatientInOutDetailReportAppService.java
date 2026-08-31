/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.inoutdetail.service;

import com.openhis.health.application.report.inoutdetail.dto.InpatientInOutDetailFooterDto;
import com.openhis.health.application.report.inoutdetail.dto.InpatientInOutDetailRowDto;
import com.openhis.health.application.report.inoutdetail.mapper.InpatientInOutDetailReportMapper;
import com.openhis.health.application.report.inoutdetail.request.ExportInpatientInOutDetailReportRequest;
import com.openhis.health.application.report.inoutdetail.request.ListInpatientInOutDetailWardOptionsRequest;
import com.openhis.health.application.report.inoutdetail.request.LoadInpatientInOutDetailReportPageInitRequest;
import com.openhis.health.application.report.inoutdetail.request.PageInpatientInOutDetailReportRequest;
import com.openhis.health.application.report.inoutdetail.response.ExportInpatientInOutDetailReportResponse;
import com.openhis.health.application.report.inoutdetail.response.ListInpatientInOutDetailWardOptionsResponse;
import com.openhis.health.application.report.inoutdetail.response.LoadInpatientInOutDetailReportPageInitResponse;
import com.openhis.health.application.report.inoutdetail.response.PageInpatientInOutDetailReportResponse;
import com.openhis.health.application.report.inoutdetail.support.InpatientInOutDetailReportEnricher;
import com.openhis.health.application.report.inoutdetail.support.InpatientInOutDetailReportQuerySupport;
import com.openhis.health.domain.administration.encounter.queries.InpatientInOutDetailReportPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientInOutDetailPersonTimesQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientInOutDetailReportLineQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientInOutDetailReportConstants;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.util.AnnotatedExcelExporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院患者入出明细报表只读编排（AS-RPT-010 IF-RPT-IOE-01/01a/02/03）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link CurrentOperatorResolver}：登录 Gate</li>
 *   <li>{@link InpatientInOutDetailReportQuerySupport}：日期/状态校验与入院病区 scope 预写</li>
 *   <li>{@link EncounterQueryService}：入出明细分页与人次计数</li>
 *   <li>{@link PaymentReconciliationQueryService}：已结净额与待重结</li>
 *   <li>{@link InpatientInOutDetailReportEnricher}：患者/位置/转科/费用空值 Asm</li>
 *   <li>{@link InpatientInOutDetailReportMapper}：Request/Response 映射</li>
 * </ul>
 * <p>接入：AppServiceControllerRegistrar →
 * {@code POST /api/app/inpatientInOutDetailReport/{methodName}}；不另写 RestController。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InpatientInOutDetailReportAppService {

    /** 登录 Gate。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 筛选校验与病区 scope。 */
    private final InpatientInOutDetailReportQuerySupport querySupport;

    /** 展示 enrich。 */
    private final InpatientInOutDetailReportEnricher enricher;

    /** Encounter 入出报表读侧。 */
    private final EncounterQueryService encounterQueryService;

    /** 出院结算净额 / 待重结。 */
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    /** 住院入院科室下拉与名称。 */
    private final OrganizationQueryService organizationQueryService;

    /** 科室→病区联动。 */
    private final LocationQueryService locationQueryService;

    /** Request/Response 映射。 */
    private final InpatientInOutDetailReportMapper mapper;

    /**
     * IF-RPT-IOE-01：页面初始化（默认近一个月窗、状态选项、入院科室；病区初始空）。
     *
     * @param request 空体占位
     * @return 初始化响应
     * @throws BusinessException {@link InpatientInOutDetailReportConstants#EX_RPT_IOE_101} 初始化失败
     */
    public LoadInpatientInOutDetailReportPageInitResponse loadInpatientInOutDetailReportPageInit(
        LoadInpatientInOutDetailReportPageInitRequest request) {
        // Gate：须已登录方可加载租户内主数据
        this.currentOperatorResolver.requireOperatorId(
            InpatientInOutDetailReportConstants.EX_RPT_IOE_101,
            "页面初始化失败，请稍后重试");
        try {
            LocalDate today = LocalDate.now();
            // Pre：默认业务窗 = 今日往前 N 个自然月～今日（BR-RPT-IOE-001）
            LocalDate fromDay = today.minusMonths(
                InpatientInOutDetailReportConstants.DEFAULT_LOOKBACK_MONTHS);
            LocalDateTime defaultFrom = fromDay.atStartOfDay();
            LocalDateTime defaultTo = today.atTime(LocalTime.of(23, 59, 59));
            // Pre：入院科室下拉 + 入出状态选项；病区初始空（BR-RPT-IOE-005a）
            List<OptionDto> organizationOptions = this.buildOrganizationOptions();
            List<OptionDto> movementStatusOptions = this.buildMovementStatusOptions();
            // Asm：默认近一个月闭区间 + 下拉；wardOptions 强制空列表
            return this.mapper.toPageInitResponse(
                defaultFrom,
                defaultTo,
                InpatientInOutDetailReportConstants.DEFAULT_PAGE_SIZE,
                InpatientInOutDetailReportConstants.MOVEMENT_STATUS_ALL,
                movementStatusOptions,
                organizationOptions,
                Collections.emptyList());
        } catch (BusinessException businessException) {
            // Gate：业务异常原样抛出
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：主数据加载异常映射 101
            log.error("住院患者入出明细报表页初始化失败", runtimeException);
            throw new BusinessException(
                InpatientInOutDetailReportConstants.EX_RPT_IOE_101,
                "页面初始化失败，请稍后重试",
                runtimeException);
        }
    }

    /**
     * IF-RPT-IOE-01a：按入院科室加载启用病区选项（科室→病区联动）。
     *
     * @param request 含可选 organizationId
     * @return 病区下拉
     * @throws BusinessException {@link InpatientInOutDetailReportConstants#EX_RPT_IOE_101} 加载失败
     */
    public ListInpatientInOutDetailWardOptionsResponse listInpatientInOutDetailWardOptions(
        ListInpatientInOutDetailWardOptionsRequest request) {
        // Gate：须已登录
        this.currentOperatorResolver.requireOperatorId(
            InpatientInOutDetailReportConstants.EX_RPT_IOE_101,
            "病区选项加载失败，请稍后重试");
        try {
            Long organizationId = null;
            // Pre：请求可空；科室空则直接空病区
            if (request != null) {
                organizationId = request.getOrganizationId();
            }
            if (organizationId == null) {
                // Asm：未选科室 → 空列表（前端禁用病区）
                return this.mapper.toWardOptionsResponse(Collections.emptyList());
            }
            // Pre：该科下启用病区（与住院登记挂靠同口径）
            List<Location> wards = this.locationQueryService.listWardsByOrganization(organizationId);
            List<OptionDto> wardOptions = this.buildWardOptions(wards);
            // Asm：病区 OptionDto 列表
            return this.mapper.toWardOptionsResponse(wardOptions);
        } catch (BusinessException businessException) {
            // Gate：业务异常原样抛出
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：病区主数据异常映射 101
            log.error("住院患者入出明细报表病区选项加载失败", runtimeException);
            throw new BusinessException(
                InpatientInOutDetailReportConstants.EX_RPT_IOE_101,
                "病区选项加载失败，请稍后重试",
                runtimeException);
        }
    }

    /**
     * IF-RPT-IOE-02：入出明细分页 + 人次页脚 + 转科列开关。
     *
     * @param request 筛选与分页
     * @return 分页响应
     * @throws BusinessException {@link InpatientInOutDetailReportConstants#EX_RPT_IOE_102} 参数非法；
     *         {@link InpatientInOutDetailReportConstants#EX_RPT_IOE_103} 查询失败
     */
    public PageInpatientInOutDetailReportResponse pageInpatientInOutDetailReport(
        PageInpatientInOutDetailReportRequest request) {
        // Gate：须已登录
        this.currentOperatorResolver.requireOperatorId(
            InpatientInOutDetailReportConstants.EX_RPT_IOE_101,
            "查询失败，请稍后重试");
        // Gate：日期/状态/分页校验
        this.querySupport.assertDateAndStatus(
            request.getDateFrom(), request.getDateTo(), request.getMovementStatus());
        this.querySupport.assertPageWindow(request.getPageNo(), request.getPageSize());
        try {
            // Pre：Request → Query + 入院病区 scope
            InpatientInOutDetailReportPageQuery query = this.mapper.toPageQuery(request);
            this.querySupport.prepareQueryScopes(
                query,
                request.getDateFrom(),
                request.getDateTo(),
                request.getMovementStatus(),
                request.getOrganizationId(),
                request.getWardLocationId());
            // Pre：领域明细分页
            Page<InpatientInOutDetailReportLineQueryModel> page =
                this.encounterQueryService.pageInpatientInOutDetailReport(query);
            // Pre：人次页脚（同科室/病区/日期窗，忽略 movementStatus）
            InpatientInOutDetailPersonTimesQueryModel personTimes =
                this.encounterQueryService.countInpatientInOutDetailPersonTimes(query);
            InpatientInOutDetailFooterDto footer = this.mapper.toFooterDto(personTimes);
            List<InpatientInOutDetailRowDto> rows = this.enrichPageRows(page.getContent());
            boolean showTransferColumn = this.enricher.resolveShowTransferColumn(rows);
            // Asm：分页壳 + 页脚 + 转科列
            return this.mapper.toPageResponse(rows, page, footer, showTransferColumn);
        } catch (BusinessException businessException) {
            // Gate：业务异常原样抛出
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：查询运行时失败映射 103
            log.error("住院患者入出明细报表明细分页失败", runtimeException);
            throw new BusinessException(
                InpatientInOutDetailReportConstants.EX_RPT_IOE_103,
                "查询失败，请稍后重试",
                runtimeException);
        }
    }

    /**
     * IF-RPT-IOE-03：同筛选导出入出明细 Excel。
     *
     * @param request 筛选（无分页）
     * @return xlsx 响应
     * @throws BusinessException {@link InpatientInOutDetailReportConstants#EX_RPT_IOE_102} 参数非法；
     *         {@link InpatientInOutDetailReportConstants#EX_RPT_IOE_104} 导出失败
     */
    public ExportInpatientInOutDetailReportResponse exportInpatientInOutDetailReport(
        ExportInpatientInOutDetailReportRequest request) {
        // Gate：须已登录（未登录映射 EX-101，与列表同码）
        Long operatorId = this.currentOperatorResolver.requireOperatorId(
            InpatientInOutDetailReportConstants.EX_RPT_IOE_101,
            "导出失败，请稍后重试");
        // Gate：日期/状态校验（失败 → EX-102）
        this.querySupport.assertDateAndStatus(
            request.getDateFrom(), request.getDateTo(), request.getMovementStatus());
        try {
            // Pre：Request → 领域 Query + 入院病区 scope
            InpatientInOutDetailReportPageQuery query = this.mapper.toExportQuery(request);
            this.querySupport.prepareQueryScopes(
                query,
                request.getDateFrom(),
                request.getDateTo(),
                request.getMovementStatus(),
                request.getOrganizationId(),
                request.getWardLocationId());
            List<InpatientInOutDetailRowDto> exportRows = new ArrayList<>();
            boolean truncated = false;
            int pageNo = 1;
            // Pre：分页循环拉全量直至空页或触顶
            while (true) {
                query.setPageNo(pageNo);
                query.setPageSize(InpatientInOutDetailReportConstants.EXPORT_PAGE_SIZE);
                Page<InpatientInOutDetailReportLineQueryModel> page =
                    this.encounterQueryService.pageInpatientInOutDetailReport(query);
                List<InpatientInOutDetailReportLineQueryModel> content = page.getContent();
                // 空页：结束循环
                if (content == null || content.isEmpty()) {
                    break;
                }
                List<InpatientInOutDetailRowDto> batch = this.enrichPageRows(content);
                for (InpatientInOutDetailRowDto row : batch) {
                    // 触顶导出行上限：截断并结束（不抛 104）
                    if (exportRows.size() >= InpatientInOutDetailReportConstants.EXPORT_MAX_ROWS) {
                        truncated = true;
                        break;
                    }
                    exportRows.add(row);
                }
                // 已截断：不再翻页
                if (truncated) {
                    break;
                }
                // 本批未满页：已无后续
                if (content.size() < InpatientInOutDetailReportConstants.EXPORT_PAGE_SIZE) {
                    break;
                }
                pageNo = pageNo + 1;
            }
            // Asm：按导出行集定转科列元数据 + 写 xlsx（固定含「转往」列）
            boolean showTransferColumn = this.enricher.resolveShowTransferColumn(exportRows);
            byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                InpatientInOutDetailReportConstants.EXPORT_SHEET_NAME,
                exportRows,
                InpatientInOutDetailRowDto.class,
                request.getExportColumns());
            String fileName = InpatientInOutDetailReportConstants.EXPORT_FILE_PREFIX
                + request.getDateFrom().format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER)
                + GeneralSharedConstants.DASH
                + request.getDateTo().format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER)
                + ".xlsx";
            log.info(
                "住院患者入出明细导出成功 operatorId={} rowCount={} truncated={} showTransferColumn={}",
                operatorId,
                exportRows.size(),
                truncated,
                showTransferColumn);
            // Asm：导出响应（含 truncated / showTransferColumn）
            return this.mapper.toExportResponse(
                fileName,
                body,
                exportRows.size(),
                truncated,
                InpatientInOutDetailReportConstants.EXPORT_CONTENT_TYPE,
                showTransferColumn);
        } catch (BusinessException businessException) {
            // Gate：业务异常原样抛出
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：导出运行时失败映射 104
            log.error("住院患者入出明细报表导出失败", runtimeException);
            throw new BusinessException(
                InpatientInOutDetailReportConstants.EX_RPT_IOE_104,
                "导出失败，请稍后重试",
                runtimeException);
        }
    }

    /**
     * 对本页领域行做支付净额/待重结装载后 enrich。
     *
     * @param lines 领域行
     * @return 展示行
     */
    private List<InpatientInOutDetailRowDto> enrichPageRows(
        List<InpatientInOutDetailReportLineQueryModel> lines) {
        // 空页：无支付批量、直接空列表
        if (lines == null || lines.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> encounterIds = new ArrayList<>();
        for (InpatientInOutDetailReportLineQueryModel line : lines) {
            // 收集本页就诊主键供支付批量读
            if (line != null && line.getEncounterId() != null) {
                encounterIds.add(line.getEncounterId());
            }
        }
        // Pre：已结净额 + 待重结集合（B.6.4 经 Payment QS）
        Map<Long, BigDecimal> settledNet =
            this.paymentReconciliationQueryService.mapInpatientClinicSettledNetAmountByEncounterIds(encounterIds);
        Set<Long> reversedPending =
            this.paymentReconciliationQueryService.filterReversedPendingResettleEncounterIds(encounterIds);
        // 待重结空入参按空集，避免 Enricher contains NPE
        if (reversedPending == null) {
            reversedPending = new HashSet<>();
        }
        // Asm：患者/位置/转科/费用空值
        return this.enricher.enrichRows(lines, settledNet, reversedPending);
    }

    /**
     * 住院入院科室 → OptionDto 列表。
     *
     * @return 科室下拉
     */
    private List<OptionDto> buildOrganizationOptions() {
        List<Organization> organizations =
            this.organizationQueryService.listOrganizationsForInpatientAdmission();
        List<OptionDto> options = new ArrayList<>();
        long sort = 1L;
        for (Organization organization : organizations) {
            // 跳过无主键或无名机构
            if (organization == null || organization.getId() == null) {
                continue;
            }
            OptionDto option = new OptionDto(
                sort,
                String.valueOf(organization.getId()),
                organization.getName());
            options.add(option);
            sort = sort + 1L;
        }
        return options;
    }

    /**
     * 病区实体 → OptionDto 列表。
     *
     * @param wards 病区列表
     * @return 病区下拉
     */
    private List<OptionDto> buildWardOptions(List<Location> wards) {
        List<OptionDto> options = new ArrayList<>();
        if (wards == null || wards.isEmpty()) {
            return options;
        }
        long sort = 1L;
        for (Location ward : wards) {
            // 跳过无效病区行
            if (ward == null || ward.getId() == null) {
                continue;
            }
            OptionDto option = new OptionDto(sort, String.valueOf(ward.getId()), ward.getName());
            options.add(option);
            sort = sort + 1L;
        }
        return options;
    }

    /**
     * 入出状态下拉（全部/入院/出院）。
     *
     * @return 状态选项
     */
    private List<OptionDto> buildMovementStatusOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(new OptionDto(
            1L,
            InpatientInOutDetailReportConstants.MOVEMENT_STATUS_ALL,
            "全部"));
        options.add(new OptionDto(
            2L,
            InpatientInOutDetailReportConstants.MOVEMENT_STATUS_ADMISSION,
            "入院"));
        options.add(new OptionDto(
            3L,
            InpatientInOutDetailReportConstants.MOVEMENT_STATUS_DISCHARGE,
            "出院"));
        return options;
    }
}
