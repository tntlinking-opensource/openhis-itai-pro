/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.financial.feedetail.mapper;

import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailAmountSummaryDto;
import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailEncounterBlockDto;
import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailExportRowDto;
import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailLineDto;
import com.openhis.health.application.outpatient.financial.feedetail.request.ExportOutpatientFeeDetailRequest;
import com.openhis.health.application.outpatient.financial.feedetail.request.PageOutpatientFeeDetailRequest;
import com.openhis.health.application.outpatient.financial.feedetail.response.ExportOutpatientFeeDetailResponse;
import com.openhis.health.application.outpatient.financial.feedetail.response.LoadOutpatientFeeDetailPageInitResponse;
import com.openhis.health.application.outpatient.financial.feedetail.response.PageOutpatientFeeDetailResponse;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.OutpatientFeeDetailConstants;
import com.openhis.health.module.common.pagination.PageQueryBinder;
import com.openhis.whale.core.application.dto.OptionDto;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * {@link OutpatientFeeDetailMapper} 实现（AS-OP-FIN-005 IF-OP-FDT-01～03 映射层）。
 */
@Component
public class OutpatientFeeDetailMapperImpl implements OutpatientFeeDetailMapper {

    /**
     * 分页查询请求 → 领域分页查询。
     *
     * @param request IF-OP-FDT-02 请求；调用方保证非空
     * @return 领域查询入参
     */
    @Override
    public OutpatientFeeDetailPageQuery toOutpatientFeeDetailPageQuery(PageOutpatientFeeDetailRequest request) {
        OutpatientFeeDetailPageQuery query = new OutpatientFeeDetailPageQuery();
        PageQueryBinder.bind(request, query);
        this.applyFilterFields(query, request.getDateFrom(), request.getDateTo(),
            request.getRegistrationDepartmentId(), request.getFeeCategoryCodes(), request.getSubCategoryCodes(),
            request.getPatientKeyword(), request.getProductNameKeyword(), request.getProductCodeKeyword(),
            request.getCashierId(), request.getTradeDirection());
        return query;
    }

    /**
     * 导出筛选请求 → 领域分页查询。
     *
     * @param request IF-OP-FDT-03 请求；调用方保证非空
     * @return 领域查询入参
     */
    @Override
    public OutpatientFeeDetailPageQuery toOutpatientFeeDetailPageQuery(ExportOutpatientFeeDetailRequest request) {
        OutpatientFeeDetailPageQuery query = new OutpatientFeeDetailPageQuery();
        query.setPageNo(1);
        query.setPageSize(OutpatientFeeDetailConstants.EXPORT_MAX_ROWS);
        this.applyFilterFields(query, request.getDateFrom(), request.getDateTo(),
            request.getRegistrationDepartmentId(), request.getFeeCategoryCodes(), request.getSubCategoryCodes(),
            request.getPatientKeyword(), request.getProductNameKeyword(), request.getProductCodeKeyword(),
            request.getCashierId(), request.getTradeDirection());
        return query;
    }

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
     * @return 初始化响应
     */
    @Override
    public LoadOutpatientFeeDetailPageInitResponse toLoadOutpatientFeeDetailPageInitResponse(
        String defaultDateFrom,
        String defaultDateTo,
        int defaultPageSize,
        List<OptionDto> tradeDirectionOptions,
        List<OptionDto> feeCategoryOptions,
        List<OptionDto> medicationSubCategoryOptions,
        List<OptionDto> activitySubCategoryOptions,
        List<OptionDto> deviceSubCategoryOptions,
        List<OptionDto> registrationDepartmentOptions,
        List<OptionDto> cashierOptions) {
        LoadOutpatientFeeDetailPageInitResponse response = new LoadOutpatientFeeDetailPageInitResponse();
        response.setDefaultDateFrom(defaultDateFrom);
        response.setDefaultDateTo(defaultDateTo);
        response.setDefaultPageSize(defaultPageSize);
        response.setTradeDirectionOptions(tradeDirectionOptions);
        response.setFeeCategoryOptions(feeCategoryOptions);
        response.setMedicationSubCategoryOptions(medicationSubCategoryOptions);
        response.setActivitySubCategoryOptions(activitySubCategoryOptions);
        response.setDeviceSubCategoryOptions(deviceSubCategoryOptions);
        response.setRegistrationDepartmentOptions(registrationDepartmentOptions);
        response.setCashierOptions(cashierOptions);
        return response;
    }

    /**
     * 就诊块 + 全量 summary → 分页响应。
     *
     * @param blocks     当前页就诊块
     * @param totalCount 命中就诊块总数
     * @param summary    全量合计
     * @param request    原分页请求（回显 pageNo/pageSize）
     * @return 分页响应
     */
    @Override
    public PageOutpatientFeeDetailResponse toPageOutpatientFeeDetailResponse(
        List<OutpatientFeeDetailEncounterBlockDto> blocks,
        long totalCount,
        OutpatientFeeDetailAmountSummaryDto summary,
        PageOutpatientFeeDetailRequest request) {
        PageOutpatientFeeDetailResponse response = new PageOutpatientFeeDetailResponse();
        if (blocks == null) {
            response.setContent(List.of());
        } else {
            response.setContent(blocks);
        }
        response.setSummary(summary);
        response.setTotalCount(totalCount);
        if (request != null) {
            response.setPageNo(this.resolvePageNo(request.getPageNo()));
            response.setPageSize(this.resolvePageSize(request.getPageSize()));
        } else {
            response.setPageNo(1);
            response.setPageSize(OutpatientFeeDetailConstants.DEFAULT_PAGE_SIZE);
        }
        return response;
    }

    /**
     * 明细行 + 就诊块头 → 导出行。
     *
     * @param line  明细行
     * @param block 所属就诊块（患者/科室头）
     * @return 导出行；{@code line} 为 {@code null} 时返回 {@code null}
     */
    @Override
    public OutpatientFeeDetailExportRowDto toExportRowDto(
        OutpatientFeeDetailLineDto line,
        OutpatientFeeDetailEncounterBlockDto block) {
        if (line == null) {
            return null;
        }
        OutpatientFeeDetailExportRowDto row = new OutpatientFeeDetailExportRowDto();
        if (block != null) {
            row.setPatientName(block.getPatientName());
            row.setBusNo(block.getBusNo());
            row.setRegistrationDepartmentName(block.getRegistrationDepartmentName());
        }
        row.setFeeCategoryDisplay(line.getFeeCategoryDisplay());
        row.setSubCategoryDisplay(line.getSubCategoryDisplay());
        row.setProductName(line.getProductName());
        row.setProductCode(line.getProductCode());
        row.setSpecification(line.getSpecification());
        row.setQuantityValue(line.getQuantityValue());
        row.setQuantityUnitDisplay(line.getQuantityUnitDisplay());
        row.setOrderingDoctorName(line.getOrderingDoctorName());
        row.setCashierName(line.getCashierName());
        row.setBillDate(line.getBillDate());
        row.setInsuranceLevelDisplay(line.getInsuranceLevelDisplay());
        row.setInsuranceTypeDisplay(line.getInsuranceTypeDisplay());
        row.setBaseAmount(line.getBaseAmount());
        row.setDiscountAmount(line.getDiscountAmount());
        row.setReceivableAmount(line.getReceivableAmount());
        row.setDiscountedAmount(line.getDiscountedAmount());
        row.setUnitPrice(line.getUnitPrice());
        row.setLineAmount(line.getLineAmount());
        row.setActualReceivedAmount(line.getActualReceivedAmount());
        row.setInsuranceAmount(line.getInsuranceAmount());
        row.setSelfPayAmount(line.getSelfPayAmount());
        return row;
    }

    /**
     * xlsx 字节 → 导出响应。
     *
     * @param body       xlsx 内容
     * @param rowCount   导出行数
     * @param truncated  是否触顶截断
     * @return 导出响应
     */
    @Override
    public ExportOutpatientFeeDetailResponse toExportOutpatientFeeDetailResponse(
        byte[] body,
        int rowCount,
        boolean truncated) {
        ExportOutpatientFeeDetailResponse response = new ExportOutpatientFeeDetailResponse();
        String timestamp = LocalDateTime.now().format(GeneralSharedConstants.EXPORT_FILE_TIMESTAMP_FORMATTER);
        response.setFileName("门诊费用明细_" + timestamp + ".xlsx");
        response.setContentType(OutpatientFeeDetailConstants.EXPORT_CONTENT_TYPE);
        response.setBody(body);
        response.setRowCount(rowCount);
        response.setTruncated(truncated);
        return response;
    }

    /**
     * 将 HTTP 筛选字段写入领域 Query（dateFrom/To 转 billDate 闭区间，**BR-OP-FDT-001/003**）。
     *
     * @param query                   目标领域 Query
     * @param dateFrom                结算日起（yyyy-MM-dd）
     * @param dateTo                  结算日止（yyyy-MM-dd）
     * @param registrationDepartmentId 挂号科室
     * @param feeCategoryCodes        费用大类多选
     * @param subCategoryCodes        细分类多选
     * @param patientKeyword          患者关键字
     * @param productNameKeyword      项目名称关键字
     * @param productCodeKeyword      项目编码关键字
     * @param cashierId               收费人
     * @param tradeDirection          收退 Tab
     */
    private void applyFilterFields(
        OutpatientFeeDetailPageQuery query,
        String dateFrom,
        String dateTo,
        Long registrationDepartmentId,
        List<String> feeCategoryCodes,
        List<String> subCategoryCodes,
        String patientKeyword,
        String productNameKeyword,
        String productCodeKeyword,
        Long cashierId,
        String tradeDirection) {
        if (StringUtils.isNotBlank(dateFrom)) {
            query.setBillDateFrom(LocalDate.parse(dateFrom.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
        }
        if (StringUtils.isNotBlank(dateTo)) {
            query.setBillDateTo(LocalDate.parse(dateTo.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
        }
        query.setRegistrationDepartmentId(registrationDepartmentId);
        query.setFeeCategoryCodes(feeCategoryCodes);
        query.setSubCategoryCodes(subCategoryCodes);
        query.setPatientKeyword(trimToNull(patientKeyword));
        query.setProductNameKeyword(trimToNull(productNameKeyword));
        query.setProductCodeKeyword(trimToNull(productCodeKeyword));
        query.setCashierId(cashierId);
        String resolvedTradeDirection = trimToNull(tradeDirection);
        if (resolvedTradeDirection == null) {
            // 未传 Tab 时默认全部（BR-OP-FDT-009）
            query.setTradeDirection(OutpatientFeeDetailConstants.TRADE_DIRECTION_ALL);
        } else {
            query.setTradeDirection(resolvedTradeDirection);
        }
    }

    /**
     * 解析页码，缺省或非法时回落第 1 页（**BR-OP-FDT-013**）。
     *
     * @param pageNo 请求页码
     * @return 有效 1-based 页码
     */
    private int resolvePageNo(Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            return 1;
        }
        return pageNo;
    }

    /**
     * 解析页大小，缺省或非法时回落默认就诊块数（**BR-OP-FDT-013**）。
     *
     * @param pageSize 请求页大小
     * @return 有效页大小
     */
    private int resolvePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return OutpatientFeeDetailConstants.DEFAULT_PAGE_SIZE;
        }
        return pageSize;
    }
}
