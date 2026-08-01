/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dispense.service;

import com.openhis.health.application.shared.option.FrequencyOptionLoader;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.CancelMedicationDispenseLineSubmitDto;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.DispenseMedicationLineSubmitDto;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.SavePrepareDispenseLineSubmitDto;
import com.openhis.health.application.outpatient.pharmacy.dispense.mapper.OutpatientPharmacyDispenseMapper;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.TraceIdentityResolveFailItemDto;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.TraceIdentityResolveItemDto;
import com.openhis.health.application.outpatient.pharmacy.dispense.request.BatchResolveTraceIdentityRequest;
import com.openhis.health.application.outpatient.pharmacy.dispense.request.CancelOutpatientMedicationDispenseRequest;
import com.openhis.health.application.outpatient.pharmacy.dispense.request.DispenseOutpatientMedicationsRequest;
import com.openhis.health.application.outpatient.pharmacy.dispense.request.LoadPharmacyDispensePageInitRequest;
import com.openhis.health.application.outpatient.pharmacy.dispense.request.PageEncounterMedicationDispenseSlipsRequest;
import com.openhis.health.application.outpatient.pharmacy.dispense.request.PagePendingDispenseEncounterQueueRequest;
import com.openhis.health.application.outpatient.pharmacy.dispense.request.SavePrepareOutpatientMedicationDispenseRequest;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.BatchResolveTraceIdentityResponse;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.CancelOutpatientMedicationDispenseResponse;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.DispenseOutpatientMedicationsResponse;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.LoadPharmacyDispensePageInitResponse;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.PageEncounterMedicationDispenseSlipsResponse;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.PagePendingDispenseEncounterQueueResponse;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.SavePrepareOutpatientMedicationDispenseResponse;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.basic.administration.support.PractitionerRoleScopeLoader;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.shared.financial.EncounterContractDisplayAssembler;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.PendingDispenseEncounterQueueRowDto;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.application.shared.product.ClinicalCatalogDispenseLocationResolver;
import com.openhis.health.domain.medication.medicationdispense.command.CancelMedicationDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.command.DispenseMedicationCommand;
import com.openhis.health.domain.medication.medicationdispense.command.PrepareMedicationDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.query.OutpatientPharmacyPendingEncounterPageQuery;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.application.shared.pharmacy.MedicationDispenseInventoryAvailabilityGate;
import com.openhis.health.application.outpatient.pharmacy.dispense.support.OutpatientEncounterMedicationDispenseSlipPageLoader;
import com.openhis.health.application.outpatient.pharmacy.dispense.support.EncounterMedicationDispenseSlipLoaderQuery;
import com.openhis.health.application.shared.pharmacy.MedicationDispenseSlipQueryLoader;
import com.openhis.health.application.outpatient.pharmacy.dispense.support.PharmacyEncounterQueueLoader;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestDetailQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.MedicationCommonConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.custom.tracenumber.queries.model.TraceNumberIdentityQueryModel;
import com.openhis.health.domain.custom.tracenumber.service.TraceNumberQueryService;
import com.openhis.health.application.shared.tracenumber.TraceNumberBatchIdentityResolver;
import com.openhis.health.application.shared.tracenumber.TraceNumberDispenseTraceOrchestrator;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.command.AllocateAndDecreaseCommand;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.health.infrastructure.integration.insurance.inventory.InventoryThirdPartyUploadExtension;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterDispenseExtensionIn;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * 门诊发药工作站应用服务（AS-OP-PHA-001）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：登录/岗位管辖、发药行状态与数量、追溯码与库存门禁。</li>
 *   <li>Pre：待发队列、发药单读模型、产品定义与库存候选加载。</li>
 *   <li>Cmd：配药保存、核发发药、作废与库存扣减、3505 进销存上传。</li>
 *   <li>Asm：经 {@link OutpatientPharmacyDispenseMapper} 装配响应。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutpatientPharmacyDispenseAppService {

    private final InventoryThirdPartyUploadExtension inventoryUploadExtension;

    private final CurrentPractitionerResolver currentPractitionerResolver;
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
    /** 就诊费用性质（优先合同名）批量回填队列卡片。 */
    private final EncounterContractDisplayAssembler encounterContractDisplayAssembler;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final PharmacyEncounterQueueLoader pharmacyEncounterQueueLoader;
    private final MedicationDispenseSlipQueryLoader medicationDispenseSlipQueryLoader;
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final ClinicalCatalogDispenseLocationResolver clinicalCatalogDispenseLocationResolver;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final MedicationDispenseInventoryAvailabilityGate medicationDispenseInventoryAvailabilityGate;
    private final InventoryItemDomainService inventoryItemDomainService;
    private final TraceNumberQueryService traceNumberQueryService;
    private final TraceNumberDispenseTraceOrchestrator traceNumberDispenseTraceOrchestrator;
    private final ChargeItemQueryService chargeItemQueryService;
    private final OutpatientPharmacyDispenseMapper outpatientPharmacyDispenseMapper;
    private final OutpatientEncounterMedicationDispenseSlipPageLoader outpatientEncounterMedicationDispenseSlipPageLoader;

    /**
     * 加载发药页初始化元数据与调配药师分页候选。
     *
     * @param request 分页（{@link com.openhis.health.domain.share.query.AbstractPageQuery}）与可选岗位过滤；药房机构由会话解析
     * @return 下拉选项与药师分页壳
     * @throws BusinessException {@code EX_OPP_101}：未登录或装载失败
     */
    public LoadPharmacyDispensePageInitResponse loadPharmacyDispensePageInit(LoadPharmacyDispensePageInitRequest request) {
        // Gate：须已登录且会话有效。
        currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.EX_OPP_101_INIT_METADATA_FAILED, "未登录或会话无效，无法加载发药页元数据");
        // Pre：请求为空时初始化默认对象。
        if (request == null) {
            request = new LoadPharmacyDispensePageInitRequest();
        }
        try {
        // Pre：药房下拉、发放状态与调配药师分页候选。
            List<Location> pharmacies = managedLocationIdsResolver.listManagedActivePharmaciesOrEmpty();
            List<OptionDto> dispenseStatusOptions = codeSystemQueryService.getOptionListByEnum(DispenseStatus.class);
            List<OptionDto> encounterClassOptions = codeSystemQueryService.getOptionListByEnum(EncounterClass.class);
            List<OptionDto> notPerformedReasonOptions = new ArrayList<>();

            int pageIndex = request.springPageIndex();
            int pageSize = request.getBoundedPageSize();
            Pageable pageable = PageRequest.of(pageIndex, pageSize);

            // 调配药师：adm_pr_assign 激活药师岗（ROLE_PHARMACIST），排除系统管理员岗。
            Page<Practitioner> preparerPage = this.pagePreparerCandidatesExcludingAdmin(pageable);

            return outpatientPharmacyDispenseMapper.toLoadInitResponse(
                pharmacies, dispenseStatusOptions, encounterClassOptions, notPerformedReasonOptions, preparerPage);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_101_INIT_METADATA_FAILED, "发药页元数据加载失败：" + ex.getMessage());
        }
    }

    /**
     * 分页查询存在门诊药品发放事实的就诊队列，并回补门诊信息读模型展示字段。
     *
     * @param request 关键字、状态子集与分页；库房范围由当前用户会话机构解析，不由调用方指定
     * @return 队列总行数与行列表
     */
    public PagePendingDispenseEncounterQueueResponse pagePendingDispenseEncounterQueue(PagePendingDispenseEncounterQueueRequest request) {
        // Pre：请求为空时初始化默认对象。
        if (request == null) {
            request = new PagePendingDispenseEncounterQueueRequest();
        }

        // Pre：管辖库位非空时拼 location_id IN；管辖 empty 时不收窄。
        List<Long> pharmacyLocationIds = managedLocationIdsResolver.listManagedLocationIdsOrEmpty();
        OutpatientPharmacyPendingEncounterPageQuery pendingEncounterQuery = new OutpatientPharmacyPendingEncounterPageQuery();
        if (!pharmacyLocationIds.isEmpty()) {
            pendingEncounterQuery.setLocationIds(pharmacyLocationIds);
        }
        PageQueryBinder.bind(request, pendingEncounterQuery);
        // 队列不按 status_changed_time 时间窗收敛（该字段随配药/发药变更）。
        pendingEncounterQuery.setStartDate(null);
        pendingEncounterQuery.setEndDate(null);
        pendingEncounterQuery.setDispenseStatusEnumIn(request.getDispenseStatusEnumIn());
        Page<Long> encounterPage = this.pharmacyEncounterQueueLoader.pageEncounterIdsForOutpatientPharmacyPending(pendingEncounterQuery);

        // 按队列顺序回补门诊信息快照（患者与科室展示）。
        List<OutpatientEncounterContextQueryModel> infos =
            EncounterContextReader.listOutpatientContextsByEncounterIdsOrdered(encounterPage.getContent());

        // Asm：按队列顺序回补门诊信息并封装分页响应。
        PagePendingDispenseEncounterQueueResponse response =
            outpatientPharmacyDispenseMapper.toQueuePage(encounterPage, infos, Map.of());
        // Asm：费用性质与医生站/护士站卡片同口径（就诊优先合同名）。
        this.applyQueueContractDisplayLabels(response);
        return response;
    }

    /**
     * 发药队列行批量回填 {@code contractDisplay}（与医生站患者卡片费用性质同口径）。
     *
     * @param response 队列分页响应
     */
    private void applyQueueContractDisplayLabels(PagePendingDispenseEncounterQueueResponse response) {
        if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
            return;
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        // Loop：收集本页就诊主键，供合同展示一次批量解析
        for (PendingDispenseEncounterQueueRowDto row : response.getContent()) {
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        if (encounterIds.isEmpty()) {
            return;
        }
        Map<Long, String> contractDisplayByEncounterId =
            encounterContractDisplayAssembler.mapContractDisplayByEncounterIds(encounterIds);
        // Loop：逐行写回费用性质展示名；无合同时留空，前端回落「自费」
        for (PendingDispenseEncounterQueueRowDto row : response.getContent()) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            row.setContractDisplay(contractDisplayByEncounterId.get(row.getEncounterId()));
        }
    }

    /**
     * 分页加载就诊下门诊药品发药单读模型行，并装配候选库存与展示副作用字段。
     *
     * @param request 就诊、库房、状态过滤、医嘱状态过滤与分页
     * @return 明细分页壳与行列表（含 {@code inventoryDetailList}）
     */
    public PageEncounterMedicationDispenseSlipsResponse pageEncounterMedicationDispenseSlips(PageEncounterMedicationDispenseSlipsRequest request) {
        // Pre：组装发药单分页查询并委托读侧 Loader。
        EncounterMedicationDispenseSlipLoaderQuery query = toSlipPageQuery(request);
        applyManagedLocationScopeWhenUnscoped(query);
        // Asm：映射明细分页响应。
        return outpatientPharmacyDispenseMapper.toSlipPageResponse(
            outpatientEncounterMedicationDispenseSlipPageLoader.pageOutpatientEncounterMedicationDispenseSlips(query));
    }

    /**
     * 未显式指定 {@code locationId} 时，按管辖库位收窄明细（与 IF-OPP-02 队列同源）。
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
     * 门诊发药单分页请求 → Loader 查询对象（含分页绑定与中西药筛选）。
     *
     * @param request IF 分页入参；null 时返回空查询壳
     * @return 读侧 Loader 查询
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
        query.setRequestStatusEnumIn(request.getRequestStatusEnumIn());
        PageQueryBinder.bind(request, query);
        return query;
    }

    /**
     * 保存门诊配药草稿（PREPARATION→IN_PROGRESS）。
     *
     * @param request 就诊、调配药师与提交行
     * @return 处理结果与提示
     * @throws BusinessException {@code EX_OPP_202}～{@code EX_OPP_205}：门控或前置校验失败
     */
    public SavePrepareOutpatientMedicationDispenseResponse savePrepareOutpatientMedicationDispense(SavePrepareOutpatientMedicationDispenseRequest request) {
        // Gate：调配药师与配药行须非空，数量须大于 0。
        if (request.getPreparerPractitionerId() == null || request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_202_PREPARER_INVALID, "调配药师或配药行不能为空");
        }
        Long operatorPractitionerId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.EX_OPP_202_PREPARER_INVALID, "未登录用户不能保存配药");

        Practitioner preparerPractitioner = practitionerQueryService.findById(request.getPreparerPractitionerId());
        if (preparerPractitioner == null) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_202_PREPARER_INVALID, "调配药师不存在或已停用");
        }
        // 系统管理员即使挂了药师岗，也不得作为门诊配药人。
        if (this.isActivePlatformAdmin(request.getPreparerPractitionerId())) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_202_PREPARER_INVALID, "系统管理员不能作为调配药师");
        }
        // BR-OPP-004：调配药师（配药人）不得与当前登录药师（发药人）同人。
        if (Objects.equals(operatorPractitionerId, request.getPreparerPractitionerId())) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_202_PREPARER_INVALID, "调配药师不能与当前登录用户为同一人");
        }

        for (SavePrepareDispenseLineSubmitDto submitLine : request.getLines()) {
            if (submitLine == null || submitLine.getQuantity() == null || submitLine.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_204_QUANTITY_MISMATCH, "配药数量必须大于 0");
            }
        }

        // 就诊上下文存在。
        encounterQueryService.mustGetEncounter(request.getEncounterId());

        List<MedicationRequestListQueryModel> encounterMedicationRequestRows =
            medicationRequestQueryService.listMedicationRequestsByEncounter(request.getEncounterId());
        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId = new HashMap<>();
        for (MedicationRequestListQueryModel medicationRequestRow : encounterMedicationRequestRows) {
            if (medicationRequestRow.getId() != null) {
                medicationRequestByMedReqId.put(medicationRequestRow.getId(), medicationRequestRow);
            }
        }

        List<Long> submittedDispenseIds = new ArrayList<>();
        for (SavePrepareDispenseLineSubmitDto submitLine : request.getLines()) {
            if (submitLine != null && submitLine.getDispenseId() != null) {
                submittedDispenseIds.add(submitLine.getDispenseId());
            }
        }
        List<MedicationDispense> loadedDispenses = medicationDispenseQueryService.listMedicationDispensesByIds(submittedDispenseIds);
        Map<Long, MedicationDispense> dispenseById = new HashMap<>();
        for (MedicationDispense loadedDispense : loadedDispenses) {
            if (loadedDispense.getId() != null) {
                dispenseById.put(loadedDispense.getId(), loadedDispense);
            }
        }

        List<String> traceCodeInputs = new ArrayList<>();
        List<String> hardHospitalCodes = new ArrayList<>();
        for (SavePrepareDispenseLineSubmitDto submitLine : request.getLines()) {
            MedicationDispense dispense = dispenseById.get(submitLine.getDispenseId());
            if (dispense == null) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED, "发放单不存在");
            }
            if (!Objects.equals(dispense.getEncounterId(), request.getEncounterId())) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED, "发放单不属于当前就诊");
            }
            assertOutpatientMedicationRow(dispense);
            if (!medicationDispenseQueryService.validateDispensePreparable(dispense.getId())) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED, "当前状态不允许保存配药");
            }

            MedicationRequestListQueryModel medicationRequestRow = medicationRequestByMedReqId.get(dispense.getMedReqId());
            Long templateOrganizationId = null;
            if (medicationRequestRow != null && medicationRequestRow.getOrgId() != null) {
                templateOrganizationId = medicationRequestRow.getOrgId();
            }
            if (templateOrganizationId == null) {
                OutpatientEncounterContextQueryModel outpatientRow =
                    EncounterContextReader.getOutpatientContextByEncounterId(request.getEncounterId()).orElse(null);
                if (outpatientRow != null) {
                    templateOrganizationId = outpatientRow.getOrganizationId();
                }
            }

            ProductDefinitionDisplayDto productRow = loadProductForMedication(dispense.getMedicationId());
            String distributionCategoryCode = MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE;
            if (productRow != null && StringUtils.isNotBlank(productRow.getCategoryCode())) {
                distributionCategoryCode = productRow.getCategoryCode();
            }
            String methodCode = null;
            if (medicationRequestRow != null) {
                methodCode = medicationRequestRow.getMethodCode();
            }
            List<Long> candidateLocationIds = clinicalCatalogDispenseLocationResolver.listApplicableCandidateLocationIds(
                templateOrganizationId,
                TableNameConstants.MED_MEDICATION_DEFINITION,
                distributionCategoryCode,
                dispense.getMedicationId(),
                methodCode,
                null);
            if (candidateLocationIds.isEmpty()) {
                if (!Objects.equals(dispense.getLocationId(), submitLine.getDispenseLocationId())) {
                    throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID, "未解析到候选发放库房且提交库位与待发记录不一致");
                }
            } else if (!candidateLocationIds.contains(submitLine.getDispenseLocationId())) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID, "发放库房不在模板候选集");
            }

            MedicationRequestDetailQueryModel medicationRequestDetail =
                medicationRequestQueryService.loadMedicationRequestById(dispense.getMedReqId());
            if (!StringUtils.equals(medicationRequestDetail.getUnitCode(), dispense.getUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_204_QUANTITY_MISMATCH, "发放单位与医嘱单位不一致");
            }

            List<String> lineTraceCodes = splitTraceCodes(submitLine.getTraceNo());
            // 有码批：硬校验本库优先、否则全院同品同批在院；无码批：跳过（仅业务行）
            boolean lotHasMaster = traceNumberQueryService.existsMasterByItemLot(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                dispense.getMedicationId(),
                submitLine.getLotNumber());
            if (lotHasMaster) {
                hardHospitalCodes.addAll(lineTraceCodes);
                if (!lineTraceCodes.isEmpty()) {
                    traceNumberQueryService.assertCodesInNetStockForDispenseLine(
                        TableNameConstants.MED_MEDICATION_DEFINITION,
                        dispense.getMedicationId(),
                        submitLine.getLotNumber(),
                        submitLine.getDispenseLocationId(),
                        lineTraceCodes);
                }
            }
            // 有码/无码批均参与本单码去重
            traceCodeInputs.addAll(lineTraceCodes);

            if (productRow == null) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID, "缺少药品目录投影，无法按最小单位校验库存");
            }
            // BR-OPP-014：码数/剩余硬 Gate 不在保存配药；核发写追溯走 IF-OPP-05 / TraceNumberDispenseTraceOrchestrator（可选扫，盖不住跳过写）
            medicationDispenseInventoryAvailabilityGate.assertSufficientForMedicationDispenseWithReserve(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                dispense.getMedicationId(),
                submitLine.getDispenseLocationId(),
                submitLine.getLotNumber(),
                submitLine.getQuantity(),
                dispense.getUnitCode(),
                productRow,
                dispense.getId());
        }

        assertPrepareChargeTotalMatchesInventoryLotPricing(
            request.getEncounterId(),
            submittedDispenseIds,
            request.getLines(),
            dispenseById);

        traceNumberQueryService.assertNoDuplicateTraceCodes(traceCodeInputs);
        // 仅有码批参与在院硬校验；无码批码不在主表
        traceNumberQueryService.assertCodesInHospitalForDispense(hardHospitalCodes);

        assertTemporaryTherapyQuantities(request.getLines(), dispenseById);

        PrepareMedicationDispenseCommand prepareMedicationDispenseCommand =
            outpatientPharmacyDispenseMapper.toPrepareMedicationDispenseCommand(
                operatorPractitionerId, request.getPreparerPractitionerId(), request.getLines());
        medicationDispenseDomainService.prepareMedicationDispense(prepareMedicationDispenseCommand);
        return outpatientPharmacyDispenseMapper.toPrepareBatchResponse(submittedDispenseIds, "保存配药成功", true);
    }

    /**
     * 核发（领域推进 {@code COMPLETED} 后同一事务内按最小单位扣减 {@code wkf_inventory_item}）；
     * 进销存上传为事务外协作（由 {@code InventoryUploadExtension#isEnabled} 门控），失败不回滚本地事实。
     *
     * @param request 就诊与勾选发放行
     * @return 处理结果；若扩展启用且上传失败，仅在 {@code message} 中合并告警文案
     * @throws BusinessException {@code EX_OPP_203}、{@code EX_OPP_205}：门控或库存断言失败
     */

    public DispenseOutpatientMedicationsResponse dispenseOutpatientMedications(DispenseOutpatientMedicationsRequest request) {
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

        // 就诊上下文存在，后续逐行门控均在该就诊范围内。
        encounterQueryService.mustGetEncounter(request.getEncounterId());

        List<Long> submittedDispenseIds = new ArrayList<>();
        for (DispenseMedicationLineSubmitDto lineSubmit : request.getLines()) {
            if (lineSubmit != null && lineSubmit.getDispenseId() != null) {
                submittedDispenseIds.add(lineSubmit.getDispenseId());
            }
        }

        // 批量加载发放事实并按提交顺序重排，保证命令项与前端勾选顺序一致。
        List<MedicationDispense> dispensersInSubmitOrder = new ArrayList<>();
        List<MedicationDispense> loadedDispenses = medicationDispenseQueryService.listMedicationDispensesByIds(submittedDispenseIds);
        Map<Long, MedicationDispense> dispenseById = new HashMap<>();
        for (MedicationDispense loadedDispense : loadedDispenses) {
            if (loadedDispense.getId() != null) {
                dispenseById.put(loadedDispense.getId(), loadedDispense);
            }
        }

        // 一次性批量加载本批次涉及药品的产品定义读模型，供库存最小单位换算与断言共用（禁止在循环内逐条打目录）。
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

        for (Long dispenseId : submittedDispenseIds) {
            MedicationDispense dispense = dispenseById.get(dispenseId);
            if (dispense == null) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED, "发放单不存在");
            }
            if (!Objects.equals(dispense.getEncounterId(), request.getEncounterId())) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED, "发放单不属于当前就诊");
            }
            assertOutpatientMedicationRow(dispense);
            if (!medicationDispenseQueryService.validateDispensable(dispense.getId())) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED, "当前状态不允许发药");
            }
            BigDecimal requiredQuantity = dispense.getQuantity();
            if (requiredQuantity == null) {
                requiredQuantity = dispense.getDispenseQuantity();
            }
            String productCompositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, dispense.getMedicationId());
            ProductDefinitionDisplayDto productRowForStockAssert = dispenseProductRowsByKey.get(productCompositeKey);
            if (productRowForStockAssert == null) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID, "缺少药品目录投影，无法按最小单位校验库存");
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

        DispenseMedicationCommand dispenseMedicationCommand =
            outpatientPharmacyDispenseMapper.toDispenseMedicationCommand(operatorPractitionerId, dispenserPractitioner.getId(), dispensersInSubmitOrder);
        medicationDispenseDomainService.dispenseMedication(dispenseMedicationCommand);

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
        applyInventoryDeductionForDispensedOutpatientLines(dispensersInSubmitOrder, operatorPractitionerId, productByMedicationIdForDispense);
        traceNumberDispenseTraceOrchestrator.recordDispenseOutLines(
            dispensersInSubmitOrder, operatorPractitionerId, productByMedicationIdForDispense);

        DispenseOutpatientMedicationsResponse response =
            outpatientPharmacyDispenseMapper.toDispenseBatchResponse(submittedDispenseIds, "发药成功", true);
        // Asm：可选医保 3505 上传（afterCommit + 异步；失败不回滚本地发药）
        inventoryUploadExtension.runUploadIfEnabled(() -> {
            InventoryUploadAfterDispenseExtensionIn uploadCommand = new InventoryUploadAfterDispenseExtensionIn()
                .setEncounterId(request.getEncounterId())
                .setDispenseIds(submittedDispenseIds);
            inventoryUploadExtension.uploadAfterDispense(uploadCommand);
        });
        return response;
    }

    /**
     * 作废未发放闭环内的发放行（领域推进至拒绝态）。
     *
     * @param request 就诊与作废行（含原因码）
     * @return 处理结果
     * @throws BusinessException {@code EX_OPP_208}：参数或状态不允许
     */

    public CancelOutpatientMedicationDispenseResponse cancelOutpatientMedicationDispense(CancelOutpatientMedicationDispenseRequest request) {
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

        // 就诊上下文存在。
        encounterQueryService.mustGetEncounter(request.getEncounterId());

        List<Long> submittedDispenseIds = new ArrayList<>();
        for (CancelMedicationDispenseLineSubmitDto cancelLine : request.getLines()) {
            if (cancelLine != null && cancelLine.getDispenseId() != null) {
                submittedDispenseIds.add(cancelLine.getDispenseId());
            }
        }
        // 逐单校验门诊药品类型与可作废状态窗口（配药中/待发药历史态）。
        for (Long dispenseId : submittedDispenseIds) {
            MedicationDispense dispense = medicationDispenseQueryService.mustGetMedicationDispense(dispenseId);
            if (!Objects.equals(dispense.getEncounterId(), request.getEncounterId())) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_208_CANCEL_NOT_ALLOWED, "发放单不属于当前就诊");
            }
            assertOutpatientMedicationRow(dispense);
            DispenseStatus statusEnum = dispense.getStatusEnum();
            boolean cancellable = false;
            if (DispenseStatus.PREPARATION.equals(statusEnum)) {
                cancellable = true;
            }
            if (DispenseStatus.IN_PROGRESS.equals(statusEnum)) {
                cancellable = true;
            }
            if (DispenseStatus.PREPARED.equals(statusEnum)) {
                cancellable = true;
            }
            if (!cancellable) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_208_CANCEL_NOT_ALLOWED, "当前状态不允许作废");
            }
        }

        CancelMedicationDispenseCommand cancelMedicationDispenseCommand =
            outpatientPharmacyDispenseMapper.toCancelMedicationDispenseCommand(operatorPractitionerId, request.getLines());
        medicationDispenseDomainService.cancelMedicationDispense(cancelMedicationDispenseCommand);
        return outpatientPharmacyDispenseMapper.toCancelBatchResponse(submittedDispenseIds, "作废成功", true);
    }

    /**
     * 门诊发药工作站仅处理门诊药品发放类型，拦截耗材等他类发放。
     *
     * @param dispense 发放聚合行
     * @throws BusinessException {@code EX_OPP_203}：类型不在受理范围
     */
    private static void assertOutpatientMedicationRow(MedicationDispense dispense) {
        MedicationDispenseType dispenseType = dispense.getDispenseEnum();
        if (dispenseType == null) {
            return;
        }
        if (dispenseType != MedicationDispenseType.OUTPATIENT && dispenseType != MedicationDispenseType.TEMPORARY) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED, "非门诊药品发放不在本工作站处理范围");
        }
    }

    /**
     * 批量加载医务人员主档用于展示名解析（内存循环 acceptable；此处为缓存型读，禁止误解为批量写 I/O）。
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
     * 按药品定义主键加载产品定义读模型单行。
     *
     * @param medicationId 药品定义主键
     * @return 读模型或 {@code null}
     */
    private ProductDefinitionDisplayDto loadProductForMedication(Long medicationId) {
        if (medicationId == null) {
            return null;
        }
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
        lookup.setProductDefinitionId(medicationId);
        Map<String, ProductDefinitionDisplayDto> map = productDefinitionDisplayTerminologyAssembler.loadAsMap(List.of(lookup));
        String key = ProductDefinitionKeyUtil.buildCompositeKey(TableNameConstants.MED_MEDICATION_DEFINITION, medicationId);
        return map.get(key);
    }

    /**
     * 批量扫码身份反查（IF-TRC-21）：只读预览，不落库。
     *
     * @param request 追溯码列表与可选场景上下文
     * @return 成功项与失败项（允许部分成功）
     */
    public BatchResolveTraceIdentityResponse batchResolveTraceIdentity(BatchResolveTraceIdentityRequest request) {
        if (request == null || request.getTraceCodes() == null) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "追溯码列表不能为空");
        }
        List<String> normalizedTraceNos = TraceNumberBatchIdentityResolver.normalizeTraceNos(request.getTraceCodes());
        TraceNumberBatchIdentityResolver.assertBatchSizeWithinLimit(normalizedTraceNos);
        List<TraceNumberIdentityQueryModel> models =
            traceNumberQueryService.batchResolveTraceIdentityByTraceNos(normalizedTraceNos);
        TraceNumberBatchIdentityResolver.ResolvedBatch resolvedBatch =
            TraceNumberBatchIdentityResolver.splitResolvedItems(models);
        BatchResolveTraceIdentityResponse response = new BatchResolveTraceIdentityResponse();
        response.setItems(this.toTraceIdentityResolveItemDtos(resolvedBatch.getItems()));
        response.setFailItems(this.toTraceIdentityResolveFailItemDtos(resolvedBatch.getFailItems()));
        return response;
    }

    /**
     * 追溯码批量解析成功项 → 前端 DTO（含解析态、产品锚点与剩余拆分量）。
     *
     * @param models 读侧解析模型
     * @return 成功项列表；null 入参返回空列表
     */
    private List<TraceIdentityResolveItemDto> toTraceIdentityResolveItemDtos(
        List<TraceNumberIdentityQueryModel> models
    ) {
        List<TraceIdentityResolveItemDto> items = new ArrayList<>();
        if (models == null) {
            return items;
        }
        for (TraceNumberIdentityQueryModel model : models) {
            if (model == null) {
                continue;
            }
            TraceIdentityResolveItemDto dto = new TraceIdentityResolveItemDto();
            dto.setTraceNo(model.getTraceNo());
            dto.setResolveStatusEnum(model.getResolveStatusEnum());
            dto.setItemTable(model.getItemTable());
            dto.setItemId(model.getItemId());
            dto.setLotNumber(model.getLotNumber());
            dto.setTraceUnitCode(model.getTraceUnitCode());
            // 主档贴码比快照
            dto.setTraceUnitRatio(model.getTraceUnitRatio());
            dto.setRemainingSplitQuantity(model.getRemainingSplitQuantity());
            dto.setInHospitalStatusEnum(model.getInHospitalStatusEnum());
            items.add(dto);
        }
        return items;
    }

    /**
     * 追溯码批量解析失败项 → 前端 DTO（traceNo + 失败原因）。
     *
     * @param failItems Support 层失败项
     * @return 失败项列表；null 入参返回空列表
     */
    private List<TraceIdentityResolveFailItemDto> toTraceIdentityResolveFailItemDtos(
        List<TraceNumberBatchIdentityResolver.FailItem> failItems
    ) {
        List<TraceIdentityResolveFailItemDto> items = new ArrayList<>();
        if (failItems == null) {
            return items;
        }
        for (TraceNumberBatchIdentityResolver.FailItem failItem : failItems) {
            if (failItem == null) {
                continue;
            }
            TraceIdentityResolveFailItemDto dto = new TraceIdentityResolveFailItemDto();
            dto.setTraceNo(failItem.getTraceNo());
            dto.setResolveStatusEnum(failItem.getResolveStatusEnum());
            items.add(dto);
        }
        return items;
    }

    /**
     * 拆分追溯码提交串（分隔符与 {@link GeneralSharedConstants#COMMA} 一致）；空串不产生条目。
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
     * 实物扣减：将发放行上的业务单位数量换算为库存最小单位后，写入 {@code wkf_inventory_item}。
     *
     * <p>批号非空时按「药品+库房+批号」解析唯一库存行并合并扣减；批号为空时在目标库房按效期优先（FEFO）占用多条账面批次。</p>
     *
     * @param dispensersInSubmitOrder   已完成领域核发的事务内快照行（含库房、批号、数量、单位）
     * @param operatorPractitionerId            当前操作用户 id（写入库存命令载体，便于审计扩展）
     * @param productByMedicationId    药品定义 id → 产品读模型
     */
    private void applyInventoryDeductionForDispensedOutpatientLines(
        List<MedicationDispense> dispensersInSubmitOrder,
        Long operatorPractitionerId,
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId
    ) {
        if (dispensersInSubmitOrder == null || dispensersInSubmitOrder.isEmpty()) {
            return;
        }
        String sep = String.valueOf(GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR);
        Map<String, BigDecimal> mergedAllocateRequiredByKey = new LinkedHashMap<>();
        Map<String, BigDecimal> mergedLotDecreaseByLotKey = new LinkedHashMap<>();

        // 第一步：按「批号是否为空」分流合并拆零数量——有批号走定点批次扣减键，无批号走 FEFO 分配键。
        for (MedicationDispense dispense : dispensersInSubmitOrder) {
            if (dispense == null) {
                continue;
            }
            ProductDefinitionDisplayDto productRow = productByMedicationId.get(dispense.getMedicationId());
            if (productRow == null) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID, "缺少药品目录投影，无法按最小单位扣减库存");
            }
            BigDecimal businessQty = dispense.getQuantity();
            if (businessQty == null) {
                businessQty = dispense.getDispenseQuantity();
            }
            BigDecimal splitQty = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
                ProductDefinitionDisplaySupport.toUnitConversionSource(productRow), dispense.getUnitCode(), businessQty);
            if (StringUtils.isNotBlank(dispense.getLotNumber())) {
                String lotKey =
                    dispense.getMedicationId()
                        + sep
                        + dispense.getLocationId()
                        + sep
                        + dispense.getLotNumber().trim();
                BigDecimal existedLot = mergedLotDecreaseByLotKey.get(lotKey);
                if (existedLot == null) {
                    mergedLotDecreaseByLotKey.put(lotKey, splitQty);
                } else {
                    mergedLotDecreaseByLotKey.put(lotKey, existedLot.add(splitQty));
                }
            } else {
                String allocateKey =
                    TableNameConstants.MED_MEDICATION_DEFINITION
                        + sep
                        + dispense.getMedicationId()
                        + sep
                        + dispense.getLocationId();
                BigDecimal existedAllocate = mergedAllocateRequiredByKey.get(allocateKey);
                if (existedAllocate == null) {
                    mergedAllocateRequiredByKey.put(allocateKey, splitQty);
                } else {
                    mergedAllocateRequiredByKey.put(allocateKey, existedAllocate.add(splitQty));
                }
            }
        }

        // 第二步：批号明确的路径——解析合并键为库存项主键并汇总扣减量后一次性 decrease。
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
                TableNameConstants.MED_MEDICATION_DEFINITION,
                medicationId,
                locationId,
                lotNumber);
            BigDecimal existedInv = inventoryIdToMergedDecrease.get(inventoryItemId);
            if (existedInv == null) {
                inventoryIdToMergedDecrease.put(inventoryItemId, lotEntry.getValue());
            } else {
                inventoryIdToMergedDecrease.put(inventoryItemId, existedInv.add(lotEntry.getValue()));
            }
        }

        inventoryItemDomainService.decreaseQuantitiesMerged(operatorPractitionerId, inventoryIdToMergedDecrease);

        // 第三步：无批号路径——委托领域按效期优先占用多条账面批次并完成扣减。
        for (Map.Entry<String, BigDecimal> allocateEntry : mergedAllocateRequiredByKey.entrySet()) {
            String allocateKey = allocateEntry.getKey();
            String[] allocateParts = allocateKey.split(Pattern.quote(sep), -1);
            if (allocateParts.length != 3) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID, "内部分配合并键解析失败");
            }
            String itemTableName = allocateParts[0];
            Long itemId = Long.parseLong(allocateParts[1]);
            Long locationId = Long.parseLong(allocateParts[2]);
            AllocateAndDecreaseCommand allocateCommand = new AllocateAndDecreaseCommand();
            allocateCommand.setOperatorId(operatorPractitionerId);
            AllocateAndDecreaseCommand.AllocateAndDecreaseItemCommand allocateItem =
                new AllocateAndDecreaseCommand.AllocateAndDecreaseItemCommand();
            allocateItem.setItemTable(itemTableName);
            allocateItem.setItemId(itemId);
            allocateItem.setLocationId(locationId);
            allocateItem.setRequiredQty(allocateEntry.getValue());
            allocateCommand.setItem(allocateItem);
            inventoryItemDomainService.allocateAndDecrease(allocateCommand);
        }
    }

    /**
     * 保存配药前金额门禁：已存在的收费明细（按发放单锚点）总价须等于「账单单价 × 发放业务单位数量」按提交行重算之和。
     *
     * <p>单价取自锚定 {@code adm_charge_item.unit_price}（**BR-PRICE-SRC-001** / **BR-OPP-013**），同发放多行时按
     * {@code sum(total_price)/sum(quantity_value)} 加权；**禁止**再查当前 {@code adm_charge_item_definition}，避免调价后误拒。
     * 数量口径须与签发费用草稿 {@link com.openhis.health.application.outpatient.doctorstation.advice.mapper.DoctorStationAdviceMapperImpl#toCreateChargeItemDraftItemFromOrder} 一致。</p>
     *
     * <p>若就诊下尚无对应收费行（未签发计费等），跳过本校验。</p>
     *
     * @param encounterId            就诊标识
     * @param submittedDispenseIds   本批次涉及发放单主键（与提交行一致）
     * @param submitLines            配药提交行
     * @param dispenseById           发放单实体映射
     * @throws BusinessException {@code EX_OPP_204}：重算总额与账单不等
     */
    private void assertPrepareChargeTotalMatchesInventoryLotPricing(
        Long encounterId,
        List<Long> submittedDispenseIds,
        List<SavePrepareDispenseLineSubmitDto> submitLines,
        Map<Long, MedicationDispense> dispenseById
    ) {
        List<ChargeItem> chargeRows =
            chargeItemQueryService.listChargeItemsForMedicationDispensePricing(encounterId, submittedDispenseIds);
        if (chargeRows.isEmpty()) {
            return;
        }
        BigDecimal baselineTotal = BigDecimal.ZERO;
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow != null && chargeRow.getTotalPrice() != null) {
                baselineTotal = baselineTotal.add(chargeRow.getTotalPrice());
            }
        }
        Map<Long, BigDecimal> unitPriceByDispenseId = buildUnitPriceByDispenseIdFromChargeRows(chargeRows);
        BigDecimal recomputedTotal = BigDecimal.ZERO;
        for (SavePrepareDispenseLineSubmitDto submitLine : submitLines) {
            if (submitLine == null || submitLine.getDispenseId() == null) {
                continue;
            }
            if (dispenseById.get(submitLine.getDispenseId()) == null) {
                continue;
            }
            BigDecimal lotUnitPrice = unitPriceByDispenseId.get(submitLine.getDispenseId());
            if (lotUnitPrice == null) {
                throw new BusinessException(
                    ErrorCodeConstants.EX_OPP_204_QUANTITY_MISMATCH,
                    "收费明细缺少与本发放单锚点一致的账单单价，不能保存配药");
            }
            BigDecimal qtyInDispenseUnit = submitLine.getQuantity();
            recomputedTotal = recomputedTotal.add(qtyInDispenseUnit.multiply(lotUnitPrice));
        }
        BigDecimal baselineRounded = baselineTotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal recomputedRounded = recomputedTotal.setScale(2, RoundingMode.HALF_UP);
        if (baselineRounded.compareTo(recomputedRounded) != 0) {
            throw new BusinessException(
                ErrorCodeConstants.EX_OPP_204_QUANTITY_MISMATCH,
                "按收费明细账单单价重算的总金额与当前收费明细不一致，不能保存配药");
        }
    }

    /**
     * 按发放锚点聚合 ChargeItem，得到 dispenseId → 账单单价（同锚点多行时 total/qty 加权）。
     *
     * @param chargeRows 与本批次发放锚点一致的收费明细
     * @return dispenseId → unitPrice
     */
    private Map<Long, BigDecimal> buildUnitPriceByDispenseIdFromChargeRows(List<ChargeItem> chargeRows) {
        Map<Long, BigDecimal> totalByDispense = new HashMap<>();
        Map<Long, BigDecimal> qtyByDispense = new HashMap<>();
        if (chargeRows == null) {
            return Map.of();
        }
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow == null || chargeRow.getDispenseId() == null || Boolean.TRUE.equals(chargeRow.getIsDeleted())) {
                continue;
            }
            Long dispenseKey = chargeRow.getDispenseId();
            if (chargeRow.getTotalPrice() != null) {
                BigDecimal existingTotal = totalByDispense.get(dispenseKey);
                if (existingTotal == null) {
                    totalByDispense.put(dispenseKey, chargeRow.getTotalPrice());
                } else {
                    totalByDispense.put(dispenseKey, existingTotal.add(chargeRow.getTotalPrice()));
                }
            }
            if (chargeRow.getQuantityValue() != null) {
                BigDecimal existingQty = qtyByDispense.get(dispenseKey);
                if (existingQty == null) {
                    qtyByDispense.put(dispenseKey, chargeRow.getQuantityValue());
                } else {
                    qtyByDispense.put(dispenseKey, existingQty.add(chargeRow.getQuantityValue()));
                }
            }
        }
        Map<Long, BigDecimal> unitPriceByDispense = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> entry : totalByDispense.entrySet()) {
            Long dispenseKey = entry.getKey();
            BigDecimal qtyVal = qtyByDispense.get(dispenseKey);
            if (qtyVal != null && qtyVal.compareTo(BigDecimal.ZERO) > 0) {
                unitPriceByDispense.put(dispenseKey, entry.getValue().divide(qtyVal, 4, RoundingMode.HALF_UP));
            }
        }
        return unitPriceByDispense;
    }

    /**
     * 按发放单聚合收费明细的总价，并按数量加权折算单价（多条分段费用合并场景）。
     *
     * @param chargeRows        就诊维度下发的收费行
     * @param unitPriceOut      输出：dispenseId→单价
     * @param totalPriceOut     输出：dispenseId→金额合计
     */
    private static void mergeChargePricingFromChargeItems(
        List<ChargeItem> chargeRows,
        Map<Long, BigDecimal> unitPriceOut,
        Map<Long, BigDecimal> totalPriceOut) {
        Map<Long, BigDecimal> totalByDispense = new HashMap<>();
        Map<Long, BigDecimal> qtyByDispense = new HashMap<>();
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow == null || chargeRow.getDispenseId() == null) {
                continue;
            }
            Long dispenseKey = chargeRow.getDispenseId();
            if (chargeRow.getTotalPrice() != null) {
                BigDecimal existedTotal = totalByDispense.get(dispenseKey);
                if (existedTotal == null) {
                    totalByDispense.put(dispenseKey, chargeRow.getTotalPrice());
                } else {
                    totalByDispense.put(dispenseKey, existedTotal.add(chargeRow.getTotalPrice()));
                }
            }
            if (chargeRow.getQuantityValue() != null) {
                BigDecimal existedQty = qtyByDispense.get(dispenseKey);
                if (existedQty == null) {
                    qtyByDispense.put(dispenseKey, chargeRow.getQuantityValue());
                } else {
                    qtyByDispense.put(dispenseKey, existedQty.add(chargeRow.getQuantityValue()));
                }
            }
        }
        for (Map.Entry<Long, BigDecimal> entry : totalByDispense.entrySet()) {
            Long dispenseKey = entry.getKey();
            BigDecimal totalVal = entry.getValue();
            totalPriceOut.put(dispenseKey, totalVal);
            BigDecimal qtyVal = qtyByDispense.get(dispenseKey);
            if (qtyVal != null && qtyVal.compareTo(BigDecimal.ZERO) > 0) {
                unitPriceOut.put(dispenseKey, totalVal.divide(qtyVal, 4, RoundingMode.HALF_UP));
            }
        }
    }

    /**
     * BR-OPP-006：临时医嘱允许多条发放行拆分总量；保存配药前须校验「本次各段数量之和」与医嘱开立总量一致。
     *
     * @param submitLines  本次提交行（含用户改量的分段数量）
     * @param dispenseById 本批次涉及发放单实体映射
     * @throws BusinessException {@code EX_OPP_204}：临时医嘱合计与开立量不一致
     */
    private void assertTemporaryTherapyQuantities(List<SavePrepareDispenseLineSubmitDto> submitLines, Map<Long, MedicationDispense> dispenseById) {
        // 建立发放单 id → 本次提交数量的映射，便于与同医嘱下其它分段对齐。
        Map<Long, BigDecimal> submittedQuantityByDispenseId = new HashMap<>();
        for (SavePrepareDispenseLineSubmitDto submitLine : submitLines) {
            if (submitLine == null || submitLine.getDispenseId() == null) {
                continue;
            }
            submittedQuantityByDispenseId.put(submitLine.getDispenseId(), submitLine.getQuantity());
        }
        Set<Long> distinctMedicationRequestIds = new LinkedHashSet<>();
        for (MedicationDispense dispense : dispenseById.values()) {
            if (dispense.getMedReqId() != null) {
                distinctMedicationRequestIds.add(dispense.getMedReqId());
            }
        }
        for (Long medicationRequestId : distinctMedicationRequestIds) {
            MedicationRequestDetailQueryModel detail = medicationRequestQueryService.loadMedicationRequestById(medicationRequestId);
            // 仅临时医嘱存在「一单多发放分段」合计门禁；长期医嘱跳过。
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
                if (submittedQuantityByDispenseId.containsKey(sibling.getId())) {
                    quantityContribution = submittedQuantityByDispenseId.get(sibling.getId());
                }
                if (quantityContribution != null) {
                    sumQuantities = sumQuantities.add(quantityContribution);
                }
            }
            if (detail.getQuantity() != null && sumQuantities.compareTo(detail.getQuantity()) != 0) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_204_QUANTITY_MISMATCH, "临时医嘱拆分发放数量合计须与开立总量一致");
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
