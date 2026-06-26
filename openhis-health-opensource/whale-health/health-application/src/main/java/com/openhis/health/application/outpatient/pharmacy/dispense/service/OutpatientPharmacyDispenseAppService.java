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

package com.openhis.health.application.outpatient.pharmacy.dispense.service;

import com.openhis.health.module.common.assembler.option.FrequencyOptionLoader;
import com.openhis.health.module.common.assembler.option.TerminologyOptionLoader;
import com.openhis.health.module.common.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.health.module.common.pagination.PageQueryBinder;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.CancelMedicationDispenseLineSubmitDto;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.DispenseMedicationLineSubmitDto;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.SavePrepareDispenseLineSubmitDto;
import com.openhis.health.application.outpatient.pharmacy.dispense.mapper.OutpatientPharmacyDispenseMapper;
import com.openhis.health.application.outpatient.pharmacy.dispense.request.CancelOutpatientMedicationDispenseRequest;
import com.openhis.health.application.outpatient.pharmacy.dispense.request.DispenseOutpatientMedicationsRequest;
import com.openhis.health.application.outpatient.pharmacy.dispense.request.LoadPharmacyDispensePageInitRequest;
import com.openhis.health.application.outpatient.pharmacy.dispense.request.PageEncounterMedicationDispenseSlipsRequest;
import com.openhis.health.application.outpatient.pharmacy.dispense.request.PagePendingDispenseEncounterQueueRequest;
import com.openhis.health.application.outpatient.pharmacy.dispense.request.SavePrepareOutpatientMedicationDispenseRequest;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.CancelOutpatientMedicationDispenseResponse;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.DispenseOutpatientMedicationsResponse;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.LoadPharmacyDispensePageInitResponse;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.PageEncounterMedicationDispenseSlipsResponse;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.PagePendingDispenseEncounterQueueResponse;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.SavePrepareOutpatientMedicationDispenseResponse;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.integration.support.insurance.inventory.DispenseTraceGateCommandSupport;
import com.openhis.health.module.querysupport.administration.PractitionerRoleScopeLoader;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.module.common.assembler.product.ClinicalCatalogDispenseLocationResolveSupport;
import com.openhis.health.domain.medication.medicationdispense.command.CancelMedicationDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.command.DispenseMedicationCommand;
import com.openhis.health.domain.medication.medicationdispense.command.PrepareMedicationDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.query.OutpatientPharmacyPendingEncounterPageQuery;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.module.querysupport.pharmacy.EncounterMedicationDispenseSlipPageLoader;
import com.openhis.health.module.querysupport.pharmacy.EncounterMedicationDispenseSlipLoaderQuery;
import com.openhis.health.module.querysupport.pharmacy.PharmacyDispenseSlipQuerySupport;
import com.openhis.health.module.querysupport.pharmacy.PharmacyEncounterQueueLoader;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestDetailQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.ProductDefinitionDisplaySupport;
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
import com.openhis.health.domain.custom.tracenumberrecord.command.AppendTraceRecordsCommand;
import com.openhis.health.domain.custom.tracenumberrecord.service.TraceNumberRecordDomainService;
import com.openhis.health.domain.custom.tracenumberrecord.service.TraceNumberRecordQueryService;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.health.domain.share.enums.TraceNumberRecordOperationType;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.command.AllocateAndDecreaseCommand;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.health.integration.insurance.inventory.InventoryThirdPartyUploadExtension;
import com.openhis.health.integration.insurance.inventory.DispenseTraceGateExtension;
import com.openhis.health.integration.insurance.inventory.command.InventoryUploadAfterDispenseCommand;
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

    private final DispenseTraceGateExtension dispenseTraceGateExtension;

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
    private final EncounterContextFacade encounterContextFacade;
    private final LocationQueryService locationQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final PharmacyEncounterQueueLoader pharmacyEncounterQueueLoader;
    private final PharmacyDispenseSlipQuerySupport pharmacyDispenseSlipQuerySupport;
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final ClinicalCatalogDispenseLocationResolveSupport clinicalCatalogDispenseLocationResolveSupport;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final InventoryItemDomainService inventoryItemDomainService;
    private final TraceNumberRecordQueryService traceNumberRecordQueryService;
    private final TraceNumberRecordDomainService traceNumberRecordDomainService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final OutpatientPharmacyDispenseMapper outpatientPharmacyDispenseMapper;
    private final EncounterMedicationDispenseSlipPageLoader encounterMedicationDispenseSlipPageLoader;

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

            // 调配药师：须为药师岗且 adm_pr_assign.location_id 落在目标药房（请求指定或管辖内启用药房）。
            List<Long> preparerPharmacyLocationIds = new ArrayList<>();
            Long requestedPharmacyLocationId = request.getPharmacyLocationId();
            if (requestedPharmacyLocationId != null) {
                Set<Long> managedLocationIds = managedLocationIdsResolver.managedLocationIdSetOrEmpty();
                if (managedLocationIds.isEmpty() || managedLocationIds.contains(requestedPharmacyLocationId)) {
                    preparerPharmacyLocationIds.add(requestedPharmacyLocationId);
                }
            } else {
                for (Location pharmacy : pharmacies) {
                    if (pharmacy != null && pharmacy.getId() != null) {
                        preparerPharmacyLocationIds.add(pharmacy.getId());
                    }
                }
            }

            Page<Practitioner> preparerPage = practitionerRoleScopeLoader.pageActivePractitionersForPlatformRoleInLocations(
                WhalePlatformRoleCodeConstants.ROLE_PHARMACIST,
                preparerPharmacyLocationIds,
                StringUtils.EMPTY,
                pageable,
                false);

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
            encounterContextFacade.listOutpatientContextsByEncounterIdsOrdered(encounterPage.getContent());

        // Asm：按队列顺序回补门诊信息并封装分页响应。
        return outpatientPharmacyDispenseMapper.toQueuePage(encounterPage, infos, Map.of());
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
        // Asm：映射明细分页响应。
        return outpatientPharmacyDispenseMapper.toSlipPageResponse(
            encounterMedicationDispenseSlipPageLoader.pageOutpatientEncounterMedicationDispenseSlips(query));
    }

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
                    encounterContextFacade.getOutpatientContextByEncounterId(request.getEncounterId()).orElse(null);
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
            List<Long> candidateLocationIds = clinicalCatalogDispenseLocationResolveSupport.listApplicableCandidateLocationIds(
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
            traceCodeInputs.addAll(lineTraceCodes);
            if (dispenseTraceGateExtension.isEnabled()) {
                dispenseTraceGateExtension.assertTraceAllowedForPrepare(
                    DispenseTraceGateCommandSupport.from(dispense), lineTraceCodes);
            }
            if (!lineTraceCodes.isEmpty()) {
                traceNumberRecordQueryService.assertCodesInNetStockForDispenseLine(
                    TableNameConstants.MED_MEDICATION_DEFINITION,
                    dispense.getMedicationId(),
                    submitLine.getLotNumber(),
                    submitLine.getDispenseLocationId(),
                    lineTraceCodes);
            }

            if (productRow == null) {
                throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID, "缺少药品目录投影，无法按最小单位校验库存");
            }
            inventoryItemQueryService.assertSufficientForMedicationDispense(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                dispense.getMedicationId(),
                submitLine.getDispenseLocationId(),
                submitLine.getLotNumber(),
                submitLine.getQuantity(),
                dispense.getUnitCode(),
                ProductDefinitionDisplaySupport.toUnitConversionSource(productRow));
        }

        assertPrepareChargeTotalMatchesInventoryLotPricing(
            request.getEncounterId(),
            submittedDispenseIds,
            request.getLines(),
            dispenseById);

        traceNumberRecordQueryService.assertNoDuplicateTraceCodes(traceCodeInputs);
        traceNumberRecordQueryService.assertCodesNotAlreadyOutbound(traceCodeInputs);

        assertTemporaryTherapyQuantities(request.getLines(), dispenseById);

        PrepareMedicationDispenseCommand prepareMedicationDispenseCommand =
            outpatientPharmacyDispenseMapper.toPrepareMedicationDispenseCommand(
                operatorPractitionerId, request.getPreparerPractitionerId(), request.getLines());
        medicationDispenseDomainService.prepareMedicationDispense(prepareMedicationDispenseCommand);
        return outpatientPharmacyDispenseMapper.toPrepareBatchResponse(submittedDispenseIds, "保存配药成功", true);
    }

    /**
     * 核发（领域推进 {@code COMPLETED} 后同一事务内按最小单位扣减 {@code wkf_inventory_item}）；医保上传为事务外协作占位，失败不回滚本地事实。
     *
     * @param request 就诊与勾选发放行
     * @return 处理结果；若开启医保开关且上传失败，仅在 {@code message} 中合并告警文案
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
            productDefinitionBatchLoader.loadAsMap(dispenseProductLookups);

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

        for (MedicationDispense dispense : dispensersInSubmitOrder) {
            if (dispenseTraceGateExtension.isEnabled()) {
                dispenseTraceGateExtension.assertTraceAllowedForDispense(
                    DispenseTraceGateCommandSupport.from(dispense));
            }
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
        appendOutTraceRecordsForDispensedOutpatientLines(dispensersInSubmitOrder, operatorPractitionerId);

        DispenseOutpatientMedicationsResponse response =
            outpatientPharmacyDispenseMapper.toDispenseBatchResponse(submittedDispenseIds, "发药成功", true);
        try {
            if (inventoryUploadExtension.isEnabled()) {
                InventoryUploadAfterDispenseCommand uploadCommand = new InventoryUploadAfterDispenseCommand()
                    .setEncounterId(request.getEncounterId())
                    .setDispenseIds(submittedDispenseIds);
                inventoryUploadExtension.uploadAfterDispense(uploadCommand);
            }
        } catch (RuntimeException ex) {
            log.warn("医保进销存3505上传失败（本地发药已成功）", ex);
            String mergedMessage = "发药成功；" + ErrorCodeConstants.EX_OPP_207_MEDICARE_PARTIAL_FAILURE + "：" + ex.getMessage();
            response.setMessage(mergedMessage);
        }
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
        Map<String, ProductDefinitionDisplayDto> map = productDefinitionBatchLoader.loadAsMap(List.of(lookup));
        String key = ProductDefinitionKeyUtil.buildCompositeKey(TableNameConstants.MED_MEDICATION_DEFINITION, medicationId);
        return map.get(key);
    }

    /**
     * 发药成功后写入追溯码 OUT 事实（BR-INV-TRC-005 数据源）。
     */
    private void appendOutTraceRecordsForDispensedOutpatientLines(
        List<MedicationDispense> dispensersInSubmitOrder,
        Long operatorPractitionerId
    ) {
        if (dispensersInSubmitOrder == null || dispensersInSubmitOrder.isEmpty()) {
            return;
        }
        List<AppendTraceRecordsCommand.AppendTraceRecordItemCommand> items = new ArrayList<>();
        for (MedicationDispense dispense : dispensersInSubmitOrder) {
            if (dispense == null || StringUtils.isBlank(dispense.getTraceNo())) {
                continue;
            }
            for (String traceNo : splitTraceCodes(dispense.getTraceNo())) {
                AppendTraceRecordsCommand.AppendTraceRecordItemCommand item =
                    new AppendTraceRecordsCommand.AppendTraceRecordItemCommand();
                item.setItemTable(TableNameConstants.MED_MEDICATION_DEFINITION);
                item.setItemId(dispense.getMedicationId());
                item.setLocationTypeEnum(LocationForm.PHARMACY.getCode());
                item.setLocationId(dispense.getLocationId());
                item.setLotNumber(dispense.getLotNumber());
                item.setTraceNo(traceNo);
                item.setStatusEnum(TraceDirection.OUT.getCode());
                item.setUnitCode(dispense.getUnitCode());
                item.setOperationType(TraceNumberRecordOperationType.MEDICATION_DISPENSE_OUT.getCode());
                item.setBusNo(dispense.getBusNo());
                items.add(item);
            }
        }
        if (items.isEmpty()) {
            return;
        }
        AppendTraceRecordsCommand command = new AppendTraceRecordsCommand();
        command.setOperatorId(operatorPractitionerId);
        command.setItems(items);
        traceNumberRecordDomainService.appendTraceRecords(command);
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
     * 保存配药前金额门禁：已存在的收费明细（按发放单锚点）总价须等于「批号售价 {@code price_value} × 发放业务单位数量」按提交行重算之和。
     *
     * <p>单价取自 {@code adm_charge_item_definition} 中 {@code LOT_NUMBER_PRICE} 且与物品表、药品定义主键、批号一致的 {@code price_value}；
     * 同键多条时按 {@code priority} 更小优先、同优先级按主键更小择优。
     * 数量口径须与签发费用草稿 {@link com.openhis.health.application.outpatient.doctorstation.advice.mapper.DoctorStationAdviceMapperImpl#toCreateChargeItemDraftItemFromOrder} 一致：
     * 使用提交行在 {@link MedicationDispense#getUnitCode()} 下的数量（与 FIFO 分段写入 {@code MedicationDispense#quantity} 同语义），<strong>禁止</strong>先换算为拆零最小单位再乘单价——否则包装/采购等单位下会把金额放大 {@code package_unit_ratio} 等倍数，与已落库 {@code adm_charge_item} 总额不一致。</p>
     *
     * <p>若就诊下尚无对应收费行（未签发计费等），跳过本校验。</p>
     *
     * @param encounterId      就诊标识
     * @param submittedDispenseIds 本批次涉及发放单主键（与提交行一致）
     * @param submitLines      配药提交行
     * @param dispenseById     发放单实体映射
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
        BigDecimal recomputedTotal = BigDecimal.ZERO;
        for (SavePrepareDispenseLineSubmitDto submitLine : submitLines) {
            if (submitLine == null || submitLine.getDispenseId() == null) {
                continue;
            }
            MedicationDispense dispense = dispenseById.get(submitLine.getDispenseId());
            if (dispense == null) {
                continue;
            }
            BigDecimal lotUnitPrice = requireLotNumberPriceValueFromChargeDefinition(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                dispense.getMedicationId(),
                submitLine.getLotNumber());
            BigDecimal qtyInDispenseUnit = submitLine.getQuantity();
            recomputedTotal = recomputedTotal.add(qtyInDispenseUnit.multiply(lotUnitPrice));
        }
        BigDecimal baselineRounded = baselineTotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal recomputedRounded = recomputedTotal.setScale(2, RoundingMode.HALF_UP);
        if (baselineRounded.compareTo(recomputedRounded) != 0) {
            throw new BusinessException(
                ErrorCodeConstants.EX_OPP_204_QUANTITY_MISMATCH,
                "按定价表批号售价重算的总金额与当前收费明细不一致，不能保存配药");
        }
    }

    /**
     * 从 {@code adm_charge_item_definition} 取药品批号维销售单价（{@code LOT_NUMBER_PRICE}），与物品表、定义主键、批号精确匹配。
     *
     * <p>排除逻辑删除行；多条命中时按 {@code priority} 更小优先、同优先级按主键更小择优（与医生站目录定价择优一致）。</p>
     *
     * @param instanceTable 物品定义物理表名（如 {@link TableNameConstants#MED_MEDICATION_DEFINITION}）
     * @param medicationId    药品定义主键，对应 {@code instance_id}
     * @param lotNumber       批号，对应 {@code lot_no}
     * @return {@code price_value}，须大于 0
     */
    private BigDecimal requireLotNumberPriceValueFromChargeDefinition(String instanceTable, Long medicationId,
        String lotNumber) {
        if (StringUtils.isBlank(instanceTable)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "批号售价查询缺少物品表名");
        }
        if (medicationId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "批号售价查询缺少药品定义主键");
        }
        if (StringUtils.isBlank(lotNumber)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT,
                "发放批号为空时无法从定价表解析批号售价，请先执行保存配药并选定批号");
        }
        ChargeItemDefinitionQueryCommand cmd = new ChargeItemDefinitionQueryCommand();
        cmd.setInstanceTable(instanceTable.trim());
        cmd.setInstanceId(medicationId);
        cmd.setLotNo(lotNumber.trim());
        List<ChargeItemDefinition> rows = chargeItemDefinitionQueryService.findLotNumberPriceByInstanceIdAndLot(cmd);
        ChargeItemDefinition best = null;
        for (ChargeItemDefinition row : rows) {
            if (row == null || Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            best = pickBetterChargeItemDefinitionPriceLine(best, row);
        }
        if (best == null || best.getPriceValue() == null || best.getPriceValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID,
                "定价表未维护该批号的有效批号售价（LOT_NUMBER_PRICE），无法按批号复核与账单一致的总金额");
        }
        return best.getPriceValue();
    }

    /**
     * 在两条定价行中取优先级更小者（同优先级时主键更小者），{@code null} 优先级视为较大。
     */
    private static ChargeItemDefinition pickBetterChargeItemDefinitionPriceLine(ChargeItemDefinition current,
        ChargeItemDefinition candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        Integer cp = candidate.getPriority();
        Integer curp = current.getPriority();
        if (cp != null && curp != null) {
            int cmp = Integer.compare(cp, curp);
            if (cmp < 0) {
                return candidate;
            }
            if (cmp > 0) {
                return current;
            }
        } else if (cp != null) {
            return candidate;
        } else if (curp != null) {
            return current;
        }
        Long cid = candidate.getId();
        Long curid = current.getId();
        if (cid != null && curid != null && cid < curid) {
            return candidate;
        }
        return current;
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
}
