/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.approval.service;

import com.zxplat.application.finance.approval.mapper.FinanceApprovalMapper;
import com.zxplat.application.finance.approval.request.AgreeFinanceApprovalRequest;
import com.zxplat.application.finance.approval.request.InitFinanceApprovalRequest;
import com.zxplat.application.finance.approval.request.PageFinanceApprovalDocumentRequest;
import com.zxplat.application.finance.approval.request.RejectFinanceApprovalRequest;
import com.zxplat.application.finance.approval.response.AgreeFinanceApprovalResponse;
import com.zxplat.application.finance.approval.response.InitFinanceApprovalResponse;
import com.zxplat.application.finance.approval.response.PageFinanceApprovalDocumentResponse;
import com.zxplat.application.finance.approval.response.RejectFinanceApprovalResponse;
import com.zxplat.application.finance.approval.support.FinanceApprovalAgreeOrchestration;
import com.zxplat.application.finance.approval.support.FinanceApprovalDocumentPageLoader;
import com.zxplat.application.finance.approval.support.FinanceApprovalPractitionerOptionSupport;
import com.zxplat.application.finance.approval.support.FinanceApprovalRejectOrchestration;
import com.zxplat.application.finance.approval.support.FinanceApprovalTypeSupport;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.zxplat.framework.core.application.paging.PageWindowGate;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.custom.priceadjustment.queries.model.PriceAdjustmentDocumentQueryModel;
import com.zxplat.domain.custom.priceadjustment.service.PriceAdjustmentQueryService;
import com.zxplat.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.zxplat.domain.custom.supplier.service.SupplierQueryService;
import com.zxplat.domain.share.authorization.FinanceApprovalAuthorizer;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.FinanceApprovalConstants;
import com.zxplat.domain.share.enums.PriceAdjustmentStatus;
import com.zxplat.domain.share.enums.SupplierType;
import com.zxplat.domain.share.enums.SupplyStatus;
import com.zxplat.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.session.CurrentOperatorResolver;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 财务审批应用编排（AS-FIN-APR-001）：统一列表与同意/驳回入口。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link FinanceApprovalAuthorizer}：写路径财务角色与供应白名单门控</li>
 *   <li>{@link FinanceApprovalTypeSupport}：类型/品类/状态判别与选项</li>
 *   <li>{@link FinanceApprovalDocumentPageLoader}：跨源分页（B.6.4）</li>
 *   <li>{@link FinanceApprovalAgreeOrchestration} / {@link FinanceApprovalRejectOrchestration}：分发复用业务同意/驳回</li>
 *   <li>{@link FinanceApprovalMapper}：DTO 装配</li>
 *   <li>{@link CurrentOperatorResolver}：登录会话 Gate</li>
 *   <li>{@link PageWindowGate}：分页窗口</li>
 * </ul>
 *
 * <p>接入：AppServiceControllerRegistrar → POST /api/app/financeApproval/{methodName}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceApprovalAppService {

    /** 登录用户主键会话解析（写/读 Gate）。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 分页窗口 Gate。 */
    private final PageWindowGate pageWindowGate;

    /** 财务审批角色与供应类型门控。 */
    private final FinanceApprovalAuthorizer financeApprovalAuthorizer;

    /** 类型/品类/状态。 */
    private final FinanceApprovalTypeSupport typeSupport;

    /** 统一列表 Loader。 */
    private final FinanceApprovalDocumentPageLoader documentPageLoader;

    /** 同意分发。 */
    private final FinanceApprovalAgreeOrchestration agreeOrchestration;

    /** 驳回分发。 */
    private final FinanceApprovalRejectOrchestration rejectOrchestration;

    /** Mapper。 */
    private final FinanceApprovalMapper financeApprovalMapper;

    /** 库位选项。 */
    private final LocationQueryService locationQueryService;

    /** 管辖库位收窄。 */
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;

    /** 供应商选项。 */
    private final SupplierQueryService supplierQueryService;

    /** 制单人/审批人下拉。 */
    private final FinanceApprovalPractitionerOptionSupport practitionerOptionSupport;

    /** 供应单头读（写前待审断言）。 */
    private final SupplyRequestQueryService supplyRequestQueryService;

    /** 调价单头读（写前待审断言）。 */
    private final PriceAdjustmentQueryService priceAdjustmentQueryService;

    /**
     * 初始化财务审批页筛选项（IF-APR-01）。
     *
     * @param request 允许空体
     * @return 类型/状态/库房/供应商/品类/制单人/审批人 Option；主数据失败时选项可空不阻断进页
     * @throws BusinessException 未登录时
     */
    public InitFinanceApprovalResponse init(InitFinanceApprovalRequest request) {
        // Gate：须已登录，防止匿名拉取机构主数据
        this.currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.FINANCE_APPROVAL_INVALID_ARGUMENT,
            "用户未登录");
        // Pre：类型与状态 Option（纯投影）
        List<OptionDto> typeOptions = this.typeSupport.listTypeOptions();
        List<OptionDto> statusOptions = this.typeSupport.listStatusOptions();
        List<OptionDto> itemTableOptions = this.typeSupport.listItemTableOptions();
        List<OptionDto> locationOptions = new ArrayList<>();
        List<OptionDto> supplierOptions = new ArrayList<>();
        List<OptionDto> applicantOptions = new ArrayList<>();
        List<OptionDto> approverOptions = new ArrayList<>();
        try {
            // Pre：药库/药房/科室药房并集后按管辖收窄
            List<Location> warehouses = this.locationQueryService.listAllActiveWarehouses();
            List<Location> pharmacies = this.locationQueryService.listAllActivePharmacies();
            List<Location> deptPharmacies = this.locationQueryService.listAllActiveDepartmentPharmacies();
            List<Location> merged = new ArrayList<>();
            merged.addAll(warehouses);
            merged.addAll(pharmacies);
            merged.addAll(deptPharmacies);
            List<Location> gated = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(merged);
            locationOptions = this.toLocationOptions(gated);
            // Pre：启用供应商
            List<SupplierListItemQueryModel> suppliers =
                this.supplierQueryService.listSuppliersByTypeAndActive(SupplierType.SUPPLIER.getCode());
            supplierOptions = this.toSupplierOptions(suppliers);
            // Pre：制单人全量在职；审批人仅财务审批登录角色
            applicantOptions = this.practitionerOptionSupport.listApplicantOptions();
            approverOptions = this.practitionerOptionSupport.listApproverOptions();
        } catch (RuntimeException ex) {
            // 主数据不可用时返回空选项，不阻断进页（AS §4 IF-APR-01）
            log.warn("finance approval init options load degraded", ex);
        }
        // Asm：默认选中值不下发（前端自带类型空=全部 + 待审 + 当月审批时间）
        return this.financeApprovalMapper.toInitResponse(
            typeOptions,
            statusOptions,
            locationOptions,
            supplierOptions,
            itemTableOptions,
            applicantOptions,
            approverOptions);
    }

    /**
     * 分页查询财务审批单据（IF-APR-02）。
     *
     * @param request 筛选与分页；缺省 type/status 不写死进货/待审
     * @return 合并分页结果
     * @throws BusinessException 非法 type/itemTable 或分页非法
     */
    public PageFinanceApprovalDocumentResponse pageDocuments(PageFinanceApprovalDocumentRequest request) {
        // Gate：请求与分页窗口
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.FINANCE_APPROVAL_INVALID_ARGUMENT, "请求不能为空");
        }
        this.pageWindowGate.requirePositivePageWindow(
            request,
            ErrorCodeConstants.FINANCE_APPROVAL_INVALID_ARGUMENT,
            "分页参数不能为空");
        if (request.getPageSize() == null) {
            request.setPageSize(FinanceApprovalConstants.DEFAULT_PAGE_SIZE);
        }
        // Gate：type/itemTable 白名单
        this.typeSupport.assertListTypeEnum(request.getTypeEnum());
        this.typeSupport.assertItemTable(request.getItemTable());
        // Pre：跨源分页 Loader（禁止默认进货/待审）
        FinanceApprovalDocumentPageLoader.PageLoadResult loaded = this.documentPageLoader.page(request);
        // Asm：PagedResultBuilder 组装（禁止 getNumber()+1）
        PageFinanceApprovalDocumentResponse response = new PageFinanceApprovalDocumentResponse();
        int pageNo = request.getPageNo();
        int pageSize = request.getPageSize();
        return PagedResultBuilder.build(response, loaded.rows(), loaded.totalCount(), pageNo, pageSize);
    }

    /**
     * 财务审批同意（IF-APR-03）。
     *
     * @param request 单号、文档种类与供应类型
     * @return 同意后状态
     * @throws BusinessException 无财务角色、非白名单、非待审或下游动账失败
     */
    public AgreeFinanceApprovalResponse agree(AgreeFinanceApprovalRequest request) {
        // Gate：财务角色
        this.financeApprovalAuthorizer.assertFinanceApprovalRole();
        // Gate：参数与白名单
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.FINANCE_APPROVAL_INVALID_ARGUMENT, "单号不能为空");
        }
        String documentKind = this.typeSupport.resolveDocumentKind(request.getDocumentKind());
        String busNo = request.getBusNo().trim();
        // 供应：type 须∈财务白名单，并再次走供应类型角色门控
        if (FinanceApprovalConstants.DOCUMENT_KIND_SUPPLY.equals(documentKind)) {
            if (StringUtils.isEmpty(request.getTypeEnum())
                || !this.typeSupport.isFinanceSupplyType(request.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.FINANCE_APPROVAL_INVALID_ARGUMENT, "单据类型不在财务审批范围");
            }
            this.financeApprovalAuthorizer.assertFinanceApprovalForSupplyType(request.getTypeEnum().trim());
        }
        // Pre：加载并断言待审（BR-APR-003），失败禁止进入 Cmd
        this.assertPendingDocument(busNo, documentKind, request.getTypeEnum());
        // Cmd：分发同意编排（写库点在被复用业务路径）
        String statusEnum = this.agreeOrchestration.agree(busNo, documentKind, request.getTypeEnum());
        // Asm：组装响应
        AgreeFinanceApprovalResponse response = new AgreeFinanceApprovalResponse();
        response.setBusNo(busNo);
        response.setDocumentKind(documentKind);
        response.setStatusEnum(statusEnum);
        return response;
    }

    /**
     * 财务审批驳回（IF-APR-04）。
     *
     * @param request 单号、种类、类型与驳回原因
     * @return 驳回后状态
     * @throws BusinessException 无财务角色、非待审、调价缺原因等
     */
    public RejectFinanceApprovalResponse reject(RejectFinanceApprovalRequest request) {
        // Gate：财务角色
        this.financeApprovalAuthorizer.assertFinanceApprovalRole();
        // Gate：参数与白名单
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.FINANCE_APPROVAL_INVALID_ARGUMENT, "单号不能为空");
        }
        String documentKind = this.typeSupport.resolveDocumentKind(request.getDocumentKind());
        String busNo = request.getBusNo().trim();
        // 供应：type 须∈财务白名单，并再次走供应类型角色门控
        if (FinanceApprovalConstants.DOCUMENT_KIND_SUPPLY.equals(documentKind)) {
            if (StringUtils.isEmpty(request.getTypeEnum())
                || !this.typeSupport.isFinanceSupplyType(request.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.FINANCE_APPROVAL_INVALID_ARGUMENT, "单据类型不在财务审批范围");
            }
            this.financeApprovalAuthorizer.assertFinanceApprovalForSupplyType(request.getTypeEnum().trim());
        }
        // Pre：待审断言；调价原因非空由 RejectOrchestration 再断言
        this.assertPendingDocument(busNo, documentKind, request.getTypeEnum());
        // Cmd：分发驳回
        String statusEnum = this.rejectOrchestration.reject(busNo, documentKind, request.getRejectReason());
        // Asm：组装响应
        RejectFinanceApprovalResponse response = new RejectFinanceApprovalResponse();
        response.setBusNo(busNo);
        response.setDocumentKind(documentKind);
        response.setStatusEnum(statusEnum);
        return response;
    }

    /**
     * 写前断言单据存在且为待审态。
     *
     * @param busNo        单号
     * @param documentKind 种类
     * @param typeEnum     供应码（供应时用于一致性校验，可空）
     * @throws BusinessException 不存在或非待审
     */
    private void assertPendingDocument(String busNo, String documentKind, String typeEnum) {
        // 调价：须 pending，否则拒绝进入 Cmd
        if (FinanceApprovalConstants.DOCUMENT_KIND_PRICE_ADJUSTMENT.equals(documentKind)) {
            PriceAdjustmentDocumentQueryModel document =
                this.priceAdjustmentQueryService.getDocumentByBusNo(busNo);
            if (document == null || StringUtils.isEmpty(document.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_NOT_FOUND, "调价单不存在");
            }
            // 非待审不可再审（BR-APR-003）
            if (!PriceAdjustmentStatus.PENDING.getCode().equals(document.getStatusEnum().trim())) {
                throw new BusinessException(ErrorCodeConstants.FINANCE_APPROVAL_INVALID_STATUS, "当前状态不可审批");
            }
            return;
        }
        // 供应：同号头行须 APPROVAL
        SupplyByBusNoQuery query = new SupplyByBusNoQuery();
        query.setBusNo(busNo);
        List<SupplyRequestModel> lines = this.supplyRequestQueryService.getSupplyByBusNo(query);
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "单据不存在或已删除");
        }
        SupplyRequestModel head = lines.get(0);
        // 请求 type 与库内 type 不一致则拒绝，防串单
        if (StringUtils.isNotEmpty(typeEnum)
            && head.getTypeEnum() != null
            && !typeEnum.trim().equals(head.getTypeEnum().trim())) {
            throw new BusinessException(ErrorCodeConstants.FINANCE_APPROVAL_INVALID_ARGUMENT, "单据类型与单号不匹配");
        }
        // 非审核中不可同意/驳回（BR-APR-003）
        if (!SupplyStatus.APPROVAL.getCode().equals(head.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.FINANCE_APPROVAL_INVALID_STATUS, "当前状态不可审批");
        }
    }

    /**
     * Location → OptionDto（按 id 去重保序）。
     *
     * @param locations 库位列表
     * @return Option 列表
     */
    private List<OptionDto> toLocationOptions(List<Location> locations) {
        List<OptionDto> options = new ArrayList<>();
        if (locations == null || locations.isEmpty()) {
            return options;
        }
        Map<Long, Location> unique = new LinkedHashMap<>();
        for (Location loc : locations) {
            if (loc == null || loc.getId() == null) {
                continue;
            }
            unique.putIfAbsent(loc.getId(), loc);
        }
        long sort = 1L;
        for (Location loc : unique.values()) {
            String label = loc.getName();
            if (StringUtils.isEmpty(label)) {
                label = String.valueOf(loc.getId());
            }
            options.add(new OptionDto(sort, String.valueOf(loc.getId()), label));
            sort = sort + 1L;
        }
        return options;
    }

    /**
     * 供应商读模型 → OptionDto。
     *
     * @param suppliers 供应商列表
     * @return Option 列表
     */
    private List<OptionDto> toSupplierOptions(List<SupplierListItemQueryModel> suppliers) {
        List<OptionDto> options = new ArrayList<>();
        if (suppliers == null || suppliers.isEmpty()) {
            return options;
        }
        long sort = 1L;
        for (SupplierListItemQueryModel supplier : suppliers) {
            if (supplier == null || supplier.getId() == null) {
                continue;
            }
            String label = supplier.getName();
            if (StringUtils.isEmpty(label)) {
                label = String.valueOf(supplier.getId());
            }
            options.add(new OptionDto(sort, String.valueOf(supplier.getId()), label));
            sort = sort + 1L;
        }
        return options;
    }
}
