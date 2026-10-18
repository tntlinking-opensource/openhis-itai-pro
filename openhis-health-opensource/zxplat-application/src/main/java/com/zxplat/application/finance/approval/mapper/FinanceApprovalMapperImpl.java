/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.approval.mapper;

import com.zxplat.application.finance.approval.dto.FinanceApprovalDocumentRowDto;
import com.zxplat.application.finance.approval.request.PageFinanceApprovalDocumentRequest;
import com.zxplat.application.finance.approval.response.InitFinanceApprovalResponse;
import com.zxplat.application.finance.approval.support.FinanceApprovalTypeSupport;
import com.zxplat.application.shared.pagination.PageQueryBinder;
import com.zxplat.domain.custom.priceadjustment.entity.PriceAdjustment;
import com.zxplat.domain.custom.priceadjustment.queries.PriceAdjustmentDocumentListQuery;
import com.zxplat.domain.share.constants.FinanceApprovalConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 财务审批 Mapper 实现：纯字段搬运与时间格式化；业务判别委托 {@link FinanceApprovalTypeSupport}。
 */
@Component
@RequiredArgsConstructor
public class FinanceApprovalMapperImpl implements FinanceApprovalMapper {

    /** 类型/品类/状态展示与校验。 */
    private final FinanceApprovalTypeSupport typeSupport;

    /**
     * 将分页请求映射为供应单据列表 Query（不含 typeEnums 默认收窄；由 Loader 按分支写入）。
     *
     * @param request 分页请求；调用方保证非空
     * @return 供应列表 Query
     */
    @Override
    public SupplyRequestDocumentListQuery toSupplyDocumentListQuery(PageFinanceApprovalDocumentRequest request) {
        SupplyRequestDocumentListQuery query = new SupplyRequestDocumentListQuery();
        // 分页/keyword 等与 AbstractPageQuery 对齐（B.10.11）
        PageQueryBinder.bind(request, query);
        // 缺省页大小回落 SDD 默认 50，禁止写死进货/待审
        if (request.getPageSize() == null) {
            query.setPageSize(FinanceApprovalConstants.DEFAULT_PAGE_SIZE);
        }
        // 单号专用模糊（BR-APR-007），不扫原因/备注
        if (StringUtils.isNotEmpty(request.getBusNo())) {
            query.setBusNoLike(request.getBusNo().trim());
        }
        // 状态透传；空则不限（BR-APR-006）
        if (StringUtils.isNotEmpty(request.getStatusEnum())) {
            query.setStatusEnum(request.getStatusEnum().trim());
        }
        query.setLocationId(request.getLocationId());
        query.setSupplierId(request.getSupplierId());
        if (StringUtils.isNotEmpty(request.getItemTable())) {
            query.setItemTable(request.getItemTable().trim());
        }
        query.setApplicantId(request.getApplicantId());
        query.setApproverId(request.getApproverId());
        query.setOccurrenceTimeFrom(request.getOccurrenceTimeFrom());
        query.setOccurrenceTimeTo(request.getOccurrenceTimeTo());
        query.setApprovalTimeFrom(request.getApprovalTimeFrom());
        query.setApprovalTimeTo(request.getApprovalTimeTo());
        return query;
    }

    /**
     * 将分页请求映射为调价单据列表 Query（状态码由调用方先映射为调价域）。
     *
     * @param request          分页请求；调用方保证非空
     * @param mappedStatusEnum 已映射的调价 status；可空表示不限
     * @return 调价列表 Query
     */
    @Override
    public PriceAdjustmentDocumentListQuery toPriceAdjustmentDocumentListQuery(
        PageFinanceApprovalDocumentRequest request,
        String mappedStatusEnum) {
        PriceAdjustmentDocumentListQuery query = new PriceAdjustmentDocumentListQuery();
        PageQueryBinder.bind(request, query);
        if (request.getPageSize() == null) {
            query.setPageSize(FinanceApprovalConstants.DEFAULT_PAGE_SIZE);
        }
        if (StringUtils.isNotEmpty(request.getBusNo())) {
            query.setBusNoLike(request.getBusNo().trim());
        }
        if (StringUtils.isNotEmpty(mappedStatusEnum)) {
            query.setStatusEnum(mappedStatusEnum.trim());
        }
        if (StringUtils.isNotEmpty(request.getItemTable())) {
            query.setItemTable(request.getItemTable().trim());
        }
        // 调价制单人落库字段为 operator_id，与列表筛选 applicantId 对齐
        query.setOperatorId(request.getApplicantId());
        query.setApproverId(request.getApproverId());
        // 制单窗 → adjustment_time；审批窗 → approve_time
        query.setAdjustmentTimeFrom(request.getOccurrenceTimeFrom());
        query.setAdjustmentTimeTo(request.getOccurrenceTimeTo());
        query.setApproveTimeFrom(request.getApprovalTimeFrom());
        query.setApproveTimeTo(request.getApprovalTimeTo());
        return query;
    }

    /**
     * 供应头行 → 列表行 DTO（金额与展示名由 {@code fillAmounts}/enrich 后续填充）。
     *
     * @param head 同号头行；null 时返回 null
     * @return 列表行
     */
    @Override
    public FinanceApprovalDocumentRowDto toRowDto(SupplyRequestModel head) {
        if (head == null) {
            return null;
        }
        FinanceApprovalDocumentRowDto row = new FinanceApprovalDocumentRowDto();
        row.setDocumentKind(FinanceApprovalConstants.DOCUMENT_KIND_SUPPLY);
        row.setBusNo(head.getBusNo());
        row.setTypeEnum(head.getTypeEnum());
        row.setTypeEnumText(this.typeSupport.resolveSupplyTypeText(head.getTypeEnum()));
        row.setStatusEnum(head.getStatusEnum());
        row.setStatusEnumText(this.typeSupport.resolveSupplyStatusText(head.getStatusEnum()));
        // 库房展示：优先目的库（进货/损益/盘点），空则回落源库（退货）
        Long locationId = head.getPurposeLocationId();
        if (locationId == null) {
            locationId = head.getSourceLocationId();
        }
        row.setLocationId(locationId);
        row.setSupplierId(head.getSupplierId());
        row.setItemTable(head.getItemTable());
        row.setItemTableText(this.typeSupport.resolveItemTableText(head.getItemTable()));
        row.setApplicantId(head.getApplicantId());
        row.setApproverId(head.getApproverId());
        row.setOccurrenceTime(this.formatDateTime(head.getOccurrenceTime()));
        row.setApplyTime(this.formatDateTime(head.getApplyTime()));
        row.setApprovalTime(this.formatDateTime(head.getApprovalTime()));
        row.setRejectReason(head.getReason());
        return row;
    }

    /**
     * 调价头行 → 列表行 DTO（金额与展示名由 {@code fillAmounts}/enrich 后续填充）。
     *
     * @param head 同号任一行头字段；null 时返回 null
     * @return 列表行
     */
    @Override
    public FinanceApprovalDocumentRowDto toRowDto(PriceAdjustment head) {
        if (head == null) {
            return null;
        }
        FinanceApprovalDocumentRowDto row = new FinanceApprovalDocumentRowDto();
        row.setDocumentKind(FinanceApprovalConstants.DOCUMENT_KIND_PRICE_ADJUSTMENT);
        row.setBusNo(head.getBusNo());
        row.setTypeEnum(FinanceApprovalConstants.TYPE_PRICE_ADJUSTMENT);
        row.setTypeEnumText(FinanceApprovalConstants.TYPE_TEXT_PRICE_ADJUSTMENT);
        row.setStatusEnum(head.getStatusEnum());
        row.setStatusEnumText(this.typeSupport.resolvePriceStatusText(head.getStatusEnum()));
        row.setItemTable(head.getItemTable());
        row.setItemTableText(this.typeSupport.resolveItemTableText(head.getItemTable()));
        // 调价制单人 = operator_id
        row.setApplicantId(head.getOperatorId());
        row.setApproverId(head.getApproverId());
        row.setOccurrenceTime(this.formatDate(head.getAdjustmentTime()));
        row.setSubmitTime(this.formatDate(head.getSubmitTime()));
        row.setApproveTime(this.formatDate(head.getApproveTime()));
        // 驳回原因落在 approve_comment
        row.setRejectReason(head.getApproveComment());
        return row;
    }

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
    @Override
    public InitFinanceApprovalResponse toInitResponse(
        List<OptionDto> typeOptions,
        List<OptionDto> statusOptions,
        List<OptionDto> locationOptions,
        List<OptionDto> supplierOptions,
        List<OptionDto> itemTableOptions,
        List<OptionDto> applicantOptions,
        List<OptionDto> approverOptions) {
        InitFinanceApprovalResponse response = new InitFinanceApprovalResponse();
        response.setTypeOptions(this.copyOptions(typeOptions));
        response.setStatusOptions(this.copyOptions(statusOptions));
        response.setLocationOptions(this.copyOptions(locationOptions));
        response.setSupplierOptions(this.copyOptions(supplierOptions));
        response.setItemTableOptions(this.copyOptions(itemTableOptions));
        response.setApplicantOptions(this.copyOptions(applicantOptions));
        response.setApproverOptions(this.copyOptions(approverOptions));
        return response;
    }

    /**
     * LocalDateTime → 展示串。
     *
     * @param value 时刻；null 返回 null
     * @return 格式化串
     */
    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.format(GeneralSharedConstants.DATE_TIME_FORMATTER);
    }

    /**
     * java.util.Date → 展示串。
     *
     * @param value 时刻；null 返回 null
     * @return 格式化串
     */
    private String formatDate(Date value) {
        if (value == null) {
            return null;
        }
        LocalDateTime ldt = LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault());
        return ldt.format(GeneralSharedConstants.DATE_TIME_FORMATTER);
    }

    /**
     * 复制 Option 列表，避免外部可变列表污染响应。
     *
     * @param source 源列表；null 视为空
     * @return 新列表
     */
    private List<OptionDto> copyOptions(List<OptionDto> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(source);
    }
}
