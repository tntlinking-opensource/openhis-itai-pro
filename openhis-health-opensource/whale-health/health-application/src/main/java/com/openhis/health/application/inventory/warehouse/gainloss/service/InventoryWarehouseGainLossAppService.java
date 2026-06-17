/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inventory.warehouse.gainloss.service;

import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogEditorHintsAssembler;
import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.module.common.pagination.PageQueryBinder;
import com.openhis.health.module.common.pagination.PagedResultBuilder;
import com.openhis.health.module.common.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.module.common.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.module.common.validator.CurrentOperatorResolver;
import com.openhis.health.module.common.validator.PageWindowGate;
import com.openhis.health.application.insurance.inventory.InventoryMedicareUploadSupport;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapper;
import com.openhis.health.application.inventory.warehouse.gainloss.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.warehouse.gainloss.dto.GainLossDetailLineDto;
import com.openhis.health.application.inventory.warehouse.gainloss.dto.GainLossLineDto;
import com.openhis.health.application.inventory.warehouse.gainloss.dto.GainLossListRowDto;
import com.openhis.health.application.inventory.warehouse.gainloss.mapper.InventoryWarehouseGainLossMapper;
import com.openhis.health.application.inventory.warehouse.gainloss.request.*;
import com.openhis.health.application.inventory.warehouse.gainloss.response.*;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.module.querysupport.administration.PractitionerRoleScopeLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.ProductDefinitionSelectionTerminologySupport;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.inventoryitem.command.CreateInventoryItemCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.DecreaseQuantityCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.health.domain.workflow.inventoryitem.queries.InventoryItemPageQuery;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.valueobject.InventoryMatchKey;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryDomainService;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryQueryService;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.VerifyAbleEditQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestValidationModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.chs.jilin.port.InventoryInsurancePort;
import com.openhis.whale.chs.jilin.port.model.UploadAfterGainLossCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 药库盘库单（{@link SupplyType#CABINET_GAIN_LOSS}）应用编排。
 *
 * <p>同意后按盘点差异：发放记录 + 在盘点库房对 {@link InventoryItemDomainService} 做盈亏加减（盘盈增加、盘亏扣减）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryWarehouseGainLossAppService {

    @Autowired(required = false)
    private InventoryInsurancePort inventoryInsurancePort;

    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    private final CurrentOperatorResolver currentOperatorResolver;
    private final PageWindowGate pageWindowGate;
    private final SupplyRequestDomainService supplyRequestDomainService;
    private final SupplyRequestQueryService supplyRequestQueryService;
    private final SupplyDeliveryDomainService supplyDeliveryDomainService;
    private final SupplyDeliveryQueryService supplyDeliveryQueryService;
    private final InventoryItemDomainService inventoryItemDomainService;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final LocationQueryService locationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final ProductDefinitionSelectionTerminologySupport productDefinitionSelectionTerminologySupport;
    private final InventoryWarehouseGainLossMapper gainLossMapper;
    /** 盘盈首次建库存项时复用进货侧命令装配。 */
    private final InventoryWarehouseInboundReceiptMapper inboundReceiptMapper;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /**
     * 加载库房单据页主数据（管辖药库、枚举选项、制单人员等）。
     *
     * @param request 页初始化请求；机构上下文由服务端解析
     * @return 下拉选项与字典项
     */
    public LoadGainLossFormInitResponse loadGainLossFormInit(LoadGainLossFormInitRequest request) {
        Long resolvedOrg = managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            ErrorCodeConstants.WHS_GL_101, "机构上下文缺失");
        try {
            // Gate：机构会话上下文
            // Pre：装载管辖范围内启用位置与角色人员
            // Asm：Mapper 映射页初始化响应
            List<Location> warehouses = locationQueryService.listAllActiveWarehouses();
            List<Location> gatedWarehouses = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(warehouses);
            List<Practitioner> pharmacists = this.practitionerRoleScopeLoader.listActivePractitionersForPlatformRoleInOrganization(
                WhalePlatformRoleCodeConstants.ROLE_PHARMACIST, resolvedOrg);
            List<Practitioner> warehouseKeepers = this.practitionerRoleScopeLoader.listActivePractitionersForPlatformRoleInOrganization(
                WhalePlatformRoleCodeConstants.ROLE_WAREHOUSE, resolvedOrg);
            return gainLossMapper.toLoadGainLossFormInitResponse(
                gainLossMapper.toOptionDtoListFromLocations(gatedWarehouses),
                gainLossMapper.toOptionDtoListInboundItemTableTypes(),
                gainLossMapper.toOptionDtoListSupplyStatuses(),
                gainLossMapper.toOptionDtoListFromPractitioners(pharmacists),
                gainLossMapper.toOptionDtoListFromPractitioners(warehouseKeepers));
        } catch (RuntimeException ex) {
            log.warn("loadGainLossFormInit failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_GL_101, "盘点页主数据加载失败");
        }
    }

    /**
     * 分页查询库房单据列表（按 busNo 聚合展示）。
     *
     * @param request 含分页窗口、库房与状态筛选
     * @return 聚合后的列表行与分页元数据
     */
    public PageGainLosssResponse pageGainLosss(PageGainLosssRequest request) {
        // Gate：请求体、分页窗口与机构上下文须齐备
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "请求不能为空");
        }
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_GL_001, "分页参数不能为空");
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_GL_101, "机构上下文缺失");
        // Pre：组装列表查询条件与管辖药库范围
        Set<Long> allowedWarehouses = this.resolveGatedWarehouseLocationIdsForWarehouseGate();
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.CABINET_GAIN_LOSS.getCode());
        if (request.getPurposeLocationId() != null) {
            if (Long.valueOf(0L).equals(request.getPurposeLocationId())) {
                if (allowedWarehouses.isEmpty()) {
                    listQuery.setPurposeLocationIds(List.of(-1L));
                } else {
                    listQuery.setPurposeLocationIds(new ArrayList<>(allowedWarehouses));
                }
            } else {
                this.assertWarehouseLocationAllowed(request.getPurposeLocationId(), allowedWarehouses);
                listQuery.setPurposeLocationId(request.getPurposeLocationId());
            }
        }
        if (StringUtils.isNotBlank(request.getStatusEnum())) {
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, request.getStatusEnum().trim());
            if (st == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "statusEnum 非法");
            }
            listQuery.setStatusEnum(st.getCode());
        }
        PageQueryBinder.bind(request, listQuery);
        LocalDateTime rangeStart = request.getApplyTimeFrom();
        if (rangeStart == null) {
            rangeStart = request.getStartDate();
        }
        listQuery.setStartDate(rangeStart);
        LocalDateTime rangeEnd = request.getApplyTimeTo();
        if (rangeEnd == null) {
            rangeEnd = request.getEndDate();
        }
        listQuery.setEndDate(rangeEnd);
        try {
            // Cmd：领域分页查询供应请求并按 busNo 聚合
            long mergedTotal = this.supplyRequestQueryService.countDistinctBusNo(listQuery);
            List<String> busNos = this.supplyRequestQueryService.listDistinctBusNoPage(listQuery);
            List<SupplyRequestModel> lines = this.supplyRequestQueryService.listByBusNosWithFilter(listQuery, busNos);
            List<GainLossListRowDto> records = this.gainLossMapper.toGainLossListAggregated(busNos, lines);
            this.enrichGainLossListDisplayNames(records, lines);
            this.enrichGainLossListPurposeLocationNames(records);
            this.enrichGainLossListStatusEnumText(records);
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            // Asm：聚合 busNo 行并映射分页响应
            return this.gainLossMapper.toPageGainLosssAggregatedResponse(mergedTotal, pageNo, pageSize, records);
        } catch (RuntimeException ex) {
            log.warn("pageGainLosss failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_GL_102, "盘库单列表查询失败");
        }
    }

    /**
     * 按 busNo 回读单据详情（含明细行与展示增强）。
     *
     * @param request 含业务单号 busNo
     * @return 单头与明细
     */
    public GetGainLossDetailResponse getGainLossDetail(GetGainLossDetailRequest request) {
        // Gate：busNo 非空方可回读盘库单
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "busNo 不能为空");
        }
        // Pre：按 busNo 回读供应请求行
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_103, "盘库单不存在");
        }
        this.assertCabinetGainLoss(lines);
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            if (line.getItemId() == null || StringUtils.isBlank(line.getItemTable())) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(line.getItemId());
            lookup.setProductTableName(line.getItemTable());
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> products;
        try {
            products = productDefinitionBatchLoader.loadAsMap(lookups);
        } catch (RuntimeException ex) {
            log.warn("batchGetProductByIds degraded for stocktake detail", ex);
            products = new HashMap<>();
        }
        ProductDefinitionSelectionTerminologyMaps terminologyMaps =
            this.productDefinitionSelectionTerminologySupport.loadSelectionTerminologyMaps();
        Map<Long, ProductDefinitionCatalogEditorHintsDto> hints = new HashMap<>();
        for (SupplyRequestModel line : lines) {
            if (line == null || line.getId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
            ProductDefinitionDisplayDto pr = products.get(key);
            ProductDefinitionCatalogEditorHintsDto hint =
                ProductDefinitionCatalogEditorHintsAssembler.fromDisplayDto(pr, terminologyMaps);
            if (hint != null) {
                hints.put(line.getId(), hint);
            }
        }
        GetGainLossDetailResponse res = gainLossMapper.toGetGainLossDetailResponse(lines, hints);
        SupplyRequestModel head = lines.get(0);
        if (head.getPurposeLocationId() != null) {
            Optional<Location> locOpt = locationQueryService.findOptionalById(head.getPurposeLocationId());
            if (locOpt.isPresent() && StringUtils.isNotBlank(locOpt.get().getName())) {
                res.setPurposeLocationName(locOpt.get().getName().trim());
            }
        }
        this.enrichGainLossDetailLines(res.getLines(), lines, products);
        this.enrichGainLossDetailLinesCatalogPricing(res.getLines());
        return res;
    }

    /**
     * 新建库房单据草稿并落库供应请求。
     *
     * @param request 单头、明细与库房门禁字段
     * @return 新建后的 busNo
     */
    public CreateGainLossResponse createGainLoss(CreateGainLossRequest request) {
        // Gate：请求体、明细与库房/供应商门禁
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "请求不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "盘点明细不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "purposeLocationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_GL_101, "机构上下文缺失");
        this.assertGainLossPurposeLocationGate(request.getPurposeLocationId());
        if (request.getPractitionerId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "经手人不能为空");
        }
        this.assertGainLossLinesFields(request.getLines());
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.WHS_GL_101, "用户未登录");
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_GL_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        try {
            // Cmd：组装 Create 命令并调用领域写服务
            String busNo = supplyRequestDomainService.createGainLoss(
                gainLossMapper.toCreateGainLossCommand(request, operatorId, applicantId, null));
            // Asm：返回 busNo
            return gainLossMapper.toCreateGainLossResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "盘点参数不合法：" + ex.getMessage());
        } catch (BusinessException ex) {
            throw this.mapGainLossLocationException(ex);
        }
    }


    /**
     * 提交单据进入待审批态。
     *
     * @param request 含业务单号 busNo
     * @return 提交后的展示状态
     */
    public SubmitGainLossApprovalResponse submitGainLossApproval(SubmitGainLossApprovalRequest request) {
        // Gate：busNo 与制单人员档案
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_GL_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法提交审批");
        try {
            // Cmd：领域提交审批
            supplyRequestDomainService.submitApproval(
                gainLossMapper.toSubmitApprovalCommand(request,
                    currentOperatorResolver.requireOperatorId(ErrorCodeConstants.WHS_GL_101, "用户未登录"),
                    applicantId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_GL_003);
        }
        // Asm：映射提交响应
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        return gainLossMapper.toSubmitGainLossApprovalResponse(st);
    }

    /**
     * 撤回审批（回到可编辑草稿态）。
     *
     * @param request 含业务单号 busNo
     * @return 撤回后的展示状态
     */
    public WithdrawGainLossApprovalResponse withdrawGainLossApproval(WithdrawGainLossApprovalRequest request) {
        // Gate：busNo 与操作人
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        try {
            // Cmd：领域撤回审批
            supplyRequestDomainService.withdrawApproval(
                gainLossMapper.toWithdrawApprovalCommand(request,
                    currentOperatorResolver.requireOperatorId(ErrorCodeConstants.WHS_GL_101, "用户未登录")));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_GL_003);
        }
        // Asm：映射撤回响应
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        return gainLossMapper.toWithdrawGainLossApprovalResponse(st);
    }

    /**
     * 药库盘盈盘亏单同意编排：领域落已同意态 → 盘点发放 → 按盈亏行增/减库存 → 可选医保进销存上报。
     *
     * @param request 含 busNo；须未重复同意
     * @return 同意后 busNo 与状态 code
     */
    public AgreeGainLossResponse agreeGainLoss(AgreeGainLossRequest request) {
        // Gate：busNo 与操作人
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "busNo 不能为空");
        }
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.WHS_GL_101, "用户未登录");

        // Pre：回读盘盈盘亏单并校验未重复同意
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> beforeLines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (beforeLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_103, "盘库单不存在");
        }
        this.assertCabinetGainLoss(beforeLines);
        SupplyRequestValidationModel validation = supplyRequestQueryService.supplyRequestValidation(request.getBusNo().trim());
        if (Boolean.TRUE.equals(validation.getHasAgreed())) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_104, "盘库单已同意，禁止重复同意");
        }

        // Cmd：领域状态机同意盘盈盘亏单
        try {
            supplyRequestDomainService.agreeRequest(gainLossMapper.toAgreeRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapAgreeException(ex);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_105, "盘库单已被并发修改，请刷新后重试", ex);
        }

        // Pre：强制读回 AGREE 行，保证发放/库存编排输入合法
        this.supplyRequestDomainService.flushPendingChanges();
        List<SupplyRequestModel> agreedLines = supplyRequestQueryService.getSupplyByBusNo(q);
        this.assertCabinetGainLoss(agreedLines);
        for (SupplyRequestModel row : agreedLines) {
            if (row == null) {
                continue;
            }
            if (!SupplyStatus.AGREE.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_GL_104, "同意后回读状态异常，中止盘点编排");
            }
            if (row.getId() == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_GL_104, "盘点明细缺少持久化主键，无法完成编排");
            }
        }
        List<Long> requestIdsForDeliveryGate = new ArrayList<>();
        for (SupplyRequestModel row : agreedLines) {
            if (row != null && row.getId() != null) {
                requestIdsForDeliveryGate.add(row.getId());
            }
        }
        Set<Long> duplicateCompletedRequestIds =
            supplyDeliveryQueryService.findRequestIdsHavingCompletedDelivery(requestIdsForDeliveryGate);
        if (!duplicateCompletedRequestIds.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_104, "供应请求行已存在完成态发放，禁止重复同意盘点");
        }

        // Cmd：盘点发放落库
        try {
            supplyDeliveryDomainService.createSupplyDelivery(
                gainLossMapper.toCreateSupplyDeliveryCommand(agreedLines, operatorId));
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_104, "盘点发放落库失败：" + ex.getMessage());
        }

        // Pre：批量产品读模型，供拆零量与盘盈建库存行使用
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getItemId() == null || StringUtils.isBlank(line.getItemTable())) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(line.getItemId());
            lookup.setProductTableName(line.getItemTable());
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> productByKey = new HashMap<>();
        if (!lookups.isEmpty()) {
            try {
                productByKey = productDefinitionBatchLoader.loadAsMap(lookups);
            } catch (RuntimeException ex) {
                log.warn("batchGetProductByIds failed during stocktake inventory assemble", ex);
            }
        }
        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getId() == null) {
                continue;
            }
            // 仅非零盈亏行调整库存；零盈亏行在发放命令中已跳过。
            BigDecimal profitLossQty = line.getItemQuantity();
            if (profitLossQty == null || profitLossQty.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            Long purposeLocationId = line.getPurposeLocationId();
            String resolvedLot = this.resolveLotNumberForGainLossInventory(line.getLotNumber());
            InventoryMatchKey matchKey = new InventoryMatchKey();
            matchKey.setLotNumber(resolvedLot);
            matchKey.setLocationId(purposeLocationId);
            matchKey.setItemTable(line.getItemTable());
            matchKey.setItemId(line.getItemId());
            Long inventoryId = inventoryItemQueryService.findFirstIdByInventoryMatchKey(matchKey).orElse(null);
            int sign = profitLossQty.compareTo(BigDecimal.ZERO);
            // 盘盈：命中既有库存行则加量，否则新建库存项
            if (sign > 0) {
                try {
                    if (inventoryId != null) {
                        IncreaseQuantityCommand incCmd =
                            gainLossMapper.toIncreaseQuantityCommand(inventoryId, profitLossQty, operatorId);
                        inventoryItemDomainService.increaseQuantity(incCmd);
                    } else {
                        String productName = this.resolveProductName(line, productByKey);
                        String key = ProductDefinitionKeyUtil.buildCompositeKey(
                            line.getItemTable(), line.getItemId());
                        ProductDefinitionDisplayDto product = productByKey.get(key);
                        String splitUnitCode = this.resolveSplitUnitCode(line, product);
                        CreateInventoryItemCommand createCmd = inboundReceiptMapper.toCreateInventoryItemCommand(
                            line,
                            purposeLocationId,
                            productName,
                            resolvedLot,
                            profitLossQty,
                            splitUnitCode,
                            operatorId,
                            product,
                            line.getSupplierId());
                        inventoryItemDomainService.createInventoryItem(createCmd);
                    }
                } catch (BusinessException ex) {
                    throw new BusinessException(ErrorCodeConstants.WHS_GL_104, "盘盈库存增加失败：" + ex.getMessage());
                }
            } else {
                // 盘亏：须命中库存行后按绝对量扣减
                BigDecimal absQty = profitLossQty.abs();
                if (inventoryId == null) {
                    throw new BusinessException(ErrorCodeConstants.WHS_GL_104, "盘点库房未找到匹配库存行，无法盘亏扣减");
                }
                try {
                    DecreaseQuantityCommand decCmd =
                        gainLossMapper.toDecreaseQuantityCommand(inventoryId, absQty, true, operatorId);
                    inventoryItemDomainService.decreaseQuantity(decCmd);
                } catch (BusinessException ex) {
                    throw new BusinessException(ErrorCodeConstants.WHS_GL_104, "盘亏库存扣减失败：" + ex.getMessage());
                }
            }
        }
        // Asm：可选医保进销存上报（CHS 模块启用时）
        tryUploadMedicareInventoryAfterStocktakeAgree(request, agreedLines);
        return gainLossMapper.toAgreeGainLossResponse(
            request.getBusNo().trim(), SupplyStatus.AGREE.getCode());
    }

    private void tryUploadMedicareInventoryAfterStocktakeAgree(
        AgreeGainLossRequest request,
        List<SupplyRequestModel> agreedLines
    ) {
        List<Long> lineIds = new ArrayList<>();
        for (SupplyRequestModel line : agreedLines) {
            if (line != null && line.getId() != null) {
                lineIds.add(line.getId());
            }
        }
        if (lineIds.isEmpty()) {
            return;
        }
        InventoryMedicareUploadSupport.uploadIfPortPresent(inventoryInsurancePort, () -> {
            UploadAfterGainLossCommand command = new UploadAfterGainLossCommand()
                .setGainLossBusNo(request.getBusNo())
                .setSupplyRequestLineIds(lineIds)
                .setSupplyTypeCode(SupplyType.CABINET_GAIN_LOSS.getCode())
                .setContractBusNo(request.getContractBusNo());
            inventoryInsurancePort.uploadAfterGainLossAgree(command);
        });
    }

    /**
     * 驳回单据并记录原因。
     *
     * @param request 含 busNo 与驳回原因
     * @return 是否驳回成功
     */
    public RejectGainLossResponse rejectGainLoss(RejectGainLossRequest request) {
        // Gate：busNo、驳回原因与操作人
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "busNo 不能为空");
        }
        if (StringUtils.isBlank(request.getChangeReason()) && StringUtils.isBlank(request.getChangeReasonCode())) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "驳回原因不能为空");
        }
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.WHS_GL_101, "用户未登录");
        try {
            // Cmd：领域驳回请求
            supplyRequestDomainService.rejectRequest(gainLossMapper.toRejectRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_GL_003);
        }
        // Asm：映射驳回响应
        return gainLossMapper.toRejectGainLossResponse(true);
    }

    /**
     * 删除单据（仅允许草稿/可删态）。
     *
     * @param request 含业务单号 busNo
     * @return 是否删除成功
     */
    public RemoveGainLossResponse removeGainLoss(RemoveGainLossRequest request) {
        // Gate：busNo 与操作人
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.WHS_GL_101, "用户未登录");
        try {
            // Cmd：领域按 busNo 软删
            supplyRequestDomainService.removeByBusNo(gainLossMapper.toRemoveByBusNoCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_GL_005);
        }
        // Asm：映射删除响应
        return gainLossMapper.toRemoveGainLossResponse(true);
    }

    /**
     * 分页查询单据选品（库存目录可用量）。
     *
     * @param request 含库房、物品表与分页参数
     * @return 选品行与分页元数据
     */
    public PageProductsForGainLossResponse pageProductsForGainLoss(PageProductsForGainLossRequest request) {
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_GL_001, "分页参数不能为空");
        if (request.getPurposeLocationId() == null) {
            // Gate：分页窗口、机构与库房门禁
            // Pre：组装库存目录分页查询
            // Cmd：查询可用库存行
            // Asm：映射选品分页并增强价格
            throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "purposeLocationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_GL_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForWarehouseGate();
        this.assertWarehouseLocationAllowed(request.getPurposeLocationId(), allowed);
        this.assertProductTableNameWhitelistForGainLossSelection(request.getProductTableName());
        InventoryItemPageQuery invQuery = new InventoryItemPageQuery();
        invQuery.setLocationId(request.getPurposeLocationId());
        invQuery.setItemTable(request.getProductTableName().trim());
        PageQueryBinder.bind(request, invQuery);
        Pageable pageable = PageRequest.of(
            request.springPageIndex(), request.getBoundedPageSize(), Sort.by(Sort.Direction.ASC, FieldNameConstants.ID));
        try {
            Page<InventoryItemModel> page = inventoryItemQueryService.queryInventoryItemsByPage(invQuery, pageable);
            List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
            for (InventoryItemModel inv : page.getContent()) {
                if (inv == null || inv.getItemId() == null || StringUtils.isBlank(inv.getItemTable())) {
                    continue;
                }
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductDefinitionId(inv.getItemId());
                lookup.setProductTableName(inv.getItemTable().trim());
                lookups.add(lookup);
            }
            Map<String, ProductDefinitionDisplayDto> products = new HashMap<>();
            if (!lookups.isEmpty()) {
                try {
                    products = productDefinitionBatchLoader.loadAsMap(lookups);
                } catch (RuntimeException ex) {
                    log.warn("batchGetProductByIds failed for stocktake selection", ex);
                }
            }
            String itemTable = request.getProductTableName().trim();
            List<Long> pageItemIds = new ArrayList<>();
            for (InventoryItemModel inv : page.getContent()) {
                if (inv != null && inv.getItemId() != null) {
                    pageItemIds.add(inv.getItemId());
                }
            }
            Map<Long, Map<String, BigDecimal>> availableQtyByItemIdAndNormLot = new HashMap<>();
            if (!pageItemIds.isEmpty()) {
                List<InventoryCatalogAvailabilityRowModel> availRows =
                    this.inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                        itemTable, pageItemIds, List.of(request.getPurposeLocationId()));
                for (InventoryCatalogAvailabilityRowModel ar : availRows) {
                    if (ar == null || ar.getItemId() == null || ar.getQuantity() == null) {
                        continue;
                    }
                    if (!Objects.equals(ar.getLocationId(), request.getPurposeLocationId())) {
                        continue;
                    }
                    String normLot = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(ar.getLotNumber());
                    availableQtyByItemIdAndNormLot
                        .computeIfAbsent(ar.getItemId(), k -> new HashMap<>())
                        .merge(normLot, ar.getQuantity(), BigDecimal::add);
                }
            }
            ProductDefinitionSelectionTerminologyMaps terminologyMaps =
                this.productDefinitionSelectionTerminologySupport.loadSelectionTerminologyMaps();
            List<ProductDefinitionItemDto> records = new ArrayList<>();
            for (InventoryItemModel inv : page.getContent()) {
                if (inv == null) {
                    continue;
                }
                String key = ProductDefinitionKeyUtil.buildCompositeKey(inv.getItemTable(), inv.getItemId());
                ProductDefinitionDisplayDto pr = products.get(key);
                ProductDefinitionItemDto dto = gainLossMapper.toProductDefinitionItemDtoFromInventory(inv, pr);
                if (inv.getItemId() != null) {
                    String normLot = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(inv.getLotNumber());
                    Map<String, BigDecimal> byLot = availableQtyByItemIdAndNormLot.get(inv.getItemId());
                    BigDecimal avail = BigDecimal.ZERO;
                    if (byLot != null) {
                        BigDecimal v = byLot.get(normLot);
                        if (v != null) {
                            avail = v;
                        }
                    }
                    dto.setAvailableQuantity(avail);
                } else {
                    dto.setAvailableQuantity(BigDecimal.ZERO);
                }
                BigDecimal lotCostUnit = null;
                if (StringUtils.isNotBlank(inv.getLotNumber())) {
                    lotCostUnit = this.chargeItemDefinitionQueryService.resolveFirstLotNumberCostPriceValue(
                        inv.getItemTable(), inv.getItemId(), inv.getLotNumber().trim());
                }
                dto.setPurchaseUnitPrice(lotCostUnit);
                ProductDefinitionCatalogEditorHintsDto hints =
                    ProductDefinitionCatalogEditorHintsAssembler.fromDisplayDto(pr, terminologyMaps);
                if (hints != null) {
                    List<ProductDefinitionCatalogUnitBandDto> bands = hints.getUnitBands();
                    dto.setUnitBands(bands);
                    BigDecimal packageAnchor = lotCostUnit;
                    if (packageAnchor == null && StringUtils.isNotBlank(inv.getItemTable()) && inv.getItemId() != null) {
                        packageAnchor = this.chargeItemDefinitionQueryService.resolveFirstPurchasePriceValue(
                            inv.getItemTable().trim(), inv.getItemId());
                    }
                    ProductDefinitionCatalogUnitBandsAssembler.applyInboundWarehouseBandPrices(
                        bands, pr, packageAnchor);
                }
                records.add(dto);
            }
            return PagedResultBuilder.build(new PageProductsForGainLossResponse(), records, page);
        } catch (RuntimeException ex) {
            log.warn("pageProductsForGainLoss failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_GL_102, "盘点选品查询失败");
        }
    }

    private void enrichGainLossListPurposeLocationNames(List<GainLossListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> purposeIds = new HashSet<>();
        for (GainLossListRowDto row : rows) {
            if (row != null && row.getPurposeLocationId() != null) {
                purposeIds.add(row.getPurposeLocationId());
            }
        }
        Map<Long, String> nameById = new HashMap<>();
        for (Long pid : purposeIds) {
            Optional<Location> locOpt = this.locationQueryService.findOptionalById(pid);
            if (locOpt.isEmpty()) {
                continue;
            }
            Location loc = locOpt.get();
            if (StringUtils.isNotBlank(loc.getName())) {
                nameById.put(pid, loc.getName().trim());
            }
        }
        for (GainLossListRowDto row : rows) {
            if (row == null || row.getPurposeLocationId() == null) {
                continue;
            }
            row.setPurposeLocationName(nameById.get(row.getPurposeLocationId()));
        }
    }

    private void enrichGainLossListStatusEnumText(List<GainLossListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (GainLossListRowDto row : rows) {
            if (row == null || StringUtils.isBlank(row.getStatusEnum())) {
                continue;
            }
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, row.getStatusEnum().trim());
            if (st != null) {
                row.setStatusEnumText(st.getDisplay());
            }
        }
    }

    private void enrichGainLossListDisplayNames(List<GainLossListRowDto> rows, List<SupplyRequestModel> detailLines) {
        if (rows == null || rows.isEmpty() || detailLines == null || detailLines.isEmpty()) {
            return;
        }
        Map<String, Long> busNoToApplicantId = new HashMap<>();
        Map<String, Long> busNoToApproverId = new HashMap<>();
        Map<String, SupplyRequestModel> headByBusNo = new LinkedHashMap<>();
        for (SupplyRequestModel line : detailLines) {
            if (line == null || StringUtils.isBlank(line.getBusNo())) {
                continue;
            }
            String bn = line.getBusNo().trim();
            SupplyRequestModel existing = headByBusNo.get(bn);
            if (existing == null) {
                headByBusNo.put(bn, line);
            } else {
                LocalDateTime existingApply = existing.getApplyTime();
                LocalDateTime lineApply = line.getApplyTime();
                if (lineApply != null) {
                    if (existingApply == null || lineApply.isAfter(existingApply)) {
                        headByBusNo.put(bn, line);
                    }
                }
            }
        }
        for (Map.Entry<String, SupplyRequestModel> e : headByBusNo.entrySet()) {
            SupplyRequestModel head = e.getValue();
            if (head == null) {
                continue;
            }
            String bn = e.getKey();
            if (head.getApplicantId() != null) {
                busNoToApplicantId.put(bn, head.getApplicantId());
            }
            if (head.getApproverId() != null) {
                busNoToApproverId.put(bn, head.getApproverId());
            }
        }
        Set<Long> practitionerIds = new HashSet<>();
        practitionerIds.addAll(busNoToApplicantId.values());
        practitionerIds.addAll(busNoToApproverId.values());
        Map<Long, String> practitionerNameById = new HashMap<>();
        for (Long pid : practitionerIds) {
            if (pid == null) {
                continue;
            }
            Practitioner pr = this.practitionerQueryService.findById(pid);
            if (pr != null && StringUtils.isNotBlank(pr.getName())) {
                practitionerNameById.put(pid, pr.getName());
            }
        }
        for (GainLossListRowDto row : rows) {
            if (row == null || StringUtils.isBlank(row.getBusNo())) {
                continue;
            }
            String bn = row.getBusNo().trim();
            Long aid = busNoToApplicantId.get(bn);
            if (aid != null) {
                String name = practitionerNameById.get(aid);
                if (name != null) {
                    row.setApplicantName(name);
                }
            }
            Long approverId = busNoToApproverId.get(bn);
            if (approverId != null) {
                String name = practitionerNameById.get(approverId);
                if (name != null) {
                    row.setApproverName(name);
                }
            }
        }
    }

    private void enrichGainLossDetailLines(
        List<GainLossDetailLineDto> detailLines,
        List<SupplyRequestModel> supplyLines,
        Map<String, ProductDefinitionDisplayDto> products) {
        if (detailLines == null || detailLines.isEmpty() || supplyLines == null) {
            return;
        }
        Map<Long, SupplyRequestModel> lineById = new HashMap<>();
        for (SupplyRequestModel line : supplyLines) {
            if (line != null && line.getId() != null) {
                lineById.put(line.getId(), line);
            }
        }
        Long purposeLocationId = null;
        if (!supplyLines.isEmpty() && supplyLines.get(0) != null) {
            purposeLocationId = supplyLines.get(0).getPurposeLocationId();
        }
        List<Long> locationIds = new ArrayList<>();
        if (purposeLocationId != null) {
            locationIds.add(purposeLocationId);
        }
        for (GainLossDetailLineDto dto : detailLines) {
            if (dto == null || dto.getLineId() == null) {
                continue;
            }
            SupplyRequestModel src = lineById.get(dto.getLineId());
            if (src == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(src.getItemTable(), src.getItemId());
            ProductDefinitionDisplayDto pr = products.get(key);
            if (pr != null) {
                dto.setProductName(pr.getProductName());
                dto.setSpecification(pr.getTotalVolume());
                dto.setManufacturer(pr.getManufacturer());
            }
            if (purposeLocationId == null || StringUtils.isBlank(src.getItemTable()) || src.getItemId() == null) {
                dto.setBookQuantity(BigDecimal.ZERO);
                continue;
            }
            String itemTable = src.getItemTable().trim();
            List<Long> itemIds = List.of(src.getItemId());
            List<InventoryCatalogAvailabilityRowModel> availRows =
                this.inventoryItemQueryService.findAvailableCatalogRowsForOrder(itemTable, itemIds, locationIds);
            String normLot = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(src.getLotNumber());
            BigDecimal bookQty = BigDecimal.ZERO;
            for (InventoryCatalogAvailabilityRowModel ar : availRows) {
                if (ar == null || ar.getQuantity() == null) {
                    continue;
                }
                if (!Objects.equals(ar.getLocationId(), purposeLocationId)) {
                    continue;
                }
                if (!Objects.equals(ar.getItemId(), src.getItemId())) {
                    continue;
                }
                String arLot = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(ar.getLotNumber());
                if (!normLot.equals(arLot)) {
                    continue;
                }
                bookQty = bookQty.add(ar.getQuantity());
            }
            dto.setBookQuantity(bookQty);
        }
    }

    private void enrichGainLossDetailLinesCatalogPricing(List<GainLossDetailLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (GainLossDetailLineDto dto : lines) {
            if (dto == null) {
                continue;
            }
            ProductDefinitionCatalogEditorHintsDto cat = dto.getProductCatalog();
            if (cat == null) {
                continue;
            }
            BigDecimal purchase = this.chargeItemDefinitionQueryService.resolveFirstPurchasePriceValue(
                dto.getItemTable(), dto.getItemId());
            cat.setPurchasePrice(purchase);
            List<ProductDefinitionCatalogUnitBandDto> bands = cat.getUnitBands();
            if (bands == null || bands.isEmpty()) {
                continue;
            }
            BigDecimal lotPackagePrice = null;
            if (StringUtils.isNotBlank(dto.getLotNumber())) {
                lotPackagePrice = this.chargeItemDefinitionQueryService.resolveFirstLotNumberCostPriceValue(
                    dto.getItemTable(), dto.getItemId(), dto.getLotNumber().trim());
            }
            ProductDefinitionCatalogUnitBandsAssembler.applyInboundWarehouseDetailLineBandPrices(
                bands, null, purchase, lotPackagePrice, dto.getPrice(), dto.getUnitCode());
        }
    }

    private void assertGainLossPurposeLocationGate(Long purposeLocationId) {
        Set<Long> warehouses = this.resolveGatedWarehouseLocationIdsForWarehouseGate();
        this.assertWarehouseLocationAllowed(purposeLocationId, warehouses);
    }

    private Set<Long> resolveGatedWarehouseLocationIdsForWarehouseGate() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveWarehouses());
    }

    private void assertWarehouseLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.WHS_GL_002, "药库位置不在当前用户可选药库范围");
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_002, "药库位置不在当前用户可选药库范围");
        }
        Optional<Location> locOpt = locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()
            || !LocationForm.WAREHOUSE.getCode().equals(locOpt.get().getFormEnum())
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_002, "盘点位置须为启用中的药库");
        }
    }

    private Long requireWarehousePreparerPractitionerId(
        String notLoggedInErrorCode,
        String notLoggedInMessage,
        String noPractitionerMessage) {
        Long userId = this.currentOperatorResolver.requireOperatorId(notLoggedInErrorCode, notLoggedInMessage);
        Practitioner practitioner = this.practitionerQueryService.findByUserId(userId);
        if (practitioner == null) {
            throw new BusinessException(notLoggedInErrorCode, noPractitionerMessage);
        }
        if (practitioner.getId() == null) {
            throw new BusinessException(notLoggedInErrorCode, noPractitionerMessage);
        }
        return practitioner.getId();
    }

    private void assertGainLossLinesFields(List<GainLossLineDto> lines) {
        if (lines == null) {
            return;
        }
        for (GainLossLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getItemId() == null || StringUtils.isBlank(line.getItemTable())) {
                throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "盘点明细须包含有效物品表与主键");
            }
            if (StringUtils.isBlank(line.getUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "盘点明细单位不能为空");
            }
            if (line.getGainLossQuantity() == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "盈亏数量不能为空");
            }
            if (line.getOccurrenceTime() == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "盘点明细制单时间不能为空");
            }
            String table = line.getItemTable().trim();
            if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)
                && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
                throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "物品表非法");
            }
        }
    }

    private void gateBusNo(String busNo) {
        if (StringUtils.isBlank(busNo)) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "busNo 不能为空");
        }
    }

    private void preAssertCabinetGainLoss(String busNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNo.trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_103, "盘库单不存在");
        }
        this.assertCabinetGainLoss(lines);
    }

    private void assertCabinetGainLoss(List<SupplyRequestModel> lines) {
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.CABINET_GAIN_LOSS.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_GL_103, "单据类型非药库盘库单");
            }
        }
    }

    private SupplyStatus loadHeadSupplyStatus(String busNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNo);
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            return null;
        }
        String statusCode = lines.get(0).getStatusEnum();
        if (StringUtils.isBlank(statusCode)) {
            return null;
        }
        return EnumUtil.fromCode(SupplyStatus.class, statusCode.trim());
    }

    private void assertProductTableNameWhitelistForGainLossSelection(String raw) {
        if (StringUtils.isBlank(raw)) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "productTableName 不能为空");
        }
        String table = raw.trim();
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)
            && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_001, "productTableName 非法");
        }
    }

    private String resolveLotNumberForGainLossInventory(String lotNumber) {
        if (StringUtils.isBlank(lotNumber)) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        return lotNumber.trim();
    }

    private String resolveProductName(SupplyRequestModel line, Map<String, ProductDefinitionDisplayDto> productByKey) {
        if (line == null || StringUtils.isBlank(line.getItemTable()) || line.getItemId() == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
        ProductDefinitionDisplayDto pr = productByKey.get(key);
        if (pr == null || StringUtils.isBlank(pr.getProductName())) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_104, "产品定义缺失，无法解析展示名称");
        }
        return pr.getProductName().trim();
    }

    private String resolveSplitUnitCode(SupplyRequestModel line, ProductDefinitionDisplayDto product) {
        if (product == null || StringUtils.isBlank(product.getSplitUnitCode())) {
            throw new BusinessException(ErrorCodeConstants.WHS_GL_104, "产品定义缺失或缺少拆零单位编码");
        }
        if (line != null && StringUtils.isNotBlank(line.getUnitCode())) {
            return line.getUnitCode().trim();
        }
        return product.getSplitUnitCode().trim();
    }

    private BusinessException mapSupplyWorkflowException(BusinessException ex, String defaultCode) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            return new BusinessException(defaultCode, ex.getMessage());
        }
        return ex;
    }

    private BusinessException mapAgreeException(BusinessException ex) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String msg = ex.getMessage();
            if (msg != null && msg.contains("已同意")) {
                return new BusinessException(ErrorCodeConstants.WHS_GL_104, msg);
            }
            return new BusinessException(ErrorCodeConstants.WHS_GL_003, msg);
        }
        return ex;
    }

    private BusinessException mapGainLossLocationException(BusinessException ex) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String msg = ex.getMessage();
            if (msg != null && (msg.contains("库房") || msg.contains("位置") || msg.contains("药库"))) {
                return new BusinessException(ErrorCodeConstants.WHS_GL_002, msg);
            }
            return new BusinessException(ErrorCodeConstants.WHS_GL_001, msg);
        }
        return ex;
    }
}
