/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.dispense.service;

import com.openhis.health.application.shared.advice.InpatientMedicationRequestExecutionMeasureHelper;
import com.openhis.health.application.shared.financial.EncounterContractDisplayAssembler;
import com.openhis.health.application.shared.option.OptionDtoAssembler;
import com.openhis.health.application.shared.option.FrequencyOptionLoader;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.inpatient.pharmacy.dispense.dto.PendingDispenseInpatientEncounterQueueRowDto;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.inpatient.pharmacy.dispense.dto.CancelMedicationDispenseLineSubmitDto;
import com.openhis.health.application.inpatient.pharmacy.dispense.dto.DispenseMedicationLineSubmitDto;
import com.openhis.health.application.inpatient.pharmacy.dispense.dto.SavePrepareInpatientDispenseLineSubmitDto;
import com.openhis.health.application.inpatient.pharmacy.dispense.mapper.InpatientPharmacyDispenseMapper;
import com.openhis.health.application.inpatient.pharmacy.dispense.request.CancelInpatientMedicationDispenseRequest;
import com.openhis.health.application.inpatient.pharmacy.dispense.request.DispenseInpatientMedicationsRequest;
import com.openhis.health.application.inpatient.pharmacy.dispense.request.LoadInpatientPharmacyDispensePageInitRequest;
import com.openhis.health.application.inpatient.pharmacy.dispense.request.PageEncounterMedicationDispenseSlipsRequest;
import com.openhis.health.application.inpatient.pharmacy.dispense.request.PagePendingDispenseInpatientEncounterQueueRequest;
import com.openhis.health.application.inpatient.pharmacy.dispense.request.SavePrepareInpatientMedicationDispenseRequest;
import com.openhis.health.application.inpatient.pharmacy.dispense.response.CancelInpatientMedicationDispenseResponse;
import com.openhis.health.application.inpatient.pharmacy.dispense.response.DispenseInpatientMedicationsResponse;
import com.openhis.health.application.inpatient.pharmacy.dispense.response.LoadInpatientPharmacyDispensePageInitResponse;
import com.openhis.health.application.inpatient.pharmacy.dispense.response.PageEncounterMedicationDispenseSlipsResponse;
import com.openhis.health.application.inpatient.pharmacy.dispense.response.PagePendingDispenseInpatientEncounterQueueResponse;
import com.openhis.health.application.inpatient.pharmacy.dispense.response.SavePrepareInpatientMedicationDispenseResponse;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.application.basic.administration.support.PractitionerRoleScopeLoader;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.custom.tracenumber.service.TraceNumberQueryService;
import com.openhis.health.application.shared.tracenumber.TraceNumberDispenseTraceOrchestrator;
import com.openhis.health.domain.medication.medicationdispense.command.CancelMedicationDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.command.DispenseMedicationCommand;
import com.openhis.health.domain.medication.medicationdispense.command.PrepareMedicationDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.query.InpatientPharmacyPendingEncounterPageQuery;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.application.inpatient.pharmacy.dispense.support.InpatientMedicationDispenseChargePricingLoader;
import com.openhis.health.application.shared.pharmacy.MedicationDispenseInventoryAvailabilityGate;
import com.openhis.health.application.inpatient.pharmacy.dispense.support.InpatientEncounterMedicationDispenseSlipPageLoader;
import com.openhis.health.application.inpatient.pharmacy.dispense.support.EncounterMedicationDispenseSlipLoaderQuery;
import com.openhis.health.application.shared.pharmacy.MedicationDispenseSlipQueryLoader;
import com.openhis.health.application.inpatient.pharmacy.dispense.support.PharmacyEncounterQueueLoader;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestDetailQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.EventStatus;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.command.AllocateAndDecreaseCommand;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.openhis.health.infrastructure.integration.insurance.inventory.InventoryThirdPartyUploadExtension;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterDispenseExtensionIn;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 住院发药工作站应用服务（AS-IP-PHA-001）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：登录/分页/岗位管辖、发药行状态与数量、追溯码与库存门禁。</li>
 *   <li>Pre：待发队列、发药单读模型、收费定价与产品定义加载。</li>
 *   <li>Cmd：配药保存、核发发药、作废与库存扣减、3505 进销存上传（按患者直发；汇总核发见 AS-IP-PHA-002，不上报）。</li>
 *   <li>Asm：经 {@link InpatientPharmacyDispenseMapper} 装配响应。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpatientPharmacyDispenseAppService {

    private final InventoryThirdPartyUploadExtension inventoryUploadExtension;

    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    private final PageWindowGate pageWindowGate;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    /** 开单科室名称回填（adm_organization），非药房下拉口径。 */
    private final OrganizationQueryService organizationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    private final CodeSystemQueryService codeSystemQueryService;
    private final TerminologyOptionLoader terminologyOptionLoader;
    /** 频次展示码表：cus_frequency 主数据。 */
    private final FrequencyOptionLoader frequencyOptionLoader;
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextReader EncounterContextReader;
    private final LocationQueryService locationQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final PharmacyEncounterQueueLoader pharmacyEncounterQueueLoader;
    private final MedicationDispenseSlipQueryLoader medicationDispenseSlipQueryLoader;
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final MedicationDispenseInventoryAvailabilityGate medicationDispenseInventoryAvailabilityGate;
    private final InventoryItemDomainService inventoryItemDomainService;
    private final TraceNumberQueryService traceNumberQueryService;
    private final TraceNumberDispenseTraceOrchestrator traceNumberDispenseTraceOrchestrator;
    private final InpatientMedicationDispenseChargePricingLoader inpatientMedicationDispenseChargePricingLoader;
    private final InpatientPharmacyDispenseMapper inpatientPharmacyDispenseMapper;
    private final InpatientEncounterMedicationDispenseSlipPageLoader inpatientEncounterMedicationDispenseSlipPageLoader;
    private final ChargeItemQueryService chargeItemQueryService;
    private final ProcedureQueryService procedureQueryService;
    /** 就诊费用性质（优先合同名）批量回填队列卡片，与门诊发药同口径。 */
    private final EncounterContractDisplayAssembler encounterContractDisplayAssembler;

    /**
     * 加载住院发药页初始化元数据、调配药师与领药人（护士）分页候选。
     *
     * @param request 分页与可选岗位过滤；药房机构由会话解析
     * @return 下拉、药师与护士候选分页
     * @throws BusinessException {@code EX_OPP_101}：未登录或元数据装载失败
     */
    public LoadInpatientPharmacyDispensePageInitResponse loadInpatientPharmacyDispensePageInit(
        LoadInpatientPharmacyDispensePageInitRequest request) {
        // Gate：须已登录且会话有效。
        currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.EX_OPP_101_INIT_METADATA_FAILED, "未登录或会话无效，无法加载发药页元数据");
        if (request == null) {
            request = new LoadInpatientPharmacyDispensePageInitRequest();
        }
        try {
            // Pre：药房下拉、病区、调配药师与领药人（护士）分页候选。
            List<Location> pharmacies = managedLocationIdsResolver.listManagedActivePharmaciesOrEmpty();
            List<OptionDto> dispenseStatusOptions = codeSystemQueryService.getOptionListByEnum(DispenseStatus.class);
            List<OptionDto> notPerformedReasonOptions = new ArrayList<>();
            List<Location> wards = new ArrayList<>();
            List<OptionDto> wardOptions = new ArrayList<>();
            Long organizationUnitId = managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery();
            if (organizationUnitId != null) {
                wards = locationQueryService.listWardsByOrganization(organizationUnitId);
                wardOptions = OptionDtoAssembler.toOptions(wards, Location::getId, Location::getName, 0L);
            }
            int pageIndex = request.springPageIndex();
            int pageSize = request.getBoundedPageSize();
            Pageable pageable = PageRequest.of(pageIndex, pageSize);

            // 调配药师：adm_pr_assign 激活药师岗（ROLE_PHARMACIST），排除系统管理员岗。
            Page<Practitioner> preparerPage = this.pagePreparerCandidatesExcludingAdmin(pageable);
            // 领药人：住院护士岗挂在病区 location；无命中时回落全机构护士。
            List<Long> wardLocationIds = new ArrayList<>();
            for (Location ward : wards) {
                if (ward != null && ward.getId() != null) {
                    wardLocationIds.add(ward.getId());
                }
            }
            Page<Practitioner> receiverPage = practitionerRoleScopeLoader.pageActivePractitionersForPlatformRoleInLocations(
                WhalePlatformRoleCodeConstants.ROLE_NURSE,
                wardLocationIds,
                StringUtils.EMPTY,
                pageable,
                false);
            if (receiverPage.isEmpty()) {
                receiverPage = practitionerRoleScopeLoader.pageActivePractitionersForPlatformRole(
                    WhalePlatformRoleCodeConstants.ROLE_NURSE,
                    null,
                    StringUtils.EMPTY,
                    pageable,
                    false);
            }
            return inpatientPharmacyDispenseMapper.toLoadInitResponse(
                pharmacies, dispenseStatusOptions, notPerformedReasonOptions, wardOptions, preparerPage, receiverPage);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_101_INIT_METADATA_FAILED, "发药页元数据加载失败：" + ex.getMessage());
        }
    }

    /**
     * 分页查询住院发药在院患者队列，并回补住院就诊上下文展示字段。
     *
     * @param request 时间窗、关键字、病区、发放状态子集与分页；库房由会话机构解析
     * @return 队列分页；无启用药房时为空分页
     * @throws BusinessException {@code EX_OPP_102}：未登录、分页非法或查询失败
     */
    public PagePendingDispenseInpatientEncounterQueueResponse pagePendingDispenseInpatientEncounterQueue(
        PagePendingDispenseInpatientEncounterQueueRequest request) {
        if (request == null) {
            request = new PagePendingDispenseInpatientEncounterQueueRequest();
        }
        // Gate：须登录且分页窗口合法。
        currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.EX_OPP_102_QUEUE_QUERY_FAILED, "未登录或会话无效，无法查询队列");
        pageWindowGate.requirePositivePageWindow(
            request, ErrorCodeConstants.EX_OPP_102_QUEUE_QUERY_FAILED, "分页参数不合法");
        // Pre：按管辖药房与可选病区筛选待发就诊。
        List<Long> pharmacyLocationIds = managedLocationIdsResolver.listManagedLocationIdsOrEmpty();
        InpatientPharmacyPendingEncounterPageQuery pendingQuery = new InpatientPharmacyPendingEncounterPageQuery();
        if (!pharmacyLocationIds.isEmpty()) {
            pendingQuery.setLocationIds(pharmacyLocationIds);
        }
        // 病区仅在前台显式传入 wardId 时参与过滤；未传则不拼病区谓词。
        if (request.getWardId() != null) {
            pendingQuery.setWardId(request.getWardId());
        }
        PageQueryBinder.bind(request, pendingQuery);
        pendingQuery.setStartDate(null);
        pendingQuery.setEndDate(null);
        if (request.getDispenseStatusEnumIn() != null && !request.getDispenseStatusEnumIn().isEmpty()) {
            pendingQuery.setDispenseStatusEnumIn(request.getDispenseStatusEnumIn());
        }
        Page<Long> encounterPage =
            this.pharmacyEncounterQueueLoader.pageEncounterIdsForInpatientPharmacyPending(pendingQuery);
        List<InpatientEncounterContextQueryModel> infos =
            EncounterContextReader.listInpatientContextsByEncounterIdsOrdered(encounterPage.getContent());
        // Asm：队列分页映射后回填费用性质（与门诊发药/医生站卡片同口径）。
        PagePendingDispenseInpatientEncounterQueueResponse response =
            inpatientPharmacyDispenseMapper.toInpatientQueuePage(encounterPage, infos);
        this.applyQueueContractDisplayLabels(response);
        return response;
    }

    /**
     * 住院发药队列行批量回填 {@code contractDisplay}（与门诊发药/医生站患者卡片费用性质同口径）。
     *
     * @param response 队列分页响应；空或无就诊主键时直接返回
     */
    private void applyQueueContractDisplayLabels(PagePendingDispenseInpatientEncounterQueueResponse response) {
        if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
            return;
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        // Loop：收集本页就诊主键，供合同展示一次批量解析
        for (PendingDispenseInpatientEncounterQueueRowDto row : response.getContent()) {
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        // 本页无有效就诊时跳过合同装配
        if (encounterIds.isEmpty()) {
            return;
        }
        Map<Long, String> contractDisplayByEncounterId =
            this.encounterContractDisplayAssembler.mapContractDisplayByEncounterIds(encounterIds);
        // Loop：逐行写回费用性质展示名；无合同时留空，前端回落「自费」
        for (PendingDispenseInpatientEncounterQueueRowDto row : response.getContent()) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            row.setContractDisplay(contractDisplayByEncounterId.get(row.getEncounterId()));
        }
    }

    /**
     * 分页加载就诊下住院药品发药单读模型行（不含候选库存）。
     *
     * @param request 就诊、库房、中西药过滤、发放状态与分页
     * @return 明细分页
     * @throws BusinessException {@code EX_OPP_103}：参数/就诊/分页非法
     */
    public PageEncounterMedicationDispenseSlipsResponse pageEncounterMedicationDispenseSlips(
        PageEncounterMedicationDispenseSlipsRequest request) {
        if (request == null) {
            request = new PageEncounterMedicationDispenseSlipsRequest();
        }
        // Gate：就诊与分页参数须合法。
        if (request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_103_DISPENSE_SLIP_LOAD_FAILED, "就诊不能为空");
        }
        pageWindowGate.requirePositivePageWindow(
            request, ErrorCodeConstants.EX_OPP_103_DISPENSE_SLIP_LOAD_FAILED, "分页参数不合法");
        try {
            encounterQueryService.mustGetEncounter(request.getEncounterId());
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_103_DISPENSE_SLIP_LOAD_FAILED, ex.getMessage());
        }
        EncounterMedicationDispenseSlipLoaderQuery query = toSlipPageQuery(request);
        applyManagedLocationScopeWhenUnscoped(query);
        // Asm：读模型分页经 Mapper 装配明细行
        return inpatientPharmacyDispenseMapper.toSlipPageResponse(
            inpatientEncounterMedicationDispenseSlipPageLoader.pageInpatientEncounterMedicationDispenseSlips(query));
    }

    /**
     * 未显式指定 {@code locationId} 时，按管辖库位收窄明细（与 IF-IPP-02 队列同源）。
     *
     * @param query slip 读侧入参（就地写入 {@code locationIds}）
     */
    private void applyManagedLocationScopeWhenUnscoped(EncounterMedicationDispenseSlipLoaderQuery query) {
        if (query == null || query.getLocationId() != null) {
            return;
        }
        List<Long> managedLocationIds = managedLocationIdsResolver.listManagedLocationIdsOrEmpty();
        if (!managedLocationIds.isEmpty()) {
            query.setLocationIds(managedLocationIds);
        }
    }

    /**
     * 分页请求 → querysupport 发放 slip 查询对象。
     *
     * @param request 前端分页请求
     * @return loader 查询；null 输入返回空 query
     */
    private static EncounterMedicationDispenseSlipLoaderQuery toSlipPageQuery(PageEncounterMedicationDispenseSlipsRequest request) {
        if (request == null) {
            return new EncounterMedicationDispenseSlipLoaderQuery();
        }
        EncounterMedicationDispenseSlipLoaderQuery query = new EncounterMedicationDispenseSlipLoaderQuery();
        query.setEncounterId(request.getEncounterId());
        query.setLocationId(request.getLocationId());
        query.setTcmOnly(request.getTcmOnly());
        query.setWesternOnly(request.getWesternOnly());
        query.setDispenseStatusEnumIn(request.getDispenseStatusEnumIn());
        PageQueryBinder.bind(request, query);
        return query;
    }

    /**
     * 保存配药（领域推进 {@code PREPARATION}→{@code IN_PROGRESS}）；库房/批号/数量以聚合持久化值为准。
     *
     * @param request 就诊、调配药师与提交行（仅 dispenseId、可选追溯码）
     * @return 处理结果
     * @throws BusinessException {@code EX_OPP_201}～{@code EX_OPP_205}：门控或前置校验失败
     */
    public SavePrepareInpatientMedicationDispenseResponse savePrepareInpatientMedicationDispense(
        SavePrepareInpatientMedicationDispenseRequest request) {
        // Gate：调配药师与配药行须非空。
        if (request.getPreparerPractitionerId() == null || request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_202_PREPARER_INVALID, "调配药师或配药行不能为空");
        }
        Long operatorPractitionerId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.EX_OPP_202_PREPARER_INVALID, "未登录用户不能保存配药");
        Practitioner preparerPractitioner = practitionerQueryService.findById(request.getPreparerPractitionerId());
        if (preparerPractitioner == null) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_202_PREPARER_INVALID, "调配药师不存在或已停用");
        }
        // 系统管理员即使挂了药师岗，也不得作为住院配药人。
        if (this.isActivePlatformAdmin(request.getPreparerPractitionerId())) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_202_PREPARER_INVALID, "系统管理员不能作为调配药师");
        }
        // BR-IPP-004：调配药师（配药人）不得与当前登录药师（发药人）同人。
        if (Objects.equals(operatorPractitionerId, request.getPreparerPractitionerId())) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_202_PREPARER_INVALID, "调配药师不能与当前登录用户为同一人");
        }
        encounterQueryService.mustGetEncounter(request.getEncounterId());
        // Pre：收集提交 dispenseId 并强加载发放聚合
        List<Long> submittedDispenseIds = new ArrayList<>();
        for (SavePrepareInpatientDispenseLineSubmitDto submitLine : request.getLines()) {
            if (submitLine != null && submitLine.getDispenseId() != null) {
                submittedDispenseIds.add(submitLine.getDispenseId());
            }
        }
        List<MedicationDispense> loadedDispenses =
            medicationDispenseQueryService.listMedicationDispensesByIds(submittedDispenseIds);
        Map<Long, MedicationDispense> dispenseById = new HashMap<>();
        for (MedicationDispense loadedDispense : loadedDispenses) {
            if (loadedDispense.getId() != null) {
                dispenseById.put(loadedDispense.getId(), loadedDispense);
            }
        }
        List<String> traceCodeInputs = new ArrayList<>();
        List<String> hardHospitalCodes = new ArrayList<>();
        // Pre：逐行 Gate（就诊一致、可配药态、追溯/库存/长临数量）
        for (SavePrepareInpatientDispenseLineSubmitDto submitLine : request.getLines()) {
            MedicationDispense dispense = dispenseById.get(submitLine.getDispenseId());
            if (dispense == null) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED, "发放单不存在");
            }
            if (!Objects.equals(dispense.getEncounterId(), request.getEncounterId())) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED, "发放单不属于当前就诊");
            }
            assertInpatientMedicationRow(dispense);
            if (!medicationDispenseQueryService.validateDispensePreparable(dispense.getId())) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED, "当前状态不允许保存配药");
            }
            MedicationRequestDetailQueryModel medicationRequestDetail =
                medicationRequestQueryService.loadMedicationRequestById(dispense.getMedReqId());
            BigDecimal dispenseQty = dispense.getQuantity();
            if (dispenseQty == null) {
                dispenseQty = dispense.getDispenseQuantity();
            }
            List<String> lineTraceCodes = splitTraceCodes(submitLine.getTraceNo());
            traceCodeInputs.addAll(lineTraceCodes);
            // 有码批硬校验；无码批仅业务行
            boolean lotHasMaster = traceNumberQueryService.existsMasterByItemLot(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                dispense.getMedicationId(),
                dispense.getLotNumber());
            if (lotHasMaster) {
                hardHospitalCodes.addAll(lineTraceCodes);
                if (!lineTraceCodes.isEmpty()) {
                    traceNumberQueryService.assertCodesInNetStockForDispenseLine(
                        TableNameConstants.MED_MEDICATION_DEFINITION,
                        dispense.getMedicationId(),
                        dispense.getLotNumber(),
                        dispense.getLocationId(),
                        lineTraceCodes);
                }
            }
            ProductDefinitionDisplayDto productRow = loadProductForMedication(dispense.getMedicationId());
            if (productRow == null) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID, "缺少药品目录投影，无法校验库存");
            }
            // 长期：quantity/unit_code=一次执行发药量/单位；临时：总量由 BR-IPP-006 合计，此处仅校单位。
            if (TherapyType.LONG_TERM.getCode().equals(medicationRequestDetail.getTherapyEnum())) {
                InpatientMedicationRequestExecutionMeasureHelper.assertLongTermDispenseMatchesPerExecutionRequest(
                    medicationRequestDetail, dispenseQty, dispense.getUnitCode(), productRow);
            } else {
                InpatientMedicationRequestExecutionMeasureHelper.assertTemporaryDispenseUnitMatchesRequest(
                    medicationRequestDetail, dispense.getUnitCode(), productRow);
            }
            medicationDispenseInventoryAvailabilityGate.assertSufficientForMedicationDispenseWithReserve(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                dispense.getMedicationId(),
                dispense.getLocationId(),
                dispense.getLotNumber(),
                dispenseQty,
                dispense.getUnitCode(),
                productRow,
                dispense.getId());
        }
        // 长临合计门控
        assertTemporaryTherapyQuantitiesForInpatient(dispenseById);
        traceNumberQueryService.assertNoDuplicateTraceCodes(traceCodeInputs);
        // 仅有码批参与在院硬校验
        traceNumberQueryService.assertCodesInHospitalForDispense(hardHospitalCodes);
        // Cmd：领域推进 PREPARATION → IN_PROGRESS，写入调配药师与追溯码
        PrepareMedicationDispenseCommand prepareCommand = inpatientPharmacyDispenseMapper.toPrepareMedicationDispenseCommand(
            operatorPractitionerId, request.getPreparerPractitionerId(), request.getLines(), dispenseById);
        medicationDispenseDomainService.prepareMedicationDispense(prepareCommand);
        // Asm：配药成功响应
        return inpatientPharmacyDispenseMapper.toPrepareBatchResponse(submittedDispenseIds, "保存配药成功", true);
    }

    /**
     * 核发发药（{@code IN_PROGRESS}→{@code COMPLETED}）并扣减库存。
     *
     * @param request 就诊与勾选发放行
     * @return 处理结果
     * @throws BusinessException {@code EX_OPP_203}、{@code EX_OPP_205}：门控或库存不足
     */
    public DispenseInpatientMedicationsResponse dispenseInpatientMedications(DispenseInpatientMedicationsRequest request) {
        // Gate：发药行须非空且当前用户须绑定医务主档。
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED, "发药行不能为空");
        }
        Long operatorPractitionerId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED, "未登录用户不能发药");
        Practitioner dispenserPractitioner = practitionerQueryService.findById(operatorPractitionerId);
        if (dispenserPractitioner == null) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED, "当前用户未绑定医务主档，不能作为发药人");
        }
        encounterQueryService.mustGetEncounter(request.getEncounterId());
        // Pre：收集提交 dispenseId 并强加载发放聚合
        List<Long> submittedDispenseIds = new ArrayList<>();
        for (DispenseMedicationLineSubmitDto lineSubmit : request.getLines()) {
            if (lineSubmit != null && lineSubmit.getDispenseId() != null) {
                submittedDispenseIds.add(lineSubmit.getDispenseId());
            }
        }
        List<MedicationDispense> dispensersInSubmitOrder = new ArrayList<>();
        List<MedicationDispense> loadedDispenses =
            medicationDispenseQueryService.listMedicationDispensesByIds(submittedDispenseIds);
        Map<Long, MedicationDispense> dispenseById = new HashMap<>();
        for (MedicationDispense loadedDispense : loadedDispenses) {
            if (loadedDispense.getId() != null) {
                dispenseById.put(loadedDispense.getId(), loadedDispense);
            }
        }
        List<ProductDefinitionBatchLookup> dispenseProductLookups = new ArrayList<>();
        for (Long dispenseId : submittedDispenseIds) {
            MedicationDispense dispenseForLookup = dispenseById.get(dispenseId);
            if (dispenseForLookup == null || dispenseForLookup.getMedicationId() == null) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(dispenseForLookup.getMedicationId());
            dispenseProductLookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> dispenseProductRowsByKey =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(dispenseProductLookups);
        // Pre：逐行 Gate（可核发态、Charge 未变更、库存充足、追溯门控）
        for (Long dispenseId : submittedDispenseIds) {
            MedicationDispense dispense = dispenseById.get(dispenseId);
            if (dispense == null) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED, "发放单不存在");
            }
            if (!Objects.equals(dispense.getEncounterId(), request.getEncounterId())) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED, "发放单不属于当前就诊");
            }
            assertInpatientMedicationRow(dispense);
            if (!medicationDispenseQueryService.validateDispensable(dispense.getId())) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED, "当前状态不允许发药");
            }
            this.assertDispenseExecutionChargeGate(dispense, request.getEncounterId());
            BigDecimal requiredQuantity = dispense.getQuantity();
            if (requiredQuantity == null) {
                requiredQuantity = dispense.getDispenseQuantity();
            }
            String productCompositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, dispense.getMedicationId());
            ProductDefinitionDisplayDto productRowForStockAssert = dispenseProductRowsByKey.get(productCompositeKey);
            if (productRowForStockAssert == null) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID, "缺少药品目录投影，无法校验库存");
            }
            medicationDispenseInventoryAvailabilityGate.assertSufficientForMedicationDispenseWithReserve(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                dispense.getMedicationId(),
                dispense.getLocationId(),
                dispense.getLotNumber(),
                requiredQuantity,
                dispense.getUnitCode(),
                productRowForStockAssert,
                dispense.getId());
            dispensersInSubmitOrder.add(dispense);
        }
        DispenseMedicationCommand dispenseCommand = inpatientPharmacyDispenseMapper.toDispenseCommand(
            operatorPractitionerId, dispenserPractitioner.getId(), dispensersInSubmitOrder);
        // Cmd：领域推进 IN_PROGRESS → COMPLETED
        medicationDispenseDomainService.dispenseMedication(dispenseCommand);
        Map<Long, ProductDefinitionDisplayDto> productByMedicationIdForDispense = new HashMap<>();
        for (MedicationDispense dispense : dispensersInSubmitOrder) {
            if (dispense == null || dispense.getMedicationId() == null) {
                continue;
            }
            String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, dispense.getMedicationId());
            ProductDefinitionDisplayDto productRow = dispenseProductRowsByKey.get(compositeKey);
            if (productRow != null) {
                productByMedicationIdForDispense.put(dispense.getMedicationId(), productRow);
            }
        }
        applyInventoryDeductionForDispensedLines(dispensersInSubmitOrder, operatorPractitionerId, productByMedicationIdForDispense);
        // Cmd：追溯出库（发放行维度）
        traceNumberDispenseTraceOrchestrator.recordDispenseOutLines(
            dispensersInSubmitOrder, operatorPractitionerId, productByMedicationIdForDispense);
        DispenseInpatientMedicationsResponse response =
            inpatientPharmacyDispenseMapper.toDispenseBatchResponse(submittedDispenseIds, "发药成功", true);
        // Asm：可选医保 3505 上传（afterCommit + 异步；失败不回滚本地发药）
        inventoryUploadExtension.runUploadIfEnabled(() -> {
            InventoryUploadAfterDispenseExtensionIn uploadCommand = new InventoryUploadAfterDispenseExtensionIn()
                .setEncounterId(request.getEncounterId())
                .setDispenseIds(submittedDispenseIds)
                .setContractBusNo(request.getContractBusNo());
            inventoryUploadExtension.uploadAfterDispense(uploadCommand);
        });
        return response;
    }

    /**
     * 作废未发放闭环内的发放行（领域推进至拒绝态）。
     *
     * @param request 就诊、发放行与未执行原因码
     * @return 处理结果
     * @throws BusinessException {@code EX_OPP_208}：参数或状态不允许
     */
    public CancelInpatientMedicationDispenseResponse cancelInpatientMedicationDispense(
        CancelInpatientMedicationDispenseRequest request) {
        // Gate：作废行与作废原因须非空。
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_208_CANCEL_NOT_ALLOWED, "作废行不能为空");
        }
        for (CancelMedicationDispenseLineSubmitDto cancelLine : request.getLines()) {
            if (cancelLine == null || StringUtils.isBlank(cancelLine.getNotPerformedReasonEnum())) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_208_CANCEL_NOT_ALLOWED, "作废原因不能为空");
            }
        }
        Long operatorPractitionerId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.EX_OPP_208_CANCEL_NOT_ALLOWED, "未登录用户不能作废发放");
        encounterQueryService.mustGetEncounter(request.getEncounterId());
        // Pre：收集提交 dispenseId 并强加载发放聚合
        List<Long> submittedDispenseIds = new ArrayList<>();
        for (CancelMedicationDispenseLineSubmitDto cancelLine : request.getLines()) {
            if (cancelLine != null && cancelLine.getDispenseId() != null) {
                submittedDispenseIds.add(cancelLine.getDispenseId());
            }
        }
        for (Long dispenseId : submittedDispenseIds) {
            MedicationDispense dispense = medicationDispenseQueryService.mustGetMedicationDispense(dispenseId);
            if (!Objects.equals(dispense.getEncounterId(), request.getEncounterId())) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_208_CANCEL_NOT_ALLOWED, "发放单不属于当前就诊");
            }
            assertInpatientMedicationRow(dispense);
            DispenseStatus statusEnum = dispense.getStatusEnum();
            // 仅配药前/配药中可作废，已核发或已汇总行须走退药/驳回链路
            boolean cancellable = DispenseStatus.PREPARATION.equals(statusEnum)
                || DispenseStatus.IN_PROGRESS.equals(statusEnum);
            if (!cancellable) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_208_CANCEL_NOT_ALLOWED, "当前状态不允许作废");
            }
        }
        // Cmd：领域推进至 DECLINED，写入未执行原因
        CancelMedicationDispenseCommand cancelCommand =
            inpatientPharmacyDispenseMapper.toCancelMedicationDispenseCommand(operatorPractitionerId, request.getLines());
        medicationDispenseDomainService.cancelMedicationDispense(cancelCommand);
        // Asm：作废成功响应
        return inpatientPharmacyDispenseMapper.toCancelBatchResponse(submittedDispenseIds, "作废成功", true);
    }

    /**
     * 住院发药工作站仅处理住院/临时药品发放，拦截耗材与门诊行。
     *
     * @param dispense 发放聚合行
     * @throws BusinessException {@code EX_OPP_203}：类型不在受理范围
     */
    private static void assertInpatientMedicationRow(MedicationDispense dispense) {
        MedicationDispenseType dispenseType = dispense.getDispenseEnum();
        if (dispenseType == null) {
            return;
        }
        if (dispenseType != MedicationDispenseType.INPATIENT && dispenseType != MedicationDispenseType.TEMPORARY) {
            throw new BusinessException(
                ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED, "非住院药品发放不在本工作站处理范围");
        }
    }

    /**
     * 核发 Gate：执行记录未被取消对冲，且关联 Charge 仍为 {@code PENDING_SETTLEMENT}（GAP-STALE-004）。
     */
    private void assertDispenseExecutionChargeGate(MedicationDispense dispense, Long encounterId) {
        if (dispense == null || dispense.getId() == null) {
            return;
        }
        Long procedureId = dispense.getProcedureId();
        if (procedureId != null && dispense.getMedReqId() != null) {
            List<ProcedureRecordQueryModel> siblings = procedureQueryService.getProcedureRecords(
                dispense.getMedReqId(), TableNameConstants.MED_MEDICATION_REQUEST);
            for (ProcedureRecordQueryModel sibling : siblings) {
                if (sibling == null) {
                    continue;
                }
                if (!EventStatus.isCancelExecutionStatusCode(sibling.getStatusEnum())) {
                    continue;
                }
                if (procedureId.equals(sibling.getRefundId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED,
                        "执行记录已取消，不允许核发");
                }
            }
        }
        boolean hasPendingSettlementCharge = false;
        List<ChargeItem> pendingByDispense = chargeItemQueryService.listPendingSettlementChargeItemsForMedicationDispensePricing(
            encounterId, List.of(dispense.getId()));
        if (pendingByDispense != null && !pendingByDispense.isEmpty()) {
            hasPendingSettlementCharge = true;
        }
        if (!hasPendingSettlementCharge && procedureId != null) {
            List<ChargeItem> procedureCharges = chargeItemQueryService.listChargeItemsByProcedureIds(List.of(procedureId));
            for (ChargeItem charge : procedureCharges) {
                if (charge == null || charge.getStatusEnum() == null) {
                    continue;
                }
                if (ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(charge.getStatusEnum())) {
                    hasPendingSettlementCharge = true;
                    break;
                }
            }
        }
        if (!hasPendingSettlementCharge) {
            throw new BusinessException(
                ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED,
                "关联费用已变更，请刷新后重试");
        }
    }

    /**
     * 批量解析医务人员展示名（明细 IF 展示用；读路径允许逐 id 查询）。
     *
     * @param practitionerIds 医务主键集合
     * @return id → 实体
     */
    private Map<Long, Practitioner> loadPractitionersByIds(Set<Long> practitionerIds) {
        Map<Long, Practitioner> practitionerById = new HashMap<>();
        if (practitionerIds == null) {
            return practitionerById;
        }
        for (Long practitionerId : practitionerIds) {
            if (practitionerId == null) {
                continue;
            }
            Practitioner practitioner = practitionerQueryService.findById(practitionerId);
            if (practitioner != null) {
                practitionerById.put(practitionerId, practitioner);
            }
        }
        return practitionerById;
    }

    /**
     * 按药品定义主键加载产品读模型单行（库存校验/拆零换算用）。
     *
     * @param medicationId 药品定义主键
     * @return 读模型或 null
     */
    private ProductDefinitionDisplayDto loadProductForMedication(Long medicationId) {
        if (medicationId == null) {
            return null;
        }
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
        lookup.setProductDefinitionId(medicationId);
        Map<String, ProductDefinitionDisplayDto> map =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(List.of(lookup));
        String key = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.MED_MEDICATION_DEFINITION, medicationId);
        return map.get(key);
    }

    /**
     * 拆分追溯码提交串（逗号分隔）；空串不产生条目。
     *
     * @param traceNo 前端提交的原始串
     * @return 去空白后的码片段列表
     */
    private static List<String> splitTraceCodes(String traceNo) {
        if (StringUtils.isBlank(traceNo)) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        String[] parts = traceNo.split(GeneralSharedConstants.COMMA);
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                codes.add(trimmed);
            }
        }
        return codes;
    }

    /**
     * BR-IPP-006：临时医嘱「待发/在途」拆分行数量合计须与开立量一致。
     */
    private void assertTemporaryTherapyQuantitiesForInpatient(Map<Long, MedicationDispense> dispenseById) {
        Set<Long> distinctMedicationRequestIds = new LinkedHashSet<>();
        for (MedicationDispense dispense : dispenseById.values()) {
            if (dispense.getMedReqId() != null) {
                distinctMedicationRequestIds.add(dispense.getMedReqId());
            }
        }
        for (Long medicationRequestId : distinctMedicationRequestIds) {
            MedicationRequestDetailQueryModel detail =
                medicationRequestQueryService.loadMedicationRequestById(medicationRequestId);
            if (!TherapyType.TEMPORARY.getCode().equals(detail.getTherapyEnum())) {
                continue;
            }
            List<MedicationDispense> siblingDispenses =
                medicationDispenseQueryService.listByMedReqIds(List.of(medicationRequestId));
            BigDecimal sumQuantities = BigDecimal.ZERO;
            for (MedicationDispense sibling : siblingDispenses) {
                DispenseStatus siblingStatus = sibling.getStatusEnum();
                if (siblingStatus == null || !siblingStatus.countsTowardTemporaryTherapyPendingQuantitySum()) {
                    continue;
                }
                BigDecimal quantityContribution = sibling.getQuantity();
                if (quantityContribution == null) {
                    quantityContribution = sibling.getDispenseQuantity();
                }
                if (quantityContribution != null) {
                    sumQuantities = sumQuantities.add(quantityContribution);
                }
            }
            if (detail.getQuantity() != null && sumQuantities.compareTo(detail.getQuantity()) != 0) {
                throw new BusinessException(
                    ErrorCodeConstants.EX_OPP_204_QUANTITY_MISMATCH, "临时医嘱拆分发放数量合计须与开立总量一致");
            }
        }
    }

    /**
     * 实物扣减：将发放行上的业务单位数量换算为库存最小单位后，写入 {@code wkf_inventory_item}。
     *
     * <p>批号非空时按「药品+库房+批号」解析唯一库存行并合并扣减；批号为空时在目标库房按效期优先（FEFO）占用多条账面批次。</p>
     *
     * @param dispensersInSubmitOrder    已完成领域核发的快照行
     * @param operatorPractitionerId             操作用户 id
     * @param productByMedicationId      药品 id → 产品读模型
     */
    private void applyInventoryDeductionForDispensedLines(
        List<MedicationDispense> dispensersInSubmitOrder,
        Long operatorPractitionerId,
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId) {
        if (dispensersInSubmitOrder == null || dispensersInSubmitOrder.isEmpty()) {
            return;
        }
        String sep = String.valueOf(GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR);
        Map<String, BigDecimal> mergedAllocateRequiredByKey = new LinkedHashMap<>();
        Map<String, BigDecimal> mergedLotDecreaseByLotKey = new LinkedHashMap<>();
        for (MedicationDispense dispense : dispensersInSubmitOrder) {
            if (dispense == null) {
                continue;
            }
            ProductDefinitionDisplayDto productRow = productByMedicationId.get(dispense.getMedicationId());
            if (productRow == null) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID, "缺少药品目录投影，无法扣减库存");
            }
            BigDecimal businessQty = dispense.getQuantity();
            if (businessQty == null) {
                businessQty = dispense.getDispenseQuantity();
            }
            BigDecimal splitQty = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
                ProductDefinitionDisplaySupport.toUnitConversionSource(productRow), dispense.getUnitCode(), businessQty);
            if (StringUtils.isNotBlank(dispense.getLotNumber())) {
                String lotKey = dispense.getMedicationId() + sep + dispense.getLocationId() + sep + dispense.getLotNumber().trim();
                BigDecimal existedLot = mergedLotDecreaseByLotKey.get(lotKey);
                if (existedLot == null) {
                    mergedLotDecreaseByLotKey.put(lotKey, splitQty);
                } else {
                    mergedLotDecreaseByLotKey.put(lotKey, existedLot.add(splitQty));
                }
            } else {
                String allocateKey = TableNameConstants.MED_MEDICATION_DEFINITION + sep
                    + dispense.getMedicationId() + sep + dispense.getLocationId();
                BigDecimal existedAllocate = mergedAllocateRequiredByKey.get(allocateKey);
                if (existedAllocate == null) {
                    mergedAllocateRequiredByKey.put(allocateKey, splitQty);
                } else {
                    mergedAllocateRequiredByKey.put(allocateKey, existedAllocate.add(splitQty));
                }
            }
        }
        Map<Long, BigDecimal> inventoryIdToMergedDecrease = new HashMap<>();
        for (Map.Entry<String, BigDecimal> lotEntry : mergedLotDecreaseByLotKey.entrySet()) {
            String[] lotParts = lotEntry.getKey().split(Pattern.quote(sep), -1);
            if (lotParts.length != 3) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID, "内部批次合并键解析失败");
            }
            Long medicationId = Long.parseLong(lotParts[0]);
            Long locationId = Long.parseLong(lotParts[1]);
            String lotNumber = lotParts[2];
            Long inventoryItemId = inventoryItemQueryService.requireInventoryItemIdForMedicationDispenseBatch(
                TableNameConstants.MED_MEDICATION_DEFINITION, medicationId, locationId, lotNumber);
            BigDecimal existedInv = inventoryIdToMergedDecrease.get(inventoryItemId);
            if (existedInv == null) {
                inventoryIdToMergedDecrease.put(inventoryItemId, lotEntry.getValue());
            } else {
                inventoryIdToMergedDecrease.put(inventoryItemId, existedInv.add(lotEntry.getValue()));
            }
        }
        inventoryItemDomainService.decreaseQuantitiesMerged(operatorPractitionerId, inventoryIdToMergedDecrease);
        for (Map.Entry<String, BigDecimal> allocateEntry : mergedAllocateRequiredByKey.entrySet()) {
            String[] allocateParts = allocateEntry.getKey().split(Pattern.quote(sep), -1);
            if (allocateParts.length != 3) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID, "内部分配合并键解析失败");
            }
            AllocateAndDecreaseCommand allocateCommand = new AllocateAndDecreaseCommand();
            allocateCommand.setOperatorId(operatorPractitionerId);
            AllocateAndDecreaseCommand.AllocateAndDecreaseItemCommand allocateItem =
                new AllocateAndDecreaseCommand.AllocateAndDecreaseItemCommand();
            allocateItem.setItemTable(allocateParts[0]);
            allocateItem.setItemId(Long.parseLong(allocateParts[1]));
            allocateItem.setLocationId(Long.parseLong(allocateParts[2]));
            allocateItem.setRequiredQty(allocateEntry.getValue());
            allocateCommand.setItem(allocateItem);
            inventoryItemDomainService.allocateAndDecrease(allocateCommand);
        }
    }

    /**
     * 住院发药：ChargeItem 与 {@code MedicationDispense} 分段 1:1，按 {@code dispenseId} 直取单价/金额（不求和、不加权）。
     *
     * @param chargeRows        就诊+发放单集合查出的收费行
     * @param unitPriceOut      输出：dispenseId→单价
     * @param totalPriceOut     输出：dispenseId→金额
     */
    private static void mapChargePricingByDispenseId(
        List<ChargeItem> chargeRows,
        Map<Long, BigDecimal> unitPriceOut,
        Map<Long, BigDecimal> totalPriceOut) {
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow == null || chargeRow.getDispenseId() == null) {
                continue;
            }
            Long dispenseKey = chargeRow.getDispenseId();
            if (chargeRow.getTotalPrice() != null) {
                totalPriceOut.put(dispenseKey, chargeRow.getTotalPrice());
            }
            if (chargeRow.getUnitPrice() != null) {
                unitPriceOut.put(dispenseKey, chargeRow.getUnitPrice());
            }
        }
    }

    /**
     * 分页加载调配药师候选：{@code adm_pr_assign} 激活药师岗，并排除系统管理员岗。
     *
     * @param pageable 分页
     * @return 调配药师分页；无命中时 empty
     */
    private Page<Practitioner> pagePreparerCandidatesExcludingAdmin(Pageable pageable) {
        Set<Long> preparerIds = new LinkedHashSet<>(
            this.practitionerRoleScopeLoader.resolvePractitionerIdsForActivePlatformRole(
                WhalePlatformRoleCodeConstants.ROLE_PHARMACIST,
                null));
        if (preparerIds.isEmpty()) {
            return Page.empty(pageable);
        }
        Set<Long> adminIds = this.practitionerRoleScopeLoader.resolvePractitionerIdsForActivePlatformRole(
            WhalePlatformRoleCodeConstants.ROLE_ADMIN,
            null);
        preparerIds.removeAll(adminIds);
        if (preparerIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return this.practitionerQueryService.pageActivePractitionersForPosition(
            preparerIds,
            StringUtils.EMPTY,
            pageable,
            false);
    }

    /**
     * 是否拥有激活的系统管理员岗位（{@code adm_pr_assign}）。
     *
     * @param practitionerId 医务人员主键
     * @return 有管理员岗时 true
     */
    private boolean isActivePlatformAdmin(Long practitionerId) {
        if (practitionerId == null) {
            return false;
        }
        Set<Long> adminIds = this.practitionerRoleScopeLoader.resolvePractitionerIdsForActivePlatformRole(
            WhalePlatformRoleCodeConstants.ROLE_ADMIN,
            null);
        return adminIds.contains(practitionerId);
    }
}
