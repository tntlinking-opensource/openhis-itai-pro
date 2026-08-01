/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.skintest.service;

import com.openhis.health.application.shared.validator.CurrentManagedWardLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.InpatientPendingSettlementClinicalGate;
import com.openhis.health.application.shared.validator.PatientEncounterConsistencyGate;
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
import com.openhis.health.application.basic.administration.support.PractitionerRoleScopeLoader;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.allergyintolerance.command.DeleteSkinTestRecordCommand;
import com.openhis.health.domain.clinical.allergyintolerance.command.SaveSkinTestRecordCommand;
import com.openhis.health.domain.clinical.allergyintolerance.queries.model.AllergyIntoleranceRecordQueryModel;
import com.openhis.health.domain.clinical.allergyintolerance.service.AllergyIntoleranceDomainService;
import com.openhis.health.domain.clinical.allergyintolerance.service.AllergyIntoleranceQueryService;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
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
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

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
 * 住院护士站皮试应用编排（IF-IP-STK-01～04，AS-IP-NUR-011 v1.5）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>列表：已校对皮试药按其全部 doctor companion SR 分行 + executedFlag + 过敏记录 + 发放批号候选。</li>
 *   <li>保存：须皮试检查已执行且批号 ∈ MedicationDispense 候选后登记 AllergyIntolerance；本 Tab 不划价。</li>
 *   <li>删除：允许删误录过敏记录；不删 SR、不冲执行费用。</li>
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
    private final EncounterContextReader EncounterContextReader;

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

    /** 发放行只读：意向库房映射 + 批号候选（BR-IP-STK-013）。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** 药品名称批量加载。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /** 人员展示名。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 皮试检查执行记录只读（已执行门禁 / executedFlag）。 */
    private final ProcedureQueryService procedureQueryService;

    /** 写路径操作人门控（B.6.1）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 岗位分配 → 护士主键预解析。 */
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    /** 就诊出院结算轨禁止皮试结果登记写路径。 */
    private final InpatientPendingSettlementClinicalGate inpatientPendingSettlementClinicalGate;

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
            // Pre：枚举选项 + 全院激活护士岗（双签可选所有护士；orgId=null 不按病区挂岗收窄）
            LoadNurseStationSkinTestInitResponse response = nurseStationSkinTestInitAssembler.assembleInitOptions();
            List<Practitioner> nurses = practitionerRoleScopeLoader.listActivePractitionersForPlatformRole(
                WhalePlatformRoleCodeConstants.ROLE_NURSE, null);
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
            // Pre：索引 doctor-prescription 皮试检查 SR（based_on → 皮试药，可多 companion，BR-IP-STK-002/009）
            Map<Long, List<ServiceRequestListModel>> doctorPrescriptionSkinTestSrByMedicationRequestId =
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

            // Pre：发放意向库房仅用于行上 performLocationId 展示
            Map<Long, Long> locationIdByMedicationRequestId =
                medicationDispenseQueryService.mapMedicationRequestToPrimaryDispenseLocationId(medicationRequestIds);

            Map<Long, AllergyIntoleranceRecordQueryModel> allergyBySkinTestServiceRequestId = new HashMap<>();
            List<AllergyIntoleranceRecordQueryModel> allergyRecords =
                allergyIntoleranceQueryService.getRecordInfo(request.getEncounterId());
            for (AllergyIntoleranceRecordQueryModel allergyRecord : allergyRecords) {
                if (allergyRecord != null && allergyRecord.getRequestId() != null) {
                    allergyBySkinTestServiceRequestId.put(allergyRecord.getRequestId(), allergyRecord);
                }
            }

            Set<Long> skinTestServiceRequestIds = new LinkedHashSet<>();
            for (List<ServiceRequestListModel> companionList :
                doctorPrescriptionSkinTestSrByMedicationRequestId.values()) {
                if (companionList == null) {
                    continue;
                }
                for (ServiceRequestListModel serviceRequest : companionList) {
                    if (serviceRequest != null && serviceRequest.getId() != null) {
                        skinTestServiceRequestIds.add(serviceRequest.getId());
                    }
                }
            }
            Map<Long, Boolean> executedBySkinTestServiceRequestId =
                this.loadExecutedFlagBySkinTestServiceRequestIds(
                    request.getEncounterId(),
                    skinTestServiceRequestIds);

            Map<Long, String> medicationNameById = this.loadMedicationNamesByIds(medicationIds);
            // Pre：批号候选取自 MedicationDispense（非库存，BR-IP-STK-013）
            Map<Long, List<NurseStationSkinTestLotOptionDto>> lotOptionsByMedicationRequestId =
                this.loadLotOptionsByMedicationRequestId(medicationRequestIds);
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
            assemblyInput.setLotOptionsByMedicationRequestId(lotOptionsByMedicationRequestId);
            assemblyInput.setPractitionerNameById(practitionerNameById);
            assemblyInput.setExecutedBySkinTestServiceRequestId(executedBySkinTestServiceRequestId);

            List<NurseStationSkinTestRowDto> rows = nurseStationSkinTestListAssembler.assembleRows(assemblyInput);
            return nurseStationSkinTestMapper.toListResponse(rows);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            Long encounterId = null;
            if (request != null) {
                encounterId = request.getEncounterId();
            }
            log.error(
                "listNurseStationSkinTestRows failed encounterId={}",
                encounterId,
                ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_STK_103,
                "加载失败，请稍后重试");
        }
    }

    /**
     * IF-IP-STK-03：保存皮试结果（须皮试检查已执行后登记/更新过敏记录）。
     *
     * @param request 保存请求
     * @return 保存结果
     * @throws BusinessException {@link InpatientNurseStationConstants#EX_IP_STK_102} 就诊不可见；
     *         {@link InpatientNurseStationConstants#EX_IP_STK_201} 双签或必填状态缺失；
     *         {@link InpatientNurseStationConstants#EX_IP_STK_202} 皮试药不可登记；
     *         {@link InpatientNurseStationConstants#EX_IP_STK_207} 医生未开立皮试检查；
     *         {@link InpatientNurseStationConstants#EX_IP_STK_208} 出院结算轨禁止登记；
     *         {@link InpatientNurseStationConstants#EX_IP_STK_209} 皮试检查尚未执行；
     *         {@link InpatientNurseStationConstants#EX_IP_STK_210} 批号缺失或不在发放候选；
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
        // Gate：待结算/已结算/已离院禁止登记结果（EX-IP-STK-208）
        inpatientPendingSettlementClinicalGate.assertEncounterAllowsOrderClinicalMutation(
            encounterSnapshot.getStatusEnum(),
            InpatientNurseStationConstants.EX_IP_STK_208,
            InpatientNurseStationConstants.MSG_ENCOUNTER_PENDING_SETTLEMENT_ORDER_FORBIDDEN);
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

        // Pre（BR-IP-STK-009）：解析目标 companion doctor-prescription 皮试检查 SR
        ServiceRequestListModel doctorSkinTestServiceRequest = this.resolveDoctorPrescriptionSkinTestServiceRequest(
            request.getEncounterId(),
            skinTestActivityId,
            request.getMedicationRequestId(),
            request.getSkinTestServiceRequestId());
        Long skinTestServiceRequestId = doctorSkinTestServiceRequest.getId();

        if (request.getAllergyRecordId() != null) {
            // 更新保存：既有记录须属本就诊且 request_id 锚定 companion doctor SR
            AllergyIntoleranceRecordQueryModel existingRecord =
                allergyIntoleranceQueryService.mustGetRecordById(request.getAllergyRecordId());
            if (!request.getEncounterId().equals(existingRecord.getEncounterId())
                || !skinTestServiceRequestId.equals(existingRecord.getRequestId())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_STK_202,
                    "该皮试医嘱不可登记，请刷新列表");
            }
        }

        // Pre（BR-IP-STK-004）：companion 皮试检查须已在医嘱执行完成
        this.assertSkinTestServiceRequestExecuted(request.getEncounterId(), skinTestServiceRequestId);

        // Pre（BR-IP-STK-013）：批号必填且须落在该皮试药发放批号候选内
        this.assertLotNumberInDispenseCandidates(request.getMedicationRequestId(), request.getLotNumber());

        try {
            // Cmd：保存/更新过敏记录（request_id 锚定 doctor 皮试检查 SR）
            SaveSkinTestRecordCommand command = nurseStationSkinTestMapper.toSaveSkinTestRecordCommand(
                request,
                skinTestServiceRequestId);
            command.setPatientId(encounterSnapshot.getPatientId());
            Long allergyRecordId = allergyIntoleranceDomainService.saveSkinTestRecord(command);
            return nurseStationSkinTestMapper.toSaveResponse(allergyRecordId, skinTestServiceRequestId);
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
     * IF-IP-STK-04：删除误录皮试记录。
     *
     * @param request 删除请求
     * @return 删除结果
     * @throws BusinessException {@link InpatientNurseStationConstants#EX_IP_STK_102} 就诊不可见；
     *         {@link InpatientNurseStationConstants#EX_IP_STK_301} 记录不存在；
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
            EncounterContextReader.listInpatientEncounterContexts(query);
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
        if (StringUtils.isEmpty(request.getClinicalStatusCode())
            || StringUtils.isEmpty(request.getVerificationStatusCode())) {
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
     * @param doctorPrescriptionSkinTestSrByMedicationRequestId doctor SR 索引（可多 companion）
     * @return 过滤后的皮试药列表
     */
    private List<MedicationRequestListQueryModel> filterMedicationsWithDoctorSkinTestServiceRequest(
        List<MedicationRequestListQueryModel> skinTestMedications,
        Map<Long, List<ServiceRequestListModel>> doctorPrescriptionSkinTestSrByMedicationRequestId) {
        List<MedicationRequestListQueryModel> filtered = new ArrayList<>();
        if (skinTestMedications == null || doctorPrescriptionSkinTestSrByMedicationRequestId == null) {
            return filtered;
        }
        for (MedicationRequestListQueryModel medicationRequest : skinTestMedications) {
            if (medicationRequest == null || medicationRequest.getId() == null) {
                continue;
            }
            List<ServiceRequestListModel> companions =
                doctorPrescriptionSkinTestSrByMedicationRequestId.get(medicationRequest.getId());
            // 无 companion 列表或为空则不进皮试 Tab
            if (companions != null && !companions.isEmpty()) {
                filtered.add(medicationRequest);
            }
        }
        return filtered;
    }

    /**
     * 索引 doctor-prescription 皮试检查 SR：皮试药 id → companion SR 列表（保留全部，BR-IP-STK-002）。
     *
     * @param serviceRequests    doctor-prescription 服务申请列表
     * @param skinTestActivityId 皮试检查活动 id
     * @return 索引表
     */
    private Map<Long, List<ServiceRequestListModel>> indexDoctorPrescriptionSkinTestServiceRequests(
        List<ServiceRequestListModel> serviceRequests,
        Long skinTestActivityId) {
        Map<Long, List<ServiceRequestListModel>> indexed = new LinkedHashMap<>();
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
            indexed.computeIfAbsent(serviceRequest.getBasedOnId(), key -> new ArrayList<>())
                .add(serviceRequest);
        }
        return indexed;
    }

    /**
     * 解析目标 doctor-prescription 皮试检查 SR（BR-IP-STK-009）。
     *
     * <p>请求携带 {@code skinTestServiceRequestId} 时按该 id 匹配；否则单 companion 自动取用，
     * 多 companion 须显式指定以免写错行。</p>
     *
     * @param encounterId              就诊 id
     * @param skinTestActivityId       皮试检查活动 id
     * @param medicationRequestId      皮试药医嘱 id
     * @param skinTestServiceRequestId 可选目标 companion SR id
     * @return doctor 皮试检查 SR 列表模型
     */
    private ServiceRequestListModel resolveDoctorPrescriptionSkinTestServiceRequest(
        Long encounterId,
        Long skinTestActivityId,
        Long medicationRequestId,
        Long skinTestServiceRequestId) {
        List<ServiceRequestListModel> companions = this.listDoctorPrescriptionSkinTestCompanions(
            encounterId,
            skinTestActivityId,
            medicationRequestId);
        // 无 companion：医生未开立皮试检查
        if (companions.isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_STK_207,
                "医生未开立皮试检查医嘱，请联系医生处理");
        }
        if (skinTestServiceRequestId != null) {
            // 显式指定 companion：须属于本皮试药 based_on 集合
            for (ServiceRequestListModel companion : companions) {
                if (skinTestServiceRequestId.equals(companion.getId())) {
                    return companion;
                }
            }
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_STK_202,
                "该皮试医嘱不可登记，请刷新列表");
        }
        // 未指定：仅当唯一 companion 时兼容旧客户端
        if (companions.size() == 1) {
            return companions.get(0);
        }
        throw new BusinessException(
            InpatientNurseStationConstants.EX_IP_STK_202,
            "该皮试医嘱不可登记，请刷新列表");
    }

    /**
     * 列出锚定指定皮试药的全部 doctor-prescription 皮试检查 companion SR。
     *
     * @param encounterId         就诊 id
     * @param skinTestActivityId  皮试检查活动 id
     * @param medicationRequestId 皮试药医嘱 id
     * @return companion 列表（可能为空）
     */
    private List<ServiceRequestListModel> listDoctorPrescriptionSkinTestCompanions(
        Long encounterId,
        Long skinTestActivityId,
        Long medicationRequestId) {
        List<ServiceRequestListModel> companions = new ArrayList<>();
        String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        List<ServiceRequestListModel> serviceRequests =
            serviceRequestQueryService.listServiceRequestsByGenerateSource(encounterId, doctorSource);
        if (serviceRequests == null) {
            return companions;
        }
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
                companions.add(serviceRequest);
            }
        }
        return companions;
    }

    /**
     * 断言批号非空且属于该皮试药 MedicationDispense 去重候选（BR-IP-STK-013 / EX-IP-STK-210）。
     *
     * @param medicationRequestId 皮试药医嘱 id
     * @param lotNumber           请求批号
     */
    private void assertLotNumberInDispenseCandidates(Long medicationRequestId, String lotNumber) {
        if (StringUtils.isEmpty(lotNumber)) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_STK_210,
                "请选择本次取药批号（须先执行皮试药）");
        }
        String trimmedLot = lotNumber.trim();
        Set<String> candidateLots = this.loadDispenseLotNumberSet(medicationRequestId);
        // 无发放批号或请求批号不在候选内：须先执行皮试药 FIFO 取药
        if (!candidateLots.contains(trimmedLot)) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_STK_210,
                "请选择本次取药批号（须先执行皮试药）");
        }
    }

    /**
     * 加载皮试药医嘱下未删除发放行的去重批号集合。
     *
     * @param medicationRequestId 皮试药医嘱 id
     * @return 去重批号
     */
    private Set<String> loadDispenseLotNumberSet(Long medicationRequestId) {
        Set<String> lotNumbers = new LinkedHashSet<>();
        if (medicationRequestId == null) {
            return lotNumbers;
        }
        List<MedicationDispense> dispenses =
            medicationDispenseQueryService.listByMedReqIds(Collections.singletonList(medicationRequestId));
        if (dispenses == null) {
            return lotNumbers;
        }
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null || StringUtils.isEmpty(dispense.getLotNumber())) {
                continue;
            }
            lotNumbers.add(dispense.getLotNumber().trim());
        }
        return lotNumbers;
    }

    /**
     * 批量判断皮试检查 SR 是否已有净剩 COMPLETED 执行记录。
     *
     * @param encounterId               就诊 id
     * @param skinTestServiceRequestIds 皮试检查 SR id 集合
     * @return SR id → 是否已执行
     */
    private Map<Long, Boolean> loadExecutedFlagBySkinTestServiceRequestIds(
        Long encounterId,
        Set<Long> skinTestServiceRequestIds) {
        Map<Long, Boolean> executedByServiceRequestId = new HashMap<>();
        if (skinTestServiceRequestIds == null || skinTestServiceRequestIds.isEmpty()) {
            return executedByServiceRequestId;
        }
        for (Long serviceRequestId : skinTestServiceRequestIds) {
            executedByServiceRequestId.put(serviceRequestId, Boolean.FALSE);
        }
        List<ProcedureRecordQueryModel> netRows =
            procedureQueryService.listNetRemainingCompletedProcedureRecords(encounterId, null, null);
        if (netRows == null || netRows.isEmpty()) {
            return executedByServiceRequestId;
        }
        for (ProcedureRecordQueryModel row : netRows) {
            if (row == null || row.getRequestId() == null) {
                continue;
            }
            if (!TableNameConstants.WKF_SERVICE_REQUEST.equals(row.getRequestTable())) {
                continue;
            }
            if (executedByServiceRequestId.containsKey(row.getRequestId())) {
                executedByServiceRequestId.put(row.getRequestId(), Boolean.TRUE);
            }
        }
        return executedByServiceRequestId;
    }

    /**
     * 断言皮试检查 SR 已在医嘱执行完成（净剩 COMPLETED Procedure）。
     *
     * @param encounterId              就诊 id
     * @param skinTestServiceRequestId 皮试检查 SR id
     */
    private void assertSkinTestServiceRequestExecuted(Long encounterId, Long skinTestServiceRequestId) {
        List<ProcedureRecordQueryModel> netRows =
            procedureQueryService.listNetRemainingCompletedProcedureRecords(
                encounterId,
                skinTestServiceRequestId,
                TableNameConstants.WKF_SERVICE_REQUEST);
        if (netRows == null || netRows.isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_STK_209,
                "请先在「医嘱执行」完成皮试检查后再登记结果");
        }
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
        Map<String, ProductDefinitionDisplayDto> productByKey = productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        for (Long medicationId : medicationIds) {
            if (medicationId == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                medicationId);
            ProductDefinitionDisplayDto product = productByKey.get(key);
            if (product != null && StringUtils.isNotEmpty(product.getProductName())) {
                medicationNameById.put(medicationId, product.getProductName().trim());
            }
        }
        return medicationNameById;
    }

    /**
     * 按皮试药医嘱批量加载发放批号候选（MedicationDispense 去重 lot_number，非库存）。
     *
     * @param medicationRequestIds 皮试药医嘱 id 集合
     * @return 医嘱 id → 批号选项
     */
    private Map<Long, List<NurseStationSkinTestLotOptionDto>> loadLotOptionsByMedicationRequestId(
        Set<Long> medicationRequestIds) {
        Map<Long, List<NurseStationSkinTestLotOptionDto>> lotOptionsByMedicationRequestId = new HashMap<>();
        if (medicationRequestIds == null || medicationRequestIds.isEmpty()) {
            return lotOptionsByMedicationRequestId;
        }
        List<MedicationDispense> dispenses =
            medicationDispenseQueryService.listByMedReqIds(medicationRequestIds);
        if (dispenses == null || dispenses.isEmpty()) {
            return lotOptionsByMedicationRequestId;
        }
        Set<String> dedupeKeys = new HashSet<>();
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null || dispense.getMedReqId() == null
                || StringUtils.isEmpty(dispense.getLotNumber())) {
                continue;
            }
            String lotNumber = dispense.getLotNumber().trim();
            String dedupeKey = dispense.getMedReqId() + "|" + lotNumber;
            // 同一医嘱同一批号只保留一条候选
            if (!dedupeKeys.add(dedupeKey)) {
                continue;
            }
            NurseStationSkinTestLotOptionDto lotOption = new NurseStationSkinTestLotOptionDto();
            lotOption.setMedicationId(dispense.getMedicationId());
            lotOption.setLotNumber(lotNumber);
            lotOption.setLocationId(dispense.getLocationId());
            lotOptionsByMedicationRequestId
                .computeIfAbsent(dispense.getMedReqId(), key -> new ArrayList<>())
                .add(lotOption);
        }
        return lotOptionsByMedicationRequestId;
    }

    /**
     * 列表行涉及人员展示名批量加载。
     *
     * @param skinTestMedications                             皮试药行
     * @param allergyBySkinTestServiceRequestId               过敏记录索引
     * @param doctorPrescriptionSkinTestSrByMedicationRequestId doctor companion SR 索引
     * @return 人员 id → 展示名
     */
    private Map<Long, String> loadPractitionerNamesForRows(
        List<MedicationRequestListQueryModel> skinTestMedications,
        Map<Long, AllergyIntoleranceRecordQueryModel> allergyBySkinTestServiceRequestId,
        Map<Long, List<ServiceRequestListModel>> doctorPrescriptionSkinTestSrByMedicationRequestId) {
        Set<Long> practitionerIds = new LinkedHashSet<>();
        if (skinTestMedications != null) {
            for (MedicationRequestListQueryModel medicationRequest : skinTestMedications) {
                if (medicationRequest != null && medicationRequest.getRequesterId() != null) {
                    practitionerIds.add(medicationRequest.getRequesterId());
                }
                if (medicationRequest == null || medicationRequest.getId() == null
                    || doctorPrescriptionSkinTestSrByMedicationRequestId == null) {
                    continue;
                }
                List<ServiceRequestListModel> companions =
                    doctorPrescriptionSkinTestSrByMedicationRequestId.get(medicationRequest.getId());
                if (companions == null || allergyBySkinTestServiceRequestId == null) {
                    continue;
                }
                // 收集各 companion 已登记过敏记录上的双签护士
                for (ServiceRequestListModel serviceRequest : companions) {
                    if (serviceRequest == null || serviceRequest.getId() == null) {
                        continue;
                    }
                    AllergyIntoleranceRecordQueryModel allergy =
                        allergyBySkinTestServiceRequestId.get(serviceRequest.getId());
                    if (allergy == null) {
                        continue;
                    }
                    if (allergy.getPractitionerId() != null) {
                        practitionerIds.add(allergy.getPractitionerId());
                    }
                    if (allergy.getCheckPractitionerId() != null) {
                        practitionerIds.add(allergy.getCheckPractitionerId());
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
                && StringUtils.isNotEmpty(practitioner.getName())) {
                practitionerNameById.put(practitioner.getId(), practitioner.getName().trim());
            }
        }
        return practitionerNameById;
    }

}
