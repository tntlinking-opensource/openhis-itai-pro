/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.mapper;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.health.application.outpatient.doctorstation.advice.request.PageDoctorStationAdviceCatalogRequest;
import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.dto.*;
import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.request.*;
import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.response.*;
import com.openhis.health.domain.administration.encounter.command.BatchUpsertEncounterDiagnosisAssociationCommand;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.clinical.condition.command.SaveDoctorDiagnosisTcmCommand;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;
import com.openhis.health.domain.medication.medicationrequest.command.ChangeHerbalMedicationOrderDraftCommand;
import com.openhis.health.domain.medication.medicationrequest.command.CreateHerbalMedicationOrderDraftCommand;
import com.openhis.health.domain.medication.medicationrequest.queries.model.TcmHerbalPrescriptionHistoryGroupQueryModel;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.IptPatientDiseaseDiagType;
import com.openhis.health.domain.share.enums.YbMedType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.application.shared.validator.PatientEncounterConsistencyGate;
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
 * {@link DoctorStationTcmMapper} 实现：字段搬运与门诊中医 Tab 命令组装。
 *
 * <p>职责：Request ↔ Command/Response 映射；患者就诊 Gate 委托 {@link PatientEncounterConsistencyGate}。</p>
 */
@Component
@RequiredArgsConstructor
public class DoctorStationTcmMapperImpl implements DoctorStationTcmMapper {

    private final PatientEncounterConsistencyGate patientEncounterConsistencyGate;

    /**
     * 校验门诊就诊快照存在且请求患者与就诊归属一致。
     *
     * @param snapshot         门诊就诊读模型
     * @param requestPatientId 请求体患者主键
     */
    @Override
    public void validatePatientEncounter(OutpatientEncounterContextQueryModel snapshot, Long requestPatientId) {
        // Gate：就诊快照缺失表示未选定有效门诊上下文
        if (snapshot == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "请先选择患者");
        }
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            requestPatientId, snapshot.getPatientId(),
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请先选择患者");
    }

    /**
     * 装配 IF-DOR-30 病证组列表响应。
     *
     * @param groups 聚合后的病证组行
     * @return 含组列表与组数的响应
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
     * 装配 IF-DOR-31 目录分页响应。
     *
     * @param page 领域分页结果
     * @param rows 已映射的目录行
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
     * 将全量病证组保存请求转为 {@link SaveDoctorDiagnosisTcmCommand}（每组展开为病+证两条事实）。
     *
     * @param request        全量批次请求
     * @param groupKeyByItem 稳定键 → 组号
     * @param operatorId     操作人
     * @return 中医诊断事实 upsert 命令
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
            String groupKey = groupKeyByItem.get(stableGroupKey(group));
            items.add(toTcmItem(group.getDiseaseConditionId(), group.getDiseaseDefinitionId(), group.getDiagnosisDesc(), operatorId));
            items.add(toTcmItem(group.getSyndromeConditionId(), group.getSyndromeDefinitionId(), group.getDiagnosisDesc(), operatorId));
        }
        cmd.setItems(items);
        return cmd;
    }

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
     * 中医诊断组稳定键：diseaseDefinitionId:syndromeDefinitionId。
     *
     * @param group 组项请求
     * @return 稳定键字符串
     */
    private String stableGroupKey(TcmDiagnosisGroupItemRequest group) {
        return String.valueOf(group.getDiseaseDefinitionId()) + ":" + String.valueOf(group.getSyndromeDefinitionId());
    }

    /**
     * 将保存结果转为中医就诊关联 batchUpsert 命令。
     *
     * @param request           全量批次请求
     * @param savedConditionIds 领域返回的事实 id 列表
     * @param groupKeyByItem    稳定键 → 组号
     * @param operatorId        操作人
     * @return 中医批次 upsert 命令
     */
    @Override
    public BatchUpsertEncounterDiagnosisAssociationCommand toBatchUpsertTcmEncounterDiagnosisAssociationCommand(
        SaveTcmDiagnosisGroupsBatchRequest request,
        List<Long> savedConditionIds,
        Map<String, String> groupKeyByItem,
        Long operatorId) {
        // Gate：领域应返回每组 2 条事实 id，数量不符说明持久化异常
        if (savedConditionIds.size() != request.getGroups().size() * 2) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_RULE_CONFLICT, "中医病证保存结果与入参不一致");
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

    private BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand toAssocItem(
        Long conditionId, TcmDiagnosisGroupItemRequest group, String groupKey) {
        BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand d =
            new BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand();
        d.setConditionId(conditionId);
        // 组级主诊标记同步到组内两行关联
        if (group.getPrimaryFlag() != null
            && group.getPrimaryFlag() == DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_YES) {
            d.setPrimaryFlag(Boolean.TRUE);
        } else {
            d.setPrimaryFlag(Boolean.FALSE);
        }
        d.setRank(group.getRank());
        d.setDescription(group.getDiagnosisDesc());
        String medTypeCode = trimToNull(group.getMedicalTypeCode());
        // 医疗类别缺省为普通门诊；非法码拒绝保存
        if (medTypeCode == null) {
            medTypeCode = YbMedType.GENERAL_OUTPATIENT.getCode();
        } else if (EnumUtil.fromCode(YbMedType.class, medTypeCode) == null) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_INVALID_ARGUMENT, "请重新选择医疗类别");
        }
        d.setMedType(medTypeCode);
        d.setDiagTypeCode(IptPatientDiseaseDiagType.OUTPATIENT.getCode());
        d.setTcmSyndromeGroup(groupKey);
        d.setTcmFlag(Boolean.TRUE);
        return d;
    }

    /**
     * 对比快照与入参，找出需删除的历史中医组号。
     *
     * @param existing 当前就诊已有病证组
     * @param request  本次全量提交
     * @return 待删组号列表
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
            // 不在本次批次中的历史组号需先删后保存
            if (row.getTcmSyndromeGroup() != null && !incoming.contains(row.getTcmSyndromeGroup())) {
                removed.add(row.getTcmSyndromeGroup());
            }
        }
        return removed;
    }

    /**
     * 装配 IF-DOR-32 保存成功响应。
     *
     * @param savedGroupKeys 已保存组号
     * @return 保存响应
     */
    @Override
    public SaveTcmDiagnosisGroupsBatchResponse toSaveTcmDiagnosisGroupsBatchResponse(List<String> savedGroupKeys) {
        SaveTcmDiagnosisGroupsBatchResponse response = new SaveTcmDiagnosisGroupsBatchResponse();
        response.setSuccess(Boolean.TRUE);
        response.setSavedGroupKeys(savedGroupKeys);
        return response;
    }

    /**
     * 装配 IF-DOR-33 删除响应。
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
     * 装配 IF-DOR-34 处方列表响应。
     *
     * @param prescriptions enrich 后的方头列表
     * @return 列表响应
     */
    @Override
    public ListTcmRxResponse toListTcmRxResponse(List<TcmRxHeaderDto> prescriptions) {
        ListTcmRxResponse response = new ListTcmRxResponse();
        response.setPrescriptions(prescriptions);
        return response;
    }

    /**
     * 将饮片目录分页请求转为医嘱开立目录请求（强制中草药 category）。
     *
     * @param request IF-DOR-35 请求
     * @return 医嘱目录分页请求
     */
    @Override
    public PageDoctorStationAdviceCatalogRequest toPageDoctorStationAdviceCatalogRequest(PageTcmMedCatalogRequest request) {
        PageDoctorStationAdviceCatalogRequest catalogRequest = new PageDoctorStationAdviceCatalogRequest();
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
     * 装配中药草稿创建命令（IF-DOR-36 CREATE 分支）。
     */
    @Override
    public CreateHerbalMedicationOrderDraftCommand toCreateHerbalMedicationOrderDraftCommand(
        SaveTcmRxDraftBatchRequest batch,
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
        // 方头用药频次写入 herbal Command，落库 med_medication_request.rate_code
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
     * 装配中药草稿修改命令（IF-DOR-36 UPDATE / IF-DOR-37 预写）。
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
        // 方头用药频次写入 herbal Command，落库 med_medication_request.rate_code
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
     * 饮片医嘱开立数量单位：与单剂用量单位一致（如 g），供签发 FIFO 与 {@code quantity} 配套。
     */
    private static String resolveHerbalBusinessUnitCode(TcmRxDraftLineRequest line) {
        if (line == null) {
            return null;
        }
        return trimToNull(line.getDoseUnitCode());
    }

    /**
     * IF-DOR-37 签发前预写处方号与 rx_type_code。
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
     * 将方头字段序列化为 {@code content_json} 快照。
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
        return json.toJSONString();
    }

    /**
     * 装配 IF-DOR-36 暂存成功响应。
     *
     * @param newIds 新建药品申请 id
     * @return 暂存响应
     */
    @Override
    public SaveTcmRxDraftBatchResponse toSaveTcmRxDraftBatchResponse(List<Long> newIds) {
        SaveTcmRxDraftBatchResponse response = new SaveTcmRxDraftBatchResponse();
        response.setSuccess(Boolean.TRUE);
        response.setNewMedicationRequestIds(newIds);
        response.setMessage("保存成功");
        return response;
    }

    /**
     * 装配 IF-DOR-37 签发成功响应。
     *
     * @param prescriptionNoByGroupKey 方标识 → 处方号
     * @return 签发响应
     */
    @Override
    public SignTcmRxBatchResponse toSignTcmRxBatchResponse(
        Map<String, String> prescriptionNoByGroupKey) {
        SignTcmRxBatchResponse response = new SignTcmRxBatchResponse();
        response.setSuccess(Boolean.TRUE);
        response.setPrescriptionNoByGroupKey(prescriptionNoByGroupKey);
        return response;
    }

    /**
     * 装配 IF-DOR-38 签退/删除响应。
     *
     * @param success 是否成功
     * @return 操作响应
     */
    @Override
    public WithdrawOrDeleteTcmRxResponse toWithdrawOrDeleteTcmRxResponse(Boolean success) {
        WithdrawOrDeleteTcmRxResponse response = new WithdrawOrDeleteTcmRxResponse();
        response.setSuccess(success);
        return response;
    }

    /**
     * 装配 IF-DOR-39 历史方列表响应。
     *
     * @param rows 历史方摘要行
     * @return 历史响应
     */
    @Override
    public ListPatientTcmRxHistoryResponse toListPatientTcmRxHistoryResponse(
        List<TcmRxHistoryRowDto> rows) {
        ListPatientTcmRxHistoryResponse response = new ListPatientTcmRxHistoryResponse();
        response.setHistoryRows(rows);
        return response;
    }

    /**
     * 将领域历史方聚合映射为展示行。
     *
     * @param model           领域历史方
     * @param medicationNames 药名列表
     * @return 历史方 DTO
     */
    @Override
    public TcmRxHistoryRowDto toHistoryRow(
        TcmHerbalPrescriptionHistoryGroupQueryModel model, List<String> medicationNames) {
        TcmRxHistoryRowDto row = new TcmRxHistoryRowDto();
        row.setPrescriptionNo(model.getPrescriptionNo());
        row.setEncounterId(model.getEncounterId());
        row.setAuthoredAt(model.getReqAuthoredTime());
        row.setMedicationNames(medicationNames);
        int lineCount = 0;
        if (model.getLineCount() != null) {
            lineCount = model.getLineCount();
        }
        row.setSummaryText(lineCount + "味");
        return row;
    }
}
