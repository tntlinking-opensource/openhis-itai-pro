/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.keymedicationusage.service;

import com.openhis.health.application.report.keymedicationusage.dto.KeyMedicationUsageSummaryRowDto;
import com.openhis.health.application.report.keymedicationusage.mapper.KeyMedicationUsageReportMapper;
import com.openhis.health.application.report.keymedicationusage.request.ExportKeyMedicationUsageReportRequest;
import com.openhis.health.application.report.keymedicationusage.request.LoadKeyMedicationUsageReportPageInitRequest;
import com.openhis.health.application.report.keymedicationusage.request.SummarizeKeyMedicationUsageReportRequest;
import com.openhis.health.application.report.keymedicationusage.response.ExportKeyMedicationUsageReportResponse;
import com.openhis.health.application.report.keymedicationusage.response.LoadKeyMedicationUsageReportPageInitResponse;
import com.openhis.health.application.report.keymedicationusage.response.SummarizeKeyMedicationUsageReportResponse;
import com.openhis.health.application.report.keymedicationusage.support.KeyMedicationUsageReportQuerySupport;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.KeyMedicationUsageReportConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.KeyDrugType;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.util.AnnotatedExcelExporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 重点药品使用统计报表只读编排（AS-RPT-011 IF-RPT-KDU-01～03）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link CurrentOperatorResolver}：登录 Gate</li>
 *   <li>{@link KeyMedicationUsageReportQuerySupport}：日期窗、类型集合、费用明细 scope、矩阵汇总</li>
 *   <li>{@link KeyMedicationUsageReportMapper}：Request/Response 映射</li>
 *   <li>{@link AnnotatedExcelExporter}：导出</li>
 * </ul>
 * <p>接入：AppServiceControllerRegistrar →
 * {@code POST /api/app/keyMedicationUsageReport/{methodName}}；不另写 RestController。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KeyMedicationUsageReportAppService {

    /** 登录 Gate。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 矩阵汇总与筛选项。 */
    private final KeyMedicationUsageReportQuerySupport querySupport;

    /** Request/Response 映射。 */
    private final KeyMedicationUsageReportMapper mapper;

    /**
     * IF-RPT-KDU-01：页面初始化（默认当日、就诊类型/重点药类型下拉）。
     *
     * @param request 空体占位
     * @return 初始化响应
     * @throws BusinessException {@link KeyMedicationUsageReportConstants#EX_RPT_KDU_101} 初始化失败
     */
    public LoadKeyMedicationUsageReportPageInitResponse loadKeyMedicationUsageReportPageInit(
        LoadKeyMedicationUsageReportPageInitRequest request) {
        // Gate：须已登录方可加载筛选项
        this.currentOperatorResolver.requireOperatorId(
            KeyMedicationUsageReportConstants.EX_RPT_KDU_101,
            "页面初始化失败，请稍后重试");
        try {
            LocalDate today = LocalDate.now();
            String todayText = today.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            // Pre：就诊类型 / 重点药类型选项（枚举展示，不查库）
            List<OptionDto> encounterClassOptions = this.querySupport.buildEncounterClassOptions();
            List<OptionDto> keyDrugTypeOptions = this.querySupport.buildKeyDrugTypeOptions();
            // Asm：默认当日 + 下拉（BR-RPT-KDU-001）
            return this.mapper.toLoadPageInitResponse(
                todayText, todayText, encounterClassOptions, keyDrugTypeOptions);
        } catch (BusinessException businessException) {
            // Gate：业务异常原样上抛，避免二次包装丢码
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：未预期异常映射初始化失败码
            log.error("重点药品使用统计报表页初始化失败", runtimeException);
            throw new BusinessException(
                KeyMedicationUsageReportConstants.EX_RPT_KDU_101,
                "页面初始化失败，请稍后重试",
                runtimeException);
        }
    }

    /**
     * IF-RPT-KDU-02：就诊类型 × 重点药类型矩阵汇总。
     *
     * @param request 日期与多选筛选
     * @return 矩阵行与页脚合计
     * @throws BusinessException {@link KeyMedicationUsageReportConstants#EX_RPT_KDU_102} 日期非法；
     *         {@link KeyMedicationUsageReportConstants#EX_RPT_KDU_103} 查询失败
     */
    public SummarizeKeyMedicationUsageReportResponse summarizeKeyMedicationUsageReport(
        SummarizeKeyMedicationUsageReportRequest request) {
        // Gate：须已登录
        this.currentOperatorResolver.requireOperatorId(
            KeyMedicationUsageReportConstants.EX_RPT_KDU_101,
            "查询失败，请稍后重试");
        SummarizeKeyMedicationUsageReportRequest safeRequest = request;
        // Gate：空体按「仅缺日期」处理，后续 requireDateWindow 抛 102
        if (safeRequest == null) {
            safeRequest = new SummarizeKeyMedicationUsageReportRequest();
        }
        // Gate：日期闭区间必填（BR-RPT-KDU-001）
        LocalDate[] dateWindow =
            this.querySupport.requireDateWindow(safeRequest.getDateFrom(), safeRequest.getDateTo());
        try {
            // Pre：空多选 = 全部类型（BR-RPT-KDU-002）
            List<EncounterClass> encounterClasses =
                this.querySupport.resolveEncounterClasses(safeRequest.getEncounterClassCodes());
            List<KeyDrugType> keyDrugTypes =
                this.querySupport.resolveKeyDrugTypes(safeRequest.getKeyDrugTypeCodes());
            // Pre：矩阵汇总（含 0 行；门诊/住院对齐费用明细 scope）
            List<KeyMedicationUsageSummaryRowDto> rows = this.querySupport.summarizeMatrixRows(
                dateWindow[0], dateWindow[1], encounterClasses, keyDrugTypes);
            // Asm：页脚 = 各行人次之和 + 金额代数和（跨行不去重就诊）
            return this.mapper.toSummarizeResponse(
                rows, this.sumEncounterNetCount(rows), this.sumSaleAmount(rows));
        } catch (BusinessException businessException) {
            // Gate：业务异常原样上抛
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：查询运行时失败映射 103
            log.error("重点药品使用统计汇总失败", runtimeException);
            throw new BusinessException(
                KeyMedicationUsageReportConstants.EX_RPT_KDU_103,
                "查询失败，请稍后重试",
                runtimeException);
        }
    }

    /**
     * IF-RPT-KDU-03：导出当前筛选矩阵为 Excel。
     *
     * @param request 与汇总同筛选
     * @return xlsx 二进制响应
     * @throws BusinessException {@link KeyMedicationUsageReportConstants#EX_RPT_KDU_102} 日期非法；
     *         {@link KeyMedicationUsageReportConstants#EX_RPT_KDU_104} 导出失败
     */
    public ExportKeyMedicationUsageReportResponse exportKeyMedicationUsageReport(
        ExportKeyMedicationUsageReportRequest request) {
        // Gate：须已登录
        this.currentOperatorResolver.requireOperatorId(
            KeyMedicationUsageReportConstants.EX_RPT_KDU_101,
            "导出失败，请稍后重试");
        ExportKeyMedicationUsageReportRequest safeRequest = request;
        // Gate：空体按缺日期处理
        if (safeRequest == null) {
            safeRequest = new ExportKeyMedicationUsageReportRequest();
        }
        // Gate：日期闭区间必填
        LocalDate[] dateWindow =
            this.querySupport.requireDateWindow(safeRequest.getDateFrom(), safeRequest.getDateTo());
        try {
            // Pre：与汇总同口径解析类型集合
            List<EncounterClass> encounterClasses =
                this.querySupport.resolveEncounterClasses(safeRequest.getEncounterClassCodes());
            List<KeyDrugType> keyDrugTypes =
                this.querySupport.resolveKeyDrugTypes(safeRequest.getKeyDrugTypeCodes());
            // Pre：复用矩阵汇总（BR-RPT-KDU-007）
            List<KeyMedicationUsageSummaryRowDto> rows = this.querySupport.summarizeMatrixRows(
                dateWindow[0], dateWindow[1], encounterClasses, keyDrugTypes);
            // Asm：xlsx 字节（矩阵 ≤6 行，truncated 恒 false）
            byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                KeyMedicationUsageReportConstants.EXPORT_SHEET_NAME,
                rows,
                KeyMedicationUsageSummaryRowDto.class,
                request.getExportColumns());
            String fileName = KeyMedicationUsageReportConstants.EXPORT_FILE_PREFIX
                + LocalDateTime.now().format(GeneralSharedConstants.EXPORT_FILE_TIMESTAMP_FORMATTER)
                + ".xlsx";
            return this.mapper.toExportResponse(
                fileName,
                KeyMedicationUsageReportConstants.EXPORT_CONTENT_TYPE,
                body,
                rows.size(),
                Boolean.FALSE);
        } catch (BusinessException businessException) {
            // Gate：业务异常原样上抛
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：导出运行时失败映射 104
            log.error("重点药品使用统计导出失败", runtimeException);
            throw new BusinessException(
                KeyMedicationUsageReportConstants.EX_RPT_KDU_104,
                "导出失败，请稍后重试",
                runtimeException);
        }
    }

    /**
     * 页脚净人次合计（各行相加，不去重跨行就诊）。
     *
     * @param rows 矩阵行
     * @return 合计
     */
    private long sumEncounterNetCount(List<KeyMedicationUsageSummaryRowDto> rows) {
        long total = 0L;
        // Gate：空列表页脚为 0
        if (rows == null) {
            return total;
        }
        // Loop：累加各行人次
        for (KeyMedicationUsageSummaryRowDto row : rows) {
            // 跳过空行或缺人次字段
            if (row != null && row.getEncounterNetCount() != null) {
                total = total + row.getEncounterNetCount();
            }
        }
        return total;
    }

    /**
     * 页脚售价净额合计。
     *
     * @param rows 矩阵行
     * @return 结算刻度合计
     */
    private BigDecimal sumSaleAmount(List<KeyMedicationUsageSummaryRowDto> rows) {
        BigDecimal total = BigDecimal.ZERO;
        // Gate：空列表页脚金额 0（结算刻度）
        if (rows == null) {
            return MonetaryAmountSupport.scaleSettle(total);
        }
        // Loop：代数加总各行金额
        for (KeyMedicationUsageSummaryRowDto row : rows) {
            // 跳过空行或缺金额字段
            if (row != null && row.getSaleAmountTotal() != null) {
                total = total.add(row.getSaleAmountTotal());
            }
        }
        return MonetaryAmountSupport.scaleSettle(total);
    }
}
