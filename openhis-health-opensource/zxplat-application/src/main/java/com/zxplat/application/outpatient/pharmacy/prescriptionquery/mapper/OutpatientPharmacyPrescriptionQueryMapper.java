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
import com.zxplat.domain.medication.medicationrequest.query.OutpatientSignedPrescriptionDrugLinePageQuery;
import com.zxplat.framework.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 门诊处方查询 Request/Response 与领域 Query 映射（AS-OP-PHA-005）。
 *
 * <p>禁止承载业务流程判断；跨聚合预解析结果由 AppService 写入 Query 后再查询。</p>
 */
public interface OutpatientPharmacyPrescriptionQueryMapper {

    /**
     * 装配页初始化响应。
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
    LoadOutpatientPharmacyPrescriptionQueryPageInitResponse toLoadPageInitResponse(
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
        List<OptionDto> medicationCategoryOptions);

    /**
     * 分页请求 + 患者范围 → 领域分页查询入参（不含收费/发药/注射/合同 resolved，由 AppService 续写）。
     *
     * @param request      分页请求；调用方保证非空
     * @param patientScope 患者关键字预解析结果；可为 null
     * @return 领域查询入参
     */
    OutpatientSignedPrescriptionDrugLinePageQuery toPageQuery(
        PageOutpatientPharmacyPrescriptionQueryRequest request,
        OutpatientPharmacyPrescriptionQuerySupport.PatientScope patientScope);

    /**
     * 装配分页响应（含筛选窗去重汇总）。
     *
     * @param page               领域分页壳
     * @param rows               平铺行
     * @param prescriptionCount  总处方量（处方号去重）
     * @param visitPatientCount  就诊人数（就诊主键去重）
     * @return 分页响应
     */
    PageOutpatientPharmacyPrescriptionQueryResponse toPageResponse(
        Page<?> page,
        List<OutpatientPharmacyPrescriptionDrugLineDto> rows,
        Integer prescriptionCount,
        Integer visitPatientCount);
}
