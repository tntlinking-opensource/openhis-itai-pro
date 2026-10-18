/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustment.mapper;

import com.zxplat.application.finance.priceadjustment.dto.AdjustableItemRowDto;
import com.zxplat.application.finance.priceadjustment.dto.InTransitCheckDto;
import com.zxplat.application.finance.priceadjustment.dto.LockPreviewRowDto;
import com.zxplat.application.finance.priceadjustment.dto.LogisticsSnapshotRowDto;
import com.zxplat.application.finance.priceadjustment.dto.PriceAdjustmentDetailDto;
import com.zxplat.application.finance.priceadjustment.dto.PriceAdjustmentDocumentListRowDto;
import com.zxplat.application.finance.priceadjustment.dto.PriceAdjustmentLineDto;
import com.zxplat.application.finance.priceadjustment.request.ApprovePriceAdjustmentRequest;
import com.zxplat.application.finance.priceadjustment.request.PagePriceAdjustmentDocumentRequest;
import com.zxplat.application.finance.priceadjustment.request.RejectPriceAdjustmentRequest;
import com.zxplat.application.finance.priceadjustment.request.SavePriceAdjustmentDraftDetailLineRequest;
import com.zxplat.application.finance.priceadjustment.request.SavePriceAdjustmentDraftLineRequest;
import com.zxplat.application.finance.priceadjustment.request.SavePriceAdjustmentDraftRequest;
import com.zxplat.application.finance.priceadjustment.request.SearchAdjustableItemRequest;
import com.zxplat.application.finance.priceadjustment.response.ApprovePriceAdjustmentResponse;
import com.zxplat.application.finance.priceadjustment.response.CheckInTransitForSubmitResponse;
import com.zxplat.application.finance.priceadjustment.response.ExistsPendingByItemResponse;
import com.zxplat.application.finance.priceadjustment.response.InitPriceAdjustmentResponse;
import com.zxplat.application.finance.priceadjustment.response.LoadClinicalOldPriceResponse;
import com.zxplat.application.finance.priceadjustment.response.LoadLogisticsAdjustmentSnapshotResponse;
import com.zxplat.application.finance.priceadjustment.response.PagePriceAdjustmentDocumentResponse;
import com.zxplat.application.finance.priceadjustment.response.PreviewSubmitLocksResponse;
import com.zxplat.application.finance.priceadjustment.response.PriceAdjustmentDocumentResponse;
import com.zxplat.application.finance.priceadjustment.response.RejectPriceAdjustmentResponse;
import com.zxplat.application.finance.priceadjustment.response.SavePriceAdjustmentDraftResponse;
import com.zxplat.application.finance.priceadjustment.response.SearchAdjustableItemsResponse;
import com.zxplat.application.finance.priceadjustment.response.SubmitPriceAdjustmentResponse;
import com.zxplat.application.finance.priceadjustment.response.WithdrawPriceAdjustmentDraftResponse;
import com.zxplat.domain.custom.priceadjustment.command.ApprovePriceAdjustmentCommand;
import com.zxplat.domain.custom.priceadjustment.command.RejectPriceAdjustmentCommand;
import com.zxplat.domain.custom.priceadjustment.command.ResubmitPriceAdjustmentCommand;
import com.zxplat.domain.custom.priceadjustment.command.SavePriceAdjustmentDraftCommand;
import com.zxplat.domain.custom.priceadjustment.command.SubmitPriceAdjustmentCommand;
import com.zxplat.domain.custom.priceadjustment.command.WithdrawPriceAdjustmentDraftCommand;
import com.zxplat.domain.custom.priceadjustment.queries.PriceAdjustmentDocumentListQuery;
import com.zxplat.domain.custom.priceadjustment.queries.model.PriceAdjustmentDetailQueryModel;
import com.zxplat.domain.custom.priceadjustment.queries.model.PriceAdjustmentDocumentQueryModel;
import com.zxplat.domain.custom.priceadjustment.queries.model.PriceAdjustmentImpactSummaryQueryModel;
import com.zxplat.domain.custom.priceadjustment.queries.model.PriceAdjustmentLineQueryModel;
import com.zxplat.domain.custom.priceadjustment.support.PriceAdjustmentItemTableSupport;
import com.zxplat.domain.share.constants.CustomConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.AdjustmentReason;
import com.zxplat.domain.share.enums.PriceAdjustmentStatus;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.inventoryitem.command.LockInventoryCommand;
import com.zxplat.domain.workflow.inventoryitem.command.UnlockByBusNoCommand;
import com.zxplat.application.shared.pagination.PageQueryBinder;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.application.finance.priceadjustment.support.dto.LogisticsDetailSnapshotRowDto;
import com.zxplat.application.finance.priceadjustment.support.dto.PriceAdjustmentInTransitCheckDto;
import com.zxplat.application.finance.priceadjustment.support.dto.PriceAdjustmentLockPreviewRowDto;
import com.zxplat.application.shared.product.ProductDefinitionPageQuery;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link PriceAdjustmentMapper} 默认实现：逐字段映射，不承载业务流程判断。
 */
@Component
public class PriceAdjustmentMapperImpl implements PriceAdjustmentMapper {

    /**
     * 装配页初始化响应。
     *
     * @param adjustmentReasonOptions 调价原因下拉
     * @param statusFilterOptions       审批状态筛选项
     * @param itemTableTabOptions       项目表 Tab 选项
     * @return 初始化响应
     */
    @Override
    public InitPriceAdjustmentResponse toInitPriceAdjustmentResponse(
        List<OptionDto> adjustmentReasonOptions,
        List<OptionDto> statusFilterOptions,
        List<OptionDto> itemTableTabOptions) {
        InitPriceAdjustmentResponse response = new InitPriceAdjustmentResponse();
        response.setAdjustmentReasonOptions(adjustmentReasonOptions);
        response.setStatusFilterOptions(statusFilterOptions);
        response.setItemTableTabOptions(itemTableTabOptions);
        // 租户选项缺失时默认批号价展示（IF-PRC-01 非阻断口径）
        response.setOrderPricingSource(CustomConstants.PriceAdjustment.ORDER_PRICING_SOURCE_DEFAULT);
        return response;
    }

    /**
     * 列表请求转领域列表 Query。
     *
     * @param request 列表分页请求；调用方保证非空
     * @param orgId   操作科室；{@code null} 时不写 org 谓词
     * @return 领域列表 Query
     */
    @Override
    public PriceAdjustmentDocumentListQuery toDocumentListQuery(
        PagePriceAdjustmentDocumentRequest request,
        Long orgId) {
        PriceAdjustmentDocumentListQuery query = new PriceAdjustmentDocumentListQuery();
        // null：历史调价单不按科室收窄；非 null：调用方显式收窄
        query.setOrgId(orgId);
        // 仅 Request 显式传 statusEnum 时过滤；空/未传则全状态可见
        if (StringUtils.isNotEmpty(request.getStatusEnum())) {
            query.setStatusEnum(request.getStatusEnum().trim());
        }
        PageQueryBinder.bind(request, query);
        LocalDateTime rangeStart = request.getSubmitTimeFrom();
        if (rangeStart == null) {
            rangeStart = request.getStartDate();
        }
        query.setStartDate(rangeStart);
        LocalDateTime rangeEnd = request.getSubmitTimeTo();
        if (rangeEnd == null) {
            rangeEnd = request.getEndDate();
        }
        query.setEndDate(rangeEnd);
        return query;
    }

    /**
     * 单据读模型列表转列表聚合行 DTO。
     *
     * @param busNoPageOrder 当前页单号顺序
     * @param documents      单据读模型
     * @param impactByBusNo  单号到盈亏汇总的映射；可空
     * @return 列表行 DTO
     */
    @Override
    public List<PriceAdjustmentDocumentListRowDto> toDocumentListRows(
        List<String> busNoPageOrder,
        List<PriceAdjustmentDocumentQueryModel> documents,
        Map<String, PriceAdjustmentImpactSummaryQueryModel> impactByBusNo) {
        List<PriceAdjustmentDocumentListRowDto> rows = new ArrayList<>();
        if (busNoPageOrder == null || busNoPageOrder.isEmpty()) {
            return rows;
        }
        Map<String, PriceAdjustmentDocumentQueryModel> documentByBusNo = new HashMap<>();
        if (documents != null) {
            for (PriceAdjustmentDocumentQueryModel document : documents) {
                if (document != null && StringUtils.isNotEmpty(document.getBusNo())) {
                    documentByBusNo.put(document.getBusNo().trim(), document);
                }
            }
        }
        for (String busNoRaw : busNoPageOrder) {
            if (StringUtils.isEmpty(busNoRaw)) {
                continue;
            }
            String busNo = busNoRaw.trim();
            PriceAdjustmentDocumentQueryModel document = documentByBusNo.get(busNo);
            if (document == null) {
                continue;
            }
            PriceAdjustmentDocumentListRowDto row = new PriceAdjustmentDocumentListRowDto();
            row.setBusNo(busNo);
            row.setStatusEnum(document.getStatusEnum());
            row.setStatusEnumText(this.resolveStatusText(document.getStatusEnum()));
            row.setSubmitterId(document.getSubmitterId());
            row.setSubmitTime(document.getSubmitTime());
            row.setApproverId(document.getApproverId());
            row.setApproveTime(document.getApproveTime());
            List<PriceAdjustmentLineQueryModel> lines = document.getLines();
            if (lines != null && !lines.isEmpty()) {
                PriceAdjustmentLineQueryModel anchor = lines.get(0);
                row.setItemTable(anchor.getItemTable());
                row.setAdjustmentStyle(anchor.getAdjustmentStyle());
                row.setOrgId(anchor.getOrgId());
                row.setOperatorId(anchor.getOperatorId());
                row.setLineCount(lines.size());
                Date maxAdjustmentTime = null;
                for (PriceAdjustmentLineQueryModel line : lines) {
                    if (line == null || line.getAdjustmentTime() == null) {
                        continue;
                    }
                    if (maxAdjustmentTime == null || line.getAdjustmentTime().after(maxAdjustmentTime)) {
                        maxAdjustmentTime = line.getAdjustmentTime();
                    }
                }
                row.setAdjustmentTime(maxAdjustmentTime);
            }
            if (impactByBusNo != null) {
                PriceAdjustmentImpactSummaryQueryModel impact = impactByBusNo.get(busNo);
                if (impact != null) {
                    row.setTotalPurchaseImpact(impact.getTotalPurchaseImpact());
                    row.setTotalSellingImpact(impact.getTotalSellingImpact());
                }
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 装配列表分页响应。
     *
     * @param mergedTotal 去重总单数
     * @param pageNo      页码（1-based）
     * @param pageSize    页大小
     * @param records     当前页行
     * @return 分页响应
     */
    @Override
    public PagePriceAdjustmentDocumentResponse toPagePriceAdjustmentDocumentResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<PriceAdjustmentDocumentListRowDto> records) {
        List<PriceAdjustmentDocumentListRowDto> content;
        if (records == null) {
            content = new ArrayList<>();
        } else {
            content = records;
        }
        return PagedResultBuilder.build(new PagePriceAdjustmentDocumentResponse(), content, mergedTotal, pageNo, pageSize);
    }

    /**
     * 单据读模型转详情响应。
     *
     * @param document  单据读模型；null 时返回 null
     * @param impact    盈亏汇总；可 null
     * @param productByKey 产品展示映射；可 null
     * @return 详情响应
     */
    @Override
    public PriceAdjustmentDocumentResponse toPriceAdjustmentDocumentResponse(
        PriceAdjustmentDocumentQueryModel document,
        PriceAdjustmentImpactSummaryQueryModel impact,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        if (document == null) {
            return null;
        }
        PriceAdjustmentDocumentResponse response = new PriceAdjustmentDocumentResponse();
        response.setBusNo(document.getBusNo());
        response.setStatusEnum(document.getStatusEnum());
        response.setStatusEnumText(this.resolveStatusText(document.getStatusEnum()));
        response.setSubmitterId(document.getSubmitterId());
        response.setSubmitTime(document.getSubmitTime());
        response.setApproverId(document.getApproverId());
        response.setApproveTime(document.getApproveTime());
        response.setApproveComment(document.getApproveComment());
        if (impact != null) {
            response.setTotalPurchaseImpact(impact.getTotalPurchaseImpact());
            response.setTotalSellingImpact(impact.getTotalSellingImpact());
        }
        List<PriceAdjustmentLineDto> lineDtos = new ArrayList<>();
        if (document.getLines() != null) {
            for (PriceAdjustmentLineQueryModel line : document.getLines()) {
                PriceAdjustmentLineDto dto = this.toLineDto(line, productByKey);
                if (dto != null) {
                    lineDtos.add(dto);
                }
            }
        }
        response.setLines(lineDtos);
        return response;
    }

    /**
     * 选品搜索请求转产品目录分页 Query。
     *
     * @param request 选品请求；调用方保证非空
     * @return 产品目录分页 Query
     */
    @Override
    public ProductDefinitionPageQuery toProductDefinitionPageQuery(SearchAdjustableItemRequest request) {
        ProductDefinitionPageQuery query = new ProductDefinitionPageQuery();
        query.setProductTableName(request.getItemTable());
        query.setKeyword(request.getKeyword());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        query.setActiveFlag(Boolean.TRUE);
        return query;
    }

    /**
     * 产品目录分页转选品搜索响应。
     *
     * @param page 产品展示分页
     * @return 选品分页响应
     */
    @Override
    public SearchAdjustableItemsResponse toSearchAdjustableItemsResponse(Page<ProductDefinitionDisplayDto> page) {
        List<AdjustableItemRowDto> rows = new ArrayList<>();
        if (page != null) {
            for (ProductDefinitionDisplayDto display : page.getContent()) {
                AdjustableItemRowDto row = this.toAdjustableItemRow(display);
                if (row != null) {
                    rows.add(row);
                }
            }
            return PagedResultBuilder.build(new SearchAdjustableItemsResponse(), rows, page);
        }
        return PagedResultBuilder.build(new SearchAdjustableItemsResponse(), rows, 0L, 1, 0);
    }

    /**
     * 医疗服务实体列表转选品分页响应（应用层内存分页后调用）。
     *
     * @param rows       当前页行
     * @param totalCount 总条数
     * @param pageNo     页码（1-based）
     * @param pageSize   页大小
     * @return 选品分页响应
     */
    @Override
    public SearchAdjustableItemsResponse toSearchAdjustableItemsResponse(
        List<AdjustableItemRowDto> rows,
        long totalCount,
        int pageNo,
        int pageSize) {
        List<AdjustableItemRowDto> content;
        if (rows == null) {
            content = new ArrayList<>();
        } else {
            content = rows;
        }
        return PagedResultBuilder.build(new SearchAdjustableItemsResponse(), content, totalCount, pageNo, pageSize);
    }

    /**
     * 物流快照行转 IF-PRC-05 响应。
     *
     * @param rows querysupport 快照行
     * @return 快照响应
     */
    @Override
    public LoadLogisticsAdjustmentSnapshotResponse toLoadLogisticsAdjustmentSnapshotResponse(
        List<LogisticsDetailSnapshotRowDto> rows) {
        LoadLogisticsAdjustmentSnapshotResponse response = new LoadLogisticsAdjustmentSnapshotResponse();
        List<LogisticsSnapshotRowDto> mapped = new ArrayList<>();
        if (rows != null) {
            for (LogisticsDetailSnapshotRowDto row : rows) {
                if (row == null) {
                    continue;
                }
                LogisticsSnapshotRowDto target = new LogisticsSnapshotRowDto();
                target.setItemId(row.getItemId());
                target.setLotNumber(row.getLotNumber());
                target.setQuantity(row.getQuantity());
                target.setOldPurchaseAmount(row.getOldPurchaseAmount());
                target.setOldSellingAmount(row.getOldSellingAmount());
                mapped.add(target);
            }
        }
        response.setRows(mapped);
        return response;
    }

    /**
     * 调前售价转 IF-PRC-06 响应。
     *
     * @param oldSellingAmount 当前 ACTIVE SALE 金额；可 null
     * @return 调前价响应
     */
    @Override
    public LoadClinicalOldPriceResponse toLoadClinicalOldPriceResponse(BigDecimal oldSellingAmount) {
        LoadClinicalOldPriceResponse response = new LoadClinicalOldPriceResponse();
        response.setOldSellingAmount(oldSellingAmount);
        return response;
    }

    /**
     * 锁预览行转应用 DTO 列表。
     *
     * @param rows querysupport 锁预览行
     * @return 锁预览 DTO 列表
     */
    @Override
    public List<LockPreviewRowDto> toLockPreviewRows(List<PriceAdjustmentLockPreviewRowDto> rows) {
        List<LockPreviewRowDto> mapped = new ArrayList<>();
        if (rows == null) {
            return mapped;
        }
        for (PriceAdjustmentLockPreviewRowDto row : rows) {
            if (row == null) {
                continue;
            }
            LockPreviewRowDto dto = new LockPreviewRowDto();
            dto.setInventoryId(row.getInventoryId());
            dto.setItemTable(row.getItemTable());
            dto.setItemId(row.getItemId());
            dto.setLocationId(row.getLocationId());
            dto.setLotNumber(row.getLotNumber());
            dto.setQuantity(row.getQuantity());
            dto.setLockedByOtherBusNo(row.isLockedByOtherBusNo());
            dto.setLockedBusNo(row.getLockedBusNo());
            mapped.add(dto);
        }
        return mapped;
    }

    /**
     * 锁预览 DTO 列表转 IF-PRC-07 响应。
     *
     * @param rows 锁预览 DTO
     * @return 锁预览响应
     */
    @Override
    public PreviewSubmitLocksResponse toPreviewSubmitLocksResponse(List<LockPreviewRowDto> rows) {
        PreviewSubmitLocksResponse response = new PreviewSubmitLocksResponse();
        response.setRows(rows);
        return response;
    }

    /**
     * querysupport 在途结果转应用 DTO。
     *
     * @param source querysupport 在途结果；null 时返回 null
     * @return 在途 DTO
     */
    @Override
    public InTransitCheckDto toInTransitCheckDto(PriceAdjustmentInTransitCheckDto source) {
        if (source == null) {
            return null;
        }
        InTransitCheckDto dto = new InTransitCheckDto();
        dto.setBlocked(source.isBlocked());
        dto.setReasonCode(source.getReasonCode());
        dto.setReasonMessage(source.getReasonMessage());
        dto.setItemTable(source.getItemTable());
        dto.setItemId(source.getItemId());
        return dto;
    }

    /**
     * 在途 DTO 转 IF-PRC-08 响应。
     *
     * @param check 在途 DTO
     * @return 在途预检响应
     */
    @Override
    public CheckInTransitForSubmitResponse toCheckInTransitForSubmitResponse(InTransitCheckDto check) {
        CheckInTransitForSubmitResponse response = new CheckInTransitForSubmitResponse();
        response.setCheck(check);
        return response;
    }

    /**
     * pending 互斥查询结果转 IF-PRC-12 响应。
     *
     * @param existsPending 是否存在 pending 调价单
     * @return 互斥查询响应
     */
    @Override
    public ExistsPendingByItemResponse toExistsPendingByItemResponse(Boolean existsPending) {
        ExistsPendingByItemResponse response = new ExistsPendingByItemResponse();
        response.setExistsPending(existsPending);
        return response;
    }

    /**
     * 保存草稿请求转领域 Command。
     *
     * @param request    保存请求；调用方保证非空
     * @param operatorId 操作人
     * @param orgId      会话操作科室（制单机构）
     * @return 保存草稿 Command
     */
    @Override
    public SavePriceAdjustmentDraftCommand toSavePriceAdjustmentDraftCommand(
        SavePriceAdjustmentDraftRequest request,
        Long operatorId,
        Long orgId) {
        SavePriceAdjustmentDraftCommand command = new SavePriceAdjustmentDraftCommand();
        command.setBusNo(request.getBusNo());
        command.setOperatorId(operatorId);
        command.setOrgId(orgId);
        command.setAdjustmentReason(request.getAdjustmentReason());
        command.setAdjustmentTime(request.getAdjustmentTime());
        List<SavePriceAdjustmentDraftCommand.Line> lines = new ArrayList<>();
        if (request.getLines() != null) {
            for (SavePriceAdjustmentDraftLineRequest lineRequest : request.getLines()) {
                if (lineRequest == null) {
                    continue;
                }
                SavePriceAdjustmentDraftCommand.Line line = new SavePriceAdjustmentDraftCommand.Line();
                line.setItemTable(lineRequest.getItemTable());
                line.setItemId(lineRequest.getItemId());
                line.setNewPurchaseAmount(lineRequest.getNewPurchaseAmount());
                line.setNewSellingAmount(lineRequest.getNewSellingAmount());
                List<SavePriceAdjustmentDraftCommand.DetailLine> details = new ArrayList<>();
                if (lineRequest.getDetails() != null) {
                    for (SavePriceAdjustmentDraftDetailLineRequest detailRequest : lineRequest.getDetails()) {
                        if (detailRequest == null) {
                            continue;
                        }
                        SavePriceAdjustmentDraftCommand.DetailLine detail = new SavePriceAdjustmentDraftCommand.DetailLine();
                        detail.setLotNumber(detailRequest.getLotNumber());
                        detail.setQuantity(detailRequest.getQuantity());
                        details.add(detail);
                    }
                }
                line.setDetails(details);
                lines.add(line);
            }
        }
        command.setLines(lines);
        return command;
    }

    /**
     * 保存成功后装配响应。
     *
     * @param busNo      调价单号
     * @param statusEnum 状态 code
     * @return 保存响应
     */
    @Override
    public SavePriceAdjustmentDraftResponse toSavePriceAdjustmentDraftResponse(String busNo, String statusEnum) {
        SavePriceAdjustmentDraftResponse response = new SavePriceAdjustmentDraftResponse();
        response.setBusNo(busNo);
        response.setStatusEnum(statusEnum);
        return response;
    }

    /**
     * 提交审批 Command（draft 路径）。
     *
     * @param busNo               调价单号
     * @param submitterId         提交人
     * @param lockedInventoryCount 加锁行数
     * @return 提交 Command
     */
    @Override
    public SubmitPriceAdjustmentCommand toSubmitPriceAdjustmentCommand(
        String busNo,
        Long submitterId,
        Integer lockedInventoryCount) {
        SubmitPriceAdjustmentCommand command = new SubmitPriceAdjustmentCommand();
        command.setBusNo(busNo);
        command.setSubmitterId(submitterId);
        command.setLockedInventoryCount(lockedInventoryCount);
        return command;
    }

    /**
     * 再次提交 Command（rejected 路径）。
     *
     * @param busNo               调价单号
     * @param submitterId         提交人
     * @param lockedInventoryCount 加锁行数
     * @return 再次提交 Command
     */
    @Override
    public ResubmitPriceAdjustmentCommand toResubmitPriceAdjustmentCommand(
        String busNo,
        Long submitterId,
        Integer lockedInventoryCount) {
        ResubmitPriceAdjustmentCommand command = new ResubmitPriceAdjustmentCommand();
        command.setBusNo(busNo);
        command.setSubmitterId(submitterId);
        command.setLockedInventoryCount(lockedInventoryCount);
        return command;
    }

    /**
     * 提交成功后装配响应。
     *
     * @param busNo      调价单号
     * @param statusEnum 状态 code
     * @return 提交响应
     */
    @Override
    public SubmitPriceAdjustmentResponse toSubmitPriceAdjustmentResponse(String busNo, String statusEnum) {
        SubmitPriceAdjustmentResponse response = new SubmitPriceAdjustmentResponse();
        response.setBusNo(busNo);
        response.setStatusEnum(statusEnum);
        return response;
    }

    /**
     * 撤销草稿 Command。
     *
     * @param busNo      调价单号
     * @param operatorId 操作人
     * @return 撤销 Command
     */
    @Override
    public WithdrawPriceAdjustmentDraftCommand toWithdrawPriceAdjustmentDraftCommand(String busNo, Long operatorId) {
        WithdrawPriceAdjustmentDraftCommand command = new WithdrawPriceAdjustmentDraftCommand();
        command.setBusNo(busNo);
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 撤销成功后装配响应。
     *
     * @param busNo      调价单号
     * @param statusEnum 状态 code
     * @return 撤销响应
     */
    @Override
    public WithdrawPriceAdjustmentDraftResponse toWithdrawPriceAdjustmentDraftResponse(String busNo, String statusEnum) {
        WithdrawPriceAdjustmentDraftResponse response = new WithdrawPriceAdjustmentDraftResponse();
        response.setBusNo(busNo);
        response.setStatusEnum(statusEnum);
        return response;
    }

    /**
     * 审批同意 Command。
     *
     * @param request    审批请求；调用方保证非空
     * @param approverId 审批人
     * @param operatorId 解锁审计操作人
     * @return 审批 Command
     */
    @Override
    public ApprovePriceAdjustmentCommand toApprovePriceAdjustmentCommand(
        ApprovePriceAdjustmentRequest request,
        Long approverId,
        Long operatorId) {
        ApprovePriceAdjustmentCommand command = new ApprovePriceAdjustmentCommand();
        command.setBusNo(request.getBusNo());
        command.setApproverId(approverId);
        command.setApproveComment(request.getApproveComment());
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 审批成功后装配响应。
     *
     * @param busNo      调价单号
     * @param statusEnum 状态 code
     * @return 审批响应
     */
    @Override
    public ApprovePriceAdjustmentResponse toApprovePriceAdjustmentResponse(String busNo, String statusEnum) {
        ApprovePriceAdjustmentResponse response = new ApprovePriceAdjustmentResponse();
        response.setBusNo(busNo);
        response.setStatusEnum(statusEnum);
        return response;
    }

    /**
     * 审批驳回 Command。
     *
     * @param request    驳回请求；调用方保证非空
     * @param approverId 审批人
     * @param operatorId 解锁审计操作人
     * @return 驳回 Command
     */
    @Override
    public RejectPriceAdjustmentCommand toRejectPriceAdjustmentCommand(
        RejectPriceAdjustmentRequest request,
        Long approverId,
        Long operatorId) {
        RejectPriceAdjustmentCommand command = new RejectPriceAdjustmentCommand();
        command.setBusNo(request.getBusNo());
        command.setApproverId(approverId);
        command.setApproveComment(request.getApproveComment());
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 驳回成功后装配响应。
     *
     * @param busNo      调价单号
     * @param statusEnum 状态 code
     * @return 驳回响应
     */
    @Override
    public RejectPriceAdjustmentResponse toRejectPriceAdjustmentResponse(String busNo, String statusEnum) {
        RejectPriceAdjustmentResponse response = new RejectPriceAdjustmentResponse();
        response.setBusNo(busNo);
        response.setStatusEnum(statusEnum);
        return response;
    }

    /**
     * 锁定单条库存 Command。
     *
     * @param inventoryId 库存主键
     * @param busNo       调价单号
     * @param operatorId  操作人
     * @return 锁定 Command
     */
    @Override
    public LockInventoryCommand toLockInventoryCommand(Long inventoryId, String busNo, Long operatorId) {
        LockInventoryCommand command = new LockInventoryCommand();
        command.setOperatorId(operatorId);
        LockInventoryCommand.LockInventoryItemCommand item = new LockInventoryCommand.LockInventoryItemCommand();
        item.setInventoryId(inventoryId);
        item.setBusNo(busNo);
        command.setItems(List.of(item));
        return command;
    }

    /**
     * 按单号解锁 Command。
     *
     * @param busNo      调价单号
     * @param operatorId 操作人
     * @return 解锁 Command
     */
    @Override
    public UnlockByBusNoCommand toUnlockByBusNoCommand(String busNo, Long operatorId) {
        UnlockByBusNoCommand command = new UnlockByBusNoCommand();
        command.setOperatorId(operatorId);
        UnlockByBusNoCommand.UnlockByBusNoItemCommand item = new UnlockByBusNoCommand.UnlockByBusNoItemCommand();
        item.setBusNo(busNo);
        command.setItems(List.of(item));
        return command;
    }

    /**
     * 调价原因选项（已废弃：原因存自由文案，固定返回空列表，兼容旧前端字段）。
     *
     * @return 空列表
     */
    @Override
    public List<OptionDto> toAdjustmentReasonOptions() {
        return new ArrayList<>();
    }

    /**
     * 枚举选项：审批状态筛选项。
     *
     * @return 下拉选项
     */
    @Override
    public List<OptionDto> toStatusFilterOptions() {
        List<OptionDto> options = new ArrayList<>();
        for (PriceAdjustmentStatus status : PriceAdjustmentStatus.values()) {
            options.add(new OptionDto(status.getSortOrder(), status.getCode(), status.getDisplay()));
        }
        return options;
    }

    /**
     * 项目表 Tab 选项（三档：药品/耗材/诊疗；不含医疗服务）。
     *
     * @return 下拉选项
     */
    @Override
    public List<OptionDto> toItemTableTabOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(new OptionDto(1L, TableNameConstants.MED_MEDICATION_DEFINITION, "药品"));
        options.add(new OptionDto(2L, TableNameConstants.ADM_DEVICE_DEFINITION, "耗材"));
        options.add(new OptionDto(3L, TableNameConstants.WKF_ACTIVITY_DEFINITION, "诊疗活动"));
        return options;
    }

    /**
     * 项目行读模型转 DTO。
     *
     * <p>目录 enrich：名称/编码/规格/厂家，以及与选品同口径的项目种类、单位；子表批号明细一并映射。</p>
     *
     * @param line         行读模型；null 时返回 null
     * @param productByKey 产品展示映射；可 null
     * @return 行 DTO
     */
    @Override
    public PriceAdjustmentLineDto toLineDto(
        PriceAdjustmentLineQueryModel line,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        if (line == null) {
            return null;
        }
        PriceAdjustmentLineDto dto = new PriceAdjustmentLineDto();
        dto.setId(line.getId());
        dto.setBusNo(line.getBusNo());
        dto.setItemTable(line.getItemTable());
        dto.setItemTableText(this.resolveItemTableText(line.getItemTable()));
        dto.setItemId(line.getItemId());
        dto.setNewPurchaseAmount(line.getNewPurchaseAmount());
        dto.setNewSellingAmount(line.getNewSellingAmount());
        dto.setAdjustmentReason(line.getAdjustmentReason());
        dto.setAdjustmentReasonText(this.resolveAdjustmentReasonText(line.getAdjustmentReason()));
        dto.setAdjustmentTime(line.getAdjustmentTime());
        dto.setStatusEnum(line.getStatusEnum());
        dto.setStatusEnumText(this.resolveStatusText(line.getStatusEnum()));
        dto.setOperatorId(line.getOperatorId());
        dto.setOrgId(line.getOrgId());
        dto.setAdjustmentStyle(line.getAdjustmentStyle());
        dto.setAdjustmentStyleText(this.resolveAdjustmentStyleText(line.getAdjustmentStyle()));
        // 目录 enrich：名称/编码/规格/厂家 + 种类/单位（与 IF-PRC-04 选品同口径，供详情回显）
        if (productByKey != null && line.getItemTable() != null && line.getItemId() != null) {
            String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
            ProductDefinitionDisplayDto product = productByKey.get(key);
            if (product != null) {
                dto.setItemName(product.getProductName());
                dto.setItemCode(product.getProductBusNo());
                dto.setSpecification(product.getTotalVolume());
                dto.setManufacturer(product.getManufacturer());
                dto.setCategoryCodeDictText(product.getCategoryCodeDictText());
                dto.setUnitName(this.resolveUnitNameFromDisplay(product));
            }
        }
        List<PriceAdjustmentDetailDto> detailDtos = new ArrayList<>();
        if (line.getDetails() != null) {
            // 调价单回显：同批号多库房行合并为全院一条（数量/盈亏相加）
            List<PriceAdjustmentDetailQueryModel> mergedDetails =
                this.mergeDetailModelsByLotForDisplay(line.getDetails());
            for (PriceAdjustmentDetailQueryModel detail : mergedDetails) {
                PriceAdjustmentDetailDto detailDto = this.toDetailDto(detail);
                if (detailDto != null) {
                    detailDtos.add(detailDto);
                }
            }
        }
        dto.setDetails(detailDtos);
        return dto;
    }

    /**
     * 按批号合并子表读模型（调价单全院展示口径；不暴露 locationId）。
     *
     * @param details 库房+批号细行；可空
     * @return 按批号合并后的展示行；入参空时为空列表
     */
    private List<PriceAdjustmentDetailQueryModel> mergeDetailModelsByLotForDisplay(
        List<PriceAdjustmentDetailQueryModel> details
    ) {
        List<PriceAdjustmentDetailQueryModel> merged = new ArrayList<>();
        if (details == null || details.isEmpty()) {
            return merged;
        }
        Map<String, PriceAdjustmentDetailQueryModel> byLot = new LinkedHashMap<>();
        for (PriceAdjustmentDetailQueryModel detail : details) {
            // 空子表行不参与合并
            if (detail == null) {
                continue;
            }
            String lotKey = detail.getLotNumber();
            // 空批号并入同一桶，避免拆成多条空白批号
            if (lotKey == null) {
                lotKey = "";
            } else {
                lotKey = lotKey.trim();
            }
            PriceAdjustmentDetailQueryModel existing = byLot.get(lotKey);
            // 同批号首行：作为合并载体（展示不带 loc）
            if (existing == null) {
                PriceAdjustmentDetailQueryModel row = new PriceAdjustmentDetailQueryModel();
                row.setId(detail.getId());
                row.setLocationId(null);
                row.setLotNumber(detail.getLotNumber());
                row.setQuantity(detail.getQuantity());
                row.setOldPurchaseAmount(detail.getOldPurchaseAmount());
                row.setOldSellingAmount(detail.getOldSellingAmount());
                row.setPurchaseImpact(detail.getPurchaseImpact());
                row.setSellingImpact(detail.getSellingImpact());
                byLot.put(lotKey, row);
                continue;
            }
            // 同批号后续行：数量与盈亏累加；调前价保留首行非空值
            if (detail.getQuantity() != null) {
                BigDecimal qty = existing.getQuantity();
                if (qty == null) {
                    existing.setQuantity(detail.getQuantity());
                } else {
                    existing.setQuantity(qty.add(detail.getQuantity()));
                }
            }
            if (detail.getPurchaseImpact() != null) {
                BigDecimal impact = existing.getPurchaseImpact();
                if (impact == null) {
                    existing.setPurchaseImpact(detail.getPurchaseImpact());
                } else {
                    existing.setPurchaseImpact(impact.add(detail.getPurchaseImpact()));
                }
            }
            if (detail.getSellingImpact() != null) {
                BigDecimal impact = existing.getSellingImpact();
                if (impact == null) {
                    existing.setSellingImpact(detail.getSellingImpact());
                } else {
                    existing.setSellingImpact(impact.add(detail.getSellingImpact()));
                }
            }
            // 首行缺调前价时用后续行补齐（同批同价）
            if (existing.getOldPurchaseAmount() == null && detail.getOldPurchaseAmount() != null) {
                existing.setOldPurchaseAmount(detail.getOldPurchaseAmount());
            }
            if (existing.getOldSellingAmount() == null && detail.getOldSellingAmount() != null) {
                existing.setOldSellingAmount(detail.getOldSellingAmount());
            }
        }
        merged.addAll(byLot.values());
        return merged;
    }

    /**
     * 产品展示 DTO 转选品行。
     *
     * @param display 产品展示 DTO
     * @return 选品行；null 输入时返回 null
     */
    private AdjustableItemRowDto toAdjustableItemRow(ProductDefinitionDisplayDto display) {
        if (display == null || display.getProductDefinitionId() == null) {
            return null;
        }
        AdjustableItemRowDto row = new AdjustableItemRowDto();
        row.setItemTable(display.getProductTableName());
        row.setItemId(display.getProductDefinitionId());
        row.setItemCode(display.getProductBusNo());
        row.setItemName(display.getProductName());
        row.setSpecification(display.getTotalVolume());
        row.setManufacturer(display.getManufacturer());
        row.setCategoryCodeDictText(display.getCategoryCodeDictText());
        row.setUnitName(this.resolveUnitNameFromDisplay(display));
        return row;
    }

    /**
     * 从产品展示 DTO 解析单位展示名（物流包装单位；诊疗开立单位回落包装）。
     *
     * @param display 产品展示 DTO；可 null
     * @return 单位展示名；无法解析时 null
     */
    private String resolveUnitNameFromDisplay(ProductDefinitionDisplayDto display) {
        if (display == null) {
            return null;
        }
        String tableName = display.getProductTableName();
        // 物流：固定包装单位（与选品/库存数量同单位）
        if (PriceAdjustmentItemTableSupport.isLogisticsItemTable(tableName)) {
            return display.getPackageUnitCodeDictText();
        }
        // 诊疗/服务：优先开立单位，缺省回落包装单位
        String unitName = display.getDefEncounterUnitCodeDictText();
        if (StringUtils.isEmpty(unitName)) {
            unitName = display.getPackageUnitCodeDictText();
        }
        return unitName;
    }

    /**
     * 子表读模型转 DTO。
     *
     * @param detail 子表读模型
     * @return 子表 DTO；null 时返回 null
     */
    private PriceAdjustmentDetailDto toDetailDto(PriceAdjustmentDetailQueryModel detail) {
        if (detail == null) {
            return null;
        }
        PriceAdjustmentDetailDto dto = new PriceAdjustmentDetailDto();
        dto.setId(detail.getId());
        dto.setLotNumber(detail.getLotNumber());
        dto.setQuantity(detail.getQuantity());
        dto.setOldPurchaseAmount(detail.getOldPurchaseAmount());
        dto.setOldSellingAmount(detail.getOldSellingAmount());
        dto.setPurchaseImpact(detail.getPurchaseImpact());
        dto.setSellingImpact(detail.getSellingImpact());
        return dto;
    }

    /**
     * 解析审批状态展示名。
     *
     * @param statusEnum 状态 code
     * @return 展示名；未知时 null
     */
    private String resolveStatusText(String statusEnum) {
        if (StringUtils.isEmpty(statusEnum)) {
            return null;
        }
        PriceAdjustmentStatus status = EnumUtil.fromCode(PriceAdjustmentStatus.class, statusEnum.trim());
        if (status == null) {
            return null;
        }
        return status.getDisplay();
    }

    /**
     * 调价原因展示名：库内已是自由文案时原样返回；历史枚举 code 过渡期映射为展示文案。
     *
     * @param adjustmentReason 原因文案或历史 code
     * @return 展示名
     */
    private String resolveAdjustmentReasonText(String adjustmentReason) {
        if (StringUtils.isEmpty(adjustmentReason)) {
            return null;
        }
        String trimmed = adjustmentReason.trim();
        AdjustmentReason reason = EnumUtil.fromCode(AdjustmentReason.class, trimmed);
        if (reason != null) {
            return reason.getDisplay();
        }
        return trimmed;
    }

    /**
     * 解析项目表展示名。
     *
     * @param itemTable 项目表
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
        return itemTable;
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
}
