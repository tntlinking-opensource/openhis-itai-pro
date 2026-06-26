/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.skintest.service;

import com.openhis.health.module.common.validator.CurrentManagedWardLocationIdsResolver;
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
import com.openhis.health.module.querysupport.administration.PractitionerRoleScopeLoader;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.location.entity.Location;
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
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院护士站皮试应用编排（IF-IP-STK-01～04，AS-IP-NUR-011）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>列表：已校对皮试药（须有 doctor 皮试检查 SR）+ 过敏记录 + 批号候选只读装配。</li>
 *   <li>保存：首次同事务锚 doctor SR 划价（priceByNurseStation）+ 过敏记录；更新仅改过敏记录。</li>
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

    /** 皮试检查 SR 查询（doctor-prescription 锚皮试药）。 */
    private final ServiceRequestQueryService serviceRequestQueryService;

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
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 岗位分配 → 护士主键预解析。 */
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;

    /**
     * IF-IP-STK-01：加载皮试表单枚举下拉与护士候选。
     *
     * @param request 可空占位请求
     * @return 四类枚举选项与护士下拉
     * @throws BusinessException {@link InpatientNurseStationConstants#EX_IP_STK_101} 枚举装载失败
     */
    public LoadNurseStationSkinTestInitResponse loadNurseStationSkinTestInit(
        LoadNurseStationSkinTestInitRequest request) {
        try {
            // Pre：枚举选项与管辖病区内激活护士岗人员
            LoadNurseStationSkinTestInitResponse response = nurseStationSkinTestInitAssembler.assembleInitOptions();
            List<Practitioner> nurses = this.listNursesInManagedWards();
            response.setNurseOptions(nurseStationSkinTestMapper.toNurseOptionList(nurses));
            // Asm：透传初始化响应
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

            List<ServiceRequestListModel> doctorPrescriptionServiceRequests =
                serviceRequestQueryService.listServiceRequestsByGenerateSource(
                    request.getEncounterId(),
                    doctorSource);
            // Pre：索引 doctor-prescription 皮试检查 SR（based_on → 皮试药，BR-IP-STK-009）
            Map<Long, ServiceRequestListModel> doctorPrescriptionSkinTestSrByMedicationRequestId =
                this.indexDoctorPrescriptionSkinTestServiceRequests(
                    doctorPrescriptionServiceRequests,
                    skinTestActivityId);

            // Pre：无 doctor 联动 SR 的皮试药不进 Tab 列表（BR-IP-STK-009）
            skinTestMedications = this.filterMedicationsWithDoctorSkinTestServiceRequest(
                skinTestMedications,
                doctorPrescriptionSkinTestSrByMedicationRequestId);

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

            Map<Long, AllergyIntoleranceRecordQueryModel> allergyBySkinTestServiceRequestId = new HashMap<>();
            List<AllergyIntoleranceRecordQueryModel> allergyRecords =
                allergyIntoleranceQueryService.getRecordInfo(request.getEncounterId());
            for (AllergyIntoleranceRecordQueryModel allergyRecord : allergyRecords) {
                if (allergyRecord != null && allergyRecord.getRequestId() != null) {
                    allergyBySkinTestServiceRequestId.put(allergyRecord.getRequestId(), allergyRecord);
                }
            }

            Set<Long> skinTestServiceRequestIds = new LinkedHashSet<>();
            for (ServiceRequestListModel serviceRequest : doctorPrescriptionSkinTestSrByMedicationRequestId.values()) {
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
                doctorPrescriptionSkinTestSrByMedicationRequestId);

            NurseStationSkinTestListAssemblyInput assemblyInput = new NurseStationSkinTestListAssemblyInput();
            assemblyInput.setEncounterId(encounterSnapshot.getEncounterId());
            assemblyInput.setPatientId(encounterSnapshot.getPatientId());
            assemblyInput.setSkinTestMedicationRequests(skinTestMedications);
            assemblyInput.setDoctorPrescriptionSkinTestSrByMedicationRequestId(
                doctorPrescriptionSkinTestSrByMedicationRequestId);
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
     * IF-IP-STK-03：保存皮试结果（首次锚 doctor SR 划价 + 登记过敏记录）。
     *
     * @param request 保存请求
     * @return 保存结果
     * @throws BusinessException {@link InpatientNurseStationConstants#EX_IP_STK_102} 就诊不可见；
     *         {@link InpatientNurseStationConstants#EX_IP_STK_201} 双签或必填状态缺失；
     *         {@link InpatientNurseStationConstants#EX_IP_STK_202} 皮试药不可登记；
     *         {@link InpatientNurseStationConstants#EX_IP_STK_203} 定价异常；
     *         {@link InpatientNurseStationConstants#EX_IP_STK_204} 重复划价；
     *         {@link InpatientNurseStationConstants#EX_IP_STK_207} 医生未开立皮试检查；
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

        // Pre（BR-IP-STK-009）：须存在 doctor-prescription 皮试检查 SR
        ServiceRequestListModel doctorSkinTestServiceRequest = this.requireDoctorPrescriptionSkinTestServiceRequest(
            request.getEncounterId(),
            skinTestActivityId,
            request.getMedicationRequestId());

        boolean firstTimePriced = false;
        Long skinTestServiceRequestId = doctorSkinTestServiceRequest.getId();

        if (request.getAllergyRecordId() == null) {
            // 首次保存：须 accountId、doctor SR 尚无 ChargeItem（BR-IP-STK-004/006）
            if (request.getAccountId() == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_STK_201,
                    "请填写皮试结果并选择执行护士与核对护士");
            }
            Map<Long, Boolean> pricedFlags = this.loadPricedFlagBySkinTestServiceRequestIds(
                Collections.singleton(skinTestServiceRequestId));
            if (Boolean.TRUE.equals(pricedFlags.get(skinTestServiceRequestId))) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_STK_204,
                    "皮试操作费已登记，请刷新后修改结果");
            }

            // Pre：解析皮试检查定价行（BR-IP-STK-004）
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

            Long operatorId = currentPractitionerResolver.requireOperatorId(
                InpatientNurseStationConstants.EX_IP_STK_206,
                "保存失败，请稍后重试");
            Long orgId = encounterSnapshot.getOrganizationId();

            try {
                // Cmd：锚 doctor SR 落待结算费用行（禁止 createFromNursePricing）
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
            // Cmd：保存/更新过敏记录（request_id 锚定 doctor 皮试检查 SR）
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

        Long operatorId = currentPractitionerResolver.requireOperatorId(
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
     * 过滤仅保留存在 doctor 皮试检查 SR 的皮试药行。
     *
     * @param skinTestMedications                            已校对皮试药
     * @param doctorPrescriptionSkinTestSrByMedicationRequestId doctor SR 索引
     * @return 过滤后的皮试药列表
     */
    private List<MedicationRequestListQueryModel> filterMedicationsWithDoctorSkinTestServiceRequest(
        List<MedicationRequestListQueryModel> skinTestMedications,
        Map<Long, ServiceRequestListModel> doctorPrescriptionSkinTestSrByMedicationRequestId) {
        List<MedicationRequestListQueryModel> filtered = new ArrayList<>();
        if (skinTestMedications == null || doctorPrescriptionSkinTestSrByMedicationRequestId == null) {
            return filtered;
        }
        for (MedicationRequestListQueryModel medicationRequest : skinTestMedications) {
            if (medicationRequest != null && medicationRequest.getId() != null
                && doctorPrescriptionSkinTestSrByMedicationRequestId.containsKey(medicationRequest.getId())) {
                filtered.add(medicationRequest);
            }
        }
        return filtered;
    }

    /**
     * 索引 doctor-prescription 皮试检查 SR：皮试药 id → SR。
     *
     * @param serviceRequests    doctor-prescription 服务申请列表
     * @param skinTestActivityId 皮试检查活动 id
     * @return 索引表
     */
    private Map<Long, ServiceRequestListModel> indexDoctorPrescriptionSkinTestServiceRequests(
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
     * 要求存在 doctor-prescription 皮试检查 SR（BR-IP-STK-009）；缺失时抛 EX-IP-STK-207。
     *
     * @param encounterId         就诊 id
     * @param skinTestActivityId  皮试检查活动 id
     * @param medicationRequestId 皮试药医嘱 id
     * @return doctor 皮试检查 SR 列表模型
     */
    private ServiceRequestListModel requireDoctorPrescriptionSkinTestServiceRequest(
        Long encounterId,
        Long skinTestActivityId,
        Long medicationRequestId) {
        String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        List<ServiceRequestListModel> serviceRequests =
            serviceRequestQueryService.listServiceRequestsByGenerateSource(encounterId, doctorSource);
        for (ServiceRequestListModel serviceRequest : serviceRequests) {
            if (serviceRequest == null || serviceRequest.getId() == null) {
                continue;
            }
            if (!skinTestActivityId.equals(serviceRequest.getActivityId())) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_REQUEST.equals(serviceRequest.getBasedOnTable())) {
                continue;
            }
            if (medicationRequestId.equals(serviceRequest.getBasedOnId())) {
                return serviceRequest;
            }
        }
        throw new BusinessException(
            InpatientNurseStationConstants.EX_IP_STK_207,
            "医生未开立皮试检查医嘱，请联系医生处理");
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
     * @param skinTestMedications                             皮试药行
     * @param allergyBySkinTestServiceRequestId               过敏记录索引
     * @param doctorPrescriptionSkinTestSrByMedicationRequestId doctor SR 索引
     * @return 人员 id → 展示名
     */
    private Map<Long, String> loadPractitionerNamesForRows(
        List<MedicationRequestListQueryModel> skinTestMedications,
        Map<Long, AllergyIntoleranceRecordQueryModel> allergyBySkinTestServiceRequestId,
        Map<Long, ServiceRequestListModel> doctorPrescriptionSkinTestSrByMedicationRequestId) {
        Set<Long> practitionerIds = new LinkedHashSet<>();
        if (skinTestMedications != null) {
            for (MedicationRequestListQueryModel medicationRequest : skinTestMedications) {
                if (medicationRequest != null && medicationRequest.getRequesterId() != null) {
                    practitionerIds.add(medicationRequest.getRequesterId());
                }
                if (medicationRequest != null && doctorPrescriptionSkinTestSrByMedicationRequestId != null) {
                    ServiceRequestListModel serviceRequest =
                        doctorPrescriptionSkinTestSrByMedicationRequestId.get(medicationRequest.getId());
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

    /**
     * 汇总当前操作者管辖病区内激活护士岗的在职人员（去重）。
     *
     * @return 护士实体列表
     */
    private List<Practitioner> listNursesInManagedWards() {
        List<Location> managedWards = managedWardLocationIdsResolver.listManagedActiveWardsOrEmpty();
        if (managedWards.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashMap<Long, Practitioner> nurseById = new LinkedHashMap<>();
        for (Location ward : managedWards) {
            if (ward == null || ward.getId() == null) {
                continue;
            }
            List<Practitioner> wardNurses = practitionerRoleScopeLoader.listActivePractitionersForPlatformRoleAtWardLocation(
                WhalePlatformRoleCodeConstants.ROLE_NURSE, ward.getId());
            if (wardNurses == null || wardNurses.isEmpty()) {
                continue;
            }
            for (Practitioner nurse : wardNurses) {
                if (nurse != null && nurse.getId() != null) {
                    nurseById.putIfAbsent(nurse.getId(), nurse);
                }
            }
        }
        return new ArrayList<>(nurseById.values());
    }
}
