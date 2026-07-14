/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.mapper;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.health.application.inpatient.doctorstation.advice.request.PageInpatientDoctorStationAdviceCatalogRequest;
import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.dto.*;
import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.request.*;
import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.response.*;
import com.openhis.health.domain.administration.encounter.command.BatchUpsertEncounterDiagnosisAssociationCommand;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.clinical.condition.command.SaveDoctorDiagnosisTcmCommand;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;
import com.openhis.health.domain.medication.medicationrequest.command.*;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientDoctorStationTcmConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.IptPatientDiseaseDiagType;
import com.openhis.health.domain.share.enums.YbMedType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.application.shared.validator.PatientEncounterConsistencyGate;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link InpatientDoctorStationTcmMapper} 实现。
 */
@Component
@RequiredArgsConstructor
public class InpatientDoctorStationTcmMapperImpl implements InpatientDoctorStationTcmMapper {

    private final PatientEncounterConsistencyGate patientEncounterConsistencyGate;

    /**
     * 校验住院就诊快照与请求患者一致。
     *
     * @param snapshot          住院就诊上下文快照
     * @param requestPatientId  请求中的患者主键
     * @throws BusinessException 快照为空或患者不一致时
     */
    @Override
    public void validatePatientEncounter(InpatientEncounterContextQueryModel snapshot, Long requestPatientId) {
        if (snapshot == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, InpatientDoctorStationTcmConstants.MSG_ENCOUNTER_SNAPSHOT_FAILED);
        }
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            requestPatientId, snapshot.getPatientId(),
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, InpatientDoctorStationTcmConstants.MSG_PATIENT_ENCOUNTER_MISMATCH);
    }

    /**
     * 装配 IF-IP-TCM-29 初始化响应。
     *
     * @param tcmMethodOptions 中药用法下拉选项
     * @param rateOptions      用药频次下拉选项
     * @return 初始化响应（含默认长临筛选）
     */
    @Override
    public InitInpatientDoctorStationTcmResponse toInitInpatientDoctorStationTcmResponse(
        List<OptionDto> tcmMethodOptions,
        List<OptionDto> rateOptions,
        List<OptionDto> dosageInstructionOptions) {
        InitInpatientDoctorStationTcmResponse response = new InitInpatientDoctorStationTcmResponse();
        response.setTcmMethodCodeOptions(tcmMethodOptions);
        response.setMedicationRateOptions(rateOptions);
        response.setDosageInstructionOptions(dosageInstructionOptions);
        response.setTherapyTypeFilterDefault(InpatientDoctorStationTcmConstants.THERAPY_FILTER_DEFAULT);
        return response;
    }

    /**
     * 装配 IF-IP-TCM-30 病证组列表响应。
     *
     * @param groups 病证组行 DTO 列表
     * @return 含 groups 与 totalCount 的响应
     */
    @Override
    public ListTcmDiagnosisGroupsResponse toListTcmDiagnosisGroupsResponse(List<TcmDiagnosisGroupRowDto> groups) {
        ListTcmDiagnosisGroupsResponse response = new ListTcmDiagnosisGroupsResponse();
        response.setGroups(groups);
        int totalCount = 0;
        if (groups != null) {
            totalCount = groups.size();
        }
        response.setTotalCount(totalCount);
        return response;
    }

    /**
     * 装配 IF-IP-TCM-31 目录分页响应。
     *
     * @param page 领域分页结果
     * @param rows 映射后的目录行
     * @return 分页响应
     */
    @Override
    public PageTcmConditionDefinitionResponse toPageTcmConditionDefinitionResponse(
        Page<DoctorStationConditionDefinitionRowModel> page, List<TcmDiagnosisCatalogRowDto> rows) {
        PageTcmConditionDefinitionResponse response = new PageTcmConditionDefinitionResponse();
        response.setRows(rows);
        response.setTotal(page.getTotalElements());
        return response;
    }

    /**
     * 将全量病证组保存请求转为 {@link SaveDoctorDiagnosisTcmCommand}。
     *
     * @param request         保存请求
     * @param groupKeyByItem  病名:证候 → 稳定组号映射
     * @param operatorId      操作人主键
     * @return 领域保存命令
     */
    @Override
    public SaveDoctorDiagnosisTcmCommand toSaveDoctorDiagnosisTcmCommand(
        SaveTcmDiagnosisGroupsBatchRequest request,
        Map<String, String> groupKeyByItem,
        Long operatorId) {
        SaveDoctorDiagnosisTcmCommand cmd = new SaveDoctorDiagnosisTcmCommand();
        cmd.setEncounterId(request.getEncounterId());
        cmd.setPatientId(request.getPatientId());
        cmd.setOperatorId(operatorId);
        List<SaveDoctorDiagnosisTcmCommand.DoctorDiagnosisTcmItemCommand> items = new ArrayList<>();
        for (TcmDiagnosisGroupItemRequest group : request.getGroups()) {
            items.add(toTcmItem(group.getDiseaseConditionId(), group.getDiseaseDefinitionId(), group.getDiagnosisDesc(), operatorId));
            items.add(toTcmItem(group.getSyndromeConditionId(), group.getSyndromeDefinitionId(), group.getDiagnosisDesc(), operatorId));
        }
        cmd.setItems(items);
        return cmd;
    }

    /**
     * 组装单条中医诊断项命令（病名或证候）。
     *
     * @param conditionId  已有 condition id，新建时为 null
     * @param definitionId 目录 definition id
     * @param desc         诊断描述
     * @param operatorId   操作人
     * @return 诊断项命令
     */
    private SaveDoctorDiagnosisTcmCommand.DoctorDiagnosisTcmItemCommand toTcmItem(
        Long conditionId, Long definitionId, String desc, Long operatorId) {
        SaveDoctorDiagnosisTcmCommand.DoctorDiagnosisTcmItemCommand item =
            new SaveDoctorDiagnosisTcmCommand.DoctorDiagnosisTcmItemCommand();
        item.setId(conditionId);
        item.setDefinitionId(definitionId);
        item.setDescription(desc);
        item.setTcmFlag(Boolean.TRUE);
        item.setClinicalStatusEnum("ACTIVE");
        item.setVerificationStatusEnum(DoctorStationDiagnosisConstants.ConditionVerificationStatusCode.CONFIRMED);
        item.setRecordedDatetime(LocalDateTime.now());
        item.setRecorderId(operatorId);
        return item;
    }

    /**
     * 将保存结果转为中医就诊关联 batchUpsert 命令。
     *
     * @param request           保存请求
     * @param savedConditionIds 领域返回的 condition id 列表（每组 2 条）
     * @param groupKeyByItem    病名:证候 → 稳定组号映射
     * @param operatorId        操作人主键
     * @return 就诊诊断关联批量 upsert 命令
     * @throws BusinessException 保存 id 数量与组数不匹配时
     */
    @Override
    public BatchUpsertEncounterDiagnosisAssociationCommand toBatchUpsertTcmEncounterDiagnosisAssociationCommand(
        SaveTcmDiagnosisGroupsBatchRequest request,
        List<Long> savedConditionIds,
        Map<String, String> groupKeyByItem,
        Long operatorId) {
        if (savedConditionIds.size() != request.getGroups().size() * 2) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_RULE_CONFLICT, InpatientDoctorStationTcmConstants.MSG_PERSISTENCE_FAILED);
        }
        BatchUpsertEncounterDiagnosisAssociationCommand cmd = new BatchUpsertEncounterDiagnosisAssociationCommand();
        cmd.setOperatorId(operatorId);
        BatchUpsertEncounterDiagnosisAssociationCommand.BatchUpsertEncounterDiagnosisAssociationItemCommand batchItem =
            new BatchUpsertEncounterDiagnosisAssociationCommand.BatchUpsertEncounterDiagnosisAssociationItemCommand();
        batchItem.setEncounterId(request.getEncounterId());
        List<BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand> diagnosisItems = new ArrayList<>();
        int idx = 0;
        for (TcmDiagnosisGroupItemRequest group : request.getGroups()) {
            String groupKey = groupKeyByItem.get(stableGroupKey(group));
            diagnosisItems.add(toAssocItem(savedConditionIds.get(idx++), group, groupKey));
            diagnosisItems.add(toAssocItem(savedConditionIds.get(idx++), group, groupKey));
        }
        batchItem.setDiagnosisItems(diagnosisItems);
        cmd.setItem(batchItem);
        return cmd;
    }

    /**
     * 由病名 definitionId 与证候 definitionId 生成稳定 map 键。
     *
     * @param group 病证组项
     * @return 格式为 diseaseId:syndromeId 的键
     */
    private String stableGroupKey(TcmDiagnosisGroupItemRequest group) {
        return String.valueOf(group.getDiseaseDefinitionId()) + ":" + String.valueOf(group.getSyndromeDefinitionId());
    }

    /**
     * 组装单条就诊诊断关联项。
     *
     * @param conditionId condition 主键
     * @param group       病证组项
     * @param groupKey    中医组号
     * @return 关联项命令
     */
    private BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand toAssocItem(
        Long conditionId, TcmDiagnosisGroupItemRequest group, String groupKey) {
        BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand d =
            new BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand();
        d.setConditionId(conditionId);
        if (group.getPrimaryFlag() != null
            && group.getPrimaryFlag() == DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_YES) {
            d.setPrimaryFlag(Boolean.TRUE);
        } else {
            d.setPrimaryFlag(Boolean.FALSE);
        }
        d.setRank(group.getRank());
        d.setDescription(group.getDiagnosisDesc());
        String medTypeCode = trimToNull(group.getMedicalTypeCode());
        if (medTypeCode == null) {
            medTypeCode = YbMedType.GENERAL_HOSPITALIZATION.getCode();
        } else if (EnumUtil.fromCode(YbMedType.class, medTypeCode) == null) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_INVALID_ARGUMENT, "请重新选择医疗类别");
        }
        d.setMedType(medTypeCode);
        d.setDiagTypeCode(IptPatientDiseaseDiagType.ADMISSION_PRELIMINARY.getCode());
        d.setTcmSyndromeGroup(groupKey);
        d.setTcmFlag(Boolean.TRUE);
        return d;
    }

    /**
     * 对比快照与入参，找出需删除的历史中医组号。
     *
     * @param existing 当前快照中的病证组
     * @param request    本次保存请求
     * @return 不在入参中的历史组号列表
     */
    @Override
    public List<String> diffRemovedTcmGroups(List<TcmDiagnosisGroupRowDto> existing, SaveTcmDiagnosisGroupsBatchRequest request) {
        Set<String> incoming = new HashSet<>();
        for (TcmDiagnosisGroupItemRequest group : request.getGroups()) {
            if (StringUtils.isNotEmpty(group.getTcmSyndromeGroup())) {
                incoming.add(group.getTcmSyndromeGroup().trim());
            }
        }
        List<String> removed = new ArrayList<>();
        if (existing == null) {
            return removed;
        }
        for (TcmDiagnosisGroupRowDto row : existing) {
            if (row.getTcmSyndromeGroup() != null && !incoming.contains(row.getTcmSyndromeGroup())) {
                removed.add(row.getTcmSyndromeGroup());
            }
        }
        return removed;
    }

    /**
     * 装配 IF-IP-TCM-32 保存成功响应。
     *
     * @param savedGroupKeys 已保存的组号列表
     * @return 保存成功响应
     */
    @Override
    public SaveTcmDiagnosisGroupsBatchResponse toSaveTcmDiagnosisGroupsBatchResponse(List<String> savedGroupKeys) {
        SaveTcmDiagnosisGroupsBatchResponse response = new SaveTcmDiagnosisGroupsBatchResponse();
        response.setSuccess(Boolean.TRUE);
        response.setSavedGroupKeys(savedGroupKeys);
        return response;
    }

    /**
     * 装配 IF-IP-TCM-33 删除响应。
     *
     * @param success 是否成功
     * @return 删除响应
     */
    @Override
    public DeleteTcmDiagnosisGroupResponse toDeleteTcmDiagnosisGroupResponse(Boolean success) {
        DeleteTcmDiagnosisGroupResponse response = new DeleteTcmDiagnosisGroupResponse();
        response.setSuccess(success);
        return response;
    }

    /**
     * 装配 IF-IP-TCM-34 处方列表响应。
     *
     * @param prescriptions 处方方头 DTO 列表
     * @return 处方列表响应
     */
    @Override
    public ListTcmHerbalPrescriptionsResponse toListTcmHerbalPrescriptionsResponse(List<TcmRxHeaderDto> prescriptions) {
        ListTcmHerbalPrescriptionsResponse response = new ListTcmHerbalPrescriptionsResponse();
        response.setPrescriptions(prescriptions);
        return response;
    }

    /**
     * 将饮片目录分页请求转为住院医嘱目录请求。
     *
     * @param request 中药饮片目录分页请求
     * @return 住院医嘱目录分页请求（限定 MED_MEDICATION_DEFINITION）
     */
    @Override
    public PageInpatientDoctorStationAdviceCatalogRequest toPageInpatientDoctorStationAdviceCatalogRequest(
        PageTcmHerbalMedicationCatalogRequest request) {
        PageInpatientDoctorStationAdviceCatalogRequest catalogRequest = new PageInpatientDoctorStationAdviceCatalogRequest();
        catalogRequest.setEncounterId(request.getEncounterId());
        catalogRequest.setPatientId(request.getPatientId());
        catalogRequest.setKeyword(request.getKeyword());
        catalogRequest.setPageNo(request.getPageNo());
        catalogRequest.setPageSize(request.getPageSize());
        catalogRequest.setPrepickedProductKeys(request.getPrepickedProductKeys());
        catalogRequest.setProductTableNames(List.of(TableNameConstants.MED_MEDICATION_DEFINITION));
        return catalogRequest;
    }

    /**
     * 装配中药草稿创建命令（IF-IP-TCM-36 CREATE）。
     *
     * @param batch       批次请求
     * @param header      方头
     * @param line        明细行
     * @param operatorId  操作人
     * @param orgId       机构主键
     * @param contentJson 方头 content_json
     * @param quantity    业务数量（剂数×单味剂量）
     * @return 创建草稿命令
     */
    @Override
    public CreateHerbalMedicationOrderDraftCommand toCreateHerbalMedicationOrderDraftCommand(
        SaveTcmHerbalPrescriptionDraftBatchRequest batch,
        TcmRxDraftHeaderRequest header,
        TcmRxDraftLineRequest line,
        Long operatorId,
        Long orgId,
        String contentJson,
        BigDecimal quantity) {
        CreateHerbalMedicationOrderDraftCommand cmd = new CreateHerbalMedicationOrderDraftCommand();
        cmd.setOperatorId(operatorId);
        cmd.setEncounterId(batch.getEncounterId());
        cmd.setPatientId(batch.getPatientId());
        cmd.setMedicationId(line.getMedicationDefinitionId());
        cmd.setTcmFlag(Boolean.TRUE);
        cmd.setChineseHerbsDoseQuantity(header.getChineseHerbsDoseQuantity());
        cmd.setSufferingFlag(header.getSufferingFlag());
        cmd.setQuantity(quantity);
        cmd.setDose(line.getDose());
        cmd.setDoseUnitCode(line.getDoseUnitCode());
        cmd.setUnitCode(resolveHerbalBusinessUnitCode(line));
        cmd.setMethodCode(header.getMethodCode());
        cmd.setRateCode(header.getRateCode());
        cmd.setDispensePerDuration(header.getDays());
        cmd.setContentJson(contentJson);
        cmd.setRemark(header.getRemark());
        cmd.setOrgId(orgId);
        cmd.setPerformLocation(line.getDispenseLocationId());
        cmd.setRecorderId(operatorId);
        cmd.setSortNumber(line.getSortNumber());
        cmd.setDosageInstruction(trimToNull(line.getDosageInstructionCode()));
        cmd.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        return cmd;
    }

    /**
     * 装配中药草稿修改命令。
     *
     * @param header         方头
     * @param line           明细行
     * @param operatorId     操作人
     * @param contentJson    方头 content_json
     * @param quantity       业务数量
     * @param prescriptionNo 处方号（签发前预写时可传）
     * @param rxTypeCode     处方类型 code
     * @return 修改草稿命令
     */
    @Override
    public ChangeHerbalMedicationOrderDraftCommand toChangeHerbalMedicationOrderDraftCommand(
        TcmRxDraftHeaderRequest header,
        TcmRxDraftLineRequest line,
        Long operatorId,
        String contentJson,
        BigDecimal quantity,
        String prescriptionNo,
        String rxTypeCode) {
        ChangeHerbalMedicationOrderDraftCommand cmd = new ChangeHerbalMedicationOrderDraftCommand();
        cmd.setOperatorId(operatorId);
        cmd.setId(line.getMedicationRequestId());
        cmd.setMedicationId(line.getMedicationDefinitionId());
        cmd.setChineseHerbsDoseQuantity(header.getChineseHerbsDoseQuantity());
        cmd.setSufferingFlag(header.getSufferingFlag());
        cmd.setQuantity(quantity);
        cmd.setDose(line.getDose());
        cmd.setDoseUnitCode(line.getDoseUnitCode());
        cmd.setUnitCode(resolveHerbalBusinessUnitCode(line));
        cmd.setMethodCode(header.getMethodCode());
        cmd.setRateCode(header.getRateCode());
        cmd.setDispensePerDuration(header.getDays());
        cmd.setContentJson(contentJson);
        cmd.setRemark(header.getRemark());
        cmd.setSortNumber(line.getSortNumber());
        cmd.setPerformLocation(line.getDispenseLocationId());
        cmd.setPrescriptionNo(prescriptionNo);
        cmd.setRxTypeCode(rxTypeCode);
        if (line.getDosageInstructionCode() != null) {
            cmd.setDosageInstruction(trimToEmpty(line.getDosageInstructionCode()));
        }
        return cmd;
    }

    /**
     * 装配仅写 therapy_enum 的草稿修改命令。
     *
     * @param medicationRequestId 药品申请主键
     * @param therapyEnum         长临 code
     * @param operatorId          操作人
     * @return 修改草稿命令
     */
    @Override
    public ChangeMedicationOrderDraftCommand toChangeMedicationOrderDraftForTherapy(
        Long medicationRequestId, String therapyEnum, Long operatorId) {
        ChangeMedicationOrderDraftCommand cmd = new ChangeMedicationOrderDraftCommand();
        cmd.setOperatorId(operatorId);
        cmd.setId(medicationRequestId);
        cmd.setTherapyEnum(therapyEnum);
        return cmd;
    }

    /**
     * 签发前预写处方号与 rx_type_code。
     *
     * @param medicationRequestId 药品申请主键
     * @param operatorId          操作人
     * @param prescriptionNo      处方号
     * @param rxTypeCode          处方类型 code
     * @return 修改草稿命令
     */
    @Override
    public ChangeHerbalMedicationOrderDraftCommand toPreSignHerbalMedicationOrderDraftCommand(
        Long medicationRequestId, Long operatorId, String prescriptionNo, String rxTypeCode) {
        ChangeHerbalMedicationOrderDraftCommand cmd = new ChangeHerbalMedicationOrderDraftCommand();
        cmd.setOperatorId(operatorId);
        cmd.setId(medicationRequestId);
        cmd.setPrescriptionNo(prescriptionNo);
        cmd.setRxTypeCode(rxTypeCode);
        return cmd;
    }

    /**
     * 将方头字段序列化为 content_json。
     *
     * @param header 方头请求
     * @return JSON 字符串
     */
    @Override
    public String buildHerbalContentJson(TcmRxDraftHeaderRequest header) {
        JSONObject json = new JSONObject();
        json.put("prescriptionGroupKey", header.getPrescriptionGroupKey());
        json.put("tcmSyndromeGroup", header.getTcmSyndromeGroup());
        json.put("decoctionMethodCode", header.getDecoctionMethodCode());
        json.put("methodCode", header.getMethodCode());
        json.put("days", header.getDays());
        json.put("remark", header.getRemark());
        json.put("sufferingFlag", header.getSufferingFlag());
        json.put("therapyEnum", header.getTherapyEnum());
        return json.toJSONString();
    }

    /**
     * 装配 IF-IP-TCM-36 暂存成功响应。
     *
     * @param newIds 新建行 id 列表
     * @return 暂存成功响应
     */
    @Override
    public SaveTcmHerbalPrescriptionDraftBatchResponse toSaveTcmHerbalPrescriptionDraftBatchResponse(List<Long> newIds) {
        SaveTcmHerbalPrescriptionDraftBatchResponse response = new SaveTcmHerbalPrescriptionDraftBatchResponse();
        response.setSuccess(Boolean.TRUE);
        response.setNewMedicationRequestIds(newIds);
        response.setMessage(InpatientDoctorStationTcmConstants.RESPONSE_MESSAGE_OK);
        return response;
    }

    /**
     * 装配 IF-IP-TCM-37 签发成功响应。
     *
     * @param prescriptionNoByGroupKey 组 key → 处方号
     * @return 签发成功响应
     */
    @Override
    public SignTcmHerbalPrescriptionBatchResponse toSignTcmHerbalPrescriptionBatchResponse(
        Map<String, String> prescriptionNoByGroupKey) {
        SignTcmHerbalPrescriptionBatchResponse response = new SignTcmHerbalPrescriptionBatchResponse();
        response.setSuccess(Boolean.TRUE);
        response.setPrescriptionNoByGroupKey(prescriptionNoByGroupKey);
        return response;
    }

    /**
     * 装配签发命令。
     *
     * @param target         签发目标
     * @param prescriptionNo 处方号
     * @param operatorId     操作人
     * @return 签发命令
     */
    @Override
    public SignMedicationOrderCommand toSignMedicationOrderCommand(
        SignTcmRxTargetRequest target, String prescriptionNo, Long operatorId) {
        SignMedicationOrderCommand cmd = new SignMedicationOrderCommand();
        cmd.setOperatorId(operatorId);
        cmd.setId(target.getMedicationRequestId());
        cmd.setPrescriptionNo(prescriptionNo);
        return cmd;
    }

    /**
     * 装配签退命令。
     *
     * @param requestId  药品申请主键
     * @param operatorId 操作人
     * @return 签退命令
     */
    @Override
    public SignOffMedicationOrderCommand toSignOffMedicationOrderCommand(Long requestId, Long operatorId) {
        SignOffMedicationOrderCommand cmd = new SignOffMedicationOrderCommand();
        cmd.setOperatorId(operatorId);
        cmd.setId(requestId);
        return cmd;
    }

    /**
     * 装配退回命令（医生签退已校对行）。
     *
     * @param requestId  药品申请主键
     * @param operatorId 操作人
     * @param reason     退回原因
     * @return 退回命令
     */
    @Override
    public RejectMedicationOrderCommand toRejectMedicationOrderCommand(Long requestId, Long operatorId, String reason) {
        RejectMedicationOrderCommand cmd = new RejectMedicationOrderCommand();
        cmd.setOperatorId(operatorId);
        cmd.setId(requestId);
        cmd.setPerformerCheckId(operatorId);
        cmd.setCheckTime(LocalDateTime.now());
        cmd.setReason(reason);
        return cmd;
    }

    /**
     * 装配删除草稿命令。
     *
     * @param requestId  药品申请主键
     * @param operatorId 操作人
     * @return 删除草稿命令
     */
    @Override
    public RemoveMedicationOrderDraftCommand toRemoveMedicationOrderDraftCommand(Long requestId, Long operatorId) {
        RemoveMedicationOrderDraftCommand cmd = new RemoveMedicationOrderDraftCommand();
        cmd.setOperatorId(operatorId);
        cmd.setId(requestId);
        return cmd;
    }

    /**
     * 装配 IF-IP-TCM-38 签退/删除响应。
     *
     * @param success 是否成功
     * @return 签退/删除响应
     */
    @Override
    public WithdrawOrDeleteTcmHerbalPrescriptionResponse toWithdrawOrDeleteTcmHerbalPrescriptionResponse(Boolean success) {
        WithdrawOrDeleteTcmHerbalPrescriptionResponse response = new WithdrawOrDeleteTcmHerbalPrescriptionResponse();
        response.setSuccess(success);
        return response;
    }

    /**
     * 装配 IF-IP-TCM-39 历史方响应。
     *
     * @param rows 历史方行 DTO
     * @return 历史方响应
     */
    @Override
    public ListPatientTcmHerbalPrescriptionHistoryResponse toListPatientTcmHerbalPrescriptionHistoryResponse(
        List<TcmRxHistoryRowDto> rows) {
        ListPatientTcmHerbalPrescriptionHistoryResponse response = new ListPatientTcmHerbalPrescriptionHistoryResponse();
        response.setHistoryRows(rows);
        return response;
    }

    /**
     * 装配长期停嘱命令。
     *
     * @param requestId  药品申请主键
     * @param stopTime   停嘱时间；null 时用当前时间
     * @param operatorId 操作人
     * @return 停嘱命令
     */
    @Override
    public StopLongTermMedicationOrderCommand toStopLongTermMedicationOrderCommand(
        Long requestId, LocalDateTime stopTime, Long operatorId) {
        StopLongTermMedicationOrderCommand cmd = new StopLongTermMedicationOrderCommand();
        cmd.setOperatorId(operatorId);
        cmd.setId(requestId);
        if (stopTime != null) {
            cmd.setStopTime(stopTime);
        } else {
            cmd.setStopTime(LocalDateTime.now());
        }
        return cmd;
    }

    /**
     * 装配取消停嘱命令。
     *
     * @param requestId  药品申请主键
     * @param operatorId 操作人
     * @return 取消停嘱命令
     */
    @Override
    public CancelStopLongTermMedicationOrderCommand toCancelStopLongTermMedicationOrderCommand(
        Long requestId, Long operatorId) {
        CancelStopLongTermMedicationOrderCommand cmd = new CancelStopLongTermMedicationOrderCommand();
        cmd.setOperatorId(operatorId);
        cmd.setId(requestId);
        return cmd;
    }

    /**
     * 装配 IF-IP-TCM-40 停嘱响应。
     *
     * @param success 是否成功
     * @param message 提示信息
     * @return 停嘱响应
     */
    @Override
    public StopLongTermTcmHerbalPrescriptionResponse toStopLongTermTcmHerbalPrescriptionResponse(
        Boolean success, String message) {
        StopLongTermTcmHerbalPrescriptionResponse response = new StopLongTermTcmHerbalPrescriptionResponse();
        response.setSuccess(success);
        response.setMessage(message);
        return response;
    }

    /**
     * 装配 IF-IP-TCM-41 取消停嘱响应。
     *
     * @param success 是否成功
     * @param message 提示信息
     * @return 取消停嘱响应
     */
    @Override
    public CancelStopLongTermTcmHerbalPrescriptionResponse toCancelStopLongTermTcmHerbalPrescriptionResponse(
        Boolean success, String message) {
        CancelStopLongTermTcmHerbalPrescriptionResponse response = new CancelStopLongTermTcmHerbalPrescriptionResponse();
        response.setSuccess(success);
        response.setMessage(message);
        return response;
    }

    /**
     * 解析中药行业务单位 code（优先 doseUnitCode）。
     *
     * @param line 明细行
     * @return trim 后的单位 code 或 null
     */
    private static String resolveHerbalBusinessUnitCode(TcmRxDraftLineRequest line) {
        if (line == null) {
            return null;
        }
        return trimToNull(line.getDoseUnitCode());
    }
}
