/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.charging.service;

import com.zxplat.application.medicaltech.charging.assembler.MedicalTechChargingCatalogAssembler;
import com.zxplat.application.medicaltech.charging.assembler.MedicalTechChargingListAssembler;
import com.zxplat.application.medicaltech.charging.assembler.MedicalTechChargingWriteAssembler;
import com.zxplat.application.medicaltech.charging.dto.MedicalTechDeviceCatalogRowDto;
import com.zxplat.application.medicaltech.charging.dto.MedicalTechDevicePricingCancelItemDto;
import com.zxplat.application.medicaltech.charging.dto.MedicalTechDevicePricingWriteItemDto;
import com.zxplat.application.medicaltech.charging.dto.MedicalTechPricedDeviceRowDto;
import com.zxplat.application.medicaltech.charging.dto.MedicalTechServicePricingWriteItemDto;
import com.zxplat.application.medicaltech.charging.mapper.MedicalTechChargingMapper;
import com.zxplat.application.medicaltech.charging.request.CancelMedicalTechDevicePricingRequest;
import com.zxplat.application.medicaltech.charging.request.ConfirmMedicalTechDevicePricingRequest;
import com.zxplat.application.medicaltech.charging.request.ConfirmMedicalTechServicePricingRequest;
import com.zxplat.application.medicaltech.charging.request.ListMedicalTechPricedDevicesRequest;
import com.zxplat.application.medicaltech.charging.request.LoadMedicalTechChargingInitRequest;
import com.zxplat.application.medicaltech.charging.request.PageMedicalTechDeviceCatalogRequest;
import com.zxplat.application.medicaltech.charging.request.PageMedicalTechServiceCatalogRequest;
import com.zxplat.application.medicaltech.charging.response.CancelMedicalTechDevicePricingResponse;
import com.zxplat.application.medicaltech.charging.response.ConfirmMedicalTechDevicePricingResponse;
import com.zxplat.application.medicaltech.charging.response.ConfirmMedicalTechServicePricingResponse;
import com.zxplat.application.medicaltech.charging.response.ListMedicalTechPricedDevicesResponse;
import com.zxplat.application.medicaltech.charging.response.LoadMedicalTechChargingInitResponse;
import com.zxplat.application.medicaltech.charging.response.PageMedicalTechDeviceCatalogResponse;
import com.zxplat.application.medicaltech.charging.response.PageMedicalTechServiceCatalogResponse;
import com.zxplat.application.medicaltech.charging.support.MedicalTechChargingProcedureOrchestrator;
import com.zxplat.application.shared.activitydefinition.ActivityDefinitionPackageChargePersistSupport;
import com.zxplat.application.shared.inventory.InventoryItemProductSelectionPageLoader;
import com.zxplat.application.shared.nursestation.DeviceDispenseNurseWorkflowOrchestrator;
import com.zxplat.application.shared.pagination.PageQueryBinder;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.application.shared.product.ProductDefinitionCatalogPageLoader;
import com.zxplat.application.shared.product.ProductDefinitionPageQuery;
import com.zxplat.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.zxplat.application.shared.validator.CurrentPractitionerResolver;
import com.zxplat.framework.core.application.paging.PageWindowGate;
import com.zxplat.domain.administration.account.entity.Account;
import com.zxplat.domain.administration.account.service.AccountQueryService;
import com.zxplat.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.zxplat.domain.administration.chargeitem.command.OffsetNursePricingChargeItemsByServiceAnchorsCommand;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.clinical.procedure.service.ProcedureQueryService;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.constants.MedicalTechChargingConstants;
import com.zxplat.domain.share.constants.ReadModelOrderTypeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.domain.share.enums.EncounterClass;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.share.enums.OrganizationClassification;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.domain.workflow.devicedispense.command.GenerateDeviceDispenseFromTemporaryPathCommand;
import com.zxplat.domain.workflow.devicedispense.queries.model.DeviceDispenseListModel;
import com.zxplat.domain.workflow.devicedispense.service.DeviceDispenseDomainService;
import com.zxplat.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.zxplat.domain.workflow.devicerequest.command.CreateExecutedDeviceOrderCommand;
import com.zxplat.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.zxplat.domain.workflow.devicerequest.service.DeviceRequestDomainService;
import com.zxplat.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.zxplat.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.zxplat.domain.workflow.inventoryitem.queries.InventoryItemPageQuery;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.zxplat.domain.workflow.servicerequest.command.CreateFromMedicalTechPricingCommand;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 医技工作站·医技计费应用服务（AS-MT-CHG-001）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>仅住院；诊疗（执行科室=登录 TECH）+ 耗材（库房锁定登录 TECH 下属耗材房）。</li>
 *   <li>耗材划价：DeviceRequest（medical-tech-pricing）→ 临时路径发放 finalize → Charge PENDING_SETTLEMENT；不写 Procedure。</li>
 *   <li>诊疗划价：ServiceRequest（medical-tech-pricing）→ Procedure COMPLETED → Charge PENDING_SETTLEMENT（可带子项账单）。</li>
 *   <li>冲销：诊疗 CANCEL Procedure + 费用 offset；耗材发放回退 + 费用 offset；Request 保持完成态。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/medicalTechCharging/{methodName}}；不另写 RestController。</p>
 */
@Service
@RequiredArgsConstructor
public class MedicalTechChargingAppService {

    /** 会话登录科室（须 TECH）。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;

    /** 组织只读：TECH 门禁。 */
    private final OrganizationQueryService organizationQueryService;

    /** 就诊只读：住院门闸。 */
    private final EncounterQueryService encounterQueryService;

    /** 位置只读：登录科下属耗材房。 */
    private final LocationQueryService locationQueryService;

    /** 当前操作人（划价/冲销审计）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 就诊挂账账户（默认医保户/现金户）。 */
    private final AccountQueryService accountQueryService;

    /** 库存批号选品分页（关键字预解析）。 */
    private final InventoryItemProductSelectionPageLoader inventoryItemProductSelectionPageLoader;

    /** 诊疗产品定义分页（执行科 org_id 下推）。 */
    private final ProductDefinitionCatalogPageLoader productDefinitionCatalogPageLoader;

    /** 分页窗口门禁。 */
    private final PageWindowGate pageWindowGate;

    /** 选品目录装配。 */
    private final MedicalTechChargingCatalogAssembler medicalTechChargingCatalogAssembler;

    /** 写路径权威定价重算。 */
    private final MedicalTechChargingWriteAssembler medicalTechChargingWriteAssembler;

    /** 已划价列表装配。 */
    private final MedicalTechChargingListAssembler medicalTechChargingListAssembler;

    /** Request/Command/Response 字段映射。 */
    private final MedicalTechChargingMapper medicalTechChargingMapper;

    /** 耗材请求写。 */
    private final DeviceRequestDomainService deviceRequestDomainService;

    /** 诊疗请求写（医技划价建单）。 */
    private final ServiceRequestDomainService serviceRequestDomainService;

    /** 耗材发放写（临时路径 + promoteDraft）。 */
    private final DeviceDispenseDomainService deviceDispenseDomainService;

    /** 收费写（划价挂账/冲销）。 */
    private final ChargeItemDomainService chargeItemDomainService;

    /** 耗材请求只读。 */
    private final DeviceRequestQueryService deviceRequestQueryService;

    /** 诊疗请求只读。 */
    private final ServiceRequestQueryService serviceRequestQueryService;

    /** 诊疗活动定义只读（执行科 Gate）。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

    /** Procedure 只读（已划价客户可见时间轴）。 */
    private final ProcedureQueryService procedureQueryService;

    /** 医技划价诊疗 Procedure 写编排。 */
    private final MedicalTechChargingProcedureOrchestrator medicalTechChargingProcedureOrchestrator;

    /** 带子项诊疗子账单冻价落库。 */
    private final ActivityDefinitionPackageChargePersistSupport activityDefinitionPackageChargePersistSupport;

    /** 耗材发放只读。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;

    /** 收费只读。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 冲销时回退发放（已扣库则累加回补量）。 */
    private final DeviceDispenseNurseWorkflowOrchestrator deviceDispenseNurseWorkflowOrchestrator;

    /** 库存写：划价扣库 / 取消回补。 */
    private final InventoryItemDomainService inventoryItemDomainService;

    /**
     * IF-MT-CHG-05：加载医技计费初始化（本科耗材房选项）。
     *
     * @param request 就诊
     * @return 默认房与选项
     * @throws BusinessException 非 TECH / 非住院（EX-MT-CHG-101）
     */
    public LoadMedicalTechChargingInitResponse loadMedicalTechChargingInit(
        LoadMedicalTechChargingInitRequest request) {
        try {
            // Gate：TECH
            Long loginTechOrgId = this.requireLoginTechOrgId(MedicalTechChargingConstants.EX_MT_CHG_101);
            if (request == null || request.getEncounterId() == null) {
                throw new BusinessException(MedicalTechChargingConstants.EX_MT_CHG_101, "encounterId 不能为空");
            }
            // Pre：须住院就诊
            EncounterDetailQueryModel encounter =
                this.encounterQueryService.mustGetEncounter(request.getEncounterId());
            this.assertInpatientEncounter(encounter.getClassEnum(), MedicalTechChargingConstants.EX_MT_CHG_101);
            // Pre：登录科下属耗材房
            List<Location> locations = this.listAllowedTechLocations(loginTechOrgId);
            List<OptionDto> options = new ArrayList<>();
            Long defaultLocationId = null;
            long sort = 1L;
            for (Location location : locations) {
                if (location == null || location.getId() == null) {
                    continue;
                }
                String label = location.getName();
                if (StringUtils.isEmpty(label)) {
                    label = String.valueOf(location.getId());
                }
                options.add(new OptionDto(Long.valueOf(sort), String.valueOf(location.getId()), label));
                // 默认取首个
                if (defaultLocationId == null) {
                    defaultLocationId = location.getId();
                }
                sort++;
            }
            // Asm：options + 默认房 → 初始化响应
            return this.medicalTechChargingMapper.toInitResponse(
                request.getEncounterId(), defaultLocationId, options);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(MedicalTechChargingConstants.EX_MT_CHG_101, "无法加载耗材目录");
        }
    }

    /**
     * IF-MT-CHG-01：分页查询医技耗材选品（TECH 下属耗材房库存驱动）。
     *
     * @param request 就诊、关键字、库房、分页
     * @return 选品分页
     * @throws BusinessException EX-MT-CHG-101
     */
    public PageMedicalTechDeviceCatalogResponse pageMedicalTechDeviceCatalog(
        PageMedicalTechDeviceCatalogRequest request) {
        try {
            // Gate：TECH + 分页窗口
            Long loginTechOrgId = this.requireLoginTechOrgId(MedicalTechChargingConstants.EX_MT_CHG_101);
            if (request == null || request.getEncounterId() == null) {
                throw new BusinessException(MedicalTechChargingConstants.EX_MT_CHG_101, "encounterId 不能为空");
            }
            this.pageWindowGate.requirePositivePageWindow(
                request, MedicalTechChargingConstants.EX_MT_CHG_101, "分页参数无效");
            // Pre：住院就诊
            EncounterDetailQueryModel encounter =
                this.encounterQueryService.mustGetEncounter(request.getEncounterId());
            this.assertInpatientEncounter(encounter.getClassEnum(), MedicalTechChargingConstants.EX_MT_CHG_101);
            Set<Long> allowedLocationIds = this.resolveAllowedLocationIds(loginTechOrgId);
            // 无本科耗材房：空页
            if (allowedLocationIds.isEmpty()) {
                return PagedResultBuilder.build(
                    new PageMedicalTechDeviceCatalogResponse(),
                    List.of(),
                    0L,
                    request.getPageNo(),
                    request.getBoundedPageSize());
            }
            List<Long> locationIds = new ArrayList<>(allowedLocationIds);
            // 指定库房须∈允许集合
            if (request.getLocationId() != null) {
                if (!allowedLocationIds.contains(request.getLocationId())) {
                    throw new BusinessException(
                        MedicalTechChargingConstants.EX_MT_CHG_101, "只能从本科耗材房选品");
                }
                locationIds = List.of(request.getLocationId());
            }
            InventoryItemPageQuery invQuery = new InventoryItemPageQuery();
            invQuery.setItemTable(TableNameConstants.ADM_DEVICE_DEFINITION);
            invQuery.setLocationIds(locationIds);
            invQuery.setInventoryStatusEnum(PublicationStatus.ACTIVE.getCode());
            invQuery.setIncludeZeroQuantity(Boolean.TRUE);
            PageQueryBinder.bind(request, invQuery);
            Pageable pageable = PageRequest.of(
                request.springPageIndex(),
                request.getBoundedPageSize(),
                Sort.by(Sort.Direction.ASC, FieldNameConstants.ID));
            // Pre：库存批号分页
            Page<InventoryItemModel> inventoryPage =
                this.inventoryItemProductSelectionPageLoader.page(invQuery, pageable);
            // Asm：库存 → 选品行
            List<MedicalTechDeviceCatalogRowDto> rows =
                this.medicalTechChargingCatalogAssembler.assembleCatalogRows(inventoryPage.getContent());
            return PagedResultBuilder.build(new PageMedicalTechDeviceCatalogResponse(), rows, inventoryPage);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(MedicalTechChargingConstants.EX_MT_CHG_101, "无法加载耗材目录");
        }
    }

    /**
     * IF-MT-CHG-06：分页查询医技诊疗选品（执行科室=登录 TECH）。
     *
     * @param request 就诊、关键字、类目、分页
     * @return 选品分页
     * @throws BusinessException EX-MT-CHG-101
     */
    public PageMedicalTechServiceCatalogResponse pageMedicalTechServiceCatalog(
        PageMedicalTechServiceCatalogRequest request) {
        try {
            // Gate：TECH + 分页窗口
            Long loginTechOrgId = this.requireLoginTechOrgId(MedicalTechChargingConstants.EX_MT_CHG_101);
            if (request == null || request.getEncounterId() == null) {
                throw new BusinessException(MedicalTechChargingConstants.EX_MT_CHG_101, "encounterId 不能为空");
            }
            this.pageWindowGate.requirePositivePageWindow(
                request, MedicalTechChargingConstants.EX_MT_CHG_101, "分页参数无效");
            // Pre：住院就诊
            EncounterDetailQueryModel encounter =
                this.encounterQueryService.mustGetEncounter(request.getEncounterId());
            this.assertInpatientEncounter(encounter.getClassEnum(), MedicalTechChargingConstants.EX_MT_CHG_101);
            // Pre：活动定义分页，锁定执行科=登录 TECH
            ProductDefinitionPageQuery pageQuery = new ProductDefinitionPageQuery();
            PageQueryBinder.bind(request, pageQuery);
            pageQuery.setPricingFlag(1);
            pageQuery.setActiveFlag(Boolean.TRUE);
            pageQuery.setProductTableNames(List.of(TableNameConstants.WKF_ACTIVITY_DEFINITION));
            pageQuery.setOrgId(loginTechOrgId);
            // 可选类目收窄；关键字已由 PageQueryBinder 写入 pageQuery
            if (StringUtils.isNotEmpty(request.getCategoryCode())) {
                pageQuery.setCategoryCode(request.getCategoryCode().trim());
            }
            Page<ProductDefinitionDisplayDto> page =
                this.productDefinitionCatalogPageLoader.pageProductDefinition(pageQuery);
            // Asm：活动定义 → 选品行
            List<MedicalTechDeviceCatalogRowDto> rows =
                this.medicalTechChargingCatalogAssembler.assembleServiceCatalogRows(page.getContent());
            return PagedResultBuilder.build(new PageMedicalTechServiceCatalogResponse(), rows, page);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(MedicalTechChargingConstants.EX_MT_CHG_101, "无法加载诊疗目录");
        }
    }

    /**
     * IF-MT-CHG-02：确认医技耗材划价（对齐护士耗材划价：finalize 发放并按开关扣库；不写 Procedure）。
     *
     * @param request 就诊与写行
     * @return 新建请求 id
     * @throws BusinessException EX-MT-CHG-201/202
     */
    @Transactional(rollbackFor = Exception.class)
    public ConfirmMedicalTechDevicePricingResponse confirmMedicalTechDevicePricing(
        ConfirmMedicalTechDevicePricingRequest request) {
        try {
            // Gate：TECH + 非空 items（非 TECH/入参非法走 EX-202；库房非法单独 EX-201）
            Long loginTechOrgId = this.requireLoginTechOrgId(MedicalTechChargingConstants.EX_MT_CHG_202);
            if (request == null || request.getEncounterId() == null) {
                throw new BusinessException(MedicalTechChargingConstants.EX_MT_CHG_202, "encounterId 不能为空");
            }
            if (request.getItems() == null || request.getItems().isEmpty()) {
                throw new BusinessException(MedicalTechChargingConstants.EX_MT_CHG_202, "划价行不能为空");
            }
            // Pre：住院就诊
            EncounterDetailQueryModel encounter =
                this.encounterQueryService.mustGetEncounter(request.getEncounterId());
            this.assertInpatientEncounter(encounter.getClassEnum(), MedicalTechChargingConstants.EX_MT_CHG_202);
            Set<Long> allowedLocationIds = this.resolveAllowedLocationIds(loginTechOrgId);
            // Pre：每行库房须∈本科
            for (MedicalTechDevicePricingWriteItemDto item : request.getItems()) {
                if (item == null || item.getLocationId() == null
                    || !allowedLocationIds.contains(item.getLocationId())) {
                    throw new BusinessException(
                        MedicalTechChargingConstants.EX_MT_CHG_201, "只能从本科耗材房划价");
                }
                if (item.getDefinitionId() == null || item.getQuantity() == null
                    || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0
                    || StringUtils.isEmpty(item.getUnitCode())) {
                    throw new BusinessException(
                        MedicalTechChargingConstants.EX_MT_CHG_202, "划价项目无效或价格异常，请重新选品");
                }
            }
            Long patientId = encounter.getPatientId();
            Map<Long, Account> accountByEncounter =
                this.accountQueryService.mapDefaultChargeAccountsByEncounterIds(
                    List.of(request.getEncounterId()));
            Account chargeAccount = accountByEncounter.get(request.getEncounterId());
            Long accountId = request.getAccountId();
            // 请求未传账户则用默认挂账户
            if (accountId == null && chargeAccount != null) {
                accountId = chargeAccount.getId();
            }
            if (accountId == null) {
                throw new BusinessException(
                    MedicalTechChargingConstants.EX_MT_CHG_202, "划价项目无效或价格异常，请重新选品");
            }
            for (MedicalTechDevicePricingWriteItemDto item : request.getItems()) {
                item.setPatientId(patientId);
                item.setAccountId(accountId);
            }
            // Pre：权威定价重算
            this.medicalTechChargingWriteAssembler.reapplyAuthoritativeWritePricing(
                request.getItems(), loginTechOrgId, request.getEncounterId());
            Long operatorId = this.currentPractitionerResolver.requireOperatorId(
                MedicalTechChargingConstants.EX_MT_CHG_202, "划价失败请重试");
            // 与护士划价同构：finalize 累计待扣量，批末合并扣库
            Map<Long, BigDecimal> inventoryDecreaseAccumulator = new HashMap<>();
            // Cmd1：创建已执行耗材请求（medical-tech-pricing）
            CreateExecutedDeviceOrderCommand deviceCmd =
                this.medicalTechChargingMapper.toCreateExecutedDeviceOrderCommand(
                    request.getItems(), request.getEncounterId(), operatorId);
            List<Long> deviceReqIds = this.deviceRequestDomainService.createExecutedDeviceOrder(deviceCmd);
            List<Long> createdIds = new ArrayList<>();
            if (deviceReqIds != null) {
                createdIds.addAll(deviceReqIds);
            }
            for (int i = 0; i < request.getItems().size(); i++) {
                MedicalTechDevicePricingWriteItemDto row = request.getItems().get(i);
                Long deviceReqId = null;
                if (deviceReqIds != null && i < deviceReqIds.size()) {
                    deviceReqId = deviceReqIds.get(i);
                }
                if (deviceReqId == null) {
                    throw new BusinessException(
                        MedicalTechChargingConstants.EX_MT_CHG_202, "划价失败请重试");
                }
                // Cmd2：临时路径生成发放草稿
                GenerateDeviceDispenseFromTemporaryPathCommand genCmd =
                    this.medicalTechChargingMapper.toGenerateDeviceDispenseFromTemporaryPathCommand(
                        deviceReqId, row, request.getEncounterId(), operatorId);
                this.deviceDispenseDomainService.generateDeviceDispenseFromTemporaryPath(genCmd);
                // Cmd3：对齐护士划价——finalize（自动扣库开则累计扣减；关则仅拣配）
                this.deviceDispenseNurseWorkflowOrchestrator.finalizeInpatientTemporaryPathDispense(
                    deviceReqId, operatorId, operatorId, inventoryDecreaseAccumulator);
                DeviceDispenseListModel primaryDispense =
                    this.deviceDispenseQueryService.resolvePrimaryDispenseForDeviceRequest(deviceReqId);
                Long dispenseId = null;
                if (primaryDispense != null) {
                    dispenseId = primaryDispense.getId();
                }
                // Cmd4：挂待结算费用（requestingOrgId=loginTech）
                CreateChargeItemCommand chargeCmd =
                    this.medicalTechChargingMapper.toCreateChargeItemCommand(
                        row, deviceReqId, dispenseId, operatorId, loginTechOrgId, request.getEncounterId());
                this.chargeItemDomainService.priceByNurseStation(chargeCmd);
            }
            // Cmd5：合并扣库（开关关闭时累加器为空则跳过）
            if (!inventoryDecreaseAccumulator.isEmpty()) {
                this.inventoryItemDomainService.decreaseQuantitiesMerged(
                    operatorId, inventoryDecreaseAccumulator);
            }
            // Asm：新建请求 id 列表 → 成功响应
            return this.medicalTechChargingMapper.toConfirmResponse(createdIds);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(MedicalTechChargingConstants.EX_MT_CHG_202, "划价失败请重试");
        }
    }

    /**
     * IF-MT-CHG-07：确认医技诊疗划价（ServiceRequest + Procedure + Charge；来源 medical-tech-pricing）。
     *
     * @param request 就诊与诊疗写行
     * @return 新建服务申请 id
     * @throws BusinessException EX-MT-CHG-202
     */
    @Transactional(rollbackFor = Exception.class)
    public ConfirmMedicalTechServicePricingResponse confirmMedicalTechServicePricing(
        ConfirmMedicalTechServicePricingRequest request) {
        try {
            // Gate：TECH + 非空 items
            Long loginTechOrgId = this.requireLoginTechOrgId(MedicalTechChargingConstants.EX_MT_CHG_202);
            if (request == null || request.getEncounterId() == null) {
                throw new BusinessException(MedicalTechChargingConstants.EX_MT_CHG_202, "encounterId 不能为空");
            }
            if (request.getItems() == null || request.getItems().isEmpty()) {
                throw new BusinessException(MedicalTechChargingConstants.EX_MT_CHG_202, "划价行不能为空");
            }
            // Pre：住院就诊
            EncounterDetailQueryModel encounter =
                this.encounterQueryService.mustGetEncounter(request.getEncounterId());
            this.assertInpatientEncounter(encounter.getClassEnum(), MedicalTechChargingConstants.EX_MT_CHG_202);
            // Pre：每行活动须属登录 TECH 执行科
            this.assertServicePricingActivitiesBelongToLoginTech(request.getItems(), loginTechOrgId);
            for (MedicalTechServicePricingWriteItemDto item : request.getItems()) {
                if (item == null || item.getActivityId() == null || item.getQuantity() == null
                    || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0
                    || StringUtils.isEmpty(item.getUnitCode())) {
                    throw new BusinessException(
                        MedicalTechChargingConstants.EX_MT_CHG_202, "划价项目无效或价格异常，请重新选品");
                }
            }
            Long patientId = encounter.getPatientId();
            Map<Long, Account> accountByEncounter =
                this.accountQueryService.mapDefaultChargeAccountsByEncounterIds(
                    List.of(request.getEncounterId()));
            Account chargeAccount = accountByEncounter.get(request.getEncounterId());
            Long accountId = request.getAccountId();
            if (accountId == null && chargeAccount != null) {
                accountId = chargeAccount.getId();
            }
            if (accountId == null) {
                throw new BusinessException(
                    MedicalTechChargingConstants.EX_MT_CHG_202, "划价项目无效或价格异常，请重新选品");
            }
            for (MedicalTechServicePricingWriteItemDto item : request.getItems()) {
                item.setPatientId(patientId);
                item.setAccountId(accountId);
            }
            // Pre：权威 SALE 定价重算
            this.medicalTechChargingWriteAssembler.reapplyAuthoritativeServiceWritePricing(
                request.getItems(), loginTechOrgId, request.getEncounterId());
            Long operatorId = this.currentPractitionerResolver.requireOperatorId(
                MedicalTechChargingConstants.EX_MT_CHG_202, "划价失败请重试");
            // Cmd1：创建已校对诊疗申请（medical-tech-pricing）
            CreateFromMedicalTechPricingCommand serviceCmd =
                this.medicalTechChargingMapper.toCreateFromMedicalTechPricingCommand(
                    request.getItems(), request.getEncounterId(), operatorId, operatorId);
            List<Long> serviceRequestIds = this.serviceRequestDomainService.createFromMedicalTechPricing(serviceCmd);
            List<Long> createdIds = new ArrayList<>();
            if (serviceRequestIds != null) {
                createdIds.addAll(serviceRequestIds);
            }
            LocalDateTime pricedAt = LocalDateTime.now();
            for (int i = 0; i < request.getItems().size(); i++) {
                MedicalTechServicePricingWriteItemDto row = request.getItems().get(i);
                Long serviceRequestId = null;
                if (serviceRequestIds != null && i < serviceRequestIds.size()) {
                    serviceRequestId = serviceRequestIds.get(i);
                }
                if (serviceRequestId == null) {
                    throw new BusinessException(
                        MedicalTechChargingConstants.EX_MT_CHG_202, "划价失败请重试");
                }
                // Cmd2：划价即视为当场执行；Procedure.org_id=登录 TECH
                Long procedureId =
                    this.medicalTechChargingProcedureOrchestrator.registerCompletedMedicalTechPricingProcedure(
                        serviceRequestId,
                        request.getEncounterId(),
                        patientId,
                        operatorId,
                        pricedAt,
                        loginTechOrgId);
                // Cmd3：挂待结算费用
                CreateChargeItemCommand chargeCmd =
                    this.medicalTechChargingMapper.toCreateServiceChargeItemCommand(
                        row, serviceRequestId, procedureId, operatorId, loginTechOrgId, request.getEncounterId());
                Long parentChargeId = this.chargeItemDomainService.priceByNurseStation(chargeCmd);
                // Cmd4：有套餐子表时冻价写子账单
                this.activityDefinitionPackageChargePersistSupport.persistPackageChildChargeItemsAfterParent(
                    parentChargeId, row.getActivityId(), operatorId);
            }
            // Asm：新建请求 id → 成功响应
            return this.medicalTechChargingMapper.toConfirmServiceResponse(createdIds);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(MedicalTechChargingConstants.EX_MT_CHG_202, "划价失败请重试");
        }
    }

    /**
     * IF-MT-CHG-03：查询已划价列表（耗材 + 诊疗；medical-tech-pricing；过渡兼容历史 nurse-pricing 耗材）。
     *
     * @param request 就诊
     * @return 已划价行（含 orderType）
     * @throws BusinessException EX-MT-CHG-101
     */
    public ListMedicalTechPricedDevicesResponse listMedicalTechPricedDevices(
        ListMedicalTechPricedDevicesRequest request) {
        try {
            // Gate：TECH
            Long loginTechOrgId = this.requireLoginTechOrgId(MedicalTechChargingConstants.EX_MT_CHG_101);
            if (request == null || request.getEncounterId() == null) {
                throw new BusinessException(MedicalTechChargingConstants.EX_MT_CHG_101, "encounterId 不能为空");
            }
            // Pre：住院
            EncounterDetailQueryModel encounter =
                this.encounterQueryService.mustGetEncounter(request.getEncounterId());
            this.assertInpatientEncounter(encounter.getClassEnum(), MedicalTechChargingConstants.EX_MT_CHG_101);
            Set<Long> allowedLocationIds = this.resolveAllowedLocationIds(loginTechOrgId);
            // Pre：耗材轨（本科房）
            List<DeviceRequestListModel> allDevices =
                this.deviceRequestQueryService.listDeviceRequestsByEncounter(request.getEncounterId());
            List<DeviceRequestListModel> filteredDevices = new ArrayList<>();
            List<Long> deviceIds = new ArrayList<>();
            if (allDevices != null) {
                for (DeviceRequestListModel row : allDevices) {
                    if (row == null || row.getId() == null) {
                        continue;
                    }
                    if (!this.isMedicalTechPricingSource(row.getGenerateSourceEnum())) {
                        continue;
                    }
                    Long locationId = row.getPerformLocation();
                    if (locationId == null) {
                        locationId = row.getLocationId();
                    }
                    if (locationId == null || !allowedLocationIds.contains(locationId)) {
                        continue;
                    }
                    filteredDevices.add(row);
                    deviceIds.add(row.getId());
                }
            }
            // Pre：诊疗轨（medical-tech-pricing；请求侧无库房维）
            String medicalTechSource = GenerateSource.MEDICAL_TECH_PRICING.getCode();
            List<ServiceRequestListModel> allServices =
                this.serviceRequestQueryService.listServiceRequestsByGenerateSource(
                    request.getEncounterId(), medicalTechSource);
            List<ServiceRequestListModel> filteredServices = new ArrayList<>();
            List<Long> serviceIds = new ArrayList<>();
            if (allServices != null) {
                for (ServiceRequestListModel row : allServices) {
                    if (row == null || row.getId() == null) {
                        continue;
                    }
                    filteredServices.add(row);
                    serviceIds.add(row.getId());
                }
            }
            Map<Long, Long> primaryDispenseIdByReq =
                this.deviceDispenseQueryService.mapPrimaryDispenseIdByDeviceReqIds(deviceIds);
            Map<Long, String> dispenseStatusByReq =
                this.deviceDispenseQueryService.mapPrimaryDispenseStatusByDeviceReqIds(deviceIds);
            Map<Long, LocalDateTime> displayTimeByReq =
                this.deviceDispenseQueryService.mapPrimaryDisplayTimeByDeviceReqIds(deviceIds);
            Map<Long, LocalDateTime> recordedTimeByServiceReq =
                this.procedureQueryService.mapNetCompletedRecordedTimeByServiceRequestIds(
                    List.of(request.getEncounterId()), serviceIds);
            Set<Long> chargeAnchorIds = new LinkedHashSet<>(deviceIds);
            chargeAnchorIds.addAll(serviceIds);
            ChargeItemQueryCommand chargeCmd = new ChargeItemQueryCommand();
            chargeCmd.setServiceIds(new ArrayList<>(chargeAnchorIds));
            chargeCmd.setExcludeSupersededSettledLines(Boolean.TRUE);
            List<ChargeItem> linkedCharges =
                this.chargeItemQueryService.listChargeItemsByServiceIds(chargeCmd);
            Map<Long, List<ChargeItem>> chargesByServiceId =
                this.medicalTechChargingListAssembler.groupChargesByServiceId(linkedCharges);
            // Asm：耗材行 + 诊疗行
            List<MedicalTechPricedDeviceRowDto> rows = new ArrayList<>();
            rows.addAll(this.medicalTechChargingListAssembler.assemblePricedRows(
                filteredDevices, primaryDispenseIdByReq, dispenseStatusByReq, displayTimeByReq, chargesByServiceId));
            rows.addAll(this.medicalTechChargingListAssembler.assembleServicePricedRows(
                filteredServices, recordedTimeByServiceReq, chargesByServiceId));
            return this.medicalTechChargingMapper.toPricedListResponse(rows);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(MedicalTechChargingConstants.EX_MT_CHG_101, "已划价列表加载失败");
        }
    }

    /**
     * IF-MT-CHG-04：取消医技划价（耗材：回退发放+冲销；诊疗：CANCEL Procedure+冲销；不 revoke 请求）。
     *
     * @param request 就诊与取消行（orderType 区分轨；空=耗材兼容）
     * @return 冲销条数
     * @throws BusinessException EX-MT-CHG-202/203
     */
    @Transactional(rollbackFor = Exception.class)
    public CancelMedicalTechDevicePricingResponse cancelMedicalTechDevicePricing(
        CancelMedicalTechDevicePricingRequest request) {
        try {
            // Gate：TECH + items 非空
            Long loginTechOrgId = this.requireLoginTechOrgId(MedicalTechChargingConstants.EX_MT_CHG_202);
            if (request == null || request.getEncounterId() == null) {
                throw new BusinessException(MedicalTechChargingConstants.EX_MT_CHG_202, "encounterId 不能为空");
            }
            if (request.getItems() == null || request.getItems().isEmpty()) {
                throw new BusinessException(MedicalTechChargingConstants.EX_MT_CHG_202, "请选择要取消的划价行");
            }
            // Pre：按 orderType 分轨收集 id
            LinkedHashSet<Long> deviceRequestIds = new LinkedHashSet<>();
            LinkedHashSet<Long> serviceRequestIds = new LinkedHashSet<>();
            for (MedicalTechDevicePricingCancelItemDto item : request.getItems()) {
                if (item == null || item.getRequestId() == null) {
                    continue;
                }
                String orderType = item.getOrderType();
                // 空 orderType 兼容历史前端：按耗材轨
                if (StringUtils.isEmpty(orderType)
                    || ReadModelOrderTypeConstants.DEVICE_REQUEST.equals(orderType.trim())) {
                    deviceRequestIds.add(item.getRequestId());
                } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(orderType.trim())) {
                    serviceRequestIds.add(item.getRequestId());
                } else {
                    throw new BusinessException(
                        MedicalTechChargingConstants.EX_MT_CHG_202, "所选划价行无效，请刷新列表后重试");
                }
            }
            if (deviceRequestIds.isEmpty() && serviceRequestIds.isEmpty()) {
                throw new BusinessException(MedicalTechChargingConstants.EX_MT_CHG_202, "请选择要取消的划价行");
            }
            // Pre：住院
            EncounterDetailQueryModel encounter =
                this.encounterQueryService.mustGetEncounter(request.getEncounterId());
            this.assertInpatientEncounter(encounter.getClassEnum(), MedicalTechChargingConstants.EX_MT_CHG_202);
            Set<Long> allowedLocationIds = this.resolveAllowedLocationIds(loginTechOrgId);
            // Pre：耗材轨 Gate（来源 + 本科房）
            if (!deviceRequestIds.isEmpty()) {
                Map<Long, DeviceRequestListModel> deviceById =
                    this.deviceRequestQueryService.mapListModelsByIds(new ArrayList<>(deviceRequestIds));
                for (Long requestId : deviceRequestIds) {
                    DeviceRequestListModel model = deviceById.get(requestId);
                    if (model == null || !this.isMedicalTechPricingSource(model.getGenerateSourceEnum())
                        || !request.getEncounterId().equals(model.getEncounterId())) {
                        throw new BusinessException(
                            MedicalTechChargingConstants.EX_MT_CHG_202, "所选划价行无效，请刷新列表后重试");
                    }
                    Long locationId = model.getPerformLocation();
                    if (locationId == null) {
                        locationId = model.getLocationId();
                    }
                    if (locationId == null || !allowedLocationIds.contains(locationId)) {
                        throw new BusinessException(
                            MedicalTechChargingConstants.EX_MT_CHG_201, "只能取消本科耗材房划价行");
                    }
                }
            }
            // Pre：诊疗轨 Gate（仅 medical-tech-pricing，禁止过渡 nurse-pricing）
            if (!serviceRequestIds.isEmpty()) {
                Map<Long, ServiceRequestListModel> serviceById =
                    this.serviceRequestQueryService.mapListModelsByIds(new ArrayList<>(serviceRequestIds));
                String medicalTechSource = GenerateSource.MEDICAL_TECH_PRICING.getCode();
                for (Long requestId : serviceRequestIds) {
                    ServiceRequestListModel model = serviceById.get(requestId);
                    if (model == null || !medicalTechSource.equals(model.getGenerateSourceEnum())
                        || !request.getEncounterId().equals(model.getEncounterId())) {
                        throw new BusinessException(
                            MedicalTechChargingConstants.EX_MT_CHG_202, "所选划价行无效，请刷新列表后重试");
                    }
                }
            }
            // Pre：费用须待结算（已结算 EX-203）
            LinkedHashSet<Long> allAnchorIds = new LinkedHashSet<>(deviceRequestIds);
            allAnchorIds.addAll(serviceRequestIds);
            ChargeItemQueryCommand chargeCmd = new ChargeItemQueryCommand();
            chargeCmd.setServiceIds(new ArrayList<>(allAnchorIds));
            chargeCmd.setExcludeSupersededSettledLines(Boolean.TRUE);
            List<ChargeItem> linkedCharges =
                this.chargeItemQueryService.listChargeItemsByServiceIds(chargeCmd);
            this.assertCancelPricingChargeGate(linkedCharges, allAnchorIds);
            Long operatorId = this.currentPractitionerResolver.requireOperatorId(
                MedicalTechChargingConstants.EX_MT_CHG_202, "取消划价失败，请稍后重试");
            Map<Long, BigDecimal> inventoryIncreaseAccumulator = new HashMap<>();
            // Cmd：耗材轨回退发放 + 冲销
            for (Long deviceReqId : deviceRequestIds) {
                this.deviceDispenseNurseWorkflowOrchestrator.revertInpatientPricingDeviceDispenses(
                    deviceReqId, operatorId, inventoryIncreaseAccumulator);
                OffsetNursePricingChargeItemsByServiceAnchorsCommand offsetChargeCmd =
                    this.medicalTechChargingMapper.toOffsetNursePricingChargeItemsByServiceAnchorsCommand(
                        request.getEncounterId(),
                        TableNameConstants.WKF_DEVICE_REQUEST,
                        deviceReqId,
                        operatorId);
                this.chargeItemDomainService.offsetNursePricingChargeItemsByServiceAnchors(offsetChargeCmd);
            }
            // Cmd：诊疗轨 CANCEL Procedure + 冲销；ServiceRequest 保持 VERIFIED
            if (!serviceRequestIds.isEmpty()) {
                this.medicalTechChargingProcedureOrchestrator.cancelCompletedMedicalTechPricingProcedures(
                    serviceRequestIds, operatorId);
            }
            for (Long serviceReqId : serviceRequestIds) {
                OffsetNursePricingChargeItemsByServiceAnchorsCommand offsetChargeCmd =
                    this.medicalTechChargingMapper.toOffsetNursePricingChargeItemsByServiceAnchorsCommand(
                        request.getEncounterId(),
                        TableNameConstants.WKF_SERVICE_REQUEST,
                        serviceReqId,
                        operatorId);
                this.chargeItemDomainService.offsetNursePricingChargeItemsByServiceAnchors(offsetChargeCmd);
            }
            if (!inventoryIncreaseAccumulator.isEmpty()) {
                this.inventoryItemDomainService.increaseQuantities(
                    this.medicalTechChargingMapper.toIncreaseQuantityCommand(
                        operatorId, inventoryIncreaseAccumulator));
            }
            // Asm：冲销条数 → 响应
            return this.medicalTechChargingMapper.toCancelResponse(allAnchorIds.size());
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(MedicalTechChargingConstants.EX_MT_CHG_202, "取消划价失败请重试");
        }
    }

    /**
     * 取消划价费用 Gate：锚点下医技划价（含历史 nurse-pricing）费用须全部待结算。
     *
     * @param linkedCharges   锚点关联费用
     * @param cancelAnchorIds 待取消请求 id
     */
    private void assertCancelPricingChargeGate(
        List<ChargeItem> linkedCharges,
        Set<Long> cancelAnchorIds) {
        if (linkedCharges == null || linkedCharges.isEmpty()) {
            return;
        }
        List<ChargeItem> gatedCharges =
            this.chargeItemQueryService.filterOutSupersededSettledLinesGroupedByEncounter(linkedCharges);
        String pendingCode = ChargeItemStatus.PENDING_SETTLEMENT.getCode();
        for (ChargeItem charge : gatedCharges) {
            if (charge == null || charge.getServiceId() == null) {
                continue;
            }
            if (!cancelAnchorIds.contains(charge.getServiceId())) {
                continue;
            }
            if (!this.isMedicalTechPricingSource(charge.getGenerateSourceEnum())) {
                continue;
            }
            String status = charge.getStatusEnum();
            // 已结算/退费链路整批拒绝
            if (ChargeItemStatus.SETTLED.getCode().equals(status)
                || ChargeItemStatus.REFUNDING.getCode().equals(status)
                || ChargeItemStatus.REFUNDED.getCode().equals(status)) {
                throw new BusinessException(
                    MedicalTechChargingConstants.EX_MT_CHG_203, "已结算不可冲销");
            }
            // 非待结算同样拒绝
            if (!pendingCode.equals(status)) {
                throw new BusinessException(
                    MedicalTechChargingConstants.EX_MT_CHG_203, "已结算不可冲销");
            }
        }
    }

    /**
     * 诊疗划价 Gate：活动定义执行科室须等于登录 TECH（对齐护士 BR-IP-PRC-034 反面）。
     *
     * @param items          诊疗写行
     * @param loginTechOrgId 登录 TECH
     */
    private void assertServicePricingActivitiesBelongToLoginTech(
        List<MedicalTechServicePricingWriteItemDto> items,
        Long loginTechOrgId) {
        Set<Long> activityIds = new LinkedHashSet<>();
        for (MedicalTechServicePricingWriteItemDto row : items) {
            if (row == null || row.getActivityId() == null) {
                continue;
            }
            activityIds.add(row.getActivityId());
        }
        if (activityIds.isEmpty()) {
            return;
        }
        List<ActivityDefinition> activities =
            this.activityDefinitionQueryService.findAllByIds(activityIds);
        Map<Long, Long> activityOrgById = new HashMap<>();
        if (activities != null) {
            for (ActivityDefinition activity : activities) {
                if (activity == null || activity.getId() == null) {
                    continue;
                }
                activityOrgById.put(activity.getId(), activity.getOrgId());
            }
        }
        for (MedicalTechServicePricingWriteItemDto row : items) {
            if (row == null || row.getActivityId() == null) {
                continue;
            }
            Long activityOrgId = activityOrgById.get(row.getActivityId());
            // Gate：无执行科或非本科 TECH 拒绝，防止划他科诊疗
            if (activityOrgId == null || !loginTechOrgId.equals(activityOrgId)) {
                throw new BusinessException(
                    MedicalTechChargingConstants.EX_MT_CHG_202,
                    "只能划价本科执行科室的诊疗项目");
            }
        }
    }

    /**
     * 是否为医技划价来源（含过渡：历史误标 nurse-pricing 的本科房行）。
     *
     * @param generateSourceEnum 生成来源持久化码
     * @return true 表示医技划价或过渡兼容护士划价码
     */
    private boolean isMedicalTechPricingSource(String generateSourceEnum) {
        if (generateSourceEnum == null) {
            return false;
        }
        if (GenerateSource.MEDICAL_TECH_PRICING.getCode().equals(generateSourceEnum)) {
            return true;
        }
        // 过渡：上线前误写 nurse-pricing 的本科房划价仍可列表/冲销
        return GenerateSource.NURSE_PRICING.getCode().equals(generateSourceEnum);
    }

    /**
     * 解析登录 TECH 下属允许的耗材房主键集合（科室药房 ∪ 药房）。
     *
     * @param loginTechOrgId 登录 TECH
     * @return 允许库房 id
     */
    private Set<Long> resolveAllowedLocationIds(Long loginTechOrgId) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Location location : this.listAllowedTechLocations(loginTechOrgId)) {
            if (location != null && location.getId() != null) {
                ids.add(location.getId());
            }
        }
        return ids;
    }

    /**
     * 列出登录 TECH 下属耗材房（科室药房优先，并集药房）。
     *
     * @param loginTechOrgId 登录 TECH
     * @return 库房实体列表（去重保序）
     */
    private List<Location> listAllowedTechLocations(Long loginTechOrgId) {
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        List<Location> rows = new ArrayList<>();
        List<Location> departmentPharmacies =
            this.locationQueryService.listDepartmentPharmaciesByOrganization(loginTechOrgId);
        if (departmentPharmacies != null) {
            for (Location location : departmentPharmacies) {
                if (location == null || location.getId() == null) {
                    continue;
                }
                if (!seen.add(location.getId())) {
                    continue;
                }
                rows.add(location);
            }
        }
        List<Location> pharmacies = this.locationQueryService.listPharmaciesByOrganization(loginTechOrgId);
        if (pharmacies != null) {
            for (Location location : pharmacies) {
                if (location == null || location.getId() == null) {
                    continue;
                }
                if (!seen.add(location.getId())) {
                    continue;
                }
                rows.add(location);
            }
        }
        return rows;
    }

    /**
     * 断言当前登录组织为单位且 class=TECH。
     *
     * @param errorCode 失败码
     * @return 登录 TECH 科主键
     */
    private Long requireLoginTechOrgId(String errorCode) {
        Long orgId = this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            errorCode, "未登录或机构上下文缺失");
        Organization org = this.organizationQueryService.loadOrganizationByIdForDisplay(orgId);
        // 业务：非 TECH 科禁止进入医技计费
        if (org == null || org.getClassEnum() != OrganizationClassification.TECH) {
            throw new BusinessException(errorCode, "当前登录科室不是医技科");
        }
        return orgId;
    }

    /**
     * 断言就诊为住院（IMP）。
     *
     * @param classEnum 就诊 class
     * @param errorCode 失败码
     */
    private void assertInpatientEncounter(String classEnum, String errorCode) {
        if (EncounterClass.IMP.getCode().equals(classEnum)) {
            return;
        }
        throw new BusinessException(errorCode, "医技计费仅支持住院就诊");
    }
}
