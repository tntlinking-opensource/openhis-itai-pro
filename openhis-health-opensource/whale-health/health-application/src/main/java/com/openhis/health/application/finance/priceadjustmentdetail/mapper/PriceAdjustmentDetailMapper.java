/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustmentdetail.mapper;

import com.openhis.health.application.finance.priceadjustment.dto.PriceAdjustmentLineDto;
import com.openhis.health.application.finance.priceadjustmentdetail.dto.PriceAdjustmentApprovedLineListRowDto;
import com.openhis.health.application.finance.priceadjustmentdetail.request.PagePriceAdjustmentApprovedLineRequest;
import com.openhis.health.application.finance.priceadjustmentdetail.response.GetPriceAdjustmentApprovedLineResponse;
import com.openhis.health.application.finance.priceadjustmentdetail.response.InitPriceAdjustmentDetailResponse;
import com.openhis.health.application.finance.priceadjustmentdetail.response.PagePriceAdjustmentApprovedLineResponse;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.custom.priceadjustment.queries.PriceAdjustmentApprovedLineListQuery;
import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentLineQueryModel;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 调价明细应用层映射：Request/Response/DTO 与领域 Query 之间的字段搬运。
 *
 * <p>禁止承载业务流程判断；编排逻辑在
 * {@link com.openhis.health.application.finance.priceadjustmentdetail.service.PriceAdjustmentDetailAppService}。</p>
 */
public interface PriceAdjustmentDetailMapper {

    /**
     * 装配调价明细页初始化响应（IF-PRC-DTL-01）。
     *
     * @return 默认时间窗、Tab 选项与默认分页大小
     */
    InitPriceAdjustmentDetailResponse toInitPriceAdjustmentDetailResponse();

    /**
     * 列表请求转已生效主行领域 Query（IF-PRC-DTL-02）。
     *
     * @param request 分页请求；调用方保证非空
     * @param itemIds 关键字预解析 item_id；{@code null} 表示不按项目收窄
     * @return 领域分页 Query
     */
    PriceAdjustmentApprovedLineListQuery toPriceAdjustmentApprovedLineListQuery(
        PagePriceAdjustmentApprovedLineRequest request,
        List<Long> itemIds);

    /**
     * 主行读模型转列表行 DTO（IF-PRC-DTL-02）。
     *
     * @param line           主行读模型；null 时返回 null
     * @param product        目录 enrich；可 null
     * @param practitioners  制单/审批人映射；可 null
     * @return 列表行 DTO（不含调前/调后价）
     */
    PriceAdjustmentApprovedLineListRowDto toPriceAdjustmentApprovedLineListRowDto(
        PriceAdjustmentLineQueryModel line,
        ProductDefinitionDisplayDto product,
        Map<Long, Practitioner> practitioners);

    /**
     * 装配列表分页响应（IF-PRC-DTL-02）。
     *
     * @param rows        当前页行
     * @param pageNo      页码（1-based）
     * @param pageSize    页大小
     * @param totalCount  总条数
     * @return 分页响应
     */
    PagePriceAdjustmentApprovedLineResponse toPagePriceAdjustmentApprovedLineResponse(
        List<PriceAdjustmentApprovedLineListRowDto> rows,
        int pageNo,
        int pageSize,
        long totalCount);

    /**
     * 装配详情响应（IF-PRC-DTL-03）。
     *
     * @param line          主行+子表 DTO
     * @param totalImpact   行级盈亏合计
     * @param approveTime   生效时间
     * @param approverId    审批人
     * @param approverName  审批人展示名
     * @return 详情响应
     */
    GetPriceAdjustmentApprovedLineResponse toGetPriceAdjustmentApprovedLineResponse(
        PriceAdjustmentLineDto line,
        BigDecimal totalImpact,
        Date approveTime,
        Long approverId,
        String approverName);
}
