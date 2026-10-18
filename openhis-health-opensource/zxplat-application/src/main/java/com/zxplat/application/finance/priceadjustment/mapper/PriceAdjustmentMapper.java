/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustment.mapper;

import com.zxplat.application.finance.priceadjustment.dto.AdjustableItemRowDto;
import com.zxplat.application.finance.priceadjustment.dto.InTransitCheckDto;
import com.zxplat.application.finance.priceadjustment.dto.LockPreviewRowDto;
import com.zxplat.application.finance.priceadjustment.dto.PriceAdjustmentDocumentListRowDto;
import com.zxplat.application.finance.priceadjustment.dto.PriceAdjustmentLineDto;
import com.zxplat.application.finance.priceadjustment.request.ApprovePriceAdjustmentRequest;
import com.zxplat.application.finance.priceadjustment.request.PagePriceAdjustmentDocumentRequest;
import com.zxplat.application.finance.priceadjustment.request.RejectPriceAdjustmentRequest;
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
import com.zxplat.domain.custom.priceadjustment.queries.model.PriceAdjustmentDocumentQueryModel;
import com.zxplat.domain.custom.priceadjustment.queries.model.PriceAdjustmentImpactSummaryQueryModel;
import com.zxplat.domain.custom.priceadjustment.queries.model.PriceAdjustmentLineQueryModel;
import com.zxplat.domain.workflow.inventoryitem.command.LockInventoryCommand;
import com.zxplat.domain.workflow.inventoryitem.command.UnlockByBusNoCommand;
import com.zxplat.application.finance.priceadjustment.support.dto.LogisticsDetailSnapshotRowDto;
import com.zxplat.application.finance.priceadjustment.support.dto.PriceAdjustmentInTransitCheckDto;
import com.zxplat.application.finance.priceadjustment.support.dto.PriceAdjustmentLockPreviewRowDto;
import com.zxplat.application.shared.product.ProductDefinitionPageQuery;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.framework.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 价格调整应用层映射：Request/Response/DTO 与领域 Command/QueryModel 之间的字段搬运。
 *
 * <p>禁止承载业务流程判断与写前 Gate；编排逻辑在 {@link com.zxplat.application.finance.priceadjustment.service.PriceAdjustmentAppService}。</p>
 */
public interface PriceAdjustmentMapper {

    /**
     * 装配页初始化响应。
     *
     * @param adjustmentReasonOptions 调价原因下拉
     * @param statusFilterOptions       审批状态筛选项
     * @param itemTableTabOptions       项目表 Tab 选项
     * @return 初始化响应
     */
    InitPriceAdjustmentResponse toInitPriceAdjustmentResponse(
        List<OptionDto> adjustmentReasonOptions,
        List<OptionDto> statusFilterOptions,
        List<OptionDto> itemTableTabOptions);

    /**
     * 列表请求转领域列表 Query。
     *
     * @param request 列表分页请求；调用方保证非空
     * @param orgId   操作科室收窄；{@code null} 表示不按科室过滤（历史调价单全院可见）
     * @return 领域列表 Query
     */
    PriceAdjustmentDocumentListQuery toDocumentListQuery(
        PagePriceAdjustmentDocumentRequest request,
        Long orgId);

    /**
     * 单据读模型列表转列表聚合行 DTO。
     *
     * @param busNoPageOrder 当前页单号顺序
     * @param documents      单据读模型
     * @param impactByBusNo  单号到盈亏汇总的映射；可空
     * @return 列表行 DTO
     */
    List<PriceAdjustmentDocumentListRowDto> toDocumentListRows(
        List<String> busNoPageOrder,
        List<PriceAdjustmentDocumentQueryModel> documents,
        Map<String, PriceAdjustmentImpactSummaryQueryModel> impactByBusNo);

    /**
     * 装配列表分页响应。
     *
     * @param mergedTotal 去重总单数
     * @param pageNo      页码（1-based）
     * @param pageSize    页大小
     * @param records     当前页行
     * @return 分页响应
     */
    PagePriceAdjustmentDocumentResponse toPagePriceAdjustmentDocumentResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<PriceAdjustmentDocumentListRowDto> records);

    /**
     * 单据读模型转详情响应。
     *
     * @param document  单据读模型；null 时返回 null
     * @param impact    盈亏汇总；可 null
     * @param productByKey 产品展示映射；可 null
     * @return 详情响应
     */
    PriceAdjustmentDocumentResponse toPriceAdjustmentDocumentResponse(
        PriceAdjustmentDocumentQueryModel document,
        PriceAdjustmentImpactSummaryQueryModel impact,
        Map<String, ProductDefinitionDisplayDto> productByKey);

    /**
     * 选品搜索请求转产品目录分页 Query。
     *
     * @param request 选品请求；调用方保证非空
     * @return 产品目录分页 Query
     */
    ProductDefinitionPageQuery toProductDefinitionPageQuery(SearchAdjustableItemRequest request);

    /**
     * 产品目录分页转选品搜索响应。
     *
     * @param page 产品展示分页
     * @return 选品分页响应
     */
    SearchAdjustableItemsResponse toSearchAdjustableItemsResponse(Page<ProductDefinitionDisplayDto> page);

    /**
     * 医疗服务实体列表转选品分页响应（应用层内存分页后调用）。
     *
     * @param rows       当前页行
     * @param totalCount 总条数
     * @param pageNo     页码（1-based）
     * @param pageSize   页大小
     * @return 选品分页响应
     */
    SearchAdjustableItemsResponse toSearchAdjustableItemsResponse(
        List<AdjustableItemRowDto> rows,
        long totalCount,
        int pageNo,
        int pageSize);

    /**
     * 物流快照行转 IF-PRC-05 响应。
     *
     * @param rows querysupport 快照行
     * @return 快照响应
     */
    LoadLogisticsAdjustmentSnapshotResponse toLoadLogisticsAdjustmentSnapshotResponse(
        List<LogisticsDetailSnapshotRowDto> rows);

    /**
     * 调前售价转 IF-PRC-06 响应。
     *
     * @param oldSellingAmount 当前 ACTIVE SALE 金额；可 null
     * @return 调前价响应
     */
    LoadClinicalOldPriceResponse toLoadClinicalOldPriceResponse(BigDecimal oldSellingAmount);

    /**
     * 锁预览行转应用 DTO 列表。
     *
     * @param rows querysupport 锁预览行
     * @return 锁预览 DTO 列表
     */
    List<LockPreviewRowDto> toLockPreviewRows(List<PriceAdjustmentLockPreviewRowDto> rows);

    /**
     * 锁预览 DTO 列表转 IF-PRC-07 响应。
     *
     * @param rows 锁预览 DTO
     * @return 锁预览响应
     */
    PreviewSubmitLocksResponse toPreviewSubmitLocksResponse(List<LockPreviewRowDto> rows);

    /**
     * querysupport 在途结果转应用 DTO。
     *
     * @param source querysupport 在途结果；null 时返回 null
     * @return 在途 DTO
     */
    InTransitCheckDto toInTransitCheckDto(PriceAdjustmentInTransitCheckDto source);

    /**
     * 在途 DTO 转 IF-PRC-08 响应。
     *
     * @param check 在途 DTO
     * @return 在途预检响应
     */
    CheckInTransitForSubmitResponse toCheckInTransitForSubmitResponse(InTransitCheckDto check);

    /**
     * pending 互斥查询结果转 IF-PRC-12 响应。
     *
     * @param existsPending 是否存在 pending 调价单
     * @return 互斥查询响应
     */
    ExistsPendingByItemResponse toExistsPendingByItemResponse(Boolean existsPending);

    /**
     * 保存草稿请求转领域 Command。
     *
     * @param request    保存请求；调用方保证非空
     * @param operatorId 操作人
     * @param orgId      会话操作科室（制单机构）
     * @return 保存草稿 Command
     */
    SavePriceAdjustmentDraftCommand toSavePriceAdjustmentDraftCommand(
        SavePriceAdjustmentDraftRequest request,
        Long operatorId,
        Long orgId);

    /**
     * 保存成功后装配响应。
     *
     * @param busNo      调价单号
     * @param statusEnum 状态 code
     * @return 保存响应
     */
    SavePriceAdjustmentDraftResponse toSavePriceAdjustmentDraftResponse(String busNo, String statusEnum);

    /**
     * 提交审批 Command（draft 路径）。
     *
     * @param busNo               调价单号
     * @param submitterId         提交人
     * @param lockedInventoryCount 加锁行数
     * @return 提交 Command
     */
    SubmitPriceAdjustmentCommand toSubmitPriceAdjustmentCommand(
        String busNo,
        Long submitterId,
        Integer lockedInventoryCount);

    /**
     * 再次提交 Command（rejected 路径）。
     *
     * @param busNo               调价单号
     * @param submitterId         提交人
     * @param lockedInventoryCount 加锁行数
     * @return 再次提交 Command
     */
    ResubmitPriceAdjustmentCommand toResubmitPriceAdjustmentCommand(
        String busNo,
        Long submitterId,
        Integer lockedInventoryCount);

    /**
     * 提交成功后装配响应。
     *
     * @param busNo      调价单号
     * @param statusEnum 状态 code
     * @return 提交响应
     */
    SubmitPriceAdjustmentResponse toSubmitPriceAdjustmentResponse(String busNo, String statusEnum);

    /**
     * 撤销草稿 Command。
     *
     * @param busNo      调价单号
     * @param operatorId 操作人
     * @return 撤销 Command
     */
    WithdrawPriceAdjustmentDraftCommand toWithdrawPriceAdjustmentDraftCommand(String busNo, Long operatorId);

    /**
     * 撤销成功后装配响应。
     *
     * @param busNo      调价单号
     * @param statusEnum 状态 code
     * @return 撤销响应
     */
    WithdrawPriceAdjustmentDraftResponse toWithdrawPriceAdjustmentDraftResponse(String busNo, String statusEnum);

    /**
     * 审批同意 Command。
     *
     * @param request    审批请求；调用方保证非空
     * @param approverId 审批人
     * @param operatorId 解锁审计操作人
     * @return 审批 Command
     */
    ApprovePriceAdjustmentCommand toApprovePriceAdjustmentCommand(
        ApprovePriceAdjustmentRequest request,
        Long approverId,
        Long operatorId);

    /**
     * 审批成功后装配响应。
     *
     * @param busNo      调价单号
     * @param statusEnum 状态 code
     * @return 审批响应
     */
    ApprovePriceAdjustmentResponse toApprovePriceAdjustmentResponse(String busNo, String statusEnum);

    /**
     * 审批驳回 Command。
     *
     * @param request    驳回请求；调用方保证非空
     * @param approverId 审批人
     * @param operatorId 解锁审计操作人
     * @return 驳回 Command
     */
    RejectPriceAdjustmentCommand toRejectPriceAdjustmentCommand(
        RejectPriceAdjustmentRequest request,
        Long approverId,
        Long operatorId);

    /**
     * 驳回成功后装配响应。
     *
     * @param busNo      调价单号
     * @param statusEnum 状态 code
     * @return 驳回响应
     */
    RejectPriceAdjustmentResponse toRejectPriceAdjustmentResponse(String busNo, String statusEnum);

    /**
     * 锁定单条库存 Command。
     *
     * @param inventoryId 库存主键
     * @param busNo       调价单号
     * @param operatorId  操作人
     * @return 锁定 Command
     */
    LockInventoryCommand toLockInventoryCommand(Long inventoryId, String busNo, Long operatorId);

    /**
     * 按单号解锁 Command。
     *
     * @param busNo      调价单号
     * @param operatorId 操作人
     * @return 解锁 Command
     */
    UnlockByBusNoCommand toUnlockByBusNoCommand(String busNo, Long operatorId);

    /**
     * 枚举选项：调价原因（已废弃，固定空列表；原因手写文案）。
     *
     * @return 空列表
     */
    List<OptionDto> toAdjustmentReasonOptions();

    /**
     * 枚举选项：审批状态筛选项。
     *
     * @return 下拉选项
     */
    List<OptionDto> toStatusFilterOptions();

    /**
     * 项目表 Tab 选项（三档：药品/耗材/诊疗；不含医疗服务）。
     *
     * @return 下拉选项
     */
    List<OptionDto> toItemTableTabOptions();

    /**
     * 项目行读模型转 DTO（含目录 enrich：种类/单位与选品同口径；子表批号明细）。
     *
     * @param line         行读模型；null 时返回 null
     * @param productByKey 产品展示映射；可 null
     * @return 行 DTO
     */
    PriceAdjustmentLineDto toLineDto(
        PriceAdjustmentLineQueryModel line,
        Map<String, ProductDefinitionDisplayDto> productByKey);
}
