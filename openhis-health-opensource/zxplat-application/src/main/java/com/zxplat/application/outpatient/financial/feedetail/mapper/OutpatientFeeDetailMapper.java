/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.feedetail.mapper;

import com.zxplat.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailAmountSummaryDto;
import com.zxplat.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailEncounterBlockDto;
import com.zxplat.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailExportRowDto;
import com.zxplat.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailLineDto;
import com.zxplat.application.outpatient.financial.feedetail.request.ExportOutpatientFeeDetailRequest;
import com.zxplat.application.outpatient.financial.feedetail.request.PageOutpatientFeeDetailRequest;
import com.zxplat.application.outpatient.financial.feedetail.response.ExportOutpatientFeeDetailResponse;
import com.zxplat.application.outpatient.financial.feedetail.response.LoadOutpatientFeeDetailPageInitResponse;
import com.zxplat.application.outpatient.financial.feedetail.response.PageOutpatientFeeDetailResponse;
import com.zxplat.application.outpatient.financial.feedetail.assembler.OutpatientFeeDetailAssembler;
import com.zxplat.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.zxplat.framework.core.application.dto.OptionDto;

import java.util.List;

/**
 * 门诊费用明细 Request/Response 与领域 Query 映射（AS-OP-FIN-005 IF-OP-FDT-01～03）。
 *
 * <p>禁止承载业务流程判断与金额计算（金额见 {@link OutpatientFeeDetailAssembler}）。</p>
 */
public interface OutpatientFeeDetailMapper {

    /**
     * 分页/导出筛选请求 → 领域分页查询。
     *
     * @param request IF-OP-FDT-02 请求；调用方保证非空
     * @return 领域查询入参
     */
    OutpatientFeeDetailPageQuery toOutpatientFeeDetailPageQuery(PageOutpatientFeeDetailRequest request);

    /**
     * 导出筛选请求 → 领域分页查询。
     *
     * @param request IF-OP-FDT-03 请求；调用方保证非空
     * @return 领域查询入参
     */
    OutpatientFeeDetailPageQuery toOutpatientFeeDetailPageQuery(ExportOutpatientFeeDetailRequest request);

    /**
     * 页初始化主数据 → 初始化响应。
     *
     * @param defaultDateFrom               默认查询日起
     * @param defaultDateTo                 默认查询日止
     * @param defaultPageSize               默认分页大小
     * @param tradeDirectionOptions         收退 Tab
     * @param feeCategoryOptions            费用大类
     * @param medicationSubCategoryOptions  药品细分类
     * @param activitySubCategoryOptions    诊疗细分类
     * @param deviceSubCategoryOptions      耗材细分类
     * @param registrationDepartmentOptions 挂号科室
     * @param cashierOptions                收费员
     * @param feeNatureOptions              费用性质（bus_no）
     * @return 初始化响应
     */
    LoadOutpatientFeeDetailPageInitResponse toLoadOutpatientFeeDetailPageInitResponse(
        String defaultDateFrom,
        String defaultDateTo,
        int defaultPageSize,
        List<OptionDto> tradeDirectionOptions,
        List<OptionDto> feeCategoryOptions,
        List<OptionDto> medicationSubCategoryOptions,
        List<OptionDto> activitySubCategoryOptions,
        List<OptionDto> deviceSubCategoryOptions,
        List<OptionDto> registrationDepartmentOptions,
        List<OptionDto> cashierOptions,
        List<OptionDto> feeNatureOptions);

    /**
     * 就诊块 + 全量 summary → 分页响应。
     *
     * @param blocks     当前页就诊块
     * @param totalCount 命中就诊块总数
     * @param summary    全量合计
     * @param request    原分页请求（回显 pageNo/pageSize）
     * @return 分页响应
     */
    PageOutpatientFeeDetailResponse toPageOutpatientFeeDetailResponse(
        List<OutpatientFeeDetailEncounterBlockDto> blocks,
        long totalCount,
        OutpatientFeeDetailAmountSummaryDto summary,
        PageOutpatientFeeDetailRequest request);

    /**
     * 明细行 + 就诊块头 → 导出行。
     *
     * @param line  明细行
     * @param block 所属就诊块（患者/科室头）
     * @return 导出行；{@code line} 为 {@code null} 时返回 {@code null}
     */
    OutpatientFeeDetailExportRowDto toExportRowDto(
        OutpatientFeeDetailLineDto line,
        OutpatientFeeDetailEncounterBlockDto block);

    /**
     * xlsx 字节 → 导出响应。
     *
     * @param body       xlsx 内容
     * @param rowCount   导出行数
     * @param truncated  是否触顶截断
     * @return 导出响应
     */
    ExportOutpatientFeeDetailResponse toExportOutpatientFeeDetailResponse(
        byte[] body,
        int rowCount,
        boolean truncated);
}
