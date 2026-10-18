/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.approval.mapper;

import com.zxplat.application.finance.approval.dto.FinanceApprovalDocumentRowDto;
import com.zxplat.application.finance.approval.request.PageFinanceApprovalDocumentRequest;
import com.zxplat.application.finance.approval.response.InitFinanceApprovalResponse;
import com.zxplat.domain.custom.priceadjustment.entity.PriceAdjustment;
import com.zxplat.domain.custom.priceadjustment.queries.PriceAdjustmentDocumentListQuery;
import com.zxplat.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.zxplat.framework.core.application.dto.OptionDto;

import java.util.List;

/**
 * 财务审批 Mapper（AS-FIN-APR-001-SDD §2.4 / §3.2～3.3）：Request↔列表 Query、行 DTO 与 init 选项装配。
 *
 * <p>禁止承载业务流程判断、金额汇总与跨聚合 enrich。</p>
 */
public interface FinanceApprovalMapper {

    /**
     * 将分页请求映射为供应单据列表 Query（不含 typeEnums 默认收窄；由 Loader 按分支写入）。
     *
     * @param request 分页请求；调用方保证非空
     * @return 供应列表 Query
     */
    SupplyRequestDocumentListQuery toSupplyDocumentListQuery(PageFinanceApprovalDocumentRequest request);

    /**
     * 将分页请求映射为调价单据列表 Query（状态码由调用方先映射为调价域）。
     *
     * @param request          分页请求；调用方保证非空
     * @param mappedStatusEnum 已映射的调价 status；可空表示不限
     * @return 调价列表 Query
     */
    PriceAdjustmentDocumentListQuery toPriceAdjustmentDocumentListQuery(
        PageFinanceApprovalDocumentRequest request,
        String mappedStatusEnum);

    /**
     * 供应头行 → 列表行 DTO（金额与展示名由 {@code fillAmounts}/enrich 后续填充）。
     *
     * @param head 同号头行；null 时返回 null
     * @return 列表行
     */
    FinanceApprovalDocumentRowDto toRowDto(SupplyRequestModel head);

    /**
     * 调价头行 → 列表行 DTO（金额与展示名由 {@code fillAmounts}/enrich 后续填充）。
     *
     * @param head 同号任一行头字段；null 时返回 null
     * @return 列表行
     */
    FinanceApprovalDocumentRowDto toRowDto(PriceAdjustment head);

    /**
     * 装配初始化响应（IF-APR-01 Asm）。
     *
     * @param typeOptions      类型选项
     * @param statusOptions    状态选项
     * @param locationOptions  库房选项
     * @param supplierOptions  供应商选项
     * @param itemTableOptions 品类选项
     * @param applicantOptions 制单人选项（可空列表）
     * @param approverOptions  审批人选项（可空列表）
     * @return init 响应
     */
    InitFinanceApprovalResponse toInitResponse(
        List<OptionDto> typeOptions,
        List<OptionDto> statusOptions,
        List<OptionDto> locationOptions,
        List<OptionDto> supplierOptions,
        List<OptionDto> itemTableOptions,
        List<OptionDto> applicantOptions,
        List<OptionDto> approverOptions);
}
