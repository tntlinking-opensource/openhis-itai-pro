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
import com.openhis.health.domain.custom.priceadjustment.support.PriceAdjustmentItemTableSupport;
import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.PriceAdjustmentDetailConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.AdjustmentReason;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * {@link PriceAdjustmentDetailMapper} 默认实现（AS-FIN-PRC-002 IF-PRC-DTL-01～03）。
 *
 * <p>逐字段映射 Request/Response 与领域 Query/读模型；禁止承载 Gate 与 enrich 判断。</p>
 */
@Component
public class PriceAdjustmentDetailMapperImpl implements PriceAdjustmentDetailMapper {

    /**
     * 装配调价明细页初始化响应（IF-PRC-DTL-01）。
     *
     * @return 默认时间窗、Tab 选项与默认分页大小
     */
    @Override
    public InitPriceAdjustmentDetailResponse toInitPriceAdjustmentDetailResponse() {
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.atTime(LocalTime.of(23, 59, 59));
        InitPriceAdjustmentDetailResponse response = new InitPriceAdjustmentDetailResponse();
        response.setDefaultApproveTimeFrom(dayStart.format(GeneralSharedConstants.DATE_TIME_FORMATTER));
        response.setDefaultApproveTimeTo(dayEnd.format(GeneralSharedConstants.DATE_TIME_FORMATTER));
        response.setItemTableTabOptions(this.buildItemTableTabOptions());
        response.setAdjustmentReasonOptions(this.buildAdjustmentReasonOptions());
        response.setDefaultPageSize(PriceAdjustmentDetailConstants.DEFAULT_PAGE_SIZE);
        return response;
    }

    /**
     * 列表请求转已生效主行领域 Query（IF-PRC-DTL-02）。
     *
     * @param request 分页请求；调用方保证非空
     * @param itemIds 关键字预解析 item_id；{@code null} 表示不按项目收窄
     * @return 领域分页 Query
     */
    @Override
    public PriceAdjustmentApprovedLineListQuery toPriceAdjustmentApprovedLineListQuery(
        PagePriceAdjustmentApprovedLineRequest request,
        List<Long> itemIds) {
        PriceAdjustmentApprovedLineListQuery query = new PriceAdjustmentApprovedLineListQuery();
        PageQueryBinder.bind(request, query);
        query.setStartDate(request.getApproveTimeFrom());
        query.setEndDate(request.getApproveTimeTo());
        query.setItemTable(trimToNull(request.getItemTable()));
        query.setItemIds(itemIds);
        query.setBusNo(trimToNull(request.getBusNo()));
        if (query.getPageNo() == null || query.getPageNo() < 1) {
            query.setPageNo(1);
        }
        if (query.getPageSize() == null || query.getPageSize() < 1) {
            query.setPageSize(PriceAdjustmentDetailConstants.DEFAULT_PAGE_SIZE);
        }
        return query;
    }

    /**
     * 主行读模型转列表行 DTO（IF-PRC-DTL-02）。
     *
     * @param line           主行读模型；null 时返回 null
     * @param product        目录 enrich；可 null
     * @param practitioners  制单/审批人映射；可 null
     * @return 列表行 DTO（不含调前/调后价）
     */
    @Override
    public PriceAdjustmentApprovedLineListRowDto toPriceAdjustmentApprovedLineListRowDto(
        PriceAdjustmentLineQueryModel line,
        ProductDefinitionDisplayDto product,
        Map<Long, Practitioner> practitioners) {
        if (line == null) {
            return null;
        }
        PriceAdjustmentApprovedLineListRowDto dto = new PriceAdjustmentApprovedLineListRowDto();
        dto.setId(line.getId());
        dto.setBusNo(line.getBusNo());
        dto.setApproveTime(line.getApproveTime());
        dto.setItemTable(line.getItemTable());
        dto.setItemTableText(this.resolveItemTableText(line.getItemTable()));
        dto.setItemId(line.getItemId());
        if (product != null) {
            dto.setItemName(product.getProductName());
            dto.setItemCode(product.getProductBusNo());
        }
        String adjustmentStyle = line.getAdjustmentStyle();
        if (StringUtils.isEmpty(adjustmentStyle)) {
            adjustmentStyle = PriceAdjustmentItemTableSupport.resolveAdjustmentStyle(line.getItemTable());
        }
        dto.setAdjustmentStyle(adjustmentStyle);
        dto.setAdjustmentStyleText(this.resolveAdjustmentStyleText(adjustmentStyle));
        dto.setAdjustmentReason(line.getAdjustmentReason());
        dto.setAdjustmentReasonText(this.resolveAdjustmentReasonText(line.getAdjustmentReason()));
        dto.setTotalImpact(line.getTotalImpact());
        dto.setOperatorId(line.getOperatorId());
        dto.setApproverId(line.getApproverId());
        dto.setOperatorName(this.resolvePractitionerName(practitioners, line.getOperatorId()));
        dto.setApproverName(this.resolvePractitionerName(practitioners, line.getApproverId()));
        return dto;
    }

    /**
     * 装配列表分页响应（IF-PRC-DTL-02）。
     *
     * @param rows        当前页行
     * @param pageNo      页码（1-based）
     * @param pageSize    页大小
     * @param totalCount  总条数
     * @return 分页响应
     */
    @Override
    public PagePriceAdjustmentApprovedLineResponse toPagePriceAdjustmentApprovedLineResponse(
        List<PriceAdjustmentApprovedLineListRowDto> rows,
        int pageNo,
        int pageSize,
        long totalCount) {
        PagePriceAdjustmentApprovedLineResponse response = new PagePriceAdjustmentApprovedLineResponse();
        response.setContent(rows);
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        response.setTotalCount(totalCount);
        return response;
    }

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
    @Override
    public GetPriceAdjustmentApprovedLineResponse toGetPriceAdjustmentApprovedLineResponse(
        PriceAdjustmentLineDto line,
        BigDecimal totalImpact,
        Date approveTime,
        Long approverId,
        String approverName) {
        GetPriceAdjustmentApprovedLineResponse response = new GetPriceAdjustmentApprovedLineResponse();
        response.setLine(line);
        response.setTotalImpact(totalImpact);
        response.setApproveTime(approveTime);
        response.setApproverId(approverId);
        response.setApproverName(approverName);
        return response;
    }

    /**
     * 四档 item_table Tab 选项。
     *
     * @return 下拉选项
     */
    private List<OptionDto> buildItemTableTabOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(new OptionDto(1L, TableNameConstants.MED_MEDICATION_DEFINITION, "药品"));
        options.add(new OptionDto(2L, TableNameConstants.ADM_DEVICE_DEFINITION, "耗材"));
        options.add(new OptionDto(3L, TableNameConstants.WKF_ACTIVITY_DEFINITION, "诊疗活动"));
        options.add(new OptionDto(4L, TableNameConstants.ADM_HEALTHCARE_SERVICE, "医疗服务"));
        return options;
    }

    /**
     * 调价原因枚举选项。
     *
     * @return 下拉选项
     */
    private List<OptionDto> buildAdjustmentReasonOptions() {
        List<OptionDto> options = new ArrayList<>();
        for (AdjustmentReason reason : AdjustmentReason.values()) {
            options.add(new OptionDto(reason.getSortOrder(), reason.getCode(), reason.getDisplay()));
        }
        return options;
    }

    /**
     * 解析项目表展示名。
     *
     * @param itemTable 项目物理表
     * @return 展示名
     */
    private String resolveItemTableText(String itemTable) {
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(itemTable)) {
            return "药品";
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(itemTable)) {
            return "耗材";
        }
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(itemTable)) {
            return "诊疗活动";
        }
        if (TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(itemTable)) {
            return "医疗服务";
        }
        return itemTable;
    }

    /**
     * 解析调价原因展示名。
     *
     * @param adjustmentReason 原因 code
     * @return 展示名；未知时 null
     */
    private String resolveAdjustmentReasonText(String adjustmentReason) {
        if (StringUtils.isEmpty(adjustmentReason)) {
            return null;
        }
        for (AdjustmentReason reason : AdjustmentReason.values()) {
            if (reason.getCode().equals(adjustmentReason.trim())) {
                return reason.getDisplay();
            }
        }
        return null;
    }

    /**
     * 解析调价展示口径文案。
     *
     * @param adjustmentStyle 展示口径 code
     * @return 文案
     */
    private String resolveAdjustmentStyleText(String adjustmentStyle) {
        if (CustomConstants.PriceAdjustment.ADJUSTMENT_STYLE_CLINICAL.equals(adjustmentStyle)) {
            return "诊疗调价";
        }
        if (CustomConstants.PriceAdjustment.ADJUSTMENT_STYLE_LOGISTICS.equals(adjustmentStyle)) {
            return "物流调价";
        }
        return adjustmentStyle;
    }

    /**
     * 从批量加载映射解析医务人员展示名。
     *
     * @param practitioners   主键 → 实体映射；可 null
     * @param practitionerId  医务人员主键
     * @return 展示名；未命中时 null
     */
    private String resolvePractitionerName(Map<Long, Practitioner> practitioners, Long practitionerId) {
        if (practitioners == null || practitionerId == null) {
            return null;
        }
        Practitioner practitioner = practitioners.get(practitionerId);
        if (practitioner == null) {
            return null;
        }
        return practitioner.getName();
    }
}
