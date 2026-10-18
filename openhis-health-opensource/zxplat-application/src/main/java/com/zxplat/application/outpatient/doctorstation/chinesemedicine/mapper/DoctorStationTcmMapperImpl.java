/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.chinesemedicine.mapper;

import static com.zxplat.framework.core.application.util.StringUtils.trimToEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.alibaba.fastjson2.JSONObject;
import com.zxplat.application.outpatient.doctorstation.advice.request.PageDoctorStationAdviceCatalogRequest;
import com.zxplat.application.outpatient.doctorstation.chinesemedicine.dto.*;
import com.zxplat.application.outpatient.doctorstation.chinesemedicine.request.*;
import com.zxplat.application.outpatient.doctorstation.chinesemedicine.response.*;
import com.zxplat.domain.administration.encounter.command.BatchUpsertEncounterDiagnosisAssociationCommand;
import com.zxplat.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.zxplat.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;
import com.zxplat.domain.medication.medicationrequest.command.ChangeHerbalMedicationOrderDraftCommand;
import com.zxplat.domain.medication.medicationrequest.command.CreateHerbalMedicationOrderDraftCommand;
import com.zxplat.domain.medication.medicationrequest.queries.model.TcmHerbalPrescriptionHistoryGroupQueryModel;
import com.zxplat.domain.share.constants.DoctorStationDiagnosisConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.share.enums.IptPatientDiseaseDiagType;
import com.zxplat.domain.share.enums.YbMedType;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.application.shared.validator.PatientEncounterConsistencyGate;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
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
     * 将全量病证组保存请求转为中医就诊关联 batchUpsert 命令。
     *
     * @param request        全量批次请求
     * @param groupKeyByItem 稳定键 → 组号
     * @param operatorId     操作人
     * @return 中医批次 upsert 命令
     */
    @Override
    public BatchUpsertEncounterDiagnosisAssociationCommand toBatchUpsertTcmEncounterDiagnosisAssociationCommand(
        SaveTcmDiagnosisGroupsBatchRequest request,
        Map<String, String> groupKeyByItem,
        Long operatorId) {
        BatchUpsertEncounterDiagnosisAssociationCommand cmd = new BatchUpsertEncounterDiagnosisAssociationCommand();
        cmd.setOperatorId(operatorId);
        BatchUpsertEncounterDiagnosisAssociationCommand.BatchUpsertEncounterDiagnosisAssociationItemCommand batchItem =
            new BatchUpsertEncounterDiagnosisAssociationCommand.BatchUpsertEncounterDiagnosisAssociationItemCommand();
        batchItem.setEncounterId(request.getEncounterId());
        List<BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand> diagnosisItems = new ArrayList<>();
        for (TcmDiagnosisGroupItemRequest group : request.getGroups()) {
            String groupKey = groupKeyByItem.get(resolveTcmGroupStableKey(group));
            // 仅写入已选侧目录，支持院点单侧成组
            if (group.getDiseaseDefinitionId() != null) {
                diagnosisItems.add(toAssocItem(
                    group.getDiseaseEncounterDiagnosisId(), group.getDiseaseDefinitionId(), group, groupKey));
            }
            if (group.getSyndromeDefinitionId() != null) {
                diagnosisItems.add(toAssocItem(
                    group.getSyndromeEncounterDiagnosisId(), group.getSyndromeDefinitionId(), group, groupKey));
            }
        }
        batchItem.setDiagnosisItems(diagnosisItems);
        cmd.setItem(batchItem);
        return cmd;
    }

    /**
     * 病证组稳定键：双侧 diseaseId:syndromeId；仅病 d:{id}；仅证 s:{id}。
     *
     * @param group 组项请求
     * @return 稳定键字符串
     */
    @Override
    public String resolveTcmGroupStableKey(TcmDiagnosisGroupItemRequest group) {
        Long diseaseId = group.getDiseaseDefinitionId();
        Long syndromeId = group.getSyndromeDefinitionId();
        if (diseaseId != null && syndromeId != null) {
            return String.valueOf(diseaseId) + ":" + String.valueOf(syndromeId);
        }
        if (diseaseId != null) {
            return "d:" + String.valueOf(diseaseId);
        }
        if (syndromeId != null) {
            return "s:" + String.valueOf(syndromeId);
        }
        return "empty";
    }

    /**
     * 组装单条中医就诊诊断关联项（病名或证候一侧）。
     *
     * @param encounterDiagnosisId   已有关联主键，可空表示新建
     * @param conditionDefinitionId  目录定义主键
     * @param group                  组级主诊/排序/描述
     * @param groupKey               tcm_syndrome_group
     * @return 关联命令项
     */
    private BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand toAssocItem(
        Long encounterDiagnosisId, Long conditionDefinitionId, TcmDiagnosisGroupItemRequest group, String groupKey) {
        BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand d =
            new BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand();
        d.setEncounterDiagnosisId(encounterDiagnosisId);
        d.setConditionDefinitionId(conditionDefinitionId);
        // 组级主诊标记同步到组内各行关联
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
        // Pre：以行为主写 contract_id（行空回落方头默认）
        cmd.setContractId(resolveEffectiveContractId(header, line));
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
        // Pre：以行为主写 contract_id（UPDATE 同步本味，行空回落方头）
        cmd.setContractId(resolveEffectiveContractId(header, line));
        return cmd;
    }

    /**
     * 解析本味合同：行级优先，缺省回落方头默认（兼容旧前端只传方头）。
     *
     * @param header 方头
     * @param line   明细行
     * @return 有效合同主键；均可空
     */
    private static Long resolveEffectiveContractId(
        TcmRxDraftHeaderRequest header,
        TcmRxDraftLineRequest line) {
        if (line != null && line.getContractId() != null) {
            return line.getContractId();
        }
        if (header != null) {
            return header.getContractId();
        }
        return null;
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
        row.setReqOpenedTime(model.getReqOpenedTime());
        row.setMedicationNames(medicationNames);
        int lineCount = 0;
        if (model.getLineCount() != null) {
            lineCount = model.getLineCount();
        }
        row.setSummaryText(lineCount + "味");
        // 开立人姓名由 Assembler enrichHistoryGroups 批量补齐
        return row;
    }
}
