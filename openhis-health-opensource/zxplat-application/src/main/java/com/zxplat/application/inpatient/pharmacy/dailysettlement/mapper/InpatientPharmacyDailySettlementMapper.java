/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.dailysettlement.mapper;

import com.zxplat.application.inpatient.pharmacy.dailysettlement.request.PreviewInpatientPharmacyDailySettlementRequest;
import com.zxplat.application.inpatient.pharmacy.dailysettlement.request.SubmitInpatientPharmacyDailySettlementRequest;
import com.zxplat.application.inpatient.pharmacy.dailysettlement.response.LoadInpatientPharmacyDailySettlementPageInitResponse;
import com.zxplat.application.inpatient.pharmacy.dailysettlement.response.SubmitInpatientPharmacyDailySettlementResponse;
import com.zxplat.domain.financial.cashiersettlement.command.SubmitCashierSettlementCommand;
import com.zxplat.domain.medication.medicationdispense.queries.PreviewPharmacyCashierSettlementQuery;
import com.zxplat.framework.core.application.dto.OptionDto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 住院药房日结 Request/Command/Response 映射（AS-IP-PHA-006）。
 */
public interface InpatientPharmacyDailySettlementMapper {

    /**
     * 装配页初始化响应。
     *
     * @param defaultDateFrom      默认窗起
     * @param defaultDateTo        默认窗止
     * @param pharmacyOptions      药房下拉
     * @param practitionerOptions  药师岗发药人下拉
     * @param keyDrugTypeOptions      重点药类型下拉（品规销售排名筛选）
     * @param medicinePropertyOptions 药品性质下拉（麻/精，品规销售排名筛选）
     * @return 初始化响应
     */
    LoadInpatientPharmacyDailySettlementPageInitResponse toLoadPageInitResponse(
        String defaultDateFrom,
        String defaultDateTo,
        List<OptionDto> pharmacyOptions,
        List<OptionDto> practitionerOptions,
        List<OptionDto> keyDrugTypeOptions,
        List<OptionDto> medicinePropertyOptions);

    /**
     * 预览请求转领域查询。
     *
     * @param request  预览请求
     * @param typeEnum 交账类型 code
     * @return 领域预览查询
     */
    PreviewPharmacyCashierSettlementQuery toPreviewQuery(
        PreviewInpatientPharmacyDailySettlementRequest request,
        String typeEnum);

    /**
     * 提交请求转领域命令。
     *
     * @param request              提交请求
     * @param typeEnum             交账类型 code
     * @param submitterId          提交人 Practitioner 主键
     * @param operatorId           操作人用户主键
     * @param selectedDispenseIds  勾选发药行
     * @param practitionerIds      窗内发药人快照
     * @param paymentCount         挂载条数
     * @param totalIncome          头表 total_income（售价合计，与大类售价之和同口径）
     * @return 提交命令
     */
    SubmitCashierSettlementCommand toSubmitCommand(
        SubmitInpatientPharmacyDailySettlementRequest request,
        String typeEnum,
        Long submitterId,
        Long operatorId,
        List<Long> selectedDispenseIds,
        String practitionerIds,
        Integer paymentCount,
        java.math.BigDecimal totalIncome);

    /**
     * 装配提交响应。
     *
     * @param settlementId  交账头主键
     * @param busNo         单号
     * @param paymentCount  挂载条数
     * @return 提交响应
     */
    SubmitInpatientPharmacyDailySettlementResponse toSubmitResponse(
        Long settlementId,
        String busNo,
        Integer paymentCount);
}
