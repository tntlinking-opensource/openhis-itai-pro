/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.cashiersettlementapproval.service;

import com.openhis.health.application.finance.cashiersettlementapproval.dto.CashierSettlementApprovalHeaderDto;
import com.openhis.health.application.finance.cashiersettlementapproval.dto.CashierSettlementApprovalListItemDto;
import com.openhis.health.application.finance.cashiersettlementapproval.mapper.CashierSettlementApprovalMapper;
import com.openhis.health.application.finance.cashiersettlementapproval.request.ApproveCashierSettlementRequest;
import com.openhis.health.application.finance.cashiersettlementapproval.request.LoadCashierSettlementApprovalDetailRequest;
import com.openhis.health.application.finance.cashiersettlementapproval.request.LoadCashierSettlementApprovalPageInitRequest;
import com.openhis.health.application.finance.cashiersettlementapproval.request.PageCashierSettlementApprovalsRequest;
import com.openhis.health.application.finance.cashiersettlementapproval.request.RejectCashierSettlementRequest;
import com.openhis.health.application.finance.cashiersettlementapproval.response.ApproveCashierSettlementResponse;
import com.openhis.health.application.finance.cashiersettlementapproval.response.LoadCashierSettlementApprovalDetailResponse;
import com.openhis.health.application.finance.cashiersettlementapproval.response.LoadCashierSettlementApprovalPageInitResponse;
import com.openhis.health.application.finance.cashiersettlementapproval.response.PageCashierSettlementApprovalsResponse;
import com.openhis.health.application.finance.cashiersettlementapproval.response.RejectCashierSettlementResponse;
import com.openhis.health.application.finance.cashiersettlementapproval.support.CashierSettlementApprovalDetailBodySupport;
import com.openhis.health.application.finance.cashiersettlementapproval.support.CashierSettlementApprovalDisplayEnricher;
import com.openhis.health.application.finance.cashiersettlementapproval.support.CashierSettlementApprovalTypeSupport;
import com.openhis.health.application.finance.pharmacydailysettlement.support.PharmacyDailySettlementPreviewAssembler;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.financial.cashiersettlement.command.ApproveCashierSettlementCommand;
import com.openhis.health.domain.financial.cashiersettlement.command.RejectCashierSettlementCommand;
import com.openhis.health.domain.financial.cashiersettlement.queries.PageCashierSettlementApprovalQuery;
import com.openhis.health.domain.financial.cashiersettlement.queries.model.CashierSettlementApprovalListItemQueryModel;
import com.openhis.health.domain.financial.cashiersettlement.queries.model.CashierSettlementHeaderQueryModel;
import com.openhis.health.domain.financial.cashiersettlement.service.CashierSettlementDomainService;
import com.openhis.health.domain.financial.cashiersettlement.service.CashierSettlementQueryService;
import com.openhis.health.domain.share.authorization.FinanceApprovalAuthorizer;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.CashierSettlementStatus;
import com.openhis.health.domain.share.enums.CashierSettlementType;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 交账审批应用编排（AS-FIN-CS-001）：列表、详情、通过/驳回。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link FinanceApprovalAuthorizer}：写路径财务角色门控</li>
 *   <li>{@link CashierSettlementApprovalTypeSupport}：类型/状态选项与入参校验</li>
 *   <li>{@link CashierSettlementQueryService}：头表读与审批分页（本聚合）</li>
 *   <li>{@link CashierSettlementDomainService}：approve/reject 写侧</li>
 *   <li>{@link CashierSettlementApprovalMapper}：DTO 装配</li>
 *   <li>{@link CashierSettlementApprovalDisplayEnricher}：交账人/审批人/药房/科室名</li>
 *   <li>{@link CurrentOperatorResolver} / {@link CurrentPractitionerResolver}：会话 Gate 与审批人</li>
 * </ul>
 *
 * <p>接入：AppServiceControllerRegistrar → POST /api/app/cashierSettlementApproval/{methodName}。</p>
 */
@Service
@RequiredArgsConstructor
public class CashierSettlementApprovalAppService {

    /** v1 详情明细区说明回落（具体文案由 DetailBodySupport 按 type 覆盖）。 */
    private static final String DETAIL_RECOMPUTE_NOTE_V1 =
        "明细区按交账类型对挂载源表现算日结单内容。";

    /** 登录用户主键会话解析（读 Gate）。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 当前医务人员解析（写路径审批人）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 分页窗口 Gate。 */
    private final PageWindowGate pageWindowGate;

    /** 财务审批角色门控。 */
    private final FinanceApprovalAuthorizer financeApprovalAuthorizer;

    /** 类型/状态选项与入参校验。 */
    private final CashierSettlementApprovalTypeSupport typeSupport;

    /** 交账头读侧。 */
    private final CashierSettlementQueryService cashierSettlementQueryService;

    /** 交账写侧。 */
    private final CashierSettlementDomainService cashierSettlementDomainService;

    /** Mapper。 */
    private final CashierSettlementApprovalMapper cashierSettlementApprovalMapper;

    /** 详情日结体现算。 */
    private final CashierSettlementApprovalDetailBodySupport cashierSettlementApprovalDetailBodySupport;

    /** 列表/详情展示名 enrich。 */
    private final CashierSettlementApprovalDisplayEnricher cashierSettlementApprovalDisplayEnricher;

    /** 药房位置读侧（初始化下拉）。 */
    private final LocationQueryService locationQueryService;

    /** 药房 Option 装配（与药房日结同构）。 */
    private final PharmacyDailySettlementPreviewAssembler pharmacyDailySettlementPreviewAssembler;

    /**
     * 初始化交账审批页筛选项（IF-CS-APR-01）。
     *
     * @param request 允许空体
     * @return 费用/药房大类、类型、状态、药房 Option
     * @throws BusinessException 未登录时
     */
    public LoadCashierSettlementApprovalPageInitResponse loadCashierSettlementApprovalPageInit(
        LoadCashierSettlementApprovalPageInitRequest request) {
        // Gate：须已登录，防止匿名拉取审批筛选项
        this.currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT,
            "用户未登录");
        // Pre：大类/类型/状态 Option（纯投影）
        List<OptionDto> categoryOptions = this.typeSupport.listCategoryOptions();
        List<OptionDto> typeOptions = this.typeSupport.listTypeOptions();
        List<OptionDto> statusOptions = this.typeSupport.listStatusOptions();
        // Pre：启用药房下拉（药房日结筛选）
        List<Location> pharmacies = this.locationQueryService.listAllActivePharmacies();
        List<OptionDto> pharmacyOptions = this.pharmacyDailySettlementPreviewAssembler.toPharmacyOptions(pharmacies);
        // Asm：装配 init 响应
        return this.cashierSettlementApprovalMapper.toPageInitResponse(
            categoryOptions,
            typeOptions,
            statusOptions,
            pharmacyOptions);
    }

    /**
     * 分页查询交账审批列表（IF-CS-APR-02）。
     *
     * @param request 筛选与分页；缺省 type/status 不限
     * @return 交账头分页列表
     * @throws BusinessException 非法 type/status 或分页非法
     */
    public PageCashierSettlementApprovalsResponse pageCashierSettlementApprovals(
        PageCashierSettlementApprovalsRequest request) {
        // Gate：请求与分页窗口
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "请求不能为空");
        }
        this.pageWindowGate.requirePositivePageWindow(
            request,
            ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT,
            "分页参数不能为空");
        if (request.getPageSize() == null) {
            request.setPageSize(CashierSettlementApprovalTypeSupport.DEFAULT_PAGE_SIZE);
        }
        // Gate：单码 type/status 白名单；大类非法在 Mapper 展开时抛错
        this.typeSupport.assertListTypeEnum(request.getTypeEnum());
        this.typeSupport.assertListStatusEnum(request.getStatusEnum());
        if (!StringUtils.isEmpty(request.getCategoryEnum())) {
            this.typeSupport.expandCategoryToTypeEnums(request.getCategoryEnum());
        }
        // Pre：Request → 领域 Query + Pageable
        PageCashierSettlementApprovalQuery query =
            this.cashierSettlementApprovalMapper.toApprovalPageQuery(request);
        Pageable pageable = PageRequest.of(
            request.springPageIndex(),
            request.getBoundedPageSize(),
            this.cashierSettlementQueryService.defaultApprovalPageSort());
        Page<CashierSettlementApprovalListItemQueryModel> page =
            this.cashierSettlementQueryService.pageForApproval(query, pageable);
        // Asm：读模型 → DTO + PagedResultBuilder
        List<CashierSettlementApprovalListItemDto> rows = new ArrayList<>();
        for (CashierSettlementApprovalListItemQueryModel model : page.getContent()) {
            CashierSettlementApprovalListItemDto row = this.cashierSettlementApprovalMapper.toListItemDto(model);
            if (row != null) {
                rows.add(row);
            }
        }
        // Asm：回填交账人/审批人/药房/科室名
        this.cashierSettlementApprovalDisplayEnricher.enrichListItems(rows);
        PageCashierSettlementApprovalsResponse response = new PageCashierSettlementApprovalsResponse();
        return PagedResultBuilder.build(response, rows, page);
    }

    /**
     * 加载交账审批详情（IF-CS-APR-02 详情）。
     *
     * @param request 交账头主键
     * @return 头表 + 按挂载现算的日结体
     * @throws BusinessException 头不存在
     */
    public LoadCashierSettlementApprovalDetailResponse loadCashierSettlementApprovalDetail(
        LoadCashierSettlementApprovalDetailRequest request) {
        // Gate：请求与主键
        if (request == null || request.getSettlementId() == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账头标识不能为空");
        }
        // Pre：读头表投影
        CashierSettlementHeaderQueryModel headerModel =
            this.cashierSettlementQueryService.findHeaderById(request.getSettlementId());
        // 无头则无法打开详情
        if (headerModel == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账记录不存在");
        }
        CashierSettlementApprovalHeaderDto headerDto =
            this.cashierSettlementApprovalMapper.toHeaderDto(headerModel);
        // Asm：头表展示名
        this.cashierSettlementApprovalDisplayEnricher.enrichHeader(headerDto);
        Integer mountedSourceCount = headerModel.getPaymentCount();
        String detailNote = this.buildDetailRecomputeNote(headerModel.getTypeEnum());
        // Asm：头表骨架
        LoadCashierSettlementApprovalDetailResponse response =
            this.cashierSettlementApprovalMapper.toDetailResponse(
                headerDto,
                mountedSourceCount,
                detailNote);
        // Pre/Asm：按挂载源表现算日结体（收款 rows/summary 或药房大类/品规）
        this.cashierSettlementApprovalDetailBodySupport.fillDetailBody(response, headerModel);
        return response;
    }

    /**
     * 交账审批通过（IF-CS-APR-03）。
     *
     * @param request 交账头主键
     * @return 通过后交账头主键
     * @throws BusinessException 无财务角色、非待审或参数非法
     */
    public ApproveCashierSettlementResponse approveCashierSettlement(ApproveCashierSettlementRequest request) {
        // Gate：财务角色
        this.financeApprovalAuthorizer.assertFinanceApprovalRole();
        // Gate：请求与主键
        if (request == null || request.getSettlementId() == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账头标识不能为空");
        }
        // Pre：写前断言待审（BR-CS-APR-002）
        this.assertSubmittedForApproval(request.getSettlementId());
        // Pre：当前医务人员作为审批人（B.6.5）
        Long approverId = this.currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT,
            "当前用户未绑定医务人员，无法审批");
        ApproveCashierSettlementCommand command = new ApproveCashierSettlementCommand();
        command.setSettlementId(request.getSettlementId());
        command.setApproverId(approverId);
        // Cmd：领域 approve（不改挂载）
        this.cashierSettlementDomainService.approveCashierSettlement(command);
        // Asm：返回主键
        ApproveCashierSettlementResponse response = new ApproveCashierSettlementResponse();
        response.setSettlementId(request.getSettlementId());
        return response;
    }

    /**
     * 交账审批驳回（IF-CS-APR-04）。
     *
     * @param request 交账头主键与可选原因
     * @return 驳回后交账头主键与原因
     * @throws BusinessException 无财务角色、非待审或参数非法
     */
    public RejectCashierSettlementResponse rejectCashierSettlement(RejectCashierSettlementRequest request) {
        // Gate：财务角色
        this.financeApprovalAuthorizer.assertFinanceApprovalRole();
        // Gate：请求与主键
        if (request == null || request.getSettlementId() == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账头标识不能为空");
        }
        // Pre：写前断言待审（BR-CS-APR-002）
        this.assertSubmittedForApproval(request.getSettlementId());
        // Pre：当前医务人员作为驳回人
        Long approverId = this.currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT,
            "当前用户未绑定医务人员，无法审批");
        RejectCashierSettlementCommand command = new RejectCashierSettlementCommand();
        command.setSettlementId(request.getSettlementId());
        command.setApproverId(approverId);
        if (!StringUtils.isEmpty(request.getReason())) {
            command.setReason(request.getReason().trim());
        }
        // Cmd：领域 reject（清挂载）
        this.cashierSettlementDomainService.rejectCashierSettlement(command);
        // Asm：返回主键与原因
        RejectCashierSettlementResponse response = new RejectCashierSettlementResponse();
        response.setSettlementId(request.getSettlementId());
        response.setReason(command.getReason());
        return response;
    }

    /**
     * 写前断言交账头存在且为待审态。
     *
     * @param settlementId 交账头主键
     * @throws BusinessException 不存在或非待审
     */
    private void assertSubmittedForApproval(Long settlementId) {
        CashierSettlementHeaderQueryModel header =
            this.cashierSettlementQueryService.findHeaderById(settlementId);
        // 无头则无法审批
        if (header == null || StringUtils.isEmpty(header.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账记录不存在");
        }
        // 非待审不可通/驳（BR-CS-APR-002）
        if (!CashierSettlementStatus.isSubmitted(header.getStatusEnum().trim())) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_STATUS, "当前交账状态不可审批");
        }
    }

    /**
     * 按交账类型生成详情明细现算说明。
     *
     * @param typeEnum 交账类型 code
     * @return 说明文案
     */
    private String buildDetailRecomputeNote(String typeEnum) {
        // 药房类型提示挂载 dispense 现算
        if (CashierSettlementType.isPharmacyType(typeEnum)) {
            return DETAIL_RECOMPUTE_NOTE_V1 + " 当前为药房日结，应对齐门诊/住院药房日结页主区现算。";
        }
        // 收款类型提示挂载 payment 现算
        if (CashierSettlementType.isPaymentType(typeEnum)) {
            return DETAIL_RECOMPUTE_NOTE_V1 + " 当前为收款日结，应对齐门诊/住院收费日结页主区现算。";
        }
        return DETAIL_RECOMPUTE_NOTE_V1;
    }
}
