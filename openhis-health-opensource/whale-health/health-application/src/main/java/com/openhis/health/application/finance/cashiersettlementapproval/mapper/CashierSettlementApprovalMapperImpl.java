/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.cashiersettlementapproval.mapper;

import com.openhis.health.application.finance.cashiersettlementapproval.dto.CashierSettlementApprovalHeaderDto;
import com.openhis.health.application.finance.cashiersettlementapproval.dto.CashierSettlementApprovalListItemDto;
import com.openhis.health.application.finance.cashiersettlementapproval.request.PageCashierSettlementApprovalsRequest;
import com.openhis.health.application.finance.cashiersettlementapproval.response.LoadCashierSettlementApprovalDetailResponse;
import com.openhis.health.application.finance.cashiersettlementapproval.response.LoadCashierSettlementApprovalPageInitResponse;
import com.openhis.health.application.finance.cashiersettlementapproval.support.CashierSettlementApprovalTypeSupport;
import com.openhis.health.domain.financial.cashiersettlement.queries.PageCashierSettlementApprovalQuery;
import com.openhis.health.domain.financial.cashiersettlement.queries.model.CashierSettlementApprovalListItemQueryModel;
import com.openhis.health.domain.financial.cashiersettlement.queries.model.CashierSettlementHeaderQueryModel;
import com.openhis.health.domain.share.enums.CashierSettlementStatus;
import com.openhis.health.domain.share.enums.CashierSettlementType;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 交账审批 Mapper 实现：纯字段搬运与枚举展示名投影。
 */
@Component
@RequiredArgsConstructor
public class CashierSettlementApprovalMapperImpl implements CashierSettlementApprovalMapper {

    /** 类型/状态选项与入参校验。 */
    private final CashierSettlementApprovalTypeSupport typeSupport;

    /**
     * 装配初始化响应（IF-CS-APR-01 Asm）。
     *
     * @param categoryOptions 大类选项
     * @param typeOptions     类型选项
     * @param statusOptions   状态选项
     * @param pharmacyOptions 药房下拉
     * @return init 响应
     */
    @Override
    public LoadCashierSettlementApprovalPageInitResponse toPageInitResponse(
        List<OptionDto> categoryOptions,
        List<OptionDto> typeOptions,
        List<OptionDto> statusOptions,
        List<OptionDto> pharmacyOptions) {
        LoadCashierSettlementApprovalPageInitResponse response = new LoadCashierSettlementApprovalPageInitResponse();
        response.setCategoryOptions(this.safeOptionList(categoryOptions));
        response.setTypeOptions(this.safeOptionList(typeOptions));
        response.setStatusOptions(this.safeOptionList(statusOptions));
        response.setPharmacyOptions(this.safeOptionList(pharmacyOptions));
        return response;
    }

    /**
     * 分页请求 → 领域审批列表 Query。
     *
     * @param request 分页请求；调用方保证非空
     * @return 领域 Query
     */
    @Override
    public PageCashierSettlementApprovalQuery toApprovalPageQuery(PageCashierSettlementApprovalsRequest request) {
        PageCashierSettlementApprovalQuery query = new PageCashierSettlementApprovalQuery();
        query.setSubmittedFrom(request.getSubmittedFrom());
        query.setSubmittedTo(request.getSubmittedTo());
        query.setOrgId(request.getOrgId());
        query.setLocationId(request.getLocationId());
        if (!StringUtils.isEmpty(request.getBusNoKeyword())) {
            query.setBusNoKeyword(request.getBusNoKeyword().trim());
        }
        query.setTypeEnums(this.resolveTypeEnums(request));
        query.setStatusEnums(this.resolveStatusEnums(request));
        return query;
    }

    /**
     * 审批列表读模型 → 列表行 DTO。
     *
     * @param model 读模型；null 时返回 null
     * @return 列表行 DTO
     */
    @Override
    public CashierSettlementApprovalListItemDto toListItemDto(CashierSettlementApprovalListItemQueryModel model) {
        if (model == null) {
            return null;
        }
        CashierSettlementApprovalListItemDto dto = new CashierSettlementApprovalListItemDto();
        dto.setSettlementId(model.getSettlementId());
        dto.setBusNo(model.getBusNo());
        dto.setTypeEnum(model.getTypeEnum());
        dto.setTypeEnumText(this.resolveTypeDisplay(model.getTypeEnum()));
        dto.setStatusEnum(model.getStatusEnum());
        dto.setStatusEnumText(this.resolveStatusDisplay(model.getStatusEnum()));
        dto.setOrgId(model.getOrgId());
        dto.setLocationId(model.getLocationId());
        dto.setDateFrom(model.getDateFrom());
        dto.setDateTo(model.getDateTo());
        dto.setPaymentCount(model.getPaymentCount());
        dto.setTotalIncome(model.getTotalIncome());
        dto.setSubmitterId(model.getSubmitterId());
        dto.setSubmittedAt(model.getSubmittedAt());
        dto.setApproverId(model.getApproverId());
        dto.setApprovedAt(model.getApprovedAt());
        return dto;
    }

    /**
     * 头表读模型 → 详情头 DTO。
     *
     * @param model 头表读模型；null 时返回 null
     * @return 详情头 DTO
     */
    @Override
    public CashierSettlementApprovalHeaderDto toHeaderDto(CashierSettlementHeaderQueryModel model) {
        if (model == null) {
            return null;
        }
        CashierSettlementApprovalHeaderDto dto = new CashierSettlementApprovalHeaderDto();
        dto.setSettlementId(model.getSettlementId());
        dto.setBusNo(model.getBusNo());
        dto.setTypeEnum(model.getTypeEnum());
        dto.setTypeEnumText(this.resolveTypeDisplay(model.getTypeEnum()));
        dto.setStatusEnum(model.getStatusEnum());
        dto.setStatusEnumText(this.resolveStatusDisplay(model.getStatusEnum()));
        dto.setOrgId(model.getOrgId());
        dto.setLocationId(model.getLocationId());
        dto.setDateFrom(model.getDateFrom());
        dto.setDateTo(model.getDateTo());
        dto.setPaymentCount(model.getPaymentCount());
        dto.setTotalIncome(model.getTotalIncome());
        dto.setPractitionerIds(model.getPractitionerIds());
        dto.setSubmitterId(model.getSubmitterId());
        dto.setSubmittedAt(model.getSubmittedAt());
        dto.setApproverId(model.getApproverId());
        dto.setApprovedAt(model.getApprovedAt());
        return dto;
    }

    /**
     * 装配详情响应（v1：头表 + 挂载笔数 + 现算说明）。
     *
     * @param header              详情头 DTO
     * @param mountedSourceCount  挂载笔数
     * @param detailRecomputeNote 明细现算说明
     * @return 详情响应
     */
    @Override
    public LoadCashierSettlementApprovalDetailResponse toDetailResponse(
        CashierSettlementApprovalHeaderDto header,
        Integer mountedSourceCount,
        String detailRecomputeNote) {
        LoadCashierSettlementApprovalDetailResponse response = new LoadCashierSettlementApprovalDetailResponse();
        response.setHeader(header);
        response.setMountedSourceCount(mountedSourceCount);
        response.setDetailBodyRecomputeNote(detailRecomputeNote);
        return response;
    }

    /**
     * 解析列表 typeEnums：显式集合 &gt; 单码 &gt; 大类展开。
     *
     * @param request 分页请求
     * @return 类型 code 列表；可 null 表示不限
     */
    private List<String> resolveTypeEnums(PageCashierSettlementApprovalsRequest request) {
        if (request.getTypeEnums() != null && !request.getTypeEnums().isEmpty()) {
            return request.getTypeEnums();
        }
        if (!StringUtils.isEmpty(request.getTypeEnum())) {
            List<String> single = new ArrayList<>();
            single.add(request.getTypeEnum().trim());
            return single;
        }
        return this.typeSupport.expandCategoryToTypeEnums(request.getCategoryEnum());
    }

    /**
     * 解析列表 statusEnums：显式集合优先，否则单码包装为列表。
     *
     * @param request 分页请求
     * @return 状态 code 列表；可 null 表示不限
     */
    private List<String> resolveStatusEnums(PageCashierSettlementApprovalsRequest request) {
        if (request.getStatusEnums() != null && !request.getStatusEnums().isEmpty()) {
            return request.getStatusEnums();
        }
        if (!StringUtils.isEmpty(request.getStatusEnum())) {
            List<String> single = new ArrayList<>();
            single.add(request.getStatusEnum().trim());
            return single;
        }
        return null;
    }

    /**
     * 类型 code → 展示名；未知 code 回落原值。
     *
     * @param typeEnum 类型 code
     * @return 展示名
     */
    private String resolveTypeDisplay(String typeEnum) {
        if (StringUtils.isEmpty(typeEnum)) {
            return typeEnum;
        }
        for (CashierSettlementType type : CashierSettlementType.values()) {
            if (type.getCode().equals(typeEnum.trim())) {
                return type.getDisplay();
            }
        }
        return typeEnum;
    }

    /**
     * 状态 code → 展示名；未知 code 回落原值。
     *
     * @param statusEnum 状态 code
     * @return 展示名
     */
    private String resolveStatusDisplay(String statusEnum) {
        if (StringUtils.isEmpty(statusEnum)) {
            return statusEnum;
        }
        for (CashierSettlementStatus status : CashierSettlementStatus.values()) {
            if (status.getCode().equals(statusEnum.trim())) {
                return status.getDisplay();
            }
        }
        return statusEnum;
    }

    /**
     * Option 列表空安全拷贝。
     *
     * @param options 源列表
     * @return 非 null 列表
     */
    private List<OptionDto> safeOptionList(List<OptionDto> options) {
        if (options == null) {
            return new ArrayList<>();
        }
        return options;
    }
}
