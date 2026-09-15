/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.prescriptionspotcheck.support;

import com.openhis.health.application.report.prescriptionspotcheck.dto.PrescriptionSpotCheckHerbalRowDto;
import com.openhis.health.application.report.prescriptionspotcheck.dto.PrescriptionSpotCheckWesternRowDto;
import com.openhis.health.application.shared.report.ReportDateTimeWindowSupport;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.query.OutpatientSignedPrescriptionDrugLinePageQuery;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.PrescriptionSpotCheckReportConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 门诊处方抽查报表筛选预解析与分页/导出收集（AS-RPT-014）。
 *
 * <p>职责：校验类型与开方日窗；AMB 就诊预解析；窗内已签发 medReq → COMPLETED 发放收窄；
 * 组装 {@link OutpatientSignedPrescriptionDrugLinePageQuery}；导出时<strong>一次</strong>预解析后循环翻页。</p>
 */
@Component
@RequiredArgsConstructor
public class PrescriptionSpotCheckReportQuerySupport {

    /** 导出翻页页大小（大于列表默认，降低导出轮次与预解析外开销）。 */
    private static final int EXPORT_PAGE_SIZE = 500;

    /** 就诊读侧：全院 AMB 窗。 */
    private final EncounterQueryService encounterQueryService;

    /** 药品申请读侧：开方窗 id 与已签发分页。 */
    private final MedicationRequestQueryService medicationRequestQueryService;

    /** 发放读侧：COMPLETED medReq 收窄。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** 行装配（医院/患者/诊断/目录/费用等）。 */
    private final PrescriptionSpotCheckReportRowAssembler rowAssembler;

    /**
     * 已发门诊范围预解析结果（AMB 就诊 + COMPLETED medReq）；导出多页复用，禁止每页重算。
     *
     * @param ambEncounterIds    AMB 就诊主键；空表示无命中
     * @param completedMedReqIds COMPLETED 药品申请主键；空表示无命中
     */
    public record CompletedScope(
        List<Long> ambEncounterIds,
        List<Long> completedMedReqIds) {
    }

    /**
     * 西药分页结果包。
     *
     * @param page Spring 分页壳（明细行条数口径）
     * @param rows 西药展示行
     */
    public record WesternPageBundle(
        Page<MedicationRequestListQueryModel> page,
        List<PrescriptionSpotCheckWesternRowDto> rows) {
    }

    /**
     * 饮片分页结果包。
     *
     * @param page Spring 分页壳（明细行条数口径）
     * @param rows 饮片展示行
     */
    public record HerbalPageBundle(
        Page<MedicationRequestListQueryModel> page,
        List<PrescriptionSpotCheckHerbalRowDto> rows) {
    }

    /**
     * 西药导出收集结果。
     *
     * @param rows      导出行
     * @param truncated 是否触顶截断
     */
    public record WesternExportBundle(
        List<PrescriptionSpotCheckWesternRowDto> rows,
        boolean truncated) {
    }

    /**
     * 饮片导出收集结果。
     *
     * @param rows      导出行
     * @param truncated 是否触顶截断
     */
    public record HerbalExportBundle(
        List<PrescriptionSpotCheckHerbalRowDto> rows,
        boolean truncated) {
    }

    /**
     * 组装报表类型下拉（西药及中成药 / 中药饮片）。
     *
     * @return 非空选项列表
     */
    public List<OptionDto> buildReportTypeOptions() {
        List<OptionDto> options = new ArrayList<>(2);
        options.add(this.toOption(
            PrescriptionSpotCheckReportConstants.REPORT_TYPE_WESTERN_PATENT,
            PrescriptionSpotCheckReportConstants.REPORT_TYPE_WESTERN_PATENT_LABEL));
        options.add(this.toOption(
            PrescriptionSpotCheckReportConstants.REPORT_TYPE_HERBAL,
            PrescriptionSpotCheckReportConstants.REPORT_TYPE_HERBAL_LABEL));
        return options;
    }

    /**
     * 校验并规范化报表类型（仅允许 WESTERN_PATENT / HERBAL）。
     *
     * @param reportTypeRaw 请求类型
     * @param exInvalid     非法异常码
     * @return 规范化类型码
     */
    public String requireReportType(String reportTypeRaw, String exInvalid) {
        String reportType = StringUtils.trimToNull(reportTypeRaw);
        // 类型必选：空则拒绝，禁止「全部」混列
        if (reportType == null) {
            throw new BusinessException(exInvalid, "请选择报表类型与有效开方时间");
        }
        // 西药及中成药
        if (PrescriptionSpotCheckReportConstants.REPORT_TYPE_WESTERN_PATENT.equals(reportType)) {
            return PrescriptionSpotCheckReportConstants.REPORT_TYPE_WESTERN_PATENT;
        }
        // 中药饮片
        if (PrescriptionSpotCheckReportConstants.REPORT_TYPE_HERBAL.equals(reportType)) {
            return PrescriptionSpotCheckReportConstants.REPORT_TYPE_HERBAL;
        }
        throw new BusinessException(exInvalid, "请选择报表类型与有效开方时间");
    }

    /**
     * 解析必填开方日窗为闭区间时刻（日起 00:00:00～日止 23:59:59）。
     *
     * @param openedDateFrom 日起 yyyy-MM-dd
     * @param openedDateTo   日止 yyyy-MM-dd
     * @param exInvalid      非法异常码
     * @return {@code [0]=窗起 inclusive，[1]=窗止 inclusive}
     */
    public LocalDateTime[] parseRequiredOpenedDateWindow(
        String openedDateFrom,
        String openedDateTo,
        String exInvalid) {
        String fromText = StringUtils.trimToNull(openedDateFrom);
        String toText = StringUtils.trimToNull(openedDateTo);
        // 开方日起止必填
        if (fromText == null || toText == null) {
            throw new BusinessException(exInvalid, "请选择报表类型与有效开方时间");
        }
        LocalDateTime from;
        LocalDateTime to;
        try {
            from = ReportDateTimeWindowSupport.parseInclusiveStart(fromText);
            to = ReportDateTimeWindowSupport.parseInclusiveEnd(toText);
        } catch (DateTimeParseException parseException) {
            // 日期格式非法：映射为筛选非法，不暴露解析细节
            throw new BusinessException(exInvalid, "请选择报表类型与有效开方时间", parseException);
        }
        // 起不得晚于止
        if (from.isAfter(to)) {
            throw new BusinessException(exInvalid, "请选择报表类型与有效开方时间");
        }
        return new LocalDateTime[] {from, to};
    }

    /**
     * 校验分页窗口（1-based pageNo；pageSize 落默认与上限）。
     *
     * @param pageNo    页码
     * @param pageSize  页大小
     * @param exInvalid 非法异常码
     * @return {@code [0]=pageNo，[1]=boundedPageSize}
     */
    public int[] resolvePageWindow(Integer pageNo, Integer pageSize, String exInvalid) {
        // 页码须 ≥1
        if (pageNo == null || pageNo < 1) {
            throw new BusinessException(exInvalid, "请选择报表类型与有效开方时间");
        }
        int size;
        // 未传页大小：回落默认
        if (pageSize == null || pageSize < 1) {
            size = PrescriptionSpotCheckReportConstants.DEFAULT_PAGE_SIZE;
        } else {
            size = pageSize;
        }
        // 超过单页上限则截断，避免无界扫描
        if (size > PrescriptionSpotCheckReportConstants.MAX_PAGE_SIZE) {
            size = PrescriptionSpotCheckReportConstants.MAX_PAGE_SIZE;
        }
        return new int[] {pageNo, size};
    }

    /**
     * 西药及中成药明细分页 + 行装配。
     *
     * @param openedStartInclusive 开方窗起
     * @param openedEndInclusive   开方窗止
     * @param pageNo               页码
     * @param pageSize             页大小
     * @return 分页包
     */
    public WesternPageBundle pageWestern(
        LocalDateTime openedStartInclusive,
        LocalDateTime openedEndInclusive,
        int pageNo,
        int pageSize) {
        CompletedScope scope = this.resolveCompletedScope(openedStartInclusive, openedEndInclusive);
        Page<MedicationRequestListQueryModel> page = this.pageDrugLinesWithScope(
            PrescriptionSpotCheckReportConstants.REPORT_TYPE_WESTERN_PATENT,
            openedStartInclusive,
            openedEndInclusive,
            scope,
            pageNo,
            pageSize);
        List<PrescriptionSpotCheckWesternRowDto> rows =
            this.rowAssembler.assembleWesternRows(page.getContent());
        return new WesternPageBundle(page, rows);
    }

    /**
     * 中药饮片明细分页 + 行装配。
     *
     * @param openedStartInclusive 开方窗起
     * @param openedEndInclusive   开方窗止
     * @param pageNo               页码
     * @param pageSize             页大小
     * @return 分页包
     */
    public HerbalPageBundle pageHerbal(
        LocalDateTime openedStartInclusive,
        LocalDateTime openedEndInclusive,
        int pageNo,
        int pageSize) {
        CompletedScope scope = this.resolveCompletedScope(openedStartInclusive, openedEndInclusive);
        Page<MedicationRequestListQueryModel> page = this.pageDrugLinesWithScope(
            PrescriptionSpotCheckReportConstants.REPORT_TYPE_HERBAL,
            openedStartInclusive,
            openedEndInclusive,
            scope,
            pageNo,
            pageSize);
        List<PrescriptionSpotCheckHerbalRowDto> rows =
            this.rowAssembler.assembleHerbalRows(page.getContent());
        return new HerbalPageBundle(page, rows);
    }

    /**
     * 循环分页收集西药导出行至上限（预解析仅一次）。
     *
     * @param openedStartInclusive 开方窗起
     * @param openedEndInclusive   开方窗止
     * @return 导出包
     */
    public WesternExportBundle collectWesternExportRows(
        LocalDateTime openedStartInclusive,
        LocalDateTime openedEndInclusive) {
        CompletedScope scope = this.resolveCompletedScope(openedStartInclusive, openedEndInclusive);
        List<PrescriptionSpotCheckWesternRowDto> exportRows = new ArrayList<>();
        boolean truncated = false;
        int pageNo = 1;
        // 循环直至空页或触顶 EXPORT_MAX_ROWS；scope 复用不重算 AMB/COMPLETED
        while (exportRows.size() < PrescriptionSpotCheckReportConstants.EXPORT_MAX_ROWS) {
            Page<MedicationRequestListQueryModel> page = this.pageDrugLinesWithScope(
                PrescriptionSpotCheckReportConstants.REPORT_TYPE_WESTERN_PATENT,
                openedStartInclusive,
                openedEndInclusive,
                scope,
                pageNo,
                EXPORT_PAGE_SIZE);
            List<PrescriptionSpotCheckWesternRowDto> pageRows =
                this.rowAssembler.assembleWesternRows(page.getContent());
            // 空页：结束
            if (pageRows == null || pageRows.isEmpty()) {
                break;
            }
            for (PrescriptionSpotCheckWesternRowDto row : pageRows) {
                // 触顶：标记截断并停止追加
                if (exportRows.size() >= PrescriptionSpotCheckReportConstants.EXPORT_MAX_ROWS) {
                    truncated = true;
                    break;
                }
                exportRows.add(row);
            }
            // 已截断：不再翻页
            if (truncated) {
                break;
            }
            // 本页不足一页：已到末页
            if (pageRows.size() < EXPORT_PAGE_SIZE) {
                break;
            }
            pageNo = pageNo + 1;
        }
        return new WesternExportBundle(exportRows, truncated);
    }

    /**
     * 循环分页收集饮片导出行至上限（预解析仅一次）。
     *
     * @param openedStartInclusive 开方窗起
     * @param openedEndInclusive   开方窗止
     * @return 导出包
     */
    public HerbalExportBundle collectHerbalExportRows(
        LocalDateTime openedStartInclusive,
        LocalDateTime openedEndInclusive) {
        CompletedScope scope = this.resolveCompletedScope(openedStartInclusive, openedEndInclusive);
        List<PrescriptionSpotCheckHerbalRowDto> exportRows = new ArrayList<>();
        boolean truncated = false;
        int pageNo = 1;
        // 循环直至空页或触顶；scope 复用
        while (exportRows.size() < PrescriptionSpotCheckReportConstants.EXPORT_MAX_ROWS) {
            Page<MedicationRequestListQueryModel> page = this.pageDrugLinesWithScope(
                PrescriptionSpotCheckReportConstants.REPORT_TYPE_HERBAL,
                openedStartInclusive,
                openedEndInclusive,
                scope,
                pageNo,
                EXPORT_PAGE_SIZE);
            List<PrescriptionSpotCheckHerbalRowDto> pageRows =
                this.rowAssembler.assembleHerbalRows(page.getContent());
            // 空页：结束
            if (pageRows == null || pageRows.isEmpty()) {
                break;
            }
            for (PrescriptionSpotCheckHerbalRowDto row : pageRows) {
                // 触顶：标记截断并停止追加
                if (exportRows.size() >= PrescriptionSpotCheckReportConstants.EXPORT_MAX_ROWS) {
                    truncated = true;
                    break;
                }
                exportRows.add(row);
            }
            // 已截断：不再翻页
            if (truncated) {
                break;
            }
            // 本页不足一页：已到末页
            if (pageRows.size() < EXPORT_PAGE_SIZE) {
                break;
            }
            pageNo = pageNo + 1;
        }
        return new HerbalExportBundle(exportRows, truncated);
    }

    /**
     * 解析当月默认开方日起止（yyyy-MM-dd）。
     *
     * @param today 参考日
     * @return {@code [0]=月初，[1]=月末}
     */
    public String[] resolveDefaultOpenedMonthBounds(LocalDate today) {
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        return new String[] {
            monthStart.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER),
            monthEnd.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER)
        };
    }

    /**
     * 预解析 AMB + 开方窗已签发 + COMPLETED（导出多页须复用本结果）。
     *
     * <p>AMB 按就诊 {@code start_time} 对齐开方日窗（与 AS-OP-PHA-005 同策略）；跨日挂号但开方落窗内的
     * 就诊可能漏收，属既有门诊处方查询口径。</p>
     *
     * @param openedStartInclusive 开方窗起
     * @param openedEndInclusive   开方窗止
     * @return 范围包；无命中时列表为空
     */
    public CompletedScope resolveCompletedScope(
        LocalDateTime openedStartInclusive,
        LocalDateTime openedEndInclusive) {
        // Pre：全院 AMB 就诊 id（按就诊 start_time 对齐开方窗，防住院混入）
        List<Long> ambEncounterIds = this.encounterQueryService.listOutpatientAmbEncounterIdsForCashierScope(
            null,
            openedStartInclusive,
            openedEndInclusive);
        // 无 AMB：空范围
        if (ambEncounterIds == null || ambEncounterIds.isEmpty()) {
            return new CompletedScope(List.of(), List.of());
        }
        // Pre：开方窗内已签发 medReq id（供发放 COMPLETED 收窄）
        List<Long> windowMedReqIds =
            this.medicationRequestQueryService.listOutpatientSignedMedicationRequestIdsInOpenedWindow(
                openedStartInclusive,
                openedEndInclusive,
                ambEncounterIds);
        // 窗内无已签发行：空范围
        if (windowMedReqIds == null || windowMedReqIds.isEmpty()) {
            return new CompletedScope(ambEncounterIds, List.of());
        }
        // Pre：发放 COMPLETED → medReqId IN
        List<Long> completedMedReqIds =
            this.medicationDispenseQueryService.listDistinctMedicationRequestIdsByStatusEnumAndMedReqIdIn(
                DispenseStatus.COMPLETED.getCode(),
                windowMedReqIds);
        // 无已发：空 COMPLETED 列表
        if (completedMedReqIds == null || completedMedReqIds.isEmpty()) {
            return new CompletedScope(ambEncounterIds, List.of());
        }
        return new CompletedScope(ambEncounterIds, completedMedReqIds);
    }

    /**
     * 在已解析 COMPLETED 范围内分页已签发药品行。
     *
     * @param reportType             报表类型
     * @param openedStartInclusive   开方窗起
     * @param openedEndInclusive     开方窗止
     * @param scope                  预解析范围
     * @param pageNo                 页码
     * @param pageSize               页大小
     * @return Spring 分页
     */
    private Page<MedicationRequestListQueryModel> pageDrugLinesWithScope(
        String reportType,
        LocalDateTime openedStartInclusive,
        LocalDateTime openedEndInclusive,
        CompletedScope scope,
        int pageNo,
        int pageSize) {
        // 无 AMB 或无已发：直接空页
        if (scope == null
            || scope.ambEncounterIds() == null
            || scope.ambEncounterIds().isEmpty()
            || scope.completedMedReqIds() == null
            || scope.completedMedReqIds().isEmpty()) {
            return Page.empty();
        }
        // Asm：组 PageQuery（rx 集合 + 排除退方 + AMB + COMPLETED）
        OutpatientSignedPrescriptionDrugLinePageQuery query =
            this.buildPageQuery(
                reportType,
                openedStartInclusive,
                openedEndInclusive,
                scope.ambEncounterIds(),
                scope.completedMedReqIds(),
                pageNo,
                pageSize);
        // Cmd：领域分页（明细行口径）
        return this.medicationRequestQueryService.pageOutpatientSignedPrescriptionDrugLines(query);
    }

    /**
     * 组装领域分页入参。
     *
     * @param reportType           报表类型
     * @param openedStartInclusive 开方窗起
     * @param openedEndInclusive   开方窗止
     * @param ambEncounterIds      AMB 就诊 id
     * @param completedMedReqIds   COMPLETED medReq id
     * @param pageNo               页码
     * @param pageSize             页大小
     * @return PageQuery
     */
    private OutpatientSignedPrescriptionDrugLinePageQuery buildPageQuery(
        String reportType,
        LocalDateTime openedStartInclusive,
        LocalDateTime openedEndInclusive,
        List<Long> ambEncounterIds,
        List<Long> completedMedReqIds,
        int pageNo,
        int pageSize) {
        OutpatientSignedPrescriptionDrugLinePageQuery query = new OutpatientSignedPrescriptionDrugLinePageQuery();
        query.setStartDate(openedStartInclusive);
        query.setEndDate(openedEndInclusive);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        query.setResolvedEncounterIds(ambEncounterIds);
        query.setResolvedMedicationRequestIdsForDispenseStatus(completedMedReqIds);
        query.setExcludeRefundPrescription(Boolean.TRUE);
        // 按类型写入 rx 码集合
        if (PrescriptionSpotCheckReportConstants.REPORT_TYPE_HERBAL.equals(reportType)) {
            query.setRxTypeCodes(PrescriptionSpotCheckReportConstants.listHerbalRxTypeCodes());
        } else {
            query.setRxTypeCodes(PrescriptionSpotCheckReportConstants.listWesternPatentRxTypeCodes());
        }
        return query;
    }

    /**
     * 构造下拉选项。
     *
     * @param value 值
     * @param label 文案
     * @return OptionDto
     */
    private OptionDto toOption(String value, String label) {
        OptionDto option = new OptionDto();
        option.setValue(value);
        option.setLabel(label);
        return option;
    }
}
