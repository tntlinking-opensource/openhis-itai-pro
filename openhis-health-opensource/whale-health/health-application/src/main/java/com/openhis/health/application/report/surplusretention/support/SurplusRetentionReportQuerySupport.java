/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.surplusretention.support;

import com.openhis.health.application.report.surplusretention.dto.SurplusRetentionReportRowDto;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.report.ReportDateTimeWindowSupport;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.SurplusRetentionReportConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 药品结余留用报表筛选预解析与分页/导出收集（AS-RPT-015）。
 *
 * <p>职责：校验批次与签发日窗；目录 id → 申请 id → 患者发退药分页；导出时<strong>一次</strong>预解析后循环翻页。</p>
 */
@Component
@RequiredArgsConstructor
public class SurplusRetentionReportQuerySupport {

    /** 导出翻页页大小（大于列表默认，降低导出轮次与预解析外开销）。 */
    private static final int EXPORT_PAGE_SIZE = 500;

    /** 批次术语下拉（surplus_retention_batch_code）。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 药品目录：按结余留用批次号收窄主键。 */
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;

    /** 药品申请：按目录主键 + 签发窗投影申请 id。 */
    private final MedicationRequestQueryService medicationRequestQueryService;

    /** 药品发放：患者侧 COMPLETED/REFUNDED 分页。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** 19 列行装配（含售价/#4M-RFND）。 */
    private final SurplusRetentionReportRowAssembler rowAssembler;

    /**
     * 筛选预解析结果（申请主键集合）；导出多页复用，禁止每页重算。
     *
     * @param medReqIds 药品申请主键；空表示无命中
     */
    public record FilterScope(List<Long> medReqIds) {
    }

    /**
     * 明细分页结果包。
     *
     * @param page Spring 分页壳（发放行口径）
     * @param rows 展示行
     */
    public record PageBundle(
        Page<MedicationDispense> page,
        List<SurplusRetentionReportRowDto> rows) {
    }

    /**
     * 导出收集结果。
     *
     * @param rows      导出行
     * @param truncated 是否触顶截断
     */
    public record ExportBundle(
        List<SurplusRetentionReportRowDto> rows,
        boolean truncated) {
    }

    /**
     * 解析当月默认签发日起止（yyyy-MM-dd）。
     *
     * @param today 参考日
     * @return {@code [0]=月初，[1]=月末}
     */
    public String[] resolveDefaultAuthoredMonthBounds(LocalDate today) {
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        return new String[] {
            monthStart.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER),
            monthEnd.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER)
        };
    }

    /**
     * 解析必填签发日窗为闭区间时刻（日起 00:00:00～日止 23:59:59）。
     *
     * @param authoredDateFrom 日起 yyyy-MM-dd
     * @param authoredDateTo   日止 yyyy-MM-dd
     * @param exInvalid        非法异常码
     * @return {@code [0]=窗起 inclusive，[1]=窗止 inclusive}
     */
    public LocalDateTime[] parseRequiredAuthoredDateWindow(
        String authoredDateFrom,
        String authoredDateTo,
        String exInvalid) {
        String fromText = StringUtils.trimToNull(authoredDateFrom);
        String toText = StringUtils.trimToNull(authoredDateTo);
        // 签发日起止必填
        if (fromText == null || toText == null) {
            throw new BusinessException(exInvalid, "请选择结余留用批次号并设置合法签发时间");
        }
        LocalDateTime from;
        LocalDateTime to;
        try {
            from = ReportDateTimeWindowSupport.parseInclusiveStart(fromText);
            to = ReportDateTimeWindowSupport.parseInclusiveEnd(toText);
        } catch (DateTimeParseException parseException) {
            // 日期格式非法：映射为筛选非法
            throw new BusinessException(exInvalid, "请选择结余留用批次号并设置合法签发时间", parseException);
        }
        // 起不得晚于止
        if (from.isAfter(to)) {
            throw new BusinessException(exInvalid, "请选择结余留用批次号并设置合法签发时间");
        }
        return new LocalDateTime[] {from, to};
    }

    /**
     * 校验并规范化结余留用批次号（必选）。
     *
     * @param batchCodeRaw 请求批次码
     * @param exInvalid    非法异常码
     * @return 规范化批次码
     */
    public String requireBatchCode(String batchCodeRaw, String exInvalid) {
        String batchCode = StringUtils.trimToNull(batchCodeRaw);
        // 批次必选：空则拒绝
        if (batchCode == null) {
            throw new BusinessException(exInvalid, "请选择结余留用批次号并设置合法签发时间");
        }
        return batchCode;
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
            throw new BusinessException(exInvalid, "请选择结余留用批次号并设置合法签发时间");
        }
        int size;
        // 未传页大小：回落默认
        if (pageSize == null || pageSize < 1) {
            size = SurplusRetentionReportConstants.DEFAULT_PAGE_SIZE;
        } else {
            size = pageSize;
        }
        // 超过单页上限则截断
        if (size > SurplusRetentionReportConstants.MAX_PAGE_SIZE) {
            size = SurplusRetentionReportConstants.MAX_PAGE_SIZE;
        }
        return new int[] {pageNo, size};
    }

    /**
     * 组装结余留用批次号术语下拉。
     *
     * @return 选项列表；无术语时可能为空列表
     */
    public List<OptionDto> buildBatchOptions() {
        return this.terminologyOptionLoader.loadOptions(
            TerminologyCodeSystemNameConstants.SURPLUS_RETENTION_BATCH_CODE);
    }

    /**
     * 预解析：批次码 → 目录 id → 签发窗申请 id（导出多页须复用）。
     *
     * @param batchCode                批次号
     * @param authoredStartInclusive   签发窗起
     * @param authoredEndInclusive     签发窗止
     * @return 范围包；无命中时 medReqIds 为空
     */
    public FilterScope resolveFilterScope(
        String batchCode,
        LocalDateTime authoredStartInclusive,
        LocalDateTime authoredEndInclusive) {
        // Pre：目录主键 ← 批次码等值
        List<Long> medDefIds =
            this.medicationDefinitionQueryService.listIdsBySurplusRetentionBatchCode(batchCode);
        // 无目录：空范围
        if (medDefIds == null || medDefIds.isEmpty()) {
            return new FilterScope(List.of());
        }
        // Pre：申请主键 ← 目录 id + 签发窗
        List<Long> medReqIds =
            this.medicationRequestQueryService.listIdsByMedicationIdsAndAuthoredTimeBetween(
                medDefIds,
                authoredStartInclusive,
                authoredEndInclusive);
        // 无申请：空范围
        if (medReqIds == null || medReqIds.isEmpty()) {
            return new FilterScope(List.of());
        }
        return new FilterScope(medReqIds);
    }

    /**
     * 在已解析申请范围内分页患者发退药 + 行装配。
     *
     * @param scope    预解析范围
     * @param pageNo   页码（1-based）
     * @param pageSize 页大小
     * @return 分页包
     */
    public PageBundle pageDetail(FilterScope scope, int pageNo, int pageSize) {
        // 无申请：空页
        if (scope == null || scope.medReqIds() == null || scope.medReqIds().isEmpty()) {
            PageRequest emptyPageable = PageRequest.of(
                Math.max(pageNo - 1, 0),
                pageSize,
                Sort.by(Sort.Order.asc(FieldNameConstants.ID)));
            return new PageBundle(Page.empty(emptyPageable), List.of());
        }
        PageRequest pageable = PageRequest.of(
            pageNo - 1,
            pageSize,
            Sort.by(Sort.Order.asc(FieldNameConstants.ID)));
        // Cmd：患者发退药 COMPLETED + REFUNDED
        Page<MedicationDispense> page =
            this.medicationDispenseQueryService.pagePatientDispensesByMedReqIdsAndStatusIn(
                scope.medReqIds(),
                List.of(DispenseStatus.COMPLETED, DispenseStatus.REFUNDED),
                pageable);
        List<SurplusRetentionReportRowDto> rows =
            this.rowAssembler.assembleRows(page.getContent());
        return new PageBundle(page, rows);
    }

    /**
     * 循环分页收集导出行至上限（预解析仅一次）。
     *
     * @param scope 预解析范围
     * @return 导出包
     */
    public ExportBundle collectExportRows(FilterScope scope) {
        List<SurplusRetentionReportRowDto> exportRows = new ArrayList<>();
        boolean truncated = false;
        int pageNo = 1;
        // 循环直至空页或触顶 EXPORT_MAX_ROWS；scope 复用不重算目录/申请
        while (exportRows.size() < SurplusRetentionReportConstants.EXPORT_MAX_ROWS) {
            PageBundle bundle = this.pageDetail(scope, pageNo, EXPORT_PAGE_SIZE);
            List<SurplusRetentionReportRowDto> pageRows = bundle.rows();
            // 空页：结束
            if (pageRows == null || pageRows.isEmpty()) {
                break;
            }
            for (SurplusRetentionReportRowDto row : pageRows) {
                // 触顶：标记截断并停止追加
                if (exportRows.size() >= SurplusRetentionReportConstants.EXPORT_MAX_ROWS) {
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
        return new ExportBundle(exportRows, truncated);
    }
}
