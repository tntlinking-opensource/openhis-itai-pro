/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.cashiersettlementapproval.mapper;

import com.zxplat.application.finance.cashiersettlementapproval.dto.CashierSettlementApprovalHeaderDto;
import com.zxplat.application.finance.cashiersettlementapproval.dto.CashierSettlementApprovalListItemDto;
import com.zxplat.application.finance.cashiersettlementapproval.request.PageCashierSettlementApprovalsRequest;
import com.zxplat.application.finance.cashiersettlementapproval.response.LoadCashierSettlementApprovalDetailResponse;
import com.zxplat.application.finance.cashiersettlementapproval.response.LoadCashierSettlementApprovalPageInitResponse;
import com.zxplat.domain.financial.cashiersettlement.queries.PageCashierSettlementApprovalQuery;
import com.zxplat.domain.financial.cashiersettlement.queries.model.CashierSettlementApprovalListItemQueryModel;
import com.zxplat.domain.financial.cashiersettlement.queries.model.CashierSettlementHeaderQueryModel;
import com.zxplat.framework.core.application.dto.OptionDto;

import java.util.List;

/**
 * 交账审批 Mapper（AS-FIN-CS-001）：Request↔Query、读模型↔DTO 装配。
 *
 * <p>禁止承载业务流程判断与写库。</p>
 */
public interface CashierSettlementApprovalMapper {

    /**
     * 装配初始化响应（IF-CS-APR-01 Asm）。
     *
     * @param categoryOptions 大类选项
     * @param typeOptions     类型选项
     * @param statusOptions   状态选项
     * @param pharmacyOptions 药房下拉
     * @return init 响应
     */
    LoadCashierSettlementApprovalPageInitResponse toPageInitResponse(
        List<OptionDto> categoryOptions,
        List<OptionDto> typeOptions,
        List<OptionDto> statusOptions,
        List<OptionDto> pharmacyOptions);

    /**
     * 分页请求 → 领域审批列表 Query。
     *
     * @param request 分页请求；调用方保证非空
     * @return 领域 Query
     */
    PageCashierSettlementApprovalQuery toApprovalPageQuery(PageCashierSettlementApprovalsRequest request);

    /**
     * 审批列表读模型 → 列表行 DTO。
     *
     * @param model 读模型；null 时返回 null
     * @return 列表行 DTO
     */
    CashierSettlementApprovalListItemDto toListItemDto(CashierSettlementApprovalListItemQueryModel model);

    /**
     * 头表读模型 → 详情头 DTO。
     *
     * @param model 头表读模型；null 时返回 null
     * @return 详情头 DTO
     */
    CashierSettlementApprovalHeaderDto toHeaderDto(CashierSettlementHeaderQueryModel model);

    /**
     * 装配详情响应（v1：头表 + 挂载笔数 + 现算说明）。
     *
     * @param header              详情头 DTO
     * @param mountedSourceCount  挂载笔数
     * @param detailRecomputeNote 明细现算说明
     * @return 详情响应
     */
    LoadCashierSettlementApprovalDetailResponse toDetailResponse(
        CashierSettlementApprovalHeaderDto header,
        Integer mountedSourceCount,
        String detailRecomputeNote);
}
