/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.mapper;

import com.openhis.health.application.inpatient.doctorstation.advice.request.PageInpatientDoctorStationAdviceCatalogRequest;
import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.dto.*;
import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.request.*;
import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.response.*;
import com.openhis.health.domain.administration.encounter.command.BatchUpsertEncounterDiagnosisAssociationCommand;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;
import com.openhis.health.domain.medication.medicationrequest.command.*;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 住院医生站中医 Tab：Request/Command/Response 字段映射。
 */
public interface InpatientDoctorStationTcmMapper {

    /**
     * 校验住院就诊快照与请求患者一致。
     *
     * @param snapshot          住院就诊上下文快照
     * @param requestPatientId  请求中的患者主键
     * @throws com.openhis.whale.core.application.exception.BusinessException 快照为空或患者不一致时
     */
    void validatePatientEncounter(InpatientEncounterContextQueryModel snapshot, Long requestPatientId);

    /**
     * 装配 IF-IP-TCM-29 初始化响应。
     *
     * @param tcmMethodOptions 中药用法下拉选项
     * @param rateOptions      用药频次下拉选项
     * @param dosageInstructionOptions 单味特殊煎法下拉选项
     * @return 初始化响应（含默认长临筛选）
     */
    InitInpatientDoctorStationTcmResponse toInitInpatientDoctorStationTcmResponse(
        List<OptionDto> tcmMethodOptions,
        List<OptionDto> rateOptions,
        List<OptionDto> dosageInstructionOptions);

    /**
     * 装配 IF-IP-TCM-30 病证组列表响应。
     *
     * @param groups 病证组行 DTO 列表
     * @return 含 groups 与 totalCount 的响应
     */
    ListTcmDiagnosisGroupsResponse toListTcmDiagnosisGroupsResponse(List<TcmDiagnosisGroupRowDto> groups);

    /**
     * 装配 IF-IP-TCM-31 目录分页响应。
     *
     * @param page 领域分页结果
     * @param rows 映射后的目录行
     * @return 分页响应
     */
    PageTcmConditionDefinitionResponse toPageTcmConditionDefinitionResponse(
        Page<DoctorStationConditionDefinitionRowModel> page, List<TcmDiagnosisCatalogRowDto> rows);

    /**
     * 将全量病证组保存请求转为中医就诊关联 batchUpsert 命令（直挂 conditionDefinitionId）。
     *
     * @param request        全量批次请求
     * @param groupKeyByItem 稳定键 → 组号
     * @param operatorId     操作人
     * @return 中医批次 upsert 命令
     */
    BatchUpsertEncounterDiagnosisAssociationCommand toBatchUpsertTcmEncounterDiagnosisAssociationCommand(
        SaveTcmDiagnosisGroupsBatchRequest request, Map<String, String> groupKeyByItem, Long operatorId);

    /**
     * 病证组稳定键：双侧为 {@code diseaseId:syndromeId}；单侧为 {@code d:{id}} / {@code s:{id}}。
     *
     * @param group 组项请求
     * @return 稳定键
     */
    String resolveTcmGroupStableKey(TcmDiagnosisGroupItemRequest group);

    /**
     * 对比快照与入参，找出需删除的历史中医组号。
     *
     * @param existing 当前快照中的病证组
     * @param request    本次保存请求
     * @return 不在入参中的历史组号列表
     */
    List<String> diffRemovedTcmGroups(List<TcmDiagnosisGroupRowDto> existing, SaveTcmDiagnosisGroupsBatchRequest request);

    /**
     * 装配 IF-IP-TCM-32 保存成功响应。
     *
     * @param savedGroupKeys 已保存的组号列表
     * @return 保存成功响应
     */
    SaveTcmDiagnosisGroupsBatchResponse toSaveTcmDiagnosisGroupsBatchResponse(List<String> savedGroupKeys);

    /**
     * 装配 IF-IP-TCM-33 删除响应。
     *
     * @param success 是否成功
     * @return 删除响应
     */
    DeleteTcmDiagnosisGroupResponse toDeleteTcmDiagnosisGroupResponse(Boolean success);

    /**
     * 装配 IF-IP-TCM-34 处方列表响应。
     *
     * @param prescriptions 处方方头 DTO 列表
     * @return 处方列表响应
     */
    ListTcmHerbalPrescriptionsResponse toListTcmHerbalPrescriptionsResponse(List<TcmRxHeaderDto> prescriptions);

    /**
     * 将饮片目录分页请求转为住院医嘱目录请求。
     *
     * @param request 中药饮片目录分页请求
     * @return 住院医嘱目录分页请求（限定 MED_MEDICATION_DEFINITION）
     */
    PageInpatientDoctorStationAdviceCatalogRequest toPageInpatientDoctorStationAdviceCatalogRequest(
        PageTcmHerbalMedicationCatalogRequest request);

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
    CreateHerbalMedicationOrderDraftCommand toCreateHerbalMedicationOrderDraftCommand(
        SaveTcmHerbalPrescriptionDraftBatchRequest batch,
        TcmRxDraftHeaderRequest header,
        TcmRxDraftLineRequest line,
        Long operatorId,
        Long orgId,
        String contentJson,
        java.math.BigDecimal quantity);

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
    ChangeHerbalMedicationOrderDraftCommand toChangeHerbalMedicationOrderDraftCommand(
        TcmRxDraftHeaderRequest header,
        TcmRxDraftLineRequest line,
        Long operatorId,
        String contentJson,
        java.math.BigDecimal quantity,
        String prescriptionNo,
        String rxTypeCode);

    /**
     * 装配仅写 therapy_enum 的草稿修改命令。
     *
     * @param medicationRequestId 药品申请主键
     * @param therapyEnum         长临 code
     * @param operatorId          操作人
     * @return 修改草稿命令
     */
    ChangeMedicationOrderDraftCommand toChangeMedicationOrderDraftForTherapy(
        Long medicationRequestId, String therapyEnum, Long operatorId);

    /**
     * 签发前预写处方号与 rx_type_code。
     *
     * @param medicationRequestId 药品申请主键
     * @param operatorId          操作人
     * @param prescriptionNo      处方号
     * @param rxTypeCode          处方类型 code
     * @return 修改草稿命令
     */
    ChangeHerbalMedicationOrderDraftCommand toPreSignHerbalMedicationOrderDraftCommand(
        Long medicationRequestId, Long operatorId, String prescriptionNo, String rxTypeCode);

    /**
     * 将方头字段序列化为 content_json。
     *
     * @param header 方头请求
     * @return JSON 字符串
     */
    String buildHerbalContentJson(TcmRxDraftHeaderRequest header);

    /**
     * 装配 IF-IP-TCM-36 暂存成功响应。
     *
     * @param newIds 新建行 id 列表
     * @return 暂存成功响应
     */
    SaveTcmHerbalPrescriptionDraftBatchResponse toSaveTcmHerbalPrescriptionDraftBatchResponse(List<Long> newIds);

    /**
     * 装配 IF-IP-TCM-37 签发成功响应。
     *
     * @param prescriptionNoByGroupKey 组 key → 处方号
     * @return 签发成功响应
     */
    SignTcmHerbalPrescriptionBatchResponse toSignTcmHerbalPrescriptionBatchResponse(
        Map<String, String> prescriptionNoByGroupKey);

    /**
     * 装配签发命令。
     *
     * @param target         签发目标
     * @param prescriptionNo 处方号
     * @param operatorId     操作人
     * @return 签发命令
     */
    SignMedicationOrderCommand toSignMedicationOrderCommand(
        SignTcmRxTargetRequest target, String prescriptionNo, Long operatorId);

    /**
     * 装配签退命令。
     *
     * @param requestId  药品申请主键
     * @param operatorId 操作人
     * @return 签退命令
     */
    SignOffMedicationOrderCommand toSignOffMedicationOrderCommand(Long requestId, Long operatorId);

    /**
     * 装配退回命令（医生签退已校对行）。
     *
     * @param requestId  药品申请主键
     * @param operatorId 操作人
     * @param reason     退回原因
     * @return 退回命令
     */
    RejectMedicationOrderCommand toRejectMedicationOrderCommand(Long requestId, Long operatorId, String reason);

    /**
     * 装配删除草稿命令。
     *
     * @param requestId  药品申请主键
     * @param operatorId 操作人
     * @return 删除草稿命令
     */
    RemoveMedicationOrderDraftCommand toRemoveMedicationOrderDraftCommand(Long requestId, Long operatorId);

    /**
     * 装配 IF-IP-TCM-38 签退/删除响应。
     *
     * @param success 是否成功
     * @return 签退/删除响应
     */
    WithdrawOrDeleteTcmHerbalPrescriptionResponse toWithdrawOrDeleteTcmHerbalPrescriptionResponse(Boolean success);

    /**
     * 装配 IF-IP-TCM-39 历史方响应。
     *
     * @param rows 历史方行 DTO
     * @return 历史方响应
     */
    ListPatientTcmHerbalPrescriptionHistoryResponse toListPatientTcmHerbalPrescriptionHistoryResponse(
        List<TcmRxHistoryRowDto> rows);

    /**
     * 装配长期停嘱命令。
     *
     * @param requestId  药品申请主键
     * @param stopTime   停嘱时间；null 时用当前时间
     * @param operatorId 操作人
     * @return 停嘱命令
     */
    StopLongTermMedicationOrderCommand toStopLongTermMedicationOrderCommand(
        Long requestId, LocalDateTime stopTime, Long operatorId);

    /**
     * 装配取消停嘱命令。
     *
     * @param requestId  药品申请主键
     * @param operatorId 操作人
     * @return 取消停嘱命令
     */
    CancelStopLongTermMedicationOrderCommand toCancelStopLongTermMedicationOrderCommand(
        Long requestId, Long operatorId);

    /**
     * 装配 IF-IP-TCM-40 停嘱响应。
     *
     * @param success 是否成功
     * @param message 提示信息
     * @return 停嘱响应
     */
    StopLongTermTcmHerbalPrescriptionResponse toStopLongTermTcmHerbalPrescriptionResponse(Boolean success, String message);

    /**
     * 装配 IF-IP-TCM-41 取消停嘱响应。
     *
     * @param success 是否成功
     * @param message 提示信息
     * @return 取消停嘱响应
     */
    CancelStopLongTermTcmHerbalPrescriptionResponse toCancelStopLongTermTcmHerbalPrescriptionResponse(
        Boolean success, String message);
}
