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
 *   <li>{@link KeyMedicationUsageReportQuerySupport}：日期窗、类型/状态集合、费用明细 scope、类型矩阵汇总</li>
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
    /** 类型矩阵汇总与筛选项。 */
    private final KeyMedicationUsageReportQuerySupport querySupport;
    /** Request/Response 映射。 */
    private final KeyMedicationUsageReportMapper mapper;
    /**
     * IF-RPT-KDU-01：页面初始化（默认近一个月、就诊类型/重点药类型下拉）。
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
            LocalDate defaultFrom = today.minusMonths(KeyMedicationUsageReportConstants.DEFAULT_LOOKBACK_MONTHS);
            String defaultFromText = defaultFrom.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            String defaultToText = today.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            // Pre：就诊类型 / 重点药类型 / 药品性质选项（两框分列，不查库）
            List<OptionDto> encounterClassOptions = this.querySupport.buildEncounterClassOptions();
            List<OptionDto> keyDrugTypeOptions = this.querySupport.buildKeyDrugTypeOptions();
            List<OptionDto> medicinePropertyOptions = this.querySupport.buildMedicinePropertyOptions();
            // Asm：默认近一个月闭区间 + 下拉（BR-RPT-KDU-001）
            return this.mapper.toLoadPageInitResponse(
                defaultFromText,
                defaultToText,
                encounterClassOptions,
                keyDrugTypeOptions,
                medicinePropertyOptions);
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
     * IF-RPT-KDU-02：按就诊类型 × 重点药类型矩阵汇总 + 各组合计行。
     *
     * @param request 日期与多选筛选
     * @return 汇总行与页脚合计
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
            // Pre：空多选 = 全部类型
            List<EncounterClass> encounterClasses =
                this.querySupport.resolveEncounterClasses(safeRequest.getEncounterClassCodes());
            List<KeyDrugType> keyDrugTypes =
                this.querySupport.resolveKeyDrugTypes(
                    safeRequest.getKeyDrugTypeCodes(),
                    safeRequest.getMedicinePropertyCodes());
            // Pre：AMB/IMP 各类型行 + 组内 TOTAL（含使用占比）
            List<KeyMedicationUsageSummaryRowDto> rows = this.querySupport.summarizeMatrixRows(
                dateWindow[0],
                dateWindow[1],
                encounterClasses,
                keyDrugTypes);
            // Asm：页脚跳过 keyDrugTypeCode=TOTAL，避免与类型行双计
            return this.mapper.toSummarizeResponse(
                rows,
                this.sumEncounterNetCount(rows),
                this.sumSaleAmount(rows));
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
     * IF-RPT-KDU-03：导出当前筛选汇总为 Excel。
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
                this.querySupport.resolveKeyDrugTypes(
                    safeRequest.getKeyDrugTypeCodes(),
                    safeRequest.getMedicinePropertyCodes());
            // Pre：复用汇总行（含各组 TOTAL；BR-RPT-KDU-007）
            List<KeyMedicationUsageSummaryRowDto> rows = this.querySupport.summarizeMatrixRows(
                dateWindow[0],
                dateWindow[1],
                encounterClasses,
                keyDrugTypes);
            // Asm：xlsx 字节（类型行+组合计，truncated 恒 false）
            byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                KeyMedicationUsageReportConstants.EXPORT_SHEET_NAME,
                rows,
                KeyMedicationUsageSummaryRowDto.class,
                safeRequest.getExportColumns());
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
     * 页脚净人次合计：累加各组「合计」行（全部药品人次）；跳过类型行以免多旗双计。
     *
     * @param rows 汇总行
     * @return 合计
     */
    private long sumEncounterNetCount(List<KeyMedicationUsageSummaryRowDto> rows) {
        long total = 0L;
        // Gate：空列表页脚为 0
        if (rows == null) {
            return total;
        }
        // Loop：只加组内合计行（AMB 并集 + IMP 并集）
        for (KeyMedicationUsageSummaryRowDto row : rows) {
            // 空行跳过
            if (row == null) {
                continue;
            }
            // 仅合计行入页脚
            if (!KeyMedicationUsageReportConstants.SUMMARY_KEY_DRUG_TYPE_TOTAL.equals(
                row.getKeyDrugTypeCode())) {
                continue;
            }
            if (row.getEncounterNetCount() != null) {
                total = total + row.getEncounterNetCount();
            }
        }
        return total;
    }

    /**
     * 页脚售价净额合计：累加各组「合计」行（全部药品金额）；跳过类型行。
     *
     * @param rows 汇总行
     * @return 结算刻度合计
     */
    private BigDecimal sumSaleAmount(List<KeyMedicationUsageSummaryRowDto> rows) {
        BigDecimal total = BigDecimal.ZERO;
        // Gate：空列表页脚金额 0（结算刻度）
        if (rows == null) {
            return MonetaryAmountSupport.scaleSettle(total);
        }
        // Loop：只加组内合计行金额
        for (KeyMedicationUsageSummaryRowDto row : rows) {
            // 空行跳过
            if (row == null) {
                continue;
            }
            // 仅合计行入页脚
            if (!KeyMedicationUsageReportConstants.SUMMARY_KEY_DRUG_TYPE_TOTAL.equals(
                row.getKeyDrugTypeCode())) {
                continue;
            }
            if (row.getSaleAmountTotal() != null) {
                total = total.add(row.getSaleAmountTotal());
            }
        }
        return MonetaryAmountSupport.scaleSettle(total);
    }
}
