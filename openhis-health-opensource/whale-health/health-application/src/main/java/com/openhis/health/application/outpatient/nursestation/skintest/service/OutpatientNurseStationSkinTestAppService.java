/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.nursestation.skintest.service;

import com.openhis.health.module.common.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.health.module.common.validator.PatientEncounterConsistencyGate;
import com.openhis.health.application.outpatient.nursestation.skintest.assembler.OutpatientSkinTestInitAssembler;
import com.openhis.health.application.outpatient.nursestation.skintest.assembler.OutpatientSkinTestListAssembler;
import com.openhis.health.application.outpatient.nursestation.skintest.dto.OutpatientSkinTestListAssemblyInput;
import com.openhis.health.application.outpatient.nursestation.skintest.dto.OutpatientSkinTestLotOptionDto;
import com.openhis.health.application.outpatient.nursestation.skintest.dto.OutpatientSkinTestRowDto;
import com.openhis.health.application.outpatient.nursestation.skintest.mapper.OutpatientSkinTestMapper;
import com.openhis.health.application.outpatient.nursestation.skintest.request.DeleteOutpatientSkinTestRecordRequest;
import com.openhis.health.application.outpatient.nursestation.skintest.request.ListOutpatientSkinTestRowsRequest;
import com.openhis.health.application.outpatient.nursestation.skintest.request.LoadOutpatientSkinTestInitRequest;
import com.openhis.health.application.outpatient.nursestation.skintest.request.SaveOutpatientSkinTestRecordRequest;
import com.openhis.health.application.outpatient.nursestation.skintest.response.DeleteOutpatientSkinTestRecordResponse;
import com.openhis.health.application.outpatient.nursestation.skintest.response.ListOutpatientSkinTestRowsResponse;
import com.openhis.health.application.outpatient.nursestation.skintest.response.LoadOutpatientSkinTestInitResponse;
import com.openhis.health.application.outpatient.nursestation.skintest.response.SaveOutpatientSkinTestRecordResponse;
import com.openhis.health.module.querysupport.administration.PractitionerRoleScopeLoader;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.allergyintolerance.command.DeleteSkinTestRecordCommand;
import com.openhis.health.domain.clinical.allergyintolerance.command.SaveSkinTestRecordCommand;
import com.openhis.health.domain.clinical.allergyintolerance.queries.model.AllergyIntoleranceRecordQueryModel;
import com.openhis.health.domain.clinical.allergyintolerance.service.AllergyIntoleranceDomainService;
import com.openhis.health.domain.clinical.allergyintolerance.service.AllergyIntoleranceQueryService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.OutpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊护士站皮试应用编排（IF-OP-STK-01～04）。
 *
 * <p>接入说明：由 AppServiceControllerRegistrar 动态注册为
 * {@code POST /api/app/outpatientNurseStationSkinTest/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutpatientNurseStationSkinTestAppService {

    private final OutpatientSkinTestInitAssembler outpatientSkinTestInitAssembler;
    private final OutpatientSkinTestListAssembler outpatientSkinTestListAssembler;
    private final OutpatientSkinTestMapper outpatientSkinTestMapper;
    private final EncounterQueryService encounterQueryService;
    private final PatientEncounterConsistencyGate patientEncounterConsistencyGate;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final AllergyIntoleranceQueryService allergyIntoleranceQueryService;
    private final AllergyIntoleranceDomainService allergyIntoleranceDomainService;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final PractitionerQueryService practitionerQueryService;
    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;

    /**
     * IF-OP-STK-01：加载皮试表单枚举下拉与护士候选。
     *
     * @param request 可空
     * @return 四类枚举选项与护士下拉
     */
    public LoadOutpatientSkinTestInitResponse loadOutpatientSkinTestInit(LoadOutpatientSkinTestInitRequest request) {
        try {
            // Pre：枚举选项与当前机构下激活护士岗人员
            LoadOutpatientSkinTestInitResponse response = outpatientSkinTestInitAssembler.assembleInitOptions();
            Long organizationId = managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery();
            List<Practitioner> nurses = practitionerRoleScopeLoader.listActivePractitionersForPlatformRoleInOrganization(
                WhalePlatformRoleCodeConstants.ROLE_NURSE, organizationId);
            response.setNurseOptions(outpatientSkinTestMapper.toNurseOptionList(nurses));
            // Asm：透传初始化响应
            return outpatientSkinTestMapper.toInitResponse(response);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("loadOutpatientSkinTestInit failed", ex);
            throw new BusinessException(OutpatientNurseStationConstants.EX_OP_STK_101, "皮试表单选项加载失败，请刷新");
        }
    }

    /**
     * IF-OP-STK-02：按就诊查询皮试列表（皮试检查 SR 已收费 COMPLETED）。
     *
     * @param request 就诊与患者主键
     * @return 皮试列表行
     */
    public ListOutpatientSkinTestRowsResponse listOutpatientSkinTestRows(ListOutpatientSkinTestRowsRequest request) {
        try {
            if (request == null || request.getEncounterId() == null) {
                throw new BusinessException(OutpatientNurseStationConstants.EX_OP_STK_102, "请先选择患者或刷新列表");
            }
            EncounterDetailQueryModel encounter = this.gateEncounterForSkinTest(
                request.getEncounterId(),
                request.getPatientId());

            Long skinTestActivityId = activityDefinitionQueryService.requireIdByExactName(
                GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME);

            List<ServiceRequestListModel> skinTestServiceRequests =
                this.filterSkinTestCompletedServiceRequests(
                    serviceRequestQueryService.listServiceRequestsByEncounter(request.getEncounterId()),
                    skinTestActivityId);

            Set<Long> medicationRequestIds = new LinkedHashSet<>();
            for (ServiceRequestListModel serviceRequest : skinTestServiceRequests) {
                if (serviceRequest.getBasedOnId() != null) {
                    medicationRequestIds.add(serviceRequest.getBasedOnId());
                }
            }

            Map<Long, MedicationRequest> medicationRequestById = new HashMap<>();
            List<MedicationRequest> medicationRequests = medicationRequestQueryService.listByIds(medicationRequestIds);
            for (MedicationRequest medicationRequest : medicationRequests) {
                if (medicationRequest != null && medicationRequest.getId() != null) {
                    medicationRequestById.put(medicationRequest.getId(), medicationRequest);
                }
            }

            Map<Long, Long> locationIdByMedicationRequestId =
                medicationDispenseQueryService.mapMedicationRequestToPrimaryDispenseLocationId(medicationRequestIds);

            Map<Long, AllergyIntoleranceRecordQueryModel> allergyByServiceRequestId = new HashMap<>();
            List<AllergyIntoleranceRecordQueryModel> allergyRecords =
                allergyIntoleranceQueryService.getRecordInfo(request.getEncounterId());
            for (AllergyIntoleranceRecordQueryModel allergyRecord : allergyRecords) {
                if (allergyRecord != null && allergyRecord.getRequestId() != null) {
                    allergyByServiceRequestId.put(allergyRecord.getRequestId(), allergyRecord);
                }
            }

            Set<Long> medicationIds = new LinkedHashSet<>();
            Set<Long> locationIds = new LinkedHashSet<>();
            for (ServiceRequestListModel serviceRequest : skinTestServiceRequests) {
                MedicationRequest medicationRequest = medicationRequestById.get(serviceRequest.getBasedOnId());
                if (medicationRequest == null || medicationRequest.getMedicationId() == null) {
                    continue;
                }
                medicationIds.add(medicationRequest.getMedicationId());
                Long locationId = locationIdByMedicationRequestId.get(serviceRequest.getBasedOnId());
                if (locationId != null) {
                    locationIds.add(locationId);
                }
            }

            Map<Long, String> medicationNameById = this.loadMedicationNamesByIds(medicationIds);
            Map<String, List<OutpatientSkinTestLotOptionDto>> lotOptionsByMedLocationKey =
                this.loadLotOptionsByMedLocation(medicationIds, locationIds);
            Map<Long, String> practitionerNameById = this.loadPractitionerNamesForRows(
                skinTestServiceRequests,
                medicationRequestById,
                allergyByServiceRequestId);

            OutpatientSkinTestListAssemblyInput assemblyInput = new OutpatientSkinTestListAssemblyInput();
            assemblyInput.setEncounterId(encounter.getId());
            assemblyInput.setPatientId(encounter.getPatientId());
            assemblyInput.setSkinTestServiceRequests(skinTestServiceRequests);
            assemblyInput.setMedicationRequestById(medicationRequestById);
            assemblyInput.setMedicationNameById(medicationNameById);
            assemblyInput.setLocationIdByMedicationRequestId(locationIdByMedicationRequestId);
            assemblyInput.setAllergyByServiceRequestId(allergyByServiceRequestId);
            assemblyInput.setLotOptionsByMedLocationKey(lotOptionsByMedLocationKey);
            assemblyInput.setPractitionerNameById(practitionerNameById);

            List<OutpatientSkinTestRowDto> rows = outpatientSkinTestListAssembler.assembleRows(assemblyInput);
            return outpatientSkinTestMapper.toListResponse(rows);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("listOutpatientSkinTestRows failed encounterId={}", request != null ? request.getEncounterId() : null, ex);
            throw new BusinessException(OutpatientNurseStationConstants.EX_OP_STK_103, "皮试列表加载失败，请稍后重试");
        }
    }

    /**
     * IF-OP-STK-03：保存皮试临床结果（仅 AllergyIntolerance，不写 SR/Charge）。
     *
     * @param request 保存请求
     * @return 保存结果
     */
    public SaveOutpatientSkinTestRecordResponse saveOutpatientSkinTestRecord(SaveOutpatientSkinTestRecordRequest request) {
        if (request == null || request.getEncounterId() == null || request.getServiceRequestId() == null) {
            throw new BusinessException(OutpatientNurseStationConstants.EX_OP_STK_102, "请先选择患者或刷新列表");
        }
        EncounterDetailQueryModel encounter = this.gateEncounterForSkinTest(
            request.getEncounterId(),
            request.getPatientId());

        this.validateSaveRequestFields(request);

        Long skinTestActivityId = activityDefinitionQueryService.requireIdByExactName(
            GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME);
        ServiceRequestListModel skinTestServiceRequest =
            serviceRequestQueryService.mustGetListModelById(request.getServiceRequestId());

        if (!request.getEncounterId().equals(skinTestServiceRequest.getEncounterId())) {
            throw new BusinessException(OutpatientNurseStationConstants.EX_OP_STK_202, "皮试费未结算或医嘱不可登记，请刷新");
        }
        if (!RequestStatus.COMPLETED.getCode().equals(skinTestServiceRequest.getStatusEnum())) {
            throw new BusinessException(OutpatientNurseStationConstants.EX_OP_STK_202, "皮试费未结算或医嘱不可登记，请刷新");
        }
        if (!skinTestActivityId.equals(skinTestServiceRequest.getActivityId())) {
            throw new BusinessException(OutpatientNurseStationConstants.EX_OP_STK_202, "皮试费未结算或医嘱不可登记，请刷新");
        }
        if (!TableNameConstants.MED_MEDICATION_REQUEST.equals(skinTestServiceRequest.getBasedOnTable())
            || skinTestServiceRequest.getBasedOnId() == null) {
            throw new BusinessException(OutpatientNurseStationConstants.EX_OP_STK_202, "皮试费未结算或医嘱不可登记，请刷新");
        }

        if (request.getAllergyRecordId() != null) {
            AllergyIntoleranceRecordQueryModel existing =
                allergyIntoleranceQueryService.mustGetRecordById(request.getAllergyRecordId());
            if (!request.getEncounterId().equals(existing.getEncounterId())) {
                throw new BusinessException(OutpatientNurseStationConstants.EX_OP_STK_202, "皮试费未结算或医嘱不可登记，请刷新");
            }
            if (!request.getServiceRequestId().equals(existing.getRequestId())) {
                throw new BusinessException(OutpatientNurseStationConstants.EX_OP_STK_202, "皮试费未结算或医嘱不可登记，请刷新");
            }
        }

        try {
            SaveSkinTestRecordCommand command = outpatientSkinTestMapper.toSaveSkinTestRecordCommand(
                request,
                request.getServiceRequestId());
            command.setPatientId(encounter.getPatientId());
            Long allergyRecordId = allergyIntoleranceDomainService.saveSkinTestRecord(command);
            return outpatientSkinTestMapper.toSaveResponse(allergyRecordId, request.getServiceRequestId());
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error(
                "saveOutpatientSkinTestRecord failed encounterId={} serviceRequestId={}",
                request.getEncounterId(),
                request.getServiceRequestId(),
                ex);
            throw new BusinessException(OutpatientNurseStationConstants.EX_OP_STK_203, "皮试结果保存失败，请稍后重试");
        }
    }

    /**
     * IF-OP-STK-04：删除误录皮试过敏记录（不删 SR/Charge）。
     *
     * @param request 删除请求
     * @return 删除结果
     */
    public DeleteOutpatientSkinTestRecordResponse deleteOutpatientSkinTestRecord(
        DeleteOutpatientSkinTestRecordRequest request) {
        if (request == null || request.getEncounterId() == null || request.getAllergyRecordId() == null) {
            throw new BusinessException(OutpatientNurseStationConstants.EX_OP_STK_102, "请先选择患者或刷新列表");
        }
        this.gateEncounterForSkinTest(request.getEncounterId(), null);

        AllergyIntoleranceRecordQueryModel record =
            allergyIntoleranceQueryService.mustGetRecordById(request.getAllergyRecordId());
        if (!request.getEncounterId().equals(record.getEncounterId())) {
            throw new BusinessException(OutpatientNurseStationConstants.EX_OP_STK_301, "皮试记录不存在，请刷新");
        }

        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT,
            "操作人不能为空");

        try {
            DeleteSkinTestRecordCommand command =
                outpatientSkinTestMapper.toDeleteSkinTestRecordCommand(request.getAllergyRecordId(), operatorId);
            allergyIntoleranceDomainService.deleteSkinTestRecord(command);
            return outpatientSkinTestMapper.toDeleteResponse(true);
        } catch (BusinessException ex) {
            if (ErrorCodeConstants.ALLERGY_INTOLERANCE_NOT_FOUND.equals(ex.getErrorCode())) {
                throw new BusinessException(OutpatientNurseStationConstants.EX_OP_STK_301, "皮试记录不存在，请刷新");
            }
            throw ex;
        } catch (RuntimeException ex) {
            log.error(
                "deleteOutpatientSkinTestRecord failed encounterId={} allergyRecordId={}",
                request.getEncounterId(),
                request.getAllergyRecordId(),
                ex);
            throw new BusinessException(OutpatientNurseStationConstants.EX_OP_STK_303, "皮试记录删除失败，请稍后重试");
        }
    }

    private EncounterDetailQueryModel gateEncounterForSkinTest(Long encounterId, Long requestPatientId) {
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounter(encounterId);
        if (requestPatientId != null) {
            patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
                requestPatientId,
                encounter.getPatientId(),
                OutpatientNurseStationConstants.EX_OP_STK_102,
                "请先选择患者或刷新列表");
        }
        return encounter;
    }

    private void validateSaveRequestFields(SaveOutpatientSkinTestRecordRequest request) {
        if (request.getPractitionerId() == null || request.getCheckPractitionerId() == null) {
            throw new BusinessException(
                OutpatientNurseStationConstants.EX_OP_STK_201,
                "请填写皮试结果并选择执行护士与核对护士");
        }
        if (StringUtils.isBlank(request.getClinicalStatusCode())
            || StringUtils.isBlank(request.getVerificationStatusCode())) {
            throw new BusinessException(
                OutpatientNurseStationConstants.EX_OP_STK_201,
                "请填写皮试结果并选择执行护士与核对护士");
        }
    }

    private List<ServiceRequestListModel> filterSkinTestCompletedServiceRequests(
        List<ServiceRequestListModel> allServiceRequests,
        Long skinTestActivityId) {
        List<ServiceRequestListModel> filtered = new ArrayList<>();
        if (allServiceRequests == null || skinTestActivityId == null) {
            return filtered;
        }
        for (ServiceRequestListModel serviceRequest : allServiceRequests) {
            if (serviceRequest == null) {
                continue;
            }
            if (!skinTestActivityId.equals(serviceRequest.getActivityId())) {
                continue;
            }
            if (!RequestStatus.COMPLETED.getCode().equals(serviceRequest.getStatusEnum())) {
                continue;
            }
            if (!GenerateSource.DOCTOR_PRESCRIPTION.getCode().equals(serviceRequest.getGenerateSourceEnum())) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_REQUEST.equals(serviceRequest.getBasedOnTable())) {
                continue;
            }
            if (serviceRequest.getBasedOnId() == null) {
                continue;
            }
            filtered.add(serviceRequest);
        }
        return filtered;
    }

    private Map<Long, String> loadMedicationNamesByIds(Set<Long> medicationIds) {
        Map<Long, String> medicationNameById = new HashMap<>();
        if (medicationIds == null || medicationIds.isEmpty()) {
            return medicationNameById;
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (Long medicationId : medicationIds) {
            if (medicationId == null) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(medicationId);
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> productByKey = productDefinitionBatchLoader.loadAsMap(lookups);
        for (Long medicationId : medicationIds) {
            if (medicationId == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                medicationId);
            ProductDefinitionDisplayDto product = productByKey.get(key);
            if (product != null && StringUtils.isNotBlank(product.getProductName())) {
                medicationNameById.put(medicationId, product.getProductName().trim());
            }
        }
        return medicationNameById;
    }

    private Map<String, List<OutpatientSkinTestLotOptionDto>> loadLotOptionsByMedLocation(
        Set<Long> medicationIds,
        Set<Long> locationIds) {
        Map<String, List<OutpatientSkinTestLotOptionDto>> lotOptionsByKey = new HashMap<>();
        if (medicationIds == null || medicationIds.isEmpty()
            || locationIds == null || locationIds.isEmpty()) {
            return lotOptionsByKey;
        }
        List<InventoryCatalogAvailabilityRowModel> inventoryRows = inventoryItemQueryService.findAvailableCatalogRowsForOrder(
            TableNameConstants.MED_MEDICATION_DEFINITION,
            new ArrayList<>(medicationIds),
            new ArrayList<>(locationIds));
        Set<String> dedupeKeys = new HashSet<>();
        for (InventoryCatalogAvailabilityRowModel inventoryRow : inventoryRows) {
            if (inventoryRow == null || inventoryRow.getItemId() == null
                || inventoryRow.getLocationId() == null
                || StringUtils.isBlank(inventoryRow.getLotNumber())) {
                continue;
            }
            String medLocationKey = inventoryRow.getItemId() + "|" + inventoryRow.getLocationId();
            String lotDedupeKey = medLocationKey + "|" + inventoryRow.getLotNumber().trim();
            if (!dedupeKeys.add(lotDedupeKey)) {
                continue;
            }
            OutpatientSkinTestLotOptionDto lotOption = new OutpatientSkinTestLotOptionDto();
            lotOption.setMedicationId(inventoryRow.getItemId());
            lotOption.setLocationId(inventoryRow.getLocationId());
            lotOption.setLotNumber(inventoryRow.getLotNumber().trim());
            lotOptionsByKey.computeIfAbsent(medLocationKey, key -> new ArrayList<>()).add(lotOption);
        }
        return lotOptionsByKey;
    }

    private Map<Long, String> loadPractitionerNamesForRows(
        List<ServiceRequestListModel> skinTestServiceRequests,
        Map<Long, MedicationRequest> medicationRequestById,
        Map<Long, AllergyIntoleranceRecordQueryModel> allergyByServiceRequestId) {
        Set<Long> practitionerIds = new LinkedHashSet<>();
        for (ServiceRequestListModel serviceRequest : skinTestServiceRequests) {
            MedicationRequest medicationRequest = medicationRequestById.get(serviceRequest.getBasedOnId());
            if (medicationRequest != null && medicationRequest.getRequesterId() != null) {
                practitionerIds.add(medicationRequest.getRequesterId());
            }
            AllergyIntoleranceRecordQueryModel allergy = allergyByServiceRequestId.get(serviceRequest.getId());
            if (allergy != null) {
                if (allergy.getPractitionerId() != null) {
                    practitionerIds.add(allergy.getPractitionerId());
                }
                if (allergy.getCheckPractitionerId() != null) {
                    practitionerIds.add(allergy.getCheckPractitionerId());
                }
            }
        }
        Map<Long, String> practitionerNameById = new HashMap<>();
        if (practitionerIds.isEmpty()) {
            return practitionerNameById;
        }
        List<Practitioner> practitioners = practitionerQueryService.findAllByIds(practitionerIds);
        for (Practitioner practitioner : practitioners) {
            if (practitioner != null && practitioner.getId() != null
                && StringUtils.isNotBlank(practitioner.getName())) {
                practitionerNameById.put(practitioner.getId(), practitioner.getName().trim());
            }
        }
        return practitionerNameById;
    }
}
