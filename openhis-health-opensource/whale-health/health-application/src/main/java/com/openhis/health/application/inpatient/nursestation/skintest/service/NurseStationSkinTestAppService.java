/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.skintest.service;

import com.openhis.health.module.common.validator.CurrentManagedWardLocationIdsResolver;
import com.openhis.health.module.common.validator.CurrentOperatorResolver;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.health.module.common.validator.PatientEncounterConsistencyGate;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.assembler.NurseStationInpatientBillingCatalogAssembler;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingWriteItemDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.mapper.NurseStationInpatientBillingMapper;
import com.openhis.health.application.inpatient.nursestation.skintest.assembler.NurseStationSkinTestInitAssembler;
import com.openhis.health.application.inpatient.nursestation.skintest.assembler.NurseStationSkinTestListAssembler;
import com.openhis.health.application.inpatient.nursestation.skintest.dto.NurseStationSkinTestListAssemblyInput;
import com.openhis.health.application.inpatient.nursestation.skintest.dto.NurseStationSkinTestLotOptionDto;
import com.openhis.health.application.inpatient.nursestation.skintest.dto.NurseStationSkinTestRowDto;
import com.openhis.health.application.inpatient.nursestation.skintest.mapper.NurseStationSkinTestMapper;
import com.openhis.health.application.inpatient.nursestation.skintest.request.DeleteNurseStationSkinTestRecordRequest;
import com.openhis.health.application.inpatient.nursestation.skintest.request.ListNurseStationSkinTestRowsRequest;
import com.openhis.health.application.inpatient.nursestation.skintest.request.LoadNurseStationSkinTestInitRequest;
import com.openhis.health.application.inpatient.nursestation.skintest.request.SaveNurseStationSkinTestRecordRequest;
import com.openhis.health.application.inpatient.nursestation.skintest.response.DeleteNurseStationSkinTestRecordResponse;
import com.openhis.health.application.inpatient.nursestation.skintest.response.ListNurseStationSkinTestRowsResponse;
import com.openhis.health.application.inpatient.nursestation.skintest.response.LoadNurseStationSkinTestInitResponse;
import com.openhis.health.application.inpatient.nursestation.skintest.response.SaveNurseStationSkinTestRecordResponse;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.allergyintolerance.command.DeleteSkinTestRecordCommand;
import com.openhis.health.domain.clinical.allergyintolerance.command.SaveSkinTestRecordCommand;
import com.openhis.health.domain.clinical.allergyintolerance.queries.model.AllergyIntoleranceRecordQueryModel;
import com.openhis.health.domain.clinical.allergyintolerance.service.AllergyIntoleranceDomainService;
import com.openhis.health.domain.clinical.allergyintolerance.service.AllergyIntoleranceQueryService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestDetailQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.servicerequest.command.CreateFromNursePricingCommand;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院护士站皮试应用编排（IF-IP-STK-01～04，AS-IP-NUR-011）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>列表：已校对皮试药 + nurse-pricing 皮试检查 SR + 过敏记录 + 批号候选只读装配。</li>
 *   <li>保存：首次同事务补费划价（createFromNursePricing + priceByNurseStation）+ 过敏记录；更新仅改过敏记录。</li>
 *   <li>删除：仅允许未划价误录；不删 SR/Charge。</li>
 * </ul>
 *
 * <p>接入说明：由 AppServiceControllerRegistrar 动态注册为
 * {@code POST /api/app/nurseStationSkinTest/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NurseStationSkinTestAppService {

    /** IF-01 枚举选项装配。 */
    private final NurseStationSkinTestInitAssembler nurseStationSkinTestInitAssembler;

    /** IF-02 列表行装配。 */
    private final NurseStationSkinTestListAssembler nurseStationSkinTestListAssembler;

    /** Request/Response 与 Command 映射。 */
    private final NurseStationSkinTestMapper nurseStationSkinTestMapper;

    /** 住院就诊可见性 Gate（护士站队列 + 管辖病区）。 */
    private final EncounterContextFacade encounterContextFacade;

    /** 管辖病区解析（B.6.1.4）。 */
    private final CurrentManagedWardLocationIdsResolver managedWardLocationIdsResolver;

    /** 患者与就诊一致性 Gate。 */
    private final PatientEncounterConsistencyGate patientEncounterConsistencyGate;

    /** 皮试检查活动定义解析。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

    /** 皮试药医嘱列表加载。 */
    private final MedicationRequestQueryService medicationRequestQueryService;

    /** 皮试检查 SR 互斥与幂等查询。 */
    private final ServiceRequestQueryService serviceRequestQueryService;

    /** nurse-pricing 皮试检查 SR 写路径。 */
    private final ServiceRequestDomainService serviceRequestDomainService;

    /** 过敏记录读侧。 */
    private final AllergyIntoleranceQueryService allergyIntoleranceQueryService;

    /** 过敏记录写侧。 */
    private final AllergyIntoleranceDomainService allergyIntoleranceDomainService;

    /** 批号候选只读库存。 */
    private final InventoryItemQueryService inventoryItemQueryService;

    /** 发放意向库房映射。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** 药品名称批量加载。 */
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;

    /** 人员展示名。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 皮试操作费写路径。 */
    private final ChargeItemDomainService chargeItemDomainService;

    /** 皮试操作费是否已划价查询。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 复用住院划价 Command 装配（禁止复制第二套补费逻辑）。 */
    private final NurseStationInpatientBillingMapper nurseStationInpatientBillingMapper;

    /** 皮试检查定价行解析。 */
    private final NurseStationInpatientBillingCatalogAssembler nurseStationInpatientBillingCatalogAssembler;

    /** 写路径操作人门控（B.6.1）。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 写路径申请人（医务人员）解析。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /**
     * IF-IP-STK-01：加载皮试表单枚举下拉。
     *
     * @param request 可空占位请求
     * @return 四类枚举选项
     * @throws BusinessException {@link InpatientNurseStationConstants#EX_IP_STK_101} 枚举装载失败
     */
    public LoadNurseStationSkinTestInitResponse loadNurseStationSkinTestInit(
        LoadNurseStationSkinTestInitRequest request) {
        try {
            LoadNurseStationSkinTestInitResponse response = nurseStationSkinTestInitAssembler.assembleInitOptions();
            return nurseStationSkinTestMapper.toInitResponse(response);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("loadNurseStationSkinTestInit failed", ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_STK_101,
                "表单选项加载失败，请刷新");
        }
    }

    /**
     * IF-IP-STK-02：按就诊查询皮试待办/已登记列表。
     *
     * @param request 就诊与患者主键
     * @return 皮试列表行
     * @throws BusinessException {@link InpatientNurseStationConstants#EX_IP_STK_102} 就诊不可见或患者不一致；
     *         {@link InpatientNurseStationConstants#EX_IP_STK_103} 列表查询失败
     */
    public ListNurseStationSkinTestRowsResponse listNurseStationSkinTestRows(
        ListNurseStationSkinTestRowsRequest request) {
        try {
            if (request == null || request.getEncounterId() == null || request.getPatientId() == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_STK_102,
                    "请先选择患者或刷新列表");
            }
            // Gate：就诊须在护士站可见范围且患者一致
            InpatientEncounterContextQueryModel encounterSnapshot =
                this.requireVisibleEncounterSnapshot(request.getEncounterId(), request.getPatientId());

            Long skinTestActivityId = activityDefinitionQueryService.requireIdByExactName(
                GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME);

            String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
            List<MedicationRequestListQueryModel> skinTestMedications =
                this.filterVerifiedSkinTestMedications(
                    medicationRequestQueryService.listMedicationRequestsByEncounterForDoctorStation(
                        request.getEncounterId(),
                        doctorSource));

            Set<Long> medicationRequestIds = new LinkedHashSet<>();
            Set<Long> medicationIds = new LinkedHashSet<>();
            for (MedicationRequestListQueryModel medicationRequest : skinTestMedications) {
                if (medicationRequest != null && medicationRequest.getId() != null) {
                    medicationRequestIds.add(medicationRequest.getId());
                }
                if (medicationRequest != null && medicationRequest.getMedicationId() != null) {
                    medicationIds.add(medicationRequest.getMedicationId());
                }
            }

            Map<Long, Long> locationIdByMedicationRequestId =
                medicationDispenseQueryService.mapMedicationRequestToPrimaryDispenseLocationId(medicationRequestIds);
            Set<Long> locationIds = new LinkedHashSet<>();
            for (Long locationId : locationIdByMedicationRequestId.values()) {
                if (locationId != null) {
                    locationIds.add(locationId);
                }
            }

            String nursePricingSource = GenerateSource.NURSE_PRICING.getCode();
            List<ServiceRequestListModel> nursePricingServiceRequests =
                serviceRequestQueryService.listServiceRequestsByGenerateSource(
                    request.getEncounterId(),
                    nursePricingSource);
            Map<Long, ServiceRequestListModel> nursePricingSkinTestSrByMedicationRequestId =
                this.indexNursePricingSkinTestServiceRequests(
                    nursePricingServiceRequests,
                    skinTestActivityId);

            Map<Long, AllergyIntoleranceRecordQueryModel> allergyBySkinTestServiceRequestId = new HashMap<>();
            List<AllergyIntoleranceRecordQueryModel> allergyRecords =
                allergyIntoleranceQueryService.getRecordInfo(request.getEncounterId());
            for (AllergyIntoleranceRecordQueryModel allergyRecord : allergyRecords) {
                if (allergyRecord != null && allergyRecord.getRequestId() != null) {
                    allergyBySkinTestServiceRequestId.put(allergyRecord.getRequestId(), allergyRecord);
                }
            }

            Set<Long> skinTestServiceRequestIds = new LinkedHashSet<>();
            for (ServiceRequestListModel serviceRequest : nursePricingSkinTestSrByMedicationRequestId.values()) {
                if (serviceRequest != null && serviceRequest.getId() != null) {
                    skinTestServiceRequestIds.add(serviceRequest.getId());
                }
            }
            Map<Long, Boolean> pricedBySkinTestServiceRequestId =
                this.loadPricedFlagBySkinTestServiceRequestIds(skinTestServiceRequestIds);

            Map<Long, String> medicationNameById = this.loadMedicationNamesByIds(medicationIds);
            Map<String, List<NurseStationSkinTestLotOptionDto>> lotOptionsByMedLocationKey =
                this.loadLotOptionsByMedLocation(medicationIds, locationIds);
            Map<Long, String> practitionerNameById = this.loadPractitionerNamesForRows(
                skinTestMedications,
                allergyBySkinTestServiceRequestId,
                nursePricingSkinTestSrByMedicationRequestId);

            NurseStationSkinTestListAssemblyInput assemblyInput = new NurseStationSkinTestListAssemblyInput();
            assemblyInput.setEncounterId(encounterSnapshot.getEncounterId());
            assemblyInput.setPatientId(encounterSnapshot.getPatientId());
            assemblyInput.setSkinTestMedicationRequests(skinTestMedications);
            assemblyInput.setNursePricingSkinTestSrByMedicationRequestId(nursePricingSkinTestSrByMedicationRequestId);
            assemblyInput.setAllergyBySkinTestServiceRequestId(allergyBySkinTestServiceRequestId);
            assemblyInput.setLocationIdByMedicationRequestId(locationIdByMedicationRequestId);
            assemblyInput.setMedicationNameById(medicationNameById);
            assemblyInput.setLotOptionsByMedLocationKey(lotOptionsByMedLocationKey);
            assemblyInput.setPractitionerNameById(practitionerNameById);
            assemblyInput.setPricedBySkinTestServiceRequestId(pricedBySkinTestServiceRequestId);

            List<NurseStationSkinTestRowDto> rows = nurseStationSkinTestListAssembler.assembleRows(assemblyInput);
            return nurseStationSkinTestMapper.toListResponse(rows);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error(
                "listNurseStationSkinTestRows failed encounterId={}",
                request != null ? request.getEncounterId() : null,
                ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_STK_103,
                "加载失败，请稍后重试");
        }
    }

    /**
     * IF-IP-STK-03：保存皮试结果（首次含 nurse-pricing 补费划价）。
     *
     * @param request 保存请求
     * @return 保存结果
     * @throws BusinessException {@link InpatientNurseStationConstants#EX_IP_STK_102} 就诊不可见；
     *         {@link InpatientNurseStationConstants#EX_IP_STK_201} 双签或必填状态缺失；
     *         {@link InpatientNurseStationConstants#EX_IP_STK_202} 皮试药不可登记；
     *         {@link InpatientNurseStationConstants#EX_IP_STK_203} 定价异常；
     *         {@link InpatientNurseStationConstants#EX_IP_STK_204} 重复补费；
     *         {@link InpatientNurseStationConstants#EX_IP_STK_205} 医生已开皮试检查；
     *         {@link InpatientNurseStationConstants#EX_IP_STK_206} 写失败
     */
    public SaveNurseStationSkinTestRecordResponse saveNurseStationSkinTestRecord(
        SaveNurseStationSkinTestRecordRequest request) {
        if (request == null || request.getEncounterId() == null || request.getPatientId() == null
            || request.getMedicationRequestId() == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_STK_102,
                "请先选择患者或刷新列表");
        }
        // Gate：就诊可见 + 患者一致
        InpatientEncounterContextQueryModel encounterSnapshot =
            this.requireVisibleEncounterSnapshot(request.getEncounterId(), request.getPatientId());
        this.validateSaveRequestFields(request);

        Long skinTestActivityId = activityDefinitionQueryService.requireIdByExactName(
            GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME);

        // Pre：皮试药须已校对且属于当前就诊列表准入集合
        MedicationRequestDetailQueryModel medicationDetail =
            medicationRequestQueryService.loadMedicationRequestById(request.getMedicationRequestId());
        if (!RequestStatus.VERIFIED.getCode().equals(medicationDetail.getStatusEnum())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_STK_202,
                "该皮试医嘱不可登记，请刷新列表");
        }
        MedicationRequestListQueryModel listRow = this.findSkinTestMedicationListRow(
            request.getEncounterId(),
            request.getMedicationRequestId());
        if (listRow == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_STK_202,
                "该皮试医嘱不可登记，请刷新列表");
        }
        if (request.getMedicationId() != null
            && listRow.getMedicationId() != null
            && !request.getMedicationId().equals(listRow.getMedicationId())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_STK_202,
                "该皮试医嘱不可登记，请刷新列表");
        }

        // Pre：医生开立皮试检查互斥（BR-IP-STK-009）
        if (this.hasDoctorPrescriptionSkinTestInspection(
            request.getEncounterId(),
            skinTestActivityId,
            request.getMedicationRequestId())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_STK_205,
                "存在重复皮试费用医嘱，请联系医生处理");
        }

        boolean firstTimePriced = false;
        Long skinTestServiceRequestId = null;

        if (request.getAllergyRecordId() == null) {
            // 首次保存：不得已存在 nurse-pricing 皮试检查 SR（BR-IP-STK-006）
            String nursePricingSource = GenerateSource.NURSE_PRICING.getCode();
            List<ServiceRequestListModel> nursePricingServiceRequests =
                serviceRequestQueryService.listServiceRequestsByGenerateSource(
                    request.getEncounterId(),
                    nursePricingSource);
            Map<Long, ServiceRequestListModel> existingNursePricingSrByMedReqId =
                this.indexNursePricingSkinTestServiceRequests(
                    nursePricingServiceRequests,
                    skinTestActivityId);
            if (existingNursePricingSrByMedReqId.containsKey(request.getMedicationRequestId())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_STK_204,
                    "皮试操作费已登记，请刷新后修改结果");
            }
            if (request.getAccountId() == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_STK_201,
                    "请填写皮试结果并选择执行护士与核对护士");
            }

            // Pre：解析皮试检查定价（BR-IP-STK-004）
            NurseStationPricingWriteItemDto pricingRow = nurseStationSkinTestMapper.toSkinTestPricingWriteItem(
                request,
                skinTestActivityId,
                listRow.getUnitCode());
            nurseStationInpatientBillingCatalogAssembler.applyChildActivityPricing(
                pricingRow,
                skinTestActivityId);
            if (pricingRow.getChargeItemDefinitionId() == null || pricingRow.getUnitPrice() == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_STK_203,
                    "皮试操作费价格异常，请联系管理员");
            }
            BigDecimal quantity = pricingRow.getQuantity();
            if (quantity == null) {
                quantity = BigDecimal.ONE;
            }
            pricingRow.setTotalPrice(pricingRow.getUnitPrice().multiply(quantity));

            Long operatorId = currentOperatorResolver.requireOperatorId(
                InpatientNurseStationConstants.EX_IP_STK_206,
                "保存失败，请稍后重试");
            Long requesterId = currentPractitionerResolver.findPractitionerIdOrNull();
            Long orgId = encounterSnapshot.getOrganizationId();

            try {
                // Cmd 1：创建 nurse-pricing 皮试检查 SR（VERIFIED）
                CreateFromNursePricingCommand serviceCmd =
                    nurseStationInpatientBillingMapper.toCreateFromNursePricingCommand(
                        List.of(pricingRow),
                        operatorId,
                        requesterId);
                List<Long> createdServiceRequestIds = serviceRequestDomainService.createFromNursePricing(serviceCmd);
                if (createdServiceRequestIds.isEmpty()) {
                    throw new BusinessException(
                        InpatientNurseStationConstants.EX_IP_STK_206,
                        "保存失败，请稍后重试");
                }
                skinTestServiceRequestId = createdServiceRequestIds.get(0);

                // Cmd 2：落待结算费用行
                var chargeCmd = nurseStationInpatientBillingMapper.toCreateChargeItemCommand(
                    pricingRow,
                    TableNameConstants.WKF_SERVICE_REQUEST,
                    skinTestServiceRequestId,
                    null,
                    null,
                    operatorId,
                    orgId);
                chargeItemDomainService.priceByNurseStation(chargeCmd);
                firstTimePriced = true;
            } catch (BusinessException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                log.error(
                    "saveNurseStationSkinTestRecord pricing failed encounterId={} medicationRequestId={}",
                    request.getEncounterId(),
                    request.getMedicationRequestId(),
                    ex);
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_STK_206,
                    "保存失败，请稍后重试");
            }
        } else {
            // 更新保存：仅改过敏记录，SR 取既有锚点
            AllergyIntoleranceRecordQueryModel existingRecord =
                allergyIntoleranceQueryService.mustGetRecordById(request.getAllergyRecordId());
            if (!request.getEncounterId().equals(existingRecord.getEncounterId())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_STK_202,
                    "该皮试医嘱不可登记，请刷新列表");
            }
            skinTestServiceRequestId = existingRecord.getRequestId();
            if (skinTestServiceRequestId == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_STK_202,
                    "该皮试医嘱不可登记，请刷新列表");
            }
        }

        try {
            // Cmd 3：保存/更新过敏记录（request_id 锚定皮试检查 SR）
            SaveSkinTestRecordCommand command = nurseStationSkinTestMapper.toSaveSkinTestRecordCommand(
                request,
                skinTestServiceRequestId);
            command.setPatientId(encounterSnapshot.getPatientId());
            Long allergyRecordId = allergyIntoleranceDomainService.saveSkinTestRecord(command);
            return nurseStationSkinTestMapper.toSaveResponse(
                allergyRecordId,
                skinTestServiceRequestId,
                firstTimePriced);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error(
                "saveNurseStationSkinTestRecord failed encounterId={} medicationRequestId={}",
                request.getEncounterId(),
                request.getMedicationRequestId(),
                ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_STK_206,
                "保存失败，请稍后重试");
        }
    }

    /**
     * IF-IP-STK-04：删除误录皮试记录（未划价前提）。
     *
     * @param request 删除请求
     * @return 删除结果
     * @throws BusinessException {@link InpatientNurseStationConstants#EX_IP_STK_102} 就诊不可见；
     *         {@link InpatientNurseStationConstants#EX_IP_STK_301} 记录不存在；
     *         {@link InpatientNurseStationConstants#EX_IP_STK_302} 已划价不可删；
     *         {@link InpatientNurseStationConstants#EX_IP_STK_303} 删除失败
     */
    public DeleteNurseStationSkinTestRecordResponse deleteNurseStationSkinTestRecord(
        DeleteNurseStationSkinTestRecordRequest request) {
        if (request == null || request.getEncounterId() == null || request.getAllergyRecordId() == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_STK_102,
                "请先选择患者或刷新列表");
        }
        // Gate：就诊可见
        this.requireVisibleEncounterSnapshot(request.getEncounterId(), null);

        AllergyIntoleranceRecordQueryModel record =
            allergyIntoleranceQueryService.mustGetRecordById(request.getAllergyRecordId());
        if (!request.getEncounterId().equals(record.getEncounterId())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_STK_301,
                "皮试记录不存在，请刷新");
        }

        // Pre：已划价（SR + Charge）不可删（BR-IP-STK-008）
        if (record.getRequestId() != null) {
            ChargeItemQueryCommand chargeQuery = new ChargeItemQueryCommand();
            chargeQuery.setServiceIds(Collections.singletonList(record.getRequestId()));
            List<ChargeItem> chargeItems = chargeItemQueryService.listChargeItemsByServiceIds(chargeQuery);
            if (chargeItems != null && !chargeItems.isEmpty()) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_STK_302,
                    "已产生费用的皮试记录不可删除");
            }
        }

        Long operatorId = currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT,
            "操作人不能为空");

        try {
            DeleteSkinTestRecordCommand command =
                nurseStationSkinTestMapper.toDeleteSkinTestRecordCommand(request.getAllergyRecordId(), operatorId);
            allergyIntoleranceDomainService.deleteSkinTestRecord(command);
            return nurseStationSkinTestMapper.toDeleteResponse(true);
        } catch (BusinessException ex) {
            if (ErrorCodeConstants.ALLERGY_INTOLERANCE_NOT_FOUND.equals(ex.getErrorCode())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_STK_301,
                    "皮试记录不存在，请刷新");
            }
            throw ex;
        } catch (RuntimeException ex) {
            log.error(
                "deleteNurseStationSkinTestRecord failed encounterId={} allergyRecordId={}",
                request.getEncounterId(),
                request.getAllergyRecordId(),
                ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_STK_303,
                "删除失败，请稍后重试");
        }
    }

    /**
     * 单就诊须在护士站可见范围；可选校验患者与读模型一致。
     *
     * @param encounterId     目标就诊
     * @param requestPatientId 请求患者 id；可空
     * @return 住院上下文快照
     */
    private InpatientEncounterContextQueryModel requireVisibleEncounterSnapshot(
        Long encounterId,
        Long requestPatientId) {
        List<Long> wardIds = managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty();
        var query = nurseStationSkinTestMapper.toInpatientEncounterPageQuery(
            Collections.singletonList(encounterId),
            wardIds);
        List<InpatientEncounterContextQueryModel> visibleRows =
            encounterContextFacade.listInpatientEncounterContexts(query);
        InpatientEncounterContextQueryModel encounterSnapshot = null;
        for (InpatientEncounterContextQueryModel row : visibleRows) {
            if (row != null && encounterId.equals(row.getEncounterId())) {
                encounterSnapshot = row;
                break;
            }
        }
        // 就诊不在护士站管辖队列内，拒绝读写以防横向越权
        if (encounterSnapshot == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_STK_102,
                "请先选择患者或刷新列表");
        }
        if (requestPatientId != null) {
            patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
                requestPatientId,
                encounterSnapshot.getPatientId(),
                InpatientNurseStationConstants.EX_IP_STK_102,
                "请先选择患者或刷新列表");
        }
        return encounterSnapshot;
    }

    /**
     * 保存请求双签与必填状态校验（BR-IP-STK-007）。
     *
     * @param request 保存请求
     */
    private void validateSaveRequestFields(SaveNurseStationSkinTestRecordRequest request) {
        if (request.getPractitionerId() == null || request.getCheckPractitionerId() == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_STK_201,
                "请填写皮试结果并选择执行护士与核对护士");
        }
        if (StringUtils.isBlank(request.getClinicalStatusCode())
            || StringUtils.isBlank(request.getVerificationStatusCode())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_STK_201,
                "请填写皮试结果并选择执行护士与核对护士");
        }
    }

    /**
     * 过滤已校对皮试药（BR-IP-STK-002）：doctor-prescription + skinTestFlag + VERIFIED，排除退药行。
     *
     * @param allMedicationRequests 就诊下医生开立药品请求
     * @return 皮试药列表行
     */
    private List<MedicationRequestListQueryModel> filterVerifiedSkinTestMedications(
        List<MedicationRequestListQueryModel> allMedicationRequests) {
        List<MedicationRequestListQueryModel> filtered = new ArrayList<>();
        if (allMedicationRequests == null) {
            return filtered;
        }
        String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        String verifiedStatus = RequestStatus.VERIFIED.getCode();
        for (MedicationRequestListQueryModel medicationRequest : allMedicationRequests) {
            if (medicationRequest == null || medicationRequest.getId() == null) {
                continue;
            }
            // 退药/作废关联行不进入皮试 Tab
            if (medicationRequest.getRefundMedicineId() != null) {
                continue;
            }
            if (!Boolean.TRUE.equals(medicationRequest.getSkinTestFlag())) {
                continue;
            }
            if (!verifiedStatus.equals(medicationRequest.getStatusEnum())) {
                continue;
            }
            if (!doctorSource.equals(medicationRequest.getGenerateSourceEnum())) {
                continue;
            }
            filtered.add(medicationRequest);
        }
        return filtered;
    }

    /**
     * 在皮试准入列表中定位指定皮试药行。
     *
     * @param encounterId         就诊 id
     * @param medicationRequestId 皮试药医嘱 id
     * @return 列表行或 null
     */
    private MedicationRequestListQueryModel findSkinTestMedicationListRow(
        Long encounterId,
        Long medicationRequestId) {
        String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        List<MedicationRequestListQueryModel> skinTestRows = this.filterVerifiedSkinTestMedications(
            medicationRequestQueryService.listMedicationRequestsByEncounterForDoctorStation(
                encounterId,
                doctorSource));
        for (MedicationRequestListQueryModel row : skinTestRows) {
            if (row != null && medicationRequestId.equals(row.getId())) {
                return row;
            }
        }
        return null;
    }

    /**
     * 索引 nurse-pricing 皮试检查 SR：皮试药 id → SR。
     *
     * @param serviceRequests    nurse-pricing 服务申请列表
     * @param skinTestActivityId 皮试检查活动 id
     * @return 索引表
     */
    private Map<Long, ServiceRequestListModel> indexNursePricingSkinTestServiceRequests(
        List<ServiceRequestListModel> serviceRequests,
        Long skinTestActivityId) {
        Map<Long, ServiceRequestListModel> indexed = new HashMap<>();
        if (serviceRequests == null || skinTestActivityId == null) {
            return indexed;
        }
        for (ServiceRequestListModel serviceRequest : serviceRequests) {
            if (serviceRequest == null || serviceRequest.getId() == null) {
                continue;
            }
            if (!skinTestActivityId.equals(serviceRequest.getActivityId())) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_REQUEST.equals(serviceRequest.getBasedOnTable())
                || serviceRequest.getBasedOnId() == null) {
                continue;
            }
            indexed.put(serviceRequest.getBasedOnId(), serviceRequest);
        }
        return indexed;
    }

    /**
     * 是否存在医生开立的皮试检查诊疗（BR-IP-STK-009 互斥）。
     *
     * @param encounterId         就诊 id
     * @param skinTestActivityId  皮试检查活动 id
     * @param medicationRequestId 皮试药医嘱 id
     * @return 存在为 true
     */
    private boolean hasDoctorPrescriptionSkinTestInspection(
        Long encounterId,
        Long skinTestActivityId,
        Long medicationRequestId) {
        String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        List<ServiceRequestListModel> serviceRequests =
            serviceRequestQueryService.listServiceRequestsByGenerateSource(encounterId, doctorSource);
        for (ServiceRequestListModel serviceRequest : serviceRequests) {
            if (serviceRequest == null) {
                continue;
            }
            if (!skinTestActivityId.equals(serviceRequest.getActivityId())) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_REQUEST.equals(serviceRequest.getBasedOnTable())) {
                continue;
            }
            if (medicationRequestId.equals(serviceRequest.getBasedOnId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 批量判断皮试检查 SR 是否已有费用行（划价标记）。
     *
     * @param skinTestServiceRequestIds 皮试检查 SR id 集合
     * @return SR id → 是否已划价
     */
    private Map<Long, Boolean> loadPricedFlagBySkinTestServiceRequestIds(Set<Long> skinTestServiceRequestIds) {
        Map<Long, Boolean> pricedByServiceRequestId = new HashMap<>();
        if (skinTestServiceRequestIds == null || skinTestServiceRequestIds.isEmpty()) {
            return pricedByServiceRequestId;
        }
        ChargeItemQueryCommand chargeQuery = new ChargeItemQueryCommand();
        chargeQuery.setServiceIds(new ArrayList<>(skinTestServiceRequestIds));
        List<ChargeItem> chargeItems = chargeItemQueryService.listChargeItemsByServiceIds(chargeQuery);
        for (Long serviceRequestId : skinTestServiceRequestIds) {
            pricedByServiceRequestId.put(serviceRequestId, Boolean.FALSE);
        }
        if (chargeItems != null) {
            for (ChargeItem chargeItem : chargeItems) {
                if (chargeItem != null && chargeItem.getServiceId() != null) {
                    pricedByServiceRequestId.put(chargeItem.getServiceId(), Boolean.TRUE);
                }
            }
        }
        return pricedByServiceRequestId;
    }

    /**
     * 批量加载药品展示名。
     *
     * @param medicationIds 药品 id 集合
     * @return 药品 id → 名称
     */
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

    /**
     * 批量加载批号候选（只读库存，不扣减）。
     *
     * @param medicationIds 药品 id 集合
     * @param locationIds   库房 id 集合
     * @return med|location → 批号选项
     */
    private Map<String, List<NurseStationSkinTestLotOptionDto>> loadLotOptionsByMedLocation(
        Set<Long> medicationIds,
        Set<Long> locationIds) {
        Map<String, List<NurseStationSkinTestLotOptionDto>> lotOptionsByKey = new HashMap<>();
        if (medicationIds == null || medicationIds.isEmpty()
            || locationIds == null || locationIds.isEmpty()) {
            return lotOptionsByKey;
        }
        List<InventoryCatalogAvailabilityRowModel> inventoryRows =
            inventoryItemQueryService.findAvailableCatalogRowsForOrder(
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
            NurseStationSkinTestLotOptionDto lotOption = new NurseStationSkinTestLotOptionDto();
            lotOption.setMedicationId(inventoryRow.getItemId());
            lotOption.setLocationId(inventoryRow.getLocationId());
            lotOption.setLotNumber(inventoryRow.getLotNumber().trim());
            lotOptionsByKey.computeIfAbsent(medLocationKey, key -> new ArrayList<>()).add(lotOption);
        }
        return lotOptionsByKey;
    }

    /**
     * 列表行涉及人员展示名批量加载。
     *
     * @param skinTestMedications                       皮试药行
     * @param allergyBySkinTestServiceRequestId         过敏记录索引
     * @param nursePricingSkinTestSrByMedicationRequestId SR 索引
     * @return 人员 id → 展示名
     */
    private Map<Long, String> loadPractitionerNamesForRows(
        List<MedicationRequestListQueryModel> skinTestMedications,
        Map<Long, AllergyIntoleranceRecordQueryModel> allergyBySkinTestServiceRequestId,
        Map<Long, ServiceRequestListModel> nursePricingSkinTestSrByMedicationRequestId) {
        Set<Long> practitionerIds = new LinkedHashSet<>();
        if (skinTestMedications != null) {
            for (MedicationRequestListQueryModel medicationRequest : skinTestMedications) {
                if (medicationRequest != null && medicationRequest.getRequesterId() != null) {
                    practitionerIds.add(medicationRequest.getRequesterId());
                }
                if (medicationRequest != null && nursePricingSkinTestSrByMedicationRequestId != null) {
                    ServiceRequestListModel serviceRequest =
                        nursePricingSkinTestSrByMedicationRequestId.get(medicationRequest.getId());
                    if (serviceRequest != null && serviceRequest.getId() != null
                        && allergyBySkinTestServiceRequestId != null) {
                        AllergyIntoleranceRecordQueryModel allergy =
                            allergyBySkinTestServiceRequestId.get(serviceRequest.getId());
                        if (allergy != null) {
                            if (allergy.getPractitionerId() != null) {
                                practitionerIds.add(allergy.getPractitionerId());
                            }
                            if (allergy.getCheckPractitionerId() != null) {
                                practitionerIds.add(allergy.getCheckPractitionerId());
                            }
                        }
                    }
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
