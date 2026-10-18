/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.surgeryanesth.mapper;

import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.AnesthesiaApplyFormLineRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.ClinicalProcV3SearchItemDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.PlannedSurgeryFieldsDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.SurgeryApplyFormLineRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.SurgeryApplyFormHeaderDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.AnesthesiaApplyFormHeaderDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.GetAnesthesiaApplyFormDetailResponse;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.GetSurgeryApplyFormDetailResponse;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.SearchSurchargeActivityDefinitionResponse;
import com.zxplat.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.zxplat.domain.clinical.proceduredefinition.queries.model.ProcedureDefinitionSearchItemQueryModel;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.zxplat.domain.workflow.servicerequest.command.CreateServiceRequestDraftCommand;
import com.zxplat.domain.workflow.servicerequest.command.RemoveServiceRequestDraftCommand;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestDetailListModel;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestQueryModel;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

/**
 * AS-IP-DOR-012 手术/麻醉申请 Mapper（Request↔Command、QueryModel↔DTO）。
 */
public interface InpatientDoctorStationSurgeryAnesthMapper {

    /**
     * 手术申请单列表头装配。
     *
     * @param rows                 过滤后的 ServiceRequest 行
     * @param requesterNameById    开单人姓名
     * @param pdByActivityId       国临 PD 反查
     * @param activityById         诊疗项目
     * @return 按 prescriptionNo 分组的头列表
     */
    List<SurgeryApplyFormHeaderDto> toSurgeryFormHeaders(
        List<ServiceRequestListModel> rows,
        Map<Long, String> requesterNameById,
        Map<Long, ProcedureDefinitionSearchItemQueryModel> pdByActivityId,
        Map<Long, ActivityDefinition> activityById);

    /**
     * 手术申请单详情装配。
     *
     * @param rows                 单内行
     * @param detailsByRequestId   明细
     * @param pdByActivityId       国临反查
     * @param activityById         诊疗项目
     * @param practitionerNameById 拟人员姓名
     * @return 详情响应
     */
    GetSurgeryApplyFormDetailResponse toSurgeryFormDetail(
        List<ServiceRequestListModel> rows,
        Map<Long, List<ServiceRequestDetailListModel>> detailsByRequestId,
        Map<Long, ProcedureDefinitionSearchItemQueryModel> pdByActivityId,
        Map<Long, ActivityDefinition> activityById,
        Map<Long, String> practitionerNameById);

    /**
     * 国临检索项装配。
     *
     * @param searchItems 域检索项
     * @param activityById  AD enrich
     * @return DTO 列表
     */
    List<ClinicalProcV3SearchItemDto> toClinicalProcV3Items(
        List<ProcedureDefinitionSearchItemQueryModel> searchItems,
        Map<Long, ActivityDefinition> activityById);

    /**
     * 加收 AD 分页装配。
     *
     * @param page AD 分页
     * @return 检索响应
     */
    SearchSurchargeActivityDefinitionResponse toSurchargeItems(Page<ActivityDefinition> page);

    /**
     * 手术行 → 创建草稿子命令。
     *
     * @param line                         行入参
     * @param context                      就诊上下文
     * @param prescriptionNo               AR 号
     * @param planned                      横表
     * @param nestedAnesthesiaPrescriptionNo 嵌套麻醉号
     * @return 单条 create 子命令
     */
    CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand toCreateDraftItem(
        SurgeryApplyFormLineRequest line,
        InpatientEncounterContextQueryModel context,
        String prescriptionNo,
        PlannedSurgeryFieldsDto planned,
        String nestedAnesthesiaPrescriptionNo);

    /**
     * 软删草稿命令。
     *
     * @param id ServiceRequest 主键
     * @return 删除命令（单条 item）
     */
    RemoveServiceRequestDraftCommand toRemoveDraftCommand(Long id);

    /**
     * 麻醉申请单列表头装配。
     *
     * @param rows                      过滤后的行
     * @param requesterNameById         开单人姓名
     * @param activityById              诊疗项目
     * @param linkedSurgeryPrescriptionByAnesthNo 麻醉 AR → 关联手术 AR
     * @return 头列表
     */
    List<AnesthesiaApplyFormHeaderDto> toAnesthesiaFormHeaders(
        List<ServiceRequestListModel> rows,
        Map<Long, String> requesterNameById,
        Map<Long, ActivityDefinition> activityById,
        Map<String, String> linkedSurgeryPrescriptionByAnesthNo);

    /**
     * 麻醉申请单详情装配。
     *
     * @param rows                 单内行
     * @param detailsByRequestId   明细
     * @param activityById         诊疗项目
     * @param practitionerNameById 拟人员姓名
     * @param linkedSurgeryPrescriptionNo 关联手术 AR
     * @return 详情响应
     */
    GetAnesthesiaApplyFormDetailResponse toAnesthesiaFormDetail(
        List<ServiceRequestListModel> rows,
        Map<Long, List<ServiceRequestDetailListModel>> detailsByRequestId,
        Map<Long, ActivityDefinition> activityById,
        Map<Long, String> practitionerNameById,
        String linkedSurgeryPrescriptionNo);

    /**
     * 麻醉行 → 创建草稿子命令（与术式/药品平级，不写 basedOn）。
     *
     * @param line           行入参
     * @param context        就诊上下文
     * @param prescriptionNo AR 号
     * @param planned        横表
     * @return 单条 create 子命令
     */
    CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand toAnesthesiaCreateDraftItem(
        AnesthesiaApplyFormLineRequest line,
        InpatientEncounterContextQueryModel context,
        String prescriptionNo,
        PlannedSurgeryFieldsDto planned);

    /**
     * 术前嘱托 → 创建草稿子命令（basedOn = 术式 SR；类目 52）。
     *
     * @param line                    嘱托行入参
     * @param context                 就诊上下文
     * @param entrustActivityId       内置「嘱托」AD 主键
     * @param basedOnProcedureSrId    挂靠术式 SR
     * @param rateCode                频次（可空）
     * @return 单条 create 子命令
     */
    CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand toEntrustCreateDraftItem(
        SurgeryApplyFormLineRequest line,
        InpatientEncounterContextQueryModel context,
        Long entrustActivityId,
        Long basedOnProcedureSrId,
        String rateCode);

    /**
     * 横表计划字段装配。
     *
     * @param model                主行 QueryModel
     * @param practitionerNameById 人员姓名
     * @return 计划 DTO
     */
    PlannedSurgeryFieldsDto toPlannedFields(
        ServiceRequestQueryModel model,
        Map<Long, String> practitionerNameById);

    /**
     * 嵌套麻醉壳行 → 创建草稿子命令（不写 basedOn；与手术关联靠 nestedAnesthesiaPrescriptionNo）。
     *
     * @param context                  就诊上下文
     * @param anesthesiaPrescriptionNo 麻醉 AR 号
     * @param activityId               复用术式 AD
     * @param unitCode                 单位
     * @param planned                  计划字段（可空）
     * @return 单条 create 子命令
     */
    CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand toNestAnesthesiaShellDraftItem(
        InpatientEncounterContextQueryModel context,
        String anesthesiaPrescriptionNo,
        Long activityId,
        String unitCode,
        PlannedSurgeryFieldsDto planned);
}
