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

package com.openhis.health.application.inpatient.pharmacy.dispense.service;

import com.openhis.health.application.common.assembler.advice.InpatientMedicationRequestExecutionMeasureSupport;
import com.openhis.health.application.common.assembler.option.OptionDtoAssembler;
import com.openhis.health.application.common.assembler.option.TerminologyOptionLoader;
import com.openhis.health.application.common.pagination.PageQueryBinder;
import com.openhis.health.application.common.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.common.validator.CurrentOperatorResolver;
import com.openhis.health.application.common.validator.PageWindowGate;
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
import com.openhis.health.application.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.custom.tracenumberrecord.service.TraceNumberRecordQueryService;
import com.openhis.health.domain.medication.medicationdispense.command.CancelMedicationDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.command.DispenseMedicationCommand;
import com.openhis.health.domain.medication.medicationdispense.command.PrepareMedicationDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.query.InpatientPharmacyPendingEncounterPageQuery;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.application.querysupport.pharmacy.PharmacyDispenseSlipQuerySupport;
import com.openhis.health.application.querysupport.pharmacy.PharmacyEncounterQueueLoader;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestDetailQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseSlipPageQuery;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.application.querysupport.product.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.domain.share.enums.PractitionerBusinessRoleCode;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.command.AllocateAndDecreaseCommand;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
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
 * 住院发药工作站应用编排。
 *
 * <p>待发队列：按管辖药房筛选有待发药的在院患者。</p>
 * <p>配药/发药：保存调配草稿 → 确认发药扣减库存；支持按行作废未发药记录。</p>
 * <p>不发候选库存；汇总发药、退药与医保上传不在本服务范围。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpatientPharmacyDispenseAppService {

    private final ICurrentUser currentUser;
    private final CurrentOperatorResolver currentOperatorResolver;
    private final PageWindowGate pageWindowGate;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    private final OrganizationQueryService organizationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final CodeSystemQueryService codeSystemQueryService;
    private final TerminologyOptionLoader terminologyOptionLoader;
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextFacade encounterContextFacade;
    private final LocationQueryService locationQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final PharmacyEncounterQueueLoader pharmacyEncounterQueueLoader;
    private final PharmacyDispenseSlipQuerySupport pharmacyDispenseSlipQuerySupport;
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final InventoryItemDomainService inventoryItemDomainService;
    private final TraceNumberRecordQueryService traceNumberRecordQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final InpatientPharmacyDispenseMapper inpatientPharmacyDispenseMapper;

    /**
     *
     * @param request 分页与可选岗位过滤；药房机构由会话解析
     * @return 下拉与药师候选分页
     * @throws BusinessException {@code EX_OPP_101}：未登录或元数据装载失败
     */
    public LoadInpatientPharmacyDispensePageInitResponse loadInpatientPharmacyDispensePageInit(
        LoadInpatientPharmacyDispensePageInitRequest request) {
        if (!currentUser.isAuthenticated()) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_101_INIT_METADATA_FAILED, "未登录或会话无效，无法加载发药页元数据");
        }
        if (request == null) {
            request = new LoadInpatientPharmacyDispensePageInitRequest();
        }
        try {
            List<Organization> pharmacies = organizationQueryService.getOrganizationTreeForPharmacy();
            List<OptionDto> dispenseStatusOptions = codeSystemQueryService.getOptionListByEnum(DispenseStatus.class);
            List<OptionDto> notPerformedReasonOptions = new ArrayList<>();
            List<OptionDto> wardOptions = new ArrayList<>();
            Long organizationUnitId = currentUser.getCurrentOrganizationUnitId();
            if (organizationUnitId != null) {
                List<Location> wards = locationQueryService.listWardsByOrganization(organizationUnitId);
                wardOptions = OptionDtoAssembler.toOptions(wards, Location::getId, Location::getName, 0L);
            }
            int pageIndex = request.springPageIndex();
            int pageSize = request.getBoundedPageSize();
            Pageable pageable = PageRequest.of(pageIndex, pageSize);
            Page<Practitioner> preparerPage;
            if (request.getPractitionerRoleFilterCode() != null) {
                preparerPage = practitionerQueryService.pageActivePractitionersForPosition(
                    request.getPractitionerRoleFilterCode(), StringUtils.EMPTY, pageable, false);
            } else {
                preparerPage = practitionerQueryService.pageActivePractitionersForPosition(
                    PractitionerBusinessRoleCode.DOCTOR.getNumericCode(), StringUtils.EMPTY, pageable, false);
            }
            return inpatientPharmacyDispenseMapper.toLoadInitResponse(
                pharmacies, dispenseStatusOptions, notPerformedReasonOptions, wardOptions, preparerPage);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_101_INIT_METADATA_FAILED, "发药页元数据加载失败：" + ex.getMessage());
        }
    }

    /**
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
        if (!currentUser.isAuthenticated()) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_102_QUEUE_QUERY_FAILED, "未登录或会话无效，无法查询队列");
        }
        pageWindowGate.requirePositivePageWindow(
            request, ErrorCodeConstants.EX_OPP_102_QUEUE_QUERY_FAILED, "分页参数不合法");
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
            encounterContextFacade.listInpatientContextsByEncounterIdsOrdered(encounterPage.getContent());
        return inpatientPharmacyDispenseMapper.toInpatientQueuePage(encounterPage, infos);
    }

    /**
     *
     * @return 明细分页
     * @throws BusinessException {@code EX_OPP_103}：参数/就诊/分页非法
     */
    public PageEncounterMedicationDispenseSlipsResponse pageEncounterMedicationDispenseSlips(
        PageEncounterMedicationDispenseSlipsRequest request) {
        if (request == null) {
            request = new PageEncounterMedicationDispenseSlipsRequest();
        }
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
        String encounterBusNo = null;
        InpatientEncounterContextQueryModel inpatientContext =
            encounterContextFacade.getInpatientContextByEncounterId(request.getEncounterId()).orElse(null);
        if (inpatientContext != null) {
            encounterBusNo = inpatientContext.getEncounterBusNo();
        }
        MedicationDispenseSlipPageQuery slipQuery = new MedicationDispenseSlipPageQuery();
        slipQuery.setEncounterId(request.getEncounterId());
        slipQuery.setLocationId(request.getLocationId());
        slipQuery.setInpatientMedicationOnly(true);
        if (request.getDispenseStatusEnumIn() != null && !request.getDispenseStatusEnumIn().isEmpty()) {
            slipQuery.setDispenseStatusEnumIn(request.getDispenseStatusEnumIn());
        }
        PageQueryBinder.bind(request, slipQuery);
        this.pharmacyDispenseSlipQuerySupport.enrichResolvedFilters(slipQuery);
        Page<MedicationDispenseSlipQueryModel> slipPage =
            medicationDispenseQueryService.pageMedicationDispenseSlip(slipQuery);
        List<MedicationRequestListQueryModel> medicationRequestRows =
            medicationRequestQueryService.listMedicationRequestsByEncounter(request.getEncounterId());
        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId = new HashMap<>();
        for (MedicationRequestListQueryModel row : medicationRequestRows) {
            if (row.getId() != null) {
                medicationRequestByMedReqId.put(row.getId(), row);
            }
        }
        List<ProductDefinitionBatchLookup> productLookups = new ArrayList<>();
        for (MedicationDispenseSlipQueryModel slipRow : slipPage.getContent()) {
            if (slipRow == null || slipRow.getMedicationId() == null) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(slipRow.getMedicationId());
            productLookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> productRowsByKey =
            productDefinitionBatchLoader.loadAsMap(productLookups);
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId = new HashMap<>();
        for (MedicationDispenseSlipQueryModel slipRow : slipPage.getContent()) {
            if (slipRow == null || slipRow.getMedicationId() == null) {
                continue;
            }
            String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, slipRow.getMedicationId());
            ProductDefinitionDisplayDto productRow = productRowsByKey.get(compositeKey);
            if (productRow != null) {
                productByMedicationId.put(slipRow.getMedicationId(), productRow);
            }
        }
        Set<Long> dispenserPractitionerIds = new LinkedHashSet<>();
        Set<Long> orderingPractitionerIds = new LinkedHashSet<>();
        Set<Long> orderingDepartmentOrganizationIds = new LinkedHashSet<>();
        Set<Long> dispenseLocationIds = new LinkedHashSet<>();
        for (MedicationDispenseSlipQueryModel slipRow : slipPage.getContent()) {
            if (slipRow == null) {
                continue;
            }
            if (slipRow.getDispenserId() != null) {
                dispenserPractitionerIds.add(slipRow.getDispenserId());
            }
            if (slipRow.getMedRequesterId() != null) {
                orderingPractitionerIds.add(slipRow.getMedRequesterId());
            }
            MedicationRequestListQueryModel mr = medicationRequestByMedReqId.get(slipRow.getMedReqId());
            if (mr != null && mr.getPerformLocation() != null) {
                orderingDepartmentOrganizationIds.add(mr.getPerformLocation());
            }
            if (slipRow.getLocationId() != null) {
                dispenseLocationIds.add(slipRow.getLocationId());
            }
        }
        Map<Long, String> dispensingPractitionerNameByDispenseId = new HashMap<>();
        Map<Long, Practitioner> dispenserById = loadPractitionersByIds(dispenserPractitionerIds);
        for (MedicationDispenseSlipQueryModel slipRow : slipPage.getContent()) {
            if (slipRow == null || slipRow.getDispenseId() == null || slipRow.getDispenserId() == null) {
                continue;
            }
            Practitioner dispenser = dispenserById.get(slipRow.getDispenserId());
            if (dispenser != null) {
                dispensingPractitionerNameByDispenseId.put(slipRow.getDispenseId(), dispenser.getName());
            }
        }
        Map<Long, Practitioner> orderingById = loadPractitionersByIds(orderingPractitionerIds);
        Map<Long, String> orderingPractitionerNameById = new HashMap<>();
        for (Long practitionerId : orderingPractitionerIds) {
            Practitioner p = orderingById.get(practitionerId);
            if (p != null) {
                orderingPractitionerNameById.put(practitionerId, p.getName());
            }
        }
        Map<Long, String> orderingDepartmentNameByOrgId =
            organizationQueryService.mapNamesByIds(orderingDepartmentOrganizationIds);
        Map<Long, String> dispenseLocationNameByLocationId = locationQueryService.mapNamesByIds(dispenseLocationIds);
        List<Long> slipDispenseIds = new ArrayList<>();
        for (MedicationDispenseSlipQueryModel slipRow : slipPage.getContent()) {
            if (slipRow != null && slipRow.getDispenseId() != null) {
                slipDispenseIds.add(slipRow.getDispenseId());
            }
        }
        List<MedicationDispense> dispenseEntitiesForTrace =
            medicationDispenseQueryService.listMedicationDispensesByIds(slipDispenseIds);
        Map<Long, String> traceNoEffectiveByDispenseId = new HashMap<>();
        Map<Long, String> lotNumberEffectiveByDispenseId = new HashMap<>();
        for (MedicationDispense dispenseEntity : dispenseEntitiesForTrace) {
            if (dispenseEntity == null || dispenseEntity.getId() == null) {
                continue;
            }
            if (StringUtils.isNotBlank(dispenseEntity.getTraceNo())) {
                traceNoEffectiveByDispenseId.put(dispenseEntity.getId(), dispenseEntity.getTraceNo().trim());
            }
            if (StringUtils.isNotBlank(dispenseEntity.getLotNumber())) {
                lotNumberEffectiveByDispenseId.put(dispenseEntity.getId(), dispenseEntity.getLotNumber().trim());
            }
        }
        List<ChargeItem> chargeRowsForSlip =
            chargeItemQueryService.listChargeItemsForMedicationDispensePricing(
                request.getEncounterId(), slipDispenseIds);
        Map<Long, BigDecimal> chargeUnitPriceByDispenseId = new HashMap<>();
        Map<Long, BigDecimal> chargeTotalPriceByDispenseId = new HashMap<>();
        mapChargePricingByDispenseId(chargeRowsForSlip, chargeUnitPriceByDispenseId, chargeTotalPriceByDispenseId);
        Map<String, String> rateLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.RATE_CODE);
        Map<String, String> methodLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE);
        Map<String, String> doseUnitLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSE_UNIT);
        Map<String, String> unitLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        Map<String, String> partAttributeLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.PART_ATTRIBUTE_ENUM);
        Map<String, String> rxFlagLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.RX_FLAG);
        return inpatientPharmacyDispenseMapper.toSlipPage(
            slipPage,
            medicationRequestByMedReqId,
            productByMedicationId,
            request.getTcmOnly(),
            request.getWesternOnly(),
            dispensingPractitionerNameByDispenseId,
            encounterBusNo,
            orderingDepartmentNameByOrgId,
            orderingPractitionerNameById,
            dispenseLocationNameByLocationId,
            traceNoEffectiveByDispenseId,
            lotNumberEffectiveByDispenseId,
            chargeUnitPriceByDispenseId,
            chargeTotalPriceByDispenseId,
            rateLabelByCode,
            methodLabelByCode,
            doseUnitLabelByCode,
            unitLabelByCode,
            rxFlagLabelByCode,
            partAttributeLabelByCode);
    }

    /**
     *
     * @param request 就诊、调配药师与提交行（仅 dispenseId、可选追溯码）
     * @return 处理结果
     * @throws BusinessException {@code EX_OPP_201}～{@code EX_OPP_205}：门控或前置校验失败
     */
    public SavePrepareInpatientMedicationDispenseResponse savePrepareInpatientMedicationDispense(
        SavePrepareInpatientMedicationDispenseRequest request) {
        if (request.getPreparerPractitionerId() == null || request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_202_PREPARER_INVALID, "调配药师或配药行不能为空");
        }
        Long operatorUserId = currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.EX_OPP_202_PREPARER_INVALID, "未登录用户不能保存配药");
        Practitioner preparerPractitioner = practitionerQueryService.findById(request.getPreparerPractitionerId());
        if (preparerPractitioner == null) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_202_PREPARER_INVALID, "调配药师不存在或已停用");
        }
        // 过渡：暂关闭 BR-IPP-004（调配药师≠当前登录用户），便于联调；恢复时取消注释。
        /*
        Practitioner loggedInPractitioner = practitionerQueryService.findByUserId(operatorUserId);
        if (loggedInPractitioner != null
            && Objects.equals(loggedInPractitioner.getId(), request.getPreparerPractitionerId())) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_202_PREPARER_INVALID, "调配药师不能与当前登录用户为同一人");
        }
        */
        encounterQueryService.mustGetEncounter(request.getEncounterId());
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
            traceCodeInputs.addAll(splitTraceCodes(submitLine.getTraceNo()));
            ProductDefinitionDisplayDto productRow = loadProductForMedication(dispense.getMedicationId());
            if (productRow == null) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID, "缺少药品目录投影，无法校验库存");
            }
            // 长期：quantity/unit_code=一次执行发药量/单位；临时：总量由 BR-IPP-006 合计，此处仅校单位。
            if (TherapyType.LONG_TERM.getCode().equals(medicationRequestDetail.getTherapyEnum())) {
                InpatientMedicationRequestExecutionMeasureSupport.assertLongTermDispenseMatchesPerExecutionRequest(
                    medicationRequestDetail, dispenseQty, dispense.getUnitCode(), productRow);
            } else {
                InpatientMedicationRequestExecutionMeasureSupport.assertTemporaryDispenseUnitMatchesRequest(
                    medicationRequestDetail, dispense.getUnitCode(), productRow);
            }
            inventoryItemQueryService.assertSufficientForMedicationDispense(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                dispense.getMedicationId(),
                dispense.getLocationId(),
                dispense.getLotNumber(),
                dispenseQty,
                dispense.getUnitCode(),
                ProductDefinitionDisplaySupport.toUnitConversionSource(productRow));
        }
        traceNumberRecordQueryService.assertCodesNotAlreadyOutbound(traceCodeInputs);
        assertTemporaryTherapyQuantitiesForInpatient(dispenseById);
        PrepareMedicationDispenseCommand prepareCommand = inpatientPharmacyDispenseMapper.toPrepareMedicationDispenseCommand(
            operatorUserId, request.getPreparerPractitionerId(), request.getLines(), dispenseById);
        medicationDispenseDomainService.prepareMedicationDispense(prepareCommand);
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
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED, "发药行不能为空");
        }
        Long operatorUserId = currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED, "未登录用户不能发药");
        Practitioner dispenserPractitioner = practitionerQueryService.findByUserId(operatorUserId);
        if (dispenserPractitioner == null) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED, "当前用户未绑定医务主档，不能作为发药人");
        }
        encounterQueryService.mustGetEncounter(request.getEncounterId());
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
            productDefinitionBatchLoader.loadAsMap(dispenseProductLookups);
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
            inventoryItemQueryService.assertSufficientForMedicationDispense(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                dispense.getMedicationId(),
                dispense.getLocationId(),
                dispense.getLotNumber(),
                requiredQuantity,
                dispense.getUnitCode(),
                ProductDefinitionDisplaySupport.toUnitConversionSource(productRowForStockAssert));
            dispensersInSubmitOrder.add(dispense);
        }
        DispenseMedicationCommand dispenseCommand = inpatientPharmacyDispenseMapper.toDispenseCommand(
            operatorUserId, dispenserPractitioner.getId(), dispensersInSubmitOrder);
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
        applyInventoryDeductionForDispensedLines(dispensersInSubmitOrder, operatorUserId, productByMedicationIdForDispense);
        return inpatientPharmacyDispenseMapper.toDispenseBatchResponse(submittedDispenseIds, "发药成功", true);
    }

    /**
     *
     * @param request 就诊、发放行与未执行原因码
     * @return 处理结果
     * @throws BusinessException {@code EX_OPP_208}：参数或状态不允许
     */
    public CancelInpatientMedicationDispenseResponse cancelInpatientMedicationDispense(
        CancelInpatientMedicationDispenseRequest request) {
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_208_CANCEL_NOT_ALLOWED, "作废行不能为空");
        }
        for (CancelMedicationDispenseLineSubmitDto cancelLine : request.getLines()) {
            if (cancelLine == null || StringUtils.isBlank(cancelLine.getNotPerformedReasonEnum())) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_208_CANCEL_NOT_ALLOWED, "作废原因不能为空");
            }
        }
        Long operatorUserId = currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.EX_OPP_208_CANCEL_NOT_ALLOWED, "未登录用户不能作废发放");
        encounterQueryService.mustGetEncounter(request.getEncounterId());
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
            boolean cancellable = DispenseStatus.PREPARATION.equals(statusEnum)
                || DispenseStatus.IN_PROGRESS.equals(statusEnum);
            if (!cancellable) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_208_CANCEL_NOT_ALLOWED, "当前状态不允许作废");
            }
        }
        CancelMedicationDispenseCommand cancelCommand =
            inpatientPharmacyDispenseMapper.toCancelMedicationDispenseCommand(operatorUserId, request.getLines());
        medicationDispenseDomainService.cancelMedicationDispense(cancelCommand);
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
            productDefinitionBatchLoader.loadAsMap(List.of(lookup));
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
     * BR-IPP-006：临时医嘱拆分行数量合计须与开立量一致（数量取自聚合持久化值）。
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
                medicationDispenseQueryService.listMedicationDispensesByMedReqIds(List.of(medicationRequestId));
            BigDecimal sumQuantities = BigDecimal.ZERO;
            for (MedicationDispense sibling : siblingDispenses) {
                if (DispenseStatus.DECLINED.equals(sibling.getStatusEnum())
                    || DispenseStatus.COMPLETED.equals(sibling.getStatusEnum())) {
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
     *
     * @param dispensersInSubmitOrder    已完成领域核发的快照行
     * @param operatorUserId             操作用户 id
     * @param productByMedicationId      药品 id → 产品读模型
     */
    private void applyInventoryDeductionForDispensedLines(
        List<MedicationDispense> dispensersInSubmitOrder,
        Long operatorUserId,
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
        inventoryItemDomainService.decreaseQuantitiesMerged(operatorUserId, inventoryIdToMergedDecrease);
        for (Map.Entry<String, BigDecimal> allocateEntry : mergedAllocateRequiredByKey.entrySet()) {
            String[] allocateParts = allocateEntry.getKey().split(Pattern.quote(sep), -1);
            if (allocateParts.length != 3) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID, "内部分配合并键解析失败");
            }
            AllocateAndDecreaseCommand allocateCommand = new AllocateAndDecreaseCommand();
            allocateCommand.setOperatorId(operatorUserId);
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
}
