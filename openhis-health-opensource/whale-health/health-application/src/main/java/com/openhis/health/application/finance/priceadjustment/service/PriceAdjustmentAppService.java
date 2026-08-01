/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustment.service;

import com.openhis.health.application.finance.priceadjustment.dto.AdjustableItemRowDto;
import com.openhis.health.application.finance.priceadjustment.dto.InTransitCheckDto;
import com.openhis.health.application.finance.priceadjustment.dto.LockPreviewRowDto;
import com.openhis.health.application.finance.priceadjustment.dto.PriceAdjustmentDocumentListRowDto;
import com.openhis.health.application.finance.priceadjustment.mapper.PriceAdjustmentMapper;
import com.openhis.health.application.finance.priceadjustment.support.PriceAdjustmentDraftPricingAssembler;
import com.openhis.health.application.finance.priceadjustment.support.PriceAdjustmentLogisticsDetailLocationExpandSupport;
import com.openhis.health.application.finance.priceadjustment.request.ApprovePriceAdjustmentRequest;
import com.openhis.health.application.finance.priceadjustment.request.CheckInTransitForSubmitRequest;
import com.openhis.health.application.finance.priceadjustment.request.ExistsPendingByItemRequest;
import com.openhis.health.application.finance.priceadjustment.request.GetPriceAdjustmentDocumentRequest;
import com.openhis.health.application.finance.priceadjustment.request.InitPriceAdjustmentRequest;
import com.openhis.health.application.finance.priceadjustment.request.LoadClinicalOldPriceRequest;
import com.openhis.health.application.finance.priceadjustment.request.LoadLogisticsAdjustmentSnapshotRequest;
import com.openhis.health.application.finance.priceadjustment.request.PagePriceAdjustmentDocumentRequest;
import com.openhis.health.application.finance.priceadjustment.request.PreviewSubmitLocksRequest;
import com.openhis.health.application.finance.priceadjustment.request.RejectPriceAdjustmentRequest;
import com.openhis.health.application.finance.priceadjustment.request.SavePriceAdjustmentDraftRequest;
import com.openhis.health.application.finance.priceadjustment.request.SearchAdjustableItemRequest;
import com.openhis.health.application.finance.priceadjustment.request.SubmitPriceAdjustmentRequest;
import com.openhis.health.application.finance.priceadjustment.request.WithdrawPriceAdjustmentDraftRequest;
import com.openhis.health.application.finance.priceadjustment.response.ApprovePriceAdjustmentResponse;
import com.openhis.health.application.finance.priceadjustment.response.CheckInTransitForSubmitResponse;
import com.openhis.health.application.finance.priceadjustment.response.ExistsPendingByItemResponse;
import com.openhis.health.application.finance.priceadjustment.response.InitPriceAdjustmentResponse;
import com.openhis.health.application.finance.priceadjustment.response.LoadClinicalOldPriceResponse;
import com.openhis.health.application.finance.priceadjustment.response.LoadLogisticsAdjustmentSnapshotResponse;
import com.openhis.health.application.finance.priceadjustment.response.PagePriceAdjustmentDocumentResponse;
import com.openhis.health.application.finance.priceadjustment.response.PreviewSubmitLocksResponse;
import com.openhis.health.application.finance.priceadjustment.response.PriceAdjustmentDocumentResponse;
import com.openhis.health.application.finance.priceadjustment.response.RejectPriceAdjustmentResponse;
import com.openhis.health.application.finance.priceadjustment.response.SavePriceAdjustmentDraftResponse;
import com.openhis.health.application.finance.priceadjustment.response.SearchAdjustableItemsResponse;
import com.openhis.health.application.finance.priceadjustment.response.SubmitPriceAdjustmentResponse;
import com.openhis.health.application.finance.priceadjustment.response.WithdrawPriceAdjustmentDraftResponse;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.custom.priceadjustment.command.ApprovePriceAdjustmentCommand;
import com.openhis.health.domain.custom.priceadjustment.command.RejectPriceAdjustmentCommand;
import com.openhis.health.domain.custom.priceadjustment.command.ResubmitPriceAdjustmentCommand;
import com.openhis.health.domain.custom.priceadjustment.command.SavePriceAdjustmentDraftCommand;
import com.openhis.health.domain.custom.priceadjustment.command.SubmitPriceAdjustmentCommand;
import com.openhis.health.domain.custom.priceadjustment.command.WithdrawPriceAdjustmentDraftCommand;
import com.openhis.health.domain.custom.priceadjustment.entity.PriceAdjustment;
import com.openhis.health.domain.custom.priceadjustment.queries.PriceAdjustmentDocumentListQuery;
import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentDocumentQueryModel;
import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentImpactSummaryQueryModel;
import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentLineQueryModel;
import com.openhis.health.domain.custom.priceadjustment.service.PriceAdjustmentDomainService;
import com.openhis.health.domain.custom.priceadjustment.service.PriceAdjustmentQueryService;
import com.openhis.health.domain.custom.priceadjustment.support.PriceAdjustmentItemTableSupport;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.PriceAdjustmentStatus;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.inventoryitem.command.LockInventoryCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.UnlockByBusNoCommand;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.infrastructure.platform.authorization.FinanceApprovalRoleGate;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.finance.priceadjustment.support.PriceAdjustmentApprovePricingOrchestration;
import com.openhis.health.application.finance.priceadjustment.support.PriceAdjustmentInTransitChecker;
import com.openhis.health.application.finance.priceadjustment.support.PriceAdjustmentLockPreviewLoader;
import com.openhis.health.application.finance.priceadjustment.support.PriceAdjustmentLogisticsSnapshotAssembler;
import com.openhis.health.application.finance.priceadjustment.support.dto.LogisticsDetailSnapshotRowDto;
import com.openhis.health.application.finance.priceadjustment.support.dto.PriceAdjustmentInTransitCheckDto;
import com.openhis.health.application.finance.priceadjustment.support.dto.PriceAdjustmentLockPreviewRowDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogPageLoader;
import com.openhis.health.application.shared.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 财务管理 · 价格调整应用编排（AS-FIN-PRC-001）。
 *
 * <p>职责边界：单页调价全链路 IF-PRC-01～14；写侧委托 {@link PriceAdjustmentDomainService} 与库存/定价白名单方法；
 * 跨聚合读组装委托 {@code module.querysupport.priceadjustment}。</p>
 *
 * <p>接入说明：由 AppServiceControllerRegistrar 注册为
 * {@code POST /api/app/priceAdjustment/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceAdjustmentAppService {

    /** 会话操作人解析（写路径 operatorId/submitterId/approverId）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 会话操作科室解析（制单机构 org_id、列表收窄、医疗服务选品）。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;

    /** 财务审批 Gate（approve/reject 须持有用户管理分配的 ROLE_FINANCE_APPROVER）。 */
    private final FinanceApprovalRoleGate financeApprovalRoleGate;

    /** 分页窗口 Gate（列表/选品 IF）。 */
    private final PageWindowGate pageWindowGate;

    /** 调价写侧领域服务。 */
    private final PriceAdjustmentDomainService priceAdjustmentDomainService;

    /** 调价读侧查询服务。 */
    private final PriceAdjustmentQueryService priceAdjustmentQueryService;

    /** 库存写侧（提交加锁 / 审批解锁）。 */
    private final InventoryItemDomainService inventoryItemDomainService;

    /** 库存读侧（选品可用量汇总）。 */
    private final InventoryItemQueryService inventoryItemQueryService;

    /** 定价读侧（诊疗调前价）。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /** 医疗服务读侧（选品 Tab）。 */
    private final HealthcareServiceQueryService healthcareServiceQueryService;

    /** 医务人员读侧（列表/详情展示名 enrich）。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 产品目录分页加载（药/耗/诊疗选品）。 */
    private final ProductDefinitionCatalogPageLoader productDefinitionCatalogPageLoader;

    /** 产品定义批量加载（详情/列表 enrich）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /** 物流批号快照组装。 */
    private final PriceAdjustmentLogisticsSnapshotAssembler logisticsSnapshotAssembler;

    /** 提交前在途预检。 */
    private final PriceAdjustmentInTransitChecker inTransitChecker;

    /** 提交锁预览加载。 */
    private final PriceAdjustmentLockPreviewLoader lockPreviewLoader;

    /** 审批同意定价落库编排。 */
    private final PriceAdjustmentApprovePricingOrchestration approvePricingOrchestration;

    /** Request/Response 与 Command 映射。 */
    private final PriceAdjustmentMapper priceAdjustmentMapper;

    /** 草稿落库前调前价权威回填。 */
    private final PriceAdjustmentDraftPricingAssembler draftPricingSupport;

    /** 物流草稿子表按库房+批号展开（日结按库；回显仍合并批号）。 */
    private final PriceAdjustmentLogisticsDetailLocationExpandSupport logisticsDetailLocationExpandSupport;

    /**
     * 价格调整页初始化（IF-PRC-01）。
     *
     * @param request 初始化请求
     * @return 下拉选项与租户定价策略
     */
    public InitPriceAdjustmentResponse init(InitPriceAdjustmentRequest request) {
        // Gate：只读页初始化，不写库、不依赖写侧操作人
        // Pre：加载枚举选项（操作科室由会话解析，不下发科室下拉）
        // Asm：映射页初始化响应
        return this.priceAdjustmentMapper.toInitPriceAdjustmentResponse(
            this.priceAdjustmentMapper.toAdjustmentReasonOptions(),
            this.priceAdjustmentMapper.toStatusFilterOptions(),
            this.priceAdjustmentMapper.toItemTableTabOptions()
        );
    }

    /**
     * 调价单列表分页（IF-PRC-02）。
     *
     * @param request 列表筛选与分页
     * @return 按 bus_no 聚合的分页结果
     * @throws BusinessException 操作科室或分页参数非法时
     */
    public PagePriceAdjustmentDocumentResponse pageDocuments(PagePriceAdjustmentDocumentRequest request) {
        // Gate：请求体与分页窗口
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "请求不能为空");
        }
        this.pageWindowGate.requirePositivePageWindow(
            request,
            ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT,
            "分页参数不能为空"
        );
        // Gate：操作科室须从当前登录会话解析（B.6.5，禁止 Request 伪造 org_id）
        Long orgId = this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT,
            "操作科室不能为空"
        );
        // Pre：组装列表 Query
        PriceAdjustmentDocumentListQuery listQuery = this.priceAdjustmentMapper.toDocumentListQuery(request, orgId);
        // Cmd：领域分页统计与拉取
        long mergedTotal = this.priceAdjustmentQueryService.countDistinctBusNo(listQuery);
        List<String> busNos = this.priceAdjustmentQueryService.listDistinctBusNoPage(listQuery);
        List<PriceAdjustment> lines = this.priceAdjustmentQueryService.listLinesByBusNosWithFilter(listQuery, busNos);
        List<PriceAdjustmentDocumentQueryModel> documents =
            this.priceAdjustmentQueryService.listDocumentsByBusNos(busNos, lines);
        Map<String, PriceAdjustmentImpactSummaryQueryModel> impactByBusNo = new HashMap<>();
        for (String busNo : busNos) {
            if (StringUtils.isEmpty(busNo)) {
                continue;
            }
            // Pre：当前页逐单汇总盈亏（列表展示列）
            PriceAdjustmentImpactSummaryQueryModel impact =
                this.priceAdjustmentQueryService.sumImpactByBusNo(busNo.trim());
            impactByBusNo.put(busNo.trim(), impact);
        }
        List<PriceAdjustmentDocumentListRowDto> records =
            this.priceAdjustmentMapper.toDocumentListRows(busNos, documents, impactByBusNo);
        this.enrichDocumentListPractitionerNames(records);
        int pageNo = request.getPageNo().intValue();
        int pageSize = listQuery.getBoundedPageSize();
        // Asm：映射分页响应
        return this.priceAdjustmentMapper.toPagePriceAdjustmentDocumentResponse(
            mergedTotal,
            pageNo,
            pageSize,
            records
        );
    }

    /**
     * 调价单详情（IF-PRC-03）。
     *
     * @param request 详情请求
     * @return 整单读模型 + enrich
     * @throws BusinessException 单号不存在时
     */
    public PriceAdjustmentDocumentResponse getDocument(GetPriceAdjustmentDocumentRequest request) {
        // Gate：单号非空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "调价单号不能为空");
        }
        String busNo = request.getBusNo().trim();
        // Pre：加载整单读模型与盈亏汇总
        PriceAdjustmentDocumentQueryModel document = this.priceAdjustmentQueryService.getDocumentByBusNo(busNo);
        PriceAdjustmentImpactSummaryQueryModel impact = this.priceAdjustmentQueryService.sumImpactByBusNo(busNo);
        Map<String, ProductDefinitionDisplayDto> productByKey = this.loadProductDisplayMap(document);
        PriceAdjustmentDocumentResponse response =
            this.priceAdjustmentMapper.toPriceAdjustmentDocumentResponse(document, impact, productByKey);
        // Asm：批量 enrich 人员展示名
        this.enrichDocumentDetailPractitionerNames(response);
        return response;
    }

    /**
     * 可选调价项目搜索（IF-PRC-04）。
     *
     * @param request 项目表、关键字与分页
     * @return 选品分页结果
     * @throws BusinessException 非法项目表或会话操作科室缺失时
     */
    public SearchAdjustableItemsResponse searchAdjustableItems(SearchAdjustableItemRequest request) {
        // Gate：请求体、项目表与分页
        if (request == null || StringUtils.isEmpty(request.getItemTable())) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "项目表不能为空");
        }
        this.pageWindowGate.requirePositivePageWindow(
            request,
            ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT,
            "分页参数不能为空"
        );
        String itemTable = request.getItemTable().trim();
        // 业务分支：医疗服务 Tab 走 HealthcareService 列表过滤
        if (TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(itemTable)) {
            return this.searchHealthcareServices(request);
        }
        if (!PriceAdjustmentItemTableSupport.isLogisticsItemTable(itemTable)
            && !PriceAdjustmentItemTableSupport.isClinicalItemTable(itemTable)) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "不支持的项目表选品");
        }
        // Pre：产品目录分页 Query
        ProductDefinitionPageQuery pageQuery = this.priceAdjustmentMapper.toProductDefinitionPageQuery(request);
        Page<ProductDefinitionDisplayDto> page =
            this.productDefinitionCatalogPageLoader.pageProductDefinition(pageQuery);
        // Asm：映射选品分页响应
        SearchAdjustableItemsResponse response = this.priceAdjustmentMapper.toSearchAdjustableItemsResponse(page);
        // Asm 续：目录当前价批量回填（新开单选品读路径，BR-PRICE-SRC-001）
        this.enrichAdjustableItemCatalogCurrentPrices(response.getContent(), itemTable);
        // Asm 续：物流 Tab 批量回填全院可用库存数量总和
        this.enrichAdjustableItemAvailableQuantities(response.getContent(), itemTable);
        return response;
    }

    /**
     * 物流批号快照加载（IF-PRC-05）。
     *
     * @param request 项目表与项目主键列表
     * @return 批号明细草稿结构
     * @throws BusinessException 非物流项目表时
     */
    public LoadLogisticsAdjustmentSnapshotResponse loadLogisticsSnapshot(
        LoadLogisticsAdjustmentSnapshotRequest request) {
        // Gate：物流表与项目列表
        if (request == null || StringUtils.isEmpty(request.getItemTable())) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "项目表不能为空");
        }
        if (request.getItemIds() == null || request.getItemIds().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "项目列表不能为空");
        }
        String itemTable = request.getItemTable().trim();
        if (!PriceAdjustmentItemTableSupport.isLogisticsItemTable(itemTable)) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "仅物流项目支持批号快照");
        }
        // Cmd：querysupport 组装全院库存批号 × 调前价
        List<LogisticsDetailSnapshotRowDto> rows =
            this.logisticsSnapshotAssembler.assemble(itemTable, request.getItemIds());
        // Asm：映射快照响应
        return this.priceAdjustmentMapper.toLoadLogisticsAdjustmentSnapshotResponse(rows);
    }

    /**
     * 诊疗调前售价加载（IF-PRC-06）。
     *
     * @param request 项目表与项目主键
     * @return 建议调前售价
     * @throws BusinessException 非诊疗项目表时
     */
    public LoadClinicalOldPriceResponse loadClinicalOldPrice(LoadClinicalOldPriceRequest request) {
        // Gate：诊疗表与项目主键
        if (request == null || StringUtils.isEmpty(request.getItemTable()) || request.getItemId() == null) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "项目参数不能为空");
        }
        String itemTable = request.getItemTable().trim();
        if (!PriceAdjustmentItemTableSupport.isClinicalItemTable(itemTable)) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "仅诊疗项目支持调前售价加载");
        }
        // Pre：读当前 ACTIVE SALE 定价
        BigDecimal oldSellingAmount =
            this.chargeItemDefinitionQueryService.resolveFirstSalePriceValue(itemTable, request.getItemId());
        // Asm：映射调前价响应（允许 null，前端可手填）
        return this.priceAdjustmentMapper.toLoadClinicalOldPriceResponse(oldSellingAmount);
    }

    /**
     * 提交锁预览（IF-PRC-07）。
     *
     * @param request 调价单号
     * @return 将锁库存行及冲突单号
     * @throws BusinessException 单号为空时
     */
    public PreviewSubmitLocksResponse previewSubmitLocks(PreviewSubmitLocksRequest request) {
        // Gate：单号非空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "调价单号不能为空");
        }
        // Pre：querysupport 加载锁预览行
        List<PriceAdjustmentLockPreviewRowDto> previewRows =
            this.lockPreviewLoader.loadByBusNo(request.getBusNo().trim());
        List<LockPreviewRowDto> rows = this.priceAdjustmentMapper.toLockPreviewRows(previewRows);
        // Asm：映射锁预览响应
        return this.priceAdjustmentMapper.toPreviewSubmitLocksResponse(rows);
    }

    /**
     * 提交前在途预检（IF-PRC-08）。
     *
     * @param request 调价单号
     * @return 在途阻断结果
     * @throws BusinessException 单号为空时
     */
    public CheckInTransitForSubmitResponse checkInTransitForSubmit(CheckInTransitForSubmitRequest request) {
        // Gate：单号非空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "调价单号不能为空");
        }
        // Pre：querysupport 在途互斥检查
        PriceAdjustmentInTransitCheckDto checkResult =
            this.inTransitChecker.checkByBusNo(request.getBusNo().trim());
        InTransitCheckDto check = this.priceAdjustmentMapper.toInTransitCheckDto(checkResult);
        // Asm：映射在途预检响应
        return this.priceAdjustmentMapper.toCheckInTransitForSubmitResponse(check);
    }

    /**
     * 保存调价草稿（IF-PRC-09）。
     *
     * @param request 整单主从表
     * @return 调价单号与 draft 状态
     * @throws BusinessException 操作人无法解析、会话操作科室缺失或领域校验失败时
     */
    public SavePriceAdjustmentDraftResponse saveDraft(SavePriceAdjustmentDraftRequest request) {
        // Gate：请求体非空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "请求不能为空");
        }
        // Pre：解析操作人与制单机构（均来自会话，不经 Request 代传）
        Long operatorId = this.currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT,
            "操作人不能为空"
        );
        Long orgId = this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT,
            "操作科室不能为空"
        );
        SavePriceAdjustmentDraftCommand command =
            this.priceAdjustmentMapper.toSavePriceAdjustmentDraftCommand(request, operatorId, orgId);
        // Pre：调前价由读侧定价回填，目标价仅刻度（禁止信任请求体 old*）
        this.draftPricingSupport.applyAuthoritativeDraftPricing(command);
        // Pre：物流批号按当前可用库存拆成库房+批号行（无库存则 loc 空）；不改盈亏公式
        this.logisticsDetailLocationExpandSupport.expandLogisticsDetailsByLocation(command);
        // Cmd：领域保存草稿（整单覆盖主从表，不加锁）
        String busNo = this.priceAdjustmentDomainService.savePriceAdjustmentDraft(command);
        // Asm：返回单号与 draft 状态
        return this.priceAdjustmentMapper.toSavePriceAdjustmentDraftResponse(
            busNo,
            PriceAdjustmentStatus.DRAFT.getCode()
        );
    }

    /**
     * 提交调价审批（IF-PRC-10）。
     *
     * @param request 调价单号
     * @return 单号与 pending 状态
     * @throws BusinessException 状态/在途/锁冲突不允许提交时
     */
    public SubmitPriceAdjustmentResponse submitForApproval(SubmitPriceAdjustmentRequest request) {
        // Gate：单号非空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "调价单号不能为空");
        }
        String busNo = request.getBusNo().trim();
        Long submitterId = this.currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT,
            "提交人不能为空"
        );
        // Pre：加载整单读模型
        PriceAdjustmentDocumentQueryModel document = this.priceAdjustmentQueryService.getDocumentByBusNo(busNo);
        // Gate：仅 draft/rejected 可提交
        this.assertEditableForSubmit(document.getStatusEnum());
        boolean logistics = this.isLogisticsDocument(document);
        int lockedInventoryCount = 0;
        if (logistics) {
            // Gate：在途预检须通过（BR-PRC-SUB-003）
            PriceAdjustmentInTransitCheckDto inTransit = this.inTransitChecker.checkByBusNo(busNo);
            if (inTransit.isBlocked()) {
                String message = inTransit.getReasonMessage();
                if (StringUtils.isEmpty(message)) {
                    message = "存在在途医嘱/发药，无法提交调价";
                }
                throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_ITEM_IN_TRANSIT, message);
            }
            // Cmd：逐库存行加锁（遇冲突整单失败回滚）
            lockedInventoryCount = this.lockInventoriesForSubmit(busNo, submitterId);
        }
        // Cmd：领域 submit 或 resubmit
        if (PriceAdjustmentStatus.REJECTED.getCode().equals(document.getStatusEnum())) {
            ResubmitPriceAdjustmentCommand resubmitCommand =
                this.priceAdjustmentMapper.toResubmitPriceAdjustmentCommand(busNo, submitterId, lockedInventoryCount);
            this.priceAdjustmentDomainService.resubmitPriceAdjustmentForApproval(resubmitCommand);
        } else {
            SubmitPriceAdjustmentCommand submitCommand =
                this.priceAdjustmentMapper.toSubmitPriceAdjustmentCommand(busNo, submitterId, lockedInventoryCount);
            this.priceAdjustmentDomainService.submitPriceAdjustmentForApproval(submitCommand);
        }
        // Asm：返回 pending 状态
        return this.priceAdjustmentMapper.toSubmitPriceAdjustmentResponse(
            busNo,
            PriceAdjustmentStatus.PENDING.getCode()
        );
    }

    /**
     * 撤销调价草稿（IF-PRC-11）。
     *
     * @param request 调价单号
     * @return 单号与 deprecated 状态
     * @throws BusinessException 非 draft 或操作人无法解析时
     */
    public WithdrawPriceAdjustmentDraftResponse withdrawDraft(WithdrawPriceAdjustmentDraftRequest request) {
        // Gate：单号非空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "调价单号不能为空");
        }
        String busNo = request.getBusNo().trim();
        Long operatorId = this.currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT,
            "操作人不能为空"
        );
        WithdrawPriceAdjustmentDraftCommand command =
            this.priceAdjustmentMapper.toWithdrawPriceAdjustmentDraftCommand(busNo, operatorId);
        // Cmd：领域撤销草稿 → deprecated
        this.priceAdjustmentDomainService.withdrawPriceAdjustmentDraft(command);
        // Asm：返回 deprecated 状态
        return this.priceAdjustmentMapper.toWithdrawPriceAdjustmentDraftResponse(
            busNo,
            PriceAdjustmentStatus.DEPRECATED.getCode()
        );
    }

    /**
     * 项目 pending 互斥查询（IF-PRC-12）。
     *
     * @param request 项目表与项目主键
     * @return 是否存在 pending 调价单
     */
    public ExistsPendingByItemResponse existsPendingByItem(ExistsPendingByItemRequest request) {
        // Gate：参数非法时按领域口径返回 false，供外部入口降级为不阻断
        if (request == null || StringUtils.isEmpty(request.getItemTable()) || request.getItemId() == null) {
            return this.priceAdjustmentMapper.toExistsPendingByItemResponse(Boolean.FALSE);
        }
        // Pre：调用调价读侧 pending 互斥查询
        Boolean pending = this.priceAdjustmentQueryService.existsPendingByItem(
            request.getItemTable().trim(),
            request.getItemId()
        );
        // Asm：映射阻断查询响应
        return this.priceAdjustmentMapper.toExistsPendingByItemResponse(pending);
    }

    /**
     * 审批同意（IF-PRC-13）。
     *
     * @param request 单号与可选审批意见
     * @return 单号与 approved 状态
     * @throws BusinessException 非 pending 时
     */
    public ApprovePriceAdjustmentResponse approve(ApprovePriceAdjustmentRequest request) {
        // Gate：调价审批须财务审批岗位
        this.financeApprovalRoleGate.assertFinanceApprovalRole();
        // Gate：单号非空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "调价单号不能为空");
        }
        String busNo = request.getBusNo().trim();
        Long approverId = this.currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT,
            "审批人不能为空"
        );
        // Pre：加载整单并校验 pending
        PriceAdjustmentDocumentQueryModel document = this.priceAdjustmentQueryService.getDocumentByBusNo(busNo);
        this.assertPending(document.getStatusEnum());
        // Cmd：停用旧价并新增 ACTIVE 定价行
        this.approvePricingOrchestration.applyApprovedPricing(busNo, approverId);
        // Cmd：物流单解锁库存
        if (this.isLogisticsDocument(document)) {
            UnlockByBusNoCommand unlockCommand = this.priceAdjustmentMapper.toUnlockByBusNoCommand(busNo, approverId);
            this.inventoryItemDomainService.unlockByBusNo(unlockCommand);
        }
        ApprovePriceAdjustmentCommand approveCommand =
            this.priceAdjustmentMapper.toApprovePriceAdjustmentCommand(request, approverId, approverId);
        // Cmd：领域迁移 approved
        this.priceAdjustmentDomainService.approvePriceAdjustment(approveCommand);
        // Asm：返回 approved 状态
        return this.priceAdjustmentMapper.toApprovePriceAdjustmentResponse(
            busNo,
            PriceAdjustmentStatus.APPROVED.getCode()
        );
    }

    /**
     * 审批驳回（IF-PRC-14）。
     *
     * @param request 单号与驳回原因
     * @return 单号与 rejected 状态
     * @throws BusinessException 非 pending 或驳回原因为空时
     */
    public RejectPriceAdjustmentResponse reject(RejectPriceAdjustmentRequest request) {
        // Gate：调价驳回须财务审批岗位
        this.financeApprovalRoleGate.assertFinanceApprovalRole();
        // Gate：单号与驳回原因
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "调价单号不能为空");
        }
        if (StringUtils.isEmpty(request.getApproveComment())) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "驳回须填写原因");
        }
        String busNo = request.getBusNo().trim();
        Long approverId = this.currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT,
            "审批人不能为空"
        );
        // Pre：加载整单并校验 pending
        PriceAdjustmentDocumentQueryModel document = this.priceAdjustmentQueryService.getDocumentByBusNo(busNo);
        this.assertPending(document.getStatusEnum());
        // Cmd：物流单先解锁库存
        if (this.isLogisticsDocument(document)) {
            UnlockByBusNoCommand unlockCommand = this.priceAdjustmentMapper.toUnlockByBusNoCommand(busNo, approverId);
            this.inventoryItemDomainService.unlockByBusNo(unlockCommand);
        }
        RejectPriceAdjustmentCommand rejectCommand =
            this.priceAdjustmentMapper.toRejectPriceAdjustmentCommand(request, approverId, approverId);
        // Cmd：领域迁移 rejected
        this.priceAdjustmentDomainService.rejectPriceAdjustment(rejectCommand);
        // Asm：返回 rejected 状态
        return this.priceAdjustmentMapper.toRejectPriceAdjustmentResponse(
            busNo,
            PriceAdjustmentStatus.REJECTED.getCode()
        );
    }

    /**
     * 医疗服务 Tab 选品：按 offeredOrgId 与关键字过滤后内存分页。
     *
     * @param request 选品请求
     * @return 选品分页响应
     * @throws BusinessException 会话操作科室缺失时
     */
    private SearchAdjustableItemsResponse searchHealthcareServices(SearchAdjustableItemRequest request) {
        Long orgId = this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT,
            "操作科室不能为空"
        );
        List<HealthcareService> allServices = this.healthcareServiceQueryService.listServicesForRegistration();
        List<AdjustableItemRowDto> matched = new ArrayList<>();
        String keyword = request.getKeyword();
        for (HealthcareService service : allServices) {
            if (service == null || service.getId() == null) {
                continue;
            }
            if (service.getOfferedOrgId() == null || !orgId.equals(service.getOfferedOrgId())) {
                continue;
            }
            if (StringUtils.isNotEmpty(keyword)) {
                if (service.getName() == null || !service.getName().contains(keyword.trim())) {
                    continue;
                }
            }
            AdjustableItemRowDto row = new AdjustableItemRowDto();
            row.setItemTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
            row.setItemId(service.getId());
            row.setItemCode(service.getBusNo());
            row.setItemName(service.getName());
            // 医疗服务选品：目录当前售价供制单预览（与 IF-PRC-06 同读路径）
            BigDecimal currentSellingPrice = this.chargeItemDefinitionQueryService.resolveFirstSalePriceValue(
                TableNameConstants.ADM_HEALTHCARE_SERVICE,
                service.getId()
            );
            row.setCurrentSellingPrice(currentSellingPrice);
            matched.add(row);
        }
        int pageNo = request.getPageNo().intValue();
        int pageSize = request.getBoundedPageSize();
        int fromIndex = (pageNo - 1) * pageSize;
        if (fromIndex < 0) {
            fromIndex = 0;
        }
        List<AdjustableItemRowDto> pageRows = new ArrayList<>();
        if (fromIndex < matched.size()) {
            int toIndex = fromIndex + pageSize;
            if (toIndex > matched.size()) {
                toIndex = matched.size();
            }
            for (int index = fromIndex; index < toIndex; index++) {
                pageRows.add(matched.get(index));
            }
        }
        return this.priceAdjustmentMapper.toSearchAdjustableItemsResponse(
            pageRows,
            matched.size(),
            pageNo,
            pageSize
        );
    }

    /**
     * 物流提交：按锁预览逐条加锁并统计锁定行数。
     *
     * @param busNo       调价单号
     * @param operatorId  操作人
     * @return 本次新加锁行数
     * @throws BusinessException 其它单已锁冲突时
     */
    private int lockInventoriesForSubmit(String busNo, Long operatorId) {
        List<PriceAdjustmentLockPreviewRowDto> previewRows = this.lockPreviewLoader.loadByBusNo(busNo);
        int lockedCount = 0;
        for (PriceAdjustmentLockPreviewRowDto previewRow : previewRows) {
            if (previewRow == null || previewRow.getInventoryId() == null) {
                continue;
            }
            if (previewRow.isLockedByOtherBusNo()) {
                throw new BusinessException(
                    ErrorCodeConstants.PRICE_ADJUSTMENT_LOCK_CONFLICT,
                    "库存已被其它单据锁定，整单提交失败"
                );
            }
            String lockedBusNo = previewRow.getLockedBusNo();
            if (StringUtils.isNotEmpty(lockedBusNo) && busNo.equals(lockedBusNo.trim())) {
                continue;
            }
            LockInventoryCommand lockCommand =
                this.priceAdjustmentMapper.toLockInventoryCommand(previewRow.getInventoryId(), busNo, operatorId);
            this.inventoryItemDomainService.lockInventory(lockCommand);
            lockedCount = lockedCount + 1;
        }
        return lockedCount;
    }

    /**
     * 判断整单是否为物流调价（取首行 item_table）。
     *
     * @param document 单据读模型
     * @return 物流调价时为 true
     */
    private boolean isLogisticsDocument(PriceAdjustmentDocumentQueryModel document) {
        if (document == null || document.getLines() == null || document.getLines().isEmpty()) {
            return false;
        }
        PriceAdjustmentLineQueryModel anchor = document.getLines().get(0);
        if (anchor == null || StringUtils.isEmpty(anchor.getItemTable())) {
            return false;
        }
        return PriceAdjustmentItemTableSupport.isLogisticsItemTable(anchor.getItemTable());
    }

    /**
     * Gate：提交前状态须为 draft 或 rejected。
     *
     * @param statusEnum 当前状态 code
     */
    private void assertEditableForSubmit(String statusEnum) {
        if (PriceAdjustmentStatus.DRAFT.getCode().equals(statusEnum)) {
            return;
        }
        if (PriceAdjustmentStatus.REJECTED.getCode().equals(statusEnum)) {
            return;
        }
        throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_STATUS, "当前状态不允许该操作");
    }

    /**
     * Gate：审批操作须为 pending。
     *
     * @param statusEnum 当前状态 code
     */
    private void assertPending(String statusEnum) {
        if (PriceAdjustmentStatus.PENDING.getCode().equals(statusEnum)) {
            return;
        }
        throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_STATUS, "当前状态不允许该操作");
    }

    /**
     * 选品列表回填目录当前价（新开单读路径，BR-PRICE-SRC-001）。
     *
     * @param rows      当前页选品行
     * @param itemTable 项目物理表
     */
    private void enrichAdjustableItemCatalogCurrentPrices(List<AdjustableItemRowDto> rows, String itemTable) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        boolean logistics = PriceAdjustmentItemTableSupport.isLogisticsItemTable(itemTable);
        for (AdjustableItemRowDto row : rows) {
            if (row == null || row.getItemId() == null || StringUtils.isEmpty(row.getItemTable())) {
                continue;
            }
            String table = row.getItemTable().trim();
            Long itemId = row.getItemId();
            // 当前 ACTIVE SALE：物流/诊疗选品均展示，供制单对照目标价
            BigDecimal salePrice = this.chargeItemDefinitionQueryService.resolveFirstSalePriceValue(table, itemId);
            row.setCurrentSellingPrice(salePrice);
            BigDecimal purchasePrice = null;
            if (logistics) {
                // 物流口径额外读 PURCHASE 当前进价（每包装）
                purchasePrice = this.chargeItemDefinitionQueryService.resolveFirstPurchasePriceValue(table, itemId);
            }
            row.setCurrentPurchasePrice(purchasePrice);
        }
    }

    /**
     * 选品列表回填全院可用库存数量总和（仅物流 {@code itemTable}，与 IF-PRC-05 可用口径一致）。
     *
     * @param rows      当前页选品行
     * @param itemTable 请求 Tab 对应项目物理表
     */
    private void enrichAdjustableItemAvailableQuantities(List<AdjustableItemRowDto> rows, String itemTable) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        if (!PriceAdjustmentItemTableSupport.isLogisticsItemTable(itemTable)) {
            return;
        }
        Set<Long> itemIds = new LinkedHashSet<>();
        for (AdjustableItemRowDto row : rows) {
            if (row != null && row.getItemId() != null) {
                itemIds.add(row.getItemId());
            }
        }
        if (itemIds.isEmpty()) {
            return;
        }
        // Pre：批量汇总全院可用量，避免逐行打库存读路径
        Map<Long, BigDecimal> quantityByItemId =
            this.inventoryItemQueryService.sumTotalAvailableInventoryByItemIds(itemTable, itemIds);
        for (AdjustableItemRowDto row : rows) {
            if (row == null || row.getItemId() == null) {
                continue;
            }
            BigDecimal quantity = quantityByItemId.get(row.getItemId());
            if (quantity == null) {
                quantity = BigDecimal.ZERO;
            }
            row.setQuantity(quantity);
        }
    }

    /**
     * 批量加载单据行产品展示映射。
     *
     * @param document 单据读模型
     * @return 复合键到展示 DTO 的映射
     */
    private Map<String, ProductDefinitionDisplayDto> loadProductDisplayMap(PriceAdjustmentDocumentQueryModel document) {
        Map<String, ProductDefinitionDisplayDto> empty = new HashMap<>();
        if (document == null || document.getLines() == null) {
            return empty;
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        for (PriceAdjustmentLineQueryModel line : document.getLines()) {
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
            if (dedupe.contains(key)) {
                continue;
            }
            dedupe.add(key);
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(line.getItemTable());
            lookup.setProductDefinitionId(line.getItemId());
            lookups.add(lookup);
        }
        if (lookups.isEmpty()) {
            return empty;
        }
        try {
            return this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        } catch (RuntimeException ex) {
            log.warn("loadProductDisplayMap degraded for price adjustment document", ex);
            return empty;
        }
    }

    /**
     * 列表行批量 enrich 制单/提交/审批人展示名。
     *
     * @param records 列表行
     */
    private void enrichDocumentListPractitionerNames(List<PriceAdjustmentDocumentListRowDto> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (PriceAdjustmentDocumentListRowDto record : records) {
            if (record == null) {
                continue;
            }
            record.setOperatorName(this.resolvePractitionerName(record.getOperatorId()));
            record.setSubmitterName(this.resolvePractitionerName(record.getSubmitterId()));
            record.setApproverName(this.resolvePractitionerName(record.getApproverId()));
        }
    }

    /**
     * 详情响应 enrich 提交/审批人展示名。
     *
     * @param response 详情响应
     */
    private void enrichDocumentDetailPractitionerNames(PriceAdjustmentDocumentResponse response) {
        if (response == null) {
            return;
        }
        response.setSubmitterName(this.resolvePractitionerName(response.getSubmitterId()));
        response.setApproverName(this.resolvePractitionerName(response.getApproverId()));
    }

    /**
     * 按主键解析医务人员展示名。
     *
     * @param practitionerId 医务人员主键
     * @return 展示名；未命中时 null
     */
    private String resolvePractitionerName(Long practitionerId) {
        if (practitionerId == null) {
            return null;
        }
        Practitioner practitioner = this.practitionerQueryService.findById(practitionerId);
        if (practitioner == null) {
            return null;
        }
        return practitioner.getName();
    }
}
