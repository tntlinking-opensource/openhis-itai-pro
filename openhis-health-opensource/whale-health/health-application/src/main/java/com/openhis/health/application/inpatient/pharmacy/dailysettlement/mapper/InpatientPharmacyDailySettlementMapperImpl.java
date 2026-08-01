/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.dailysettlement.mapper;

import com.openhis.health.application.inpatient.pharmacy.dailysettlement.request.PreviewInpatientPharmacyDailySettlementRequest;
import com.openhis.health.application.inpatient.pharmacy.dailysettlement.request.SubmitInpatientPharmacyDailySettlementRequest;
import com.openhis.health.application.inpatient.pharmacy.dailysettlement.response.LoadInpatientPharmacyDailySettlementPageInitResponse;
import com.openhis.health.application.inpatient.pharmacy.dailysettlement.response.SubmitInpatientPharmacyDailySettlementResponse;
import com.openhis.health.domain.financial.cashiersettlement.command.SubmitCashierSettlementCommand;
import com.openhis.health.domain.medication.medicationdispense.queries.PreviewPharmacyCashierSettlementQuery;
import com.openhis.whale.core.application.dto.OptionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@link InpatientPharmacyDailySettlementMapper} 实现（AS-IP-PHA-006）。
 *
 * <p>职责：Request/Command/Response 字段映射；禁止承载 Gate 与写编排。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientPharmacyDailySettlementMapperImpl implements InpatientPharmacyDailySettlementMapper {

    /**
     * 装配页初始化响应（IF-IP-PDC-01）。
     *
     * @param defaultDateFrom  默认窗起
     * @param defaultDateTo    默认窗止
     * @param pharmacyOptions      药房下拉
     * @param practitionerOptions  药师岗发药人下拉
     * @return 初始化响应
     */
    @Override
    public LoadInpatientPharmacyDailySettlementPageInitResponse toLoadPageInitResponse(
        String defaultDateFrom,
        String defaultDateTo,
        List<OptionDto> pharmacyOptions,
        List<OptionDto> practitionerOptions) {
        LoadInpatientPharmacyDailySettlementPageInitResponse response =
            new LoadInpatientPharmacyDailySettlementPageInitResponse();
        response.setDefaultDateFrom(defaultDateFrom);
        response.setDefaultDateTo(defaultDateTo);
        response.setPharmacyOptions(pharmacyOptions);
        response.setPractitionerOptions(practitionerOptions);
        return response;
    }

    /**
     * 预览请求转领域查询（IF-IP-PDC-02）。
     *
     * @param request  预览请求
     * @param typeEnum 交账类型 code
     * @return 领域预览查询
     */
    @Override
    public PreviewPharmacyCashierSettlementQuery toPreviewQuery(
        PreviewInpatientPharmacyDailySettlementRequest request,
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
     * 提交请求转领域命令（IF-IP-PDC-03）。
     *
     * <p>头表 {@code total_income} 由 AppService 传入挂载发药关联 payment 净收入（DD §3.2.1）。</p>
     *
     * @param request              提交请求
     * @param typeEnum             交账类型 code
     * @param submitterId          提交人
     * @param operatorId           操作人
     * @param selectedDispenseIds  勾选发药行
     * @param practitionerIds      发药人快照
     * @param paymentCount         挂载条数
     * @param totalIncome          列表金额快照
     * @return 提交命令
     */
    @Override
    public SubmitCashierSettlementCommand toSubmitCommand(
        SubmitInpatientPharmacyDailySettlementRequest request,
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
    public SubmitInpatientPharmacyDailySettlementResponse toSubmitResponse(
        Long settlementId,
        String busNo,
        Integer paymentCount) {
        SubmitInpatientPharmacyDailySettlementResponse response = new SubmitInpatientPharmacyDailySettlementResponse();
        response.setSettlementId(settlementId);
        response.setBusNo(busNo);
        response.setPaymentCount(paymentCount);
        return response;
    }
}
