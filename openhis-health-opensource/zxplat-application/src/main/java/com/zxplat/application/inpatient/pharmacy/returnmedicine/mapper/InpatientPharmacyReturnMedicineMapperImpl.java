/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.returnmedicine.mapper;

import com.zxplat.application.shared.pagination.PageQueryBinder;
import com.zxplat.application.inpatient.pharmacy.returnmedicine.dto.InpatientReturnMedicineDetailRowDto;
import com.zxplat.application.inpatient.pharmacy.returnmedicine.dto.InpatientReturnPatientRowDto;
import com.zxplat.application.inpatient.pharmacy.returnmedicine.request.GetInpatientReturnMedicineDetailRequest;
import com.zxplat.application.inpatient.pharmacy.returnmedicine.request.PageInpatientReturnPatientRequest;
import com.zxplat.application.inpatient.pharmacy.returnmedicine.response.LoadInpatientReturnMedicineInitResponse;
import com.zxplat.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.zxplat.domain.medication.medicationdispense.query.InpatientPharmacyReturnEncounterPageQuery;
import com.zxplat.domain.medication.medicationdispense.query.ReturnMedicineDetailQuery;
import com.zxplat.domain.medication.medicationdispense.queries.model.ReturnMedicineDetailReadRow;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.enums.DispenseStatus;
import com.zxplat.domain.share.utils.AdministrativeGenderDisplayUtil;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link InpatientPharmacyReturnMedicineMapper} 的手写实现：逐字段搬运与展示脱敏。
 */
@Component
public class InpatientPharmacyReturnMedicineMapperImpl implements InpatientPharmacyReturnMedicineMapper {

    /**
     * 装配初始化响应。
     *
     * @param pharmacyOptions     药房下拉
     * @param wardOptions         病区下拉
     * @param refundStatusOptions 退药状态下拉
     * @return 初始化响应
     */
    @Override
    public LoadInpatientReturnMedicineInitResponse toLoadInitResponse(
        List<OptionDto> pharmacyOptions,
        List<OptionDto> wardOptions,
        List<OptionDto> refundStatusOptions) {
        LoadInpatientReturnMedicineInitResponse response = new LoadInpatientReturnMedicineInitResponse();
        response.setPharmacyOrganizationOptions(pharmacyOptions);
        response.setWardOptions(wardOptions);
        response.setRefundStatusOptions(refundStatusOptions);
        return response;
    }

    /**
     * 装配退药状态下拉项（固定 PENDING_REFUND/REFUNDED 子集）。
     *
     * @return 非空列表
     */
    @Override
    public List<OptionDto> mapToRefundStatusOptions() {
        List<OptionDto> list = new ArrayList<>();
        OptionDto pending = new OptionDto(0L, DispenseStatus.PENDING_REFUND.getCode(), DispenseStatus.PENDING_REFUND.getDisplay());
        list.add(pending);
        OptionDto refunded = new OptionDto(1L, DispenseStatus.REFUNDED.getCode(), DispenseStatus.REFUNDED.getDisplay());
        list.add(refunded);
        return list;
    }

    /**
     * 将分页请求映射为住院退药患者队列查询。
     *
     * @param request 应用请求；调用方保证非空
     * @return 领域查询入参
     */
    @Override
    public InpatientPharmacyReturnEncounterPageQuery toPageQuery(PageInpatientReturnPatientRequest request) {
        InpatientPharmacyReturnEncounterPageQuery query = new InpatientPharmacyReturnEncounterPageQuery();
        PageQueryBinder.bind(request, query);
        query.setWardId(request.getWardId());
        return query;
    }

    /**
     * 将住院就诊上下文与聚合退药态映射为队列行 DTO。
     *
     * @param ctx            住院就诊上下文；调用方保证非空
     * @param refundEnumCode 聚合退药态 code
     * @return 队列行 DTO
     */
    @Override
    public InpatientReturnPatientRowDto toReturnPatientRow(InpatientEncounterContextQueryModel ctx, String refundEnumCode) {
        if (ctx == null) {
            return null;
        }
        InpatientReturnPatientRowDto dto = new InpatientReturnPatientRowDto();
        dto.setEncounterId(ctx.getEncounterId());
        dto.setPatientName(StringUtils.defaultString(ctx.getPatientName()));
        dto.setGenderEnum(ctx.getGenderEnum());
        dto.setGenderEnumText(AdministrativeGenderDisplayUtil.toDisplayText(ctx.getGenderEnum()));
        dto.setAge(StringUtils.defaultString(ctx.getAgeText()));
        dto.setIdCard(GeneralSharedConstants.EMPTY_STRING);
        dto.setWardName(StringUtils.defaultString(ctx.getWardName()));
        dto.setBedName(StringUtils.defaultString(ctx.getBedName()));
        dto.setEncounterNo(StringUtils.defaultString(ctx.getEncounterBusNo()));
        String agg = refundEnumCode;
        if (agg == null) {
            agg = GeneralSharedConstants.EMPTY_STRING;
        }
        dto.setRefundEnum(agg);
        dto.setRefundEnumText(this.toRefundStatusText(agg));
        return dto;
    }

    /**
     * 将明细请求映射为领域查询。
     *
     * @param request 应用请求；调用方保证非空
     * @return 领域查询入参
     */
    @Override
    public ReturnMedicineDetailQuery toDetailQuery(GetInpatientReturnMedicineDetailRequest request) {
        ReturnMedicineDetailQuery query = new ReturnMedicineDetailQuery();
        query.setEncounterId(request.getEncounterId());
        query.setRefundStatus(request.getRefundStatus());
        return query;
    }

    /**
     * 将退药明细读行映射为对外 DTO 列表。
     *
     * @param rows 读侧装配行；允许为空
     * @return DTO 列表；空入参返回空列表
     */
    @Override
    public List<InpatientReturnMedicineDetailRowDto> toDetailRows(List<ReturnMedicineDetailReadRow> rows) {
        List<InpatientReturnMedicineDetailRowDto> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (ReturnMedicineDetailReadRow row : rows) {
            if (row == null) {
                continue;
            }
            InpatientReturnMedicineDetailRowDto dto = new InpatientReturnMedicineDetailRowDto();
            dto.setDispenseId(row.getDispenseId());
            dto.setRequestId(row.getRequestId());
            dto.setItemId(row.getItemId());
            dto.setItemName(row.getItemName());
            dto.setLotNumber(row.getLotNumber());
            dto.setQuantity(row.getQuantity());
            dto.setUnitCode(row.getUnitCode());
            dto.setUnitCodeText(row.getUnitCodeText());
            dto.setDoctorName(row.getDoctorName());
            dto.setRefundEnum(row.getRefundEnum());
            dto.setRefundEnumText(row.getRefundEnumText());
            dto.setServiceTable(row.getServiceTable());
            dto.setTraceNo(row.getTraceNo());
            dto.setBusNo(row.getBusNo());
            boolean selectable = DispenseStatus.PENDING_REFUND.getCode().equals(row.getRefundEnum());
            dto.setSelectable(selectable);
            list.add(dto);
        }
        return list;
    }

    /**
     * 退药聚合态 code 转展示文案。
     *
     * @param code 持久化 code
     * @return 展示文案
     */
    private String toRefundStatusText(String code) {
        DispenseStatus st = EnumUtil.fromCode(DispenseStatus.class, code);
        if (st == null) {
            if (code == null) {
                return GeneralSharedConstants.EMPTY_STRING;
            }
            return code;
        }
        return st.getDisplay();
    }
}
