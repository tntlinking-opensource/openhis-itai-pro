/*
 * Copyright © 2026 OpenHIS Contributors
 */
package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.mapper;

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
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

/**
 * 门诊医生站中医 Tab：Request/Command/Response 字段映射（不含业务流程判断）。
 */
public interface DoctorStationTcmMapper {

    /**
     * Gate：校验请求患者与就诊快照归属一致。
     *
     * @param snapshot         门诊就诊读模型
     * @param requestPatientId 请求体患者主键
     */
    void validatePatientEncounter(OutpatientEncounterContextQueryModel snapshot, Long requestPatientId);

    /**
     * 装配 IF-DOR-30 病证组列表响应。
     *
     * @param groups 聚合后的病证组行
     * @return 含组列表与组数的响应
     */
    ListTcmDiagnosisGroupsResponse toListTcmDiagnosisGroupsResponse(List<TcmDiagnosisGroupRowDto> groups);

    /**
     * 装配 IF-DOR-31 目录分页响应。
     *
     * @param page 领域分页结果
     * @param rows 已映射的目录行
     * @return 分页响应
     */
    PageTcmConditionDefinitionResponse toPageTcmConditionDefinitionResponse(
        Page<DoctorStationConditionDefinitionRowModel> page, List<TcmDiagnosisCatalogRowDto> rows);

    /**
     * 将全量病证组保存请求转为 {@link SaveDoctorDiagnosisTcmCommand}。
     *
     * @param request         全量批次请求
     * @param groupKeyByItem  稳定键 → 组号
     * @param operatorId      操作人
     * @return 中医诊断事实 upsert 命令
     */
    SaveDoctorDiagnosisTcmCommand toSaveDoctorDiagnosisTcmCommand(
        SaveTcmDiagnosisGroupsBatchRequest request, Map<String, String> groupKeyByItem, Long operatorId);

    /**
     * 将保存结果转为中医就诊关联 batchUpsert 命令（仅 tcm_flag 子集）。
     *
     * @param request           全量批次请求
     * @param savedConditionIds 领域返回的事实 id 列表（每组 2 条）
     * @param groupKeyByItem    稳定键 → 组号
     * @param operatorId        操作人
     * @return 中医批次 upsert 命令
     */
    BatchUpsertEncounterDiagnosisAssociationCommand toBatchUpsertTcmEncounterDiagnosisAssociationCommand(
        SaveTcmDiagnosisGroupsBatchRequest request, List<Long> savedConditionIds, Map<String, String> groupKeyByItem, Long operatorId);

    /**
     * 对比快照与入参，找出需删除的历史中医组号。
     *
     * @param existing 当前就诊已有病证组
     * @param request  本次全量提交
     * @return 待删组号列表
     */
    List<String> diffRemovedTcmGroups(List<TcmDiagnosisGroupRowDto> existing, SaveTcmDiagnosisGroupsBatchRequest request);

    /**
     * 装配 IF-DOR-32 保存成功响应。
     *
     * @param savedGroupKeys 已保存组号
     * @return 保存响应
     */
    SaveTcmDiagnosisGroupsBatchResponse toSaveTcmDiagnosisGroupsBatchResponse(List<String> savedGroupKeys);

    /**
     * 装配 IF-DOR-33 删除响应。
     *
     * @param success 是否成功
     * @return 删除响应
     */
    DeleteTcmDiagnosisGroupResponse toDeleteTcmDiagnosisGroupResponse(Boolean success);

    /**
     * 装配 IF-DOR-34 处方列表响应。
     *
     * @param prescriptions enrich 后的方头列表
     * @return 列表响应
     */
    ListTcmRxResponse toListTcmRxResponse(List<TcmRxHeaderDto> prescriptions);

    /**
     * 将饮片目录分页请求转为医嘱开立目录请求（强制中草药 category）。
     *
     * @param request IF-DOR-35 请求
     * @return 医嘱目录分页请求
     */
    PageDoctorStationAdviceCatalogRequest toPageDoctorStationAdviceCatalogRequest(PageTcmMedCatalogRequest request);

    /**
     * 装配中药草稿创建命令（IF-DOR-36 CREATE 分支）。
     */
    CreateHerbalMedicationOrderDraftCommand toCreateHerbalMedicationOrderDraftCommand(
        SaveTcmRxDraftBatchRequest batch,
        TcmRxDraftHeaderRequest header,
        TcmRxDraftLineRequest line,
        Long operatorId,
        Long orgId,
        String contentJson,
        java.math.BigDecimal quantity);

    /**
     * 装配中药草稿修改命令（IF-DOR-36 UPDATE / IF-DOR-37 预写）。
     */
    ChangeHerbalMedicationOrderDraftCommand toChangeHerbalMedicationOrderDraftCommand(
        TcmRxDraftHeaderRequest header,
        TcmRxDraftLineRequest line,
        Long operatorId,
        String contentJson,
        java.math.BigDecimal quantity,
        String prescriptionNo,
        String rxTypeCode);

    /**
     * IF-DOR-37 签发前预写处方号与 rx_type_code。
     */
    ChangeHerbalMedicationOrderDraftCommand toPreSignHerbalMedicationOrderDraftCommand(
        Long medicationRequestId, Long operatorId, String prescriptionNo, String rxTypeCode);

    /**
     * 将方头字段序列化为 {@code content_json} 快照。
     *
     * @param header 方头请求
     * @return JSON 字符串
     */
    String buildHerbalContentJson(TcmRxDraftHeaderRequest header);

    /**
     * 装配 IF-DOR-36 暂存成功响应。
     *
     * @param newIds 新建药品申请 id
     * @return 暂存响应
     */
    SaveTcmRxDraftBatchResponse toSaveTcmRxDraftBatchResponse(List<Long> newIds);

    /**
     * 装配 IF-DOR-37 签发成功响应。
     *
     * @param prescriptionNoByGroupKey 方标识 → 处方号
     * @param signCode                   批次签发码
     * @return 签发响应
     */
    SignTcmRxBatchResponse toSignTcmRxBatchResponse(
        Map<String, String> prescriptionNoByGroupKey, String signCode);

    /**
     * 装配 IF-DOR-38 签退/删除响应。
     *
     * @param success 是否成功
     * @return 操作响应
     */
    WithdrawOrDeleteTcmRxResponse toWithdrawOrDeleteTcmRxResponse(Boolean success);

    /**
     * 装配 IF-DOR-39 历史方列表响应。
     *
     * @param rows 历史方摘要行
     * @return 历史响应
     */
    ListPatientTcmRxHistoryResponse toListPatientTcmRxHistoryResponse(
        List<TcmRxHistoryRowDto> rows);

    /**
     * 将领域历史方聚合映射为展示行（不含药名 enrich，由 Assembler 负责）。
     *
     * @param model           领域历史方
     * @param medicationNames 药名列表
     * @return 历史方 DTO
     */
    TcmRxHistoryRowDto toHistoryRow(
        TcmHerbalPrescriptionHistoryGroupQueryModel model, List<String> medicationNames);
}
