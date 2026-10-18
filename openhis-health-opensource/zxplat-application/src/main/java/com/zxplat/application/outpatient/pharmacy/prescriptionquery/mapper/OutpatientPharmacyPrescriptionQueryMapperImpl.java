/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.prescriptionquery.mapper;

import com.zxplat.application.outpatient.pharmacy.prescriptionquery.dto.OutpatientPharmacyPrescriptionDrugLineDto;
import com.zxplat.application.outpatient.pharmacy.prescriptionquery.request.PageOutpatientPharmacyPrescriptionQueryRequest;
import com.zxplat.application.outpatient.pharmacy.prescriptionquery.response.LoadOutpatientPharmacyPrescriptionQueryPageInitResponse;
import com.zxplat.application.outpatient.pharmacy.prescriptionquery.response.PageOutpatientPharmacyPrescriptionQueryResponse;
import com.zxplat.application.outpatient.pharmacy.prescriptionquery.support.OutpatientPharmacyPrescriptionQuerySupport;
import com.zxplat.application.shared.pagination.PageQueryBinder;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.domain.medication.medicationrequest.query.OutpatientSignedPrescriptionDrugLinePageQuery;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.OutpatientPharmacyPrescriptionQueryConstants;
import com.zxplat.domain.share.constants.PrescriptionSplitConstants;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * {@link OutpatientPharmacyPrescriptionQueryMapper} 实现：仅做 Request/Query/Response 字段搬运，
 * 不承载跨聚合预解析或方级业务判定。
 */
@Component
public class OutpatientPharmacyPrescriptionQueryMapperImpl implements OutpatientPharmacyPrescriptionQueryMapper {

    /**
     * 装配页初始化响应（接口方法全文说明复制于此，禁 {@code inheritDoc}）。
     *
     * @param defaultFrom                 默认开方日起
     * @param defaultTo                   默认开方日止
     * @param chargeStatusOptions         收费状态下拉
     * @param dispenseStatusOptions       发药状态下拉
     * @param rxTypeOptions               处方类别下拉
     * @param medTypeOptions              医保类别下拉
     * @param contractCategoryOptions     费用性质分类下拉
     * @param infusionFilterOptions       含输液三态
     * @param organizationOptions         开单科室下拉
     * @param reviewStatusOptions         点评状态下拉
     * @param reasonableOptions           是否合理下拉
     * @param medicationCategoryOptions   药品类型下拉（药典）
     * @return 初始化响应
     */
    @Override
    public LoadOutpatientPharmacyPrescriptionQueryPageInitResponse toLoadPageInitResponse(
        String defaultFrom,
        String defaultTo,
        List<OptionDto> chargeStatusOptions,
        List<OptionDto> dispenseStatusOptions,
        List<OptionDto> rxTypeOptions,
        List<OptionDto> medTypeOptions,
        List<OptionDto> contractCategoryOptions,
        List<OptionDto> infusionFilterOptions,
        List<OptionDto> organizationOptions,
        List<OptionDto> reviewStatusOptions,
        List<OptionDto> reasonableOptions,
        List<OptionDto> medicationCategoryOptions) {
        LoadOutpatientPharmacyPrescriptionQueryPageInitResponse response =
            new LoadOutpatientPharmacyPrescriptionQueryPageInitResponse();
        response.setDefaultOpenedDateFrom(defaultFrom);
        response.setDefaultOpenedDateTo(defaultTo);
        response.setChargeStatusOptions(chargeStatusOptions);
        response.setDispenseStatusOptions(dispenseStatusOptions);
        response.setRxTypeOptions(rxTypeOptions);
        response.setMedTypeOptions(medTypeOptions);
        response.setContractCategoryOptions(contractCategoryOptions);
        response.setInfusionFilterOptions(infusionFilterOptions);
        response.setOrganizationOptions(organizationOptions);
        response.setReviewStatusOptions(reviewStatusOptions);
        response.setReasonableOptions(reasonableOptions);
        response.setMedicationCategoryOptions(medicationCategoryOptions);
        return response;
    }

    /**
     * 分页请求 + 患者范围 → 领域分页查询入参（不含收费/发药/注射/合同 resolved，由 AppService 续写）。
     *
     * @param request      分页请求；调用方保证非空
     * @param patientScope 患者关键字预解析结果；可为 null
     * @return 领域查询入参
     */
    @Override
    public OutpatientSignedPrescriptionDrugLinePageQuery toPageQuery(
        PageOutpatientPharmacyPrescriptionQueryRequest request,
        OutpatientPharmacyPrescriptionQuerySupport.PatientScope patientScope) {
        OutpatientSignedPrescriptionDrugLinePageQuery query = new OutpatientSignedPrescriptionDrugLinePageQuery();
        PageQueryBinder.bind(request, query);
        // 开立日闭区间：日起 00:00:00 ～ 日止 23:59:59.999999999（BR-OPQ-003）
        if (StringUtils.isNotEmpty(request.getOpenedDateFrom())) {
            LocalDate from = LocalDate.parse(
                request.getOpenedDateFrom().trim(),
                GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            query.setStartDate(from.atStartOfDay());
        }
        if (StringUtils.isNotEmpty(request.getOpenedDateTo())) {
            LocalDate to = LocalDate.parse(
                request.getOpenedDateTo().trim(),
                GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            query.setEndDate(to.atTime(LocalTime.MAX));
        }
        query.setRxTypeCode(PrescriptionSplitConstants.normalizeRxTypeCode(trimToNull(request.getRxTypeCode())));
        // 详情整方：处方号精确匹配（空则不限）
        query.setPrescriptionNo(trimToNull(request.getPrescriptionNo()));
        // 开单科室：本聚合 org_id 等值；空则不限
        query.setOrderingDepartmentId(request.getOrderingDepartmentId());
        String infusionFilter = trimToNull(request.getInfusionFilterCode());
        // 空含输液筛回落 ALL，与领域空视为不限口径一致
        if (infusionFilter == null) {
            infusionFilter = OutpatientPharmacyPrescriptionQueryConstants.INFUSION_FILTER_ALL;
        }
        query.setInfusionFilterCode(infusionFilter);
        if (patientScope != null) {
            query.setResolvedEncounterIds(patientScope.getResolvedEncounterIds());
        }
        return query;
    }

    /**
     * 装配分页响应（含筛选窗去重汇总）。
     *
     * @param page               领域分页壳
     * @param rows               平铺行
     * @param prescriptionCount  总处方量（处方号去重）
     * @param visitPatientCount  就诊人数（就诊主键去重）
     * @return 分页响应（{@code totalCount} 对应 AS-SDD {@code total}）
     */
    @Override
    public PageOutpatientPharmacyPrescriptionQueryResponse toPageResponse(
        Page<?> page,
        List<OutpatientPharmacyPrescriptionDrugLineDto> rows,
        Integer prescriptionCount,
        Integer visitPatientCount) {
        PageOutpatientPharmacyPrescriptionQueryResponse response =
            PagedResultBuilder.build(new PageOutpatientPharmacyPrescriptionQueryResponse(), rows, page);
        response.setPrescriptionCount(prescriptionCount);
        response.setVisitPatientCount(visitPatientCount);
        return response;
    }
}
