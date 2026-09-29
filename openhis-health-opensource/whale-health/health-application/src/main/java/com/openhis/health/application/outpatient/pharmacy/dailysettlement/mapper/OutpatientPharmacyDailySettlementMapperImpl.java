/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dailysettlement.mapper;

import com.openhis.health.application.outpatient.pharmacy.dailysettlement.request.PreviewOutpatientPharmacyDailySettlementRequest;
import com.openhis.health.application.outpatient.pharmacy.dailysettlement.request.SubmitOutpatientPharmacyDailySettlementRequest;
import com.openhis.health.application.outpatient.pharmacy.dailysettlement.response.LoadOutpatientPharmacyDailySettlementPageInitResponse;
import com.openhis.health.application.outpatient.pharmacy.dailysettlement.response.SubmitOutpatientPharmacyDailySettlementResponse;
import com.openhis.health.domain.financial.cashiersettlement.command.SubmitCashierSettlementCommand;
import com.openhis.health.domain.medication.medicationdispense.queries.PreviewPharmacyCashierSettlementQuery;
import com.openhis.whale.core.application.dto.OptionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@link OutpatientPharmacyDailySettlementMapper} 实现（AS-OP-PHA-006）。
 *
 * <p>职责：Request/Command/Response 字段映射；禁止承载 Gate 与写编排。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientPharmacyDailySettlementMapperImpl implements OutpatientPharmacyDailySettlementMapper {

    /**
     * 装配页初始化响应（IF-OP-PDC-01）。
     *
     * @param defaultDateFrom      默认窗起
     * @param defaultDateTo        默认窗止
     * @param pharmacyOptions      药房下拉
     * @param practitionerOptions  药师岗发药人下拉
     * @param keyDrugTypeOptions      重点药类型下拉
     * @param medicinePropertyOptions 药品性质下拉（麻/精）
     * @return 初始化响应
     */
    @Override
    public LoadOutpatientPharmacyDailySettlementPageInitResponse toLoadPageInitResponse(
        String defaultDateFrom,
        String defaultDateTo,
        List<OptionDto> pharmacyOptions,
        List<OptionDto> practitionerOptions,
        List<OptionDto> keyDrugTypeOptions,
        List<OptionDto> medicinePropertyOptions) {
        LoadOutpatientPharmacyDailySettlementPageInitResponse response =
            new LoadOutpatientPharmacyDailySettlementPageInitResponse();
        response.setDefaultDateFrom(defaultDateFrom);
        response.setDefaultDateTo(defaultDateTo);
        response.setPharmacyOptions(pharmacyOptions);
        response.setPractitionerOptions(practitionerOptions);
        response.setKeyDrugTypeOptions(keyDrugTypeOptions);
        response.setMedicinePropertyOptions(medicinePropertyOptions);
        return response;
    }

    /**
     * 预览请求转领域查询（IF-OP-PDC-02）。
     *
     * @param request  预览请求
     * @param typeEnum 交账类型 code
     * @return 领域预览查询
     */
    @Override
    public PreviewPharmacyCashierSettlementQuery toPreviewQuery(
        PreviewOutpatientPharmacyDailySettlementRequest request,
        String typeEnum) {
        PreviewPharmacyCashierSettlementQuery query = new PreviewPharmacyCashierSettlementQuery();
        query.setTypeEnum(typeEnum);
        query.setLocationId(request.getLocationId());
        query.setDateFrom(request.getDateFrom());
        query.setDateTo(request.getDateTo());
        // 发药人软筛透传（空=不按人过滤）
        query.setPractitionerIds(request.getPractitionerIds());
        return query;
    }

    /**
     * 提交请求转领域命令（IF-OP-PDC-03）。
     *
     * <p>头表 {@code total_income} 由 AppService 传入勾选发药<strong>售价合计</strong>（与主区大类售价之和同口径）。</p>
     *
     * @param request              提交请求
     * @param typeEnum             交账类型 code
     * @param submitterId          提交人
     * @param operatorId           操作人
     * @param selectedDispenseIds  勾选发药行
     * @param practitionerIds      发药人快照
     * @param paymentCount         挂载条数
     * @param totalIncome          售价合计（与大类售价之和同口径）
     * @return 提交命令
     */
    @Override
    public SubmitCashierSettlementCommand toSubmitCommand(
        SubmitOutpatientPharmacyDailySettlementRequest request,
        String typeEnum,
        Long submitterId,
        Long operatorId,
        List<Long> selectedDispenseIds,
        String practitionerIds,
        Integer paymentCount,
        BigDecimal totalIncome) {
        SubmitCashierSettlementCommand command = new SubmitCashierSettlementCommand();
        command.setTypeEnum(typeEnum);
        command.setLocationId(request.getLocationId());
        command.setDateFrom(request.getDateFrom());
        command.setDateTo(request.getDateTo());
        command.setSubmitterId(submitterId);
        command.setOperatorId(operatorId);
        command.setSelectedSourceIds(selectedDispenseIds);
        command.setSelectedPaymentIds(selectedDispenseIds);
        command.setPractitionerIds(practitionerIds);
        command.setPaymentCount(paymentCount);
        command.setTotalIncome(totalIncome);
        return command;
    }

    /**
     * 装配提交响应。
     *
     * @param settlementId 交账头主键
     * @param busNo        单号
     * @param paymentCount 挂载条数
     * @return 提交响应
     */
    @Override
    public SubmitOutpatientPharmacyDailySettlementResponse toSubmitResponse(
        Long settlementId,
        String busNo,
        Integer paymentCount) {
        SubmitOutpatientPharmacyDailySettlementResponse response = new SubmitOutpatientPharmacyDailySettlementResponse();
        response.setSettlementId(settlementId);
        response.setBusNo(busNo);
        response.setPaymentCount(paymentCount);
        return response;
    }
}
