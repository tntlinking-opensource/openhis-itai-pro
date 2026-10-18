/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.surgeryanesth.mapper;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.AnesthesiaApplyFormHeaderDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.AnesthesiaApplyFormLineDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.AnesthesiaApplyFormLineRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.ClinicalProcV3SearchItemDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.PlannedPerformerDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.PlannedSurgeryFieldsDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.SurgeryApplyFormHeaderDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.SurgeryApplyFormLineDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.SurgeryApplyFormLineRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.SurchargeActivityDefinitionItemDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.GetAnesthesiaApplyFormDetailResponse;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.GetSurgeryApplyFormDetailResponse;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.response.SearchSurchargeActivityDefinitionResponse;
import com.zxplat.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.zxplat.domain.clinical.proceduredefinition.queries.model.ProcedureDefinitionSearchItemQueryModel;
import com.zxplat.domain.share.constants.ClinicalProcedureConstants;
import com.zxplat.domain.share.constants.ServiceRequestCategoryConstants;
import com.zxplat.domain.share.constants.SurgeryAnesthApplyConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.share.enums.RequestStatus;
import com.zxplat.domain.share.enums.TherapyType;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.zxplat.domain.workflow.servicerequest.command.CreateServiceRequestDraftCommand;
import com.zxplat.domain.workflow.servicerequest.command.RemoveServiceRequestDraftCommand;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestDetailListModel;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestQueryModel;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link InpatientDoctorStationSurgeryAnesthMapper} 实现（AS-IP-DOR-012）。
 *
 * <p>纯 DTO/Command 装配，不含领域写与门控。</p>
 */
@Component
public class InpatientDoctorStationSurgeryAnesthMapperImpl implements InpatientDoctorStationSurgeryAnesthMapper {

    /** 国临手术定义明细锚表（cln_procedure_definition）。 */
    private static final String TABLE_CLN_PROCEDURE_DEFINITION = "cln_procedure_definition";

    /** 头状态：混合。 */
    private static final String HEADER_STATUS_MIXED = "MIXED";

    /**
     * 手术申请单列表头装配。
     *
     * @param rows                 过滤后的 ServiceRequest 行
     * @param requesterNameById    开单人姓名
     * @param pdByActivityId       国临 PD 反查
     * @param activityById         诊疗项目
     * @return 按 prescriptionNo 分组的头列表
     */
    @Override
    public List<SurgeryApplyFormHeaderDto> toSurgeryFormHeaders(
        List<ServiceRequestListModel> rows,
        Map<Long, String> requesterNameById,
        Map<Long, ProcedureDefinitionSearchItemQueryModel> pdByActivityId,
        Map<Long, ActivityDefinition> activityById) {
        Map<String, List<ServiceRequestListModel>> grouped = groupByPrescriptionNo(rows);
        List<SurgeryApplyFormHeaderDto> result = new ArrayList<>();
        for (Map.Entry<String, List<ServiceRequestListModel>> entry : grouped.entrySet()) {
            List<ServiceRequestListModel> groupRows = sortGroupRows(entry.getValue());
            ServiceRequestListModel carrier = resolvePlannedCarrierLine(groupRows);
            SurgeryApplyFormHeaderDto header = new SurgeryApplyFormHeaderDto();
            header.setPrescriptionNo(entry.getKey());
            header.setStatusEnum(aggregateHeaderStatus(groupRows));
            header.setLineCount(groupRows.size());
            if (carrier != null) {
                header.setPlannedProcedureTime(carrier.getProcedureTime());
                header.setNestedAnesthesiaPrescriptionNo(carrier.getNestedAnesthesiaPrescriptionNo());
                header.setMainProcedureName(resolveMainProcedureName(carrier, pdByActivityId, activityById));
                if (carrier.getRequesterId() != null && requesterNameById != null) {
                    header.setRequesterName(requesterNameById.get(carrier.getRequesterId()));
                }
            }
            result.add(header);
        }
        return result;
    }

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
    @Override
    public GetSurgeryApplyFormDetailResponse toSurgeryFormDetail(
        List<ServiceRequestListModel> rows,
        Map<Long, List<ServiceRequestDetailListModel>> detailsByRequestId,
        Map<Long, ProcedureDefinitionSearchItemQueryModel> pdByActivityId,
        Map<Long, ActivityDefinition> activityById,
        Map<Long, String> practitionerNameById) {
        List<ServiceRequestListModel> groupRows = sortGroupRows(rows);
        GetSurgeryApplyFormDetailResponse response = new GetSurgeryApplyFormDetailResponse();
        if (groupRows.isEmpty()) {
            return response;
        }
        Map<Long, String> names = requesterNameByIdSafe(practitionerNameById);
        List<SurgeryApplyFormHeaderDto> headers = toSurgeryFormHeaders(
            groupRows, names, pdByActivityId, activityById);
        if (!headers.isEmpty()) {
            response.setHeader(headers.get(0));
        }
        List<SurgeryApplyFormLineDto> lineDtos = new ArrayList<>();
        for (ServiceRequestListModel row : groupRows) {
            SurgeryApplyFormLineDto lineDto =
                toSurgeryLineDto(row, detailsByRequestId, pdByActivityId, activityById);
            if (lineDto != null) {
                lineDtos.add(lineDto);
            }
        }
        response.setLines(lineDtos);
        ServiceRequestListModel carrier = resolvePlannedCarrierLine(groupRows);
        if (carrier != null) {
            PlannedSurgeryFieldsDto planned = toPlannedFieldsFromListModel(carrier, practitionerNameById);
            if (planned != null) {
                response.setPlanned(planned);
            }
        }
        return response;
    }

    /**
     * 国临检索项装配。
     *
     * @param searchItems 域检索项
     * @param activityById  AD enrich
     * @return DTO 列表
     */
    @Override
    public List<ClinicalProcV3SearchItemDto> toClinicalProcV3Items(
        List<ProcedureDefinitionSearchItemQueryModel> searchItems,
        Map<Long, ActivityDefinition> activityById) {
        List<ClinicalProcV3SearchItemDto> items = new ArrayList<>();
        if (searchItems == null) {
            return items;
        }
        for (ProcedureDefinitionSearchItemQueryModel model : searchItems) {
            if (model == null) {
                continue;
            }
            ClinicalProcV3SearchItemDto dto = new ClinicalProcV3SearchItemDto();
            dto.setId(model.getId());
            dto.setProcedureCode(model.getProcedureCode());
            dto.setName(model.getName());
            dto.setPyStr(model.getPyStr());
            dto.setWbStr(model.getWbStr());
            dto.setYbNo(model.getYbNo());
            dto.setStatusEnum(model.getStatusEnum());
            dto.setActivityDefinitionId(model.getActivityDefinitionId());
            if (model.getActivityDefinitionId() != null && activityById != null) {
                ActivityDefinition ad = activityById.get(model.getActivityDefinitionId());
                if (ad != null) {
                    dto.setActivityName(ad.getName());
                }
            }
            items.add(dto);
        }
        return items;
    }

    /**
     * 加收 AD 分页装配。
     *
     * @param page AD 分页
     * @return 检索响应
     */
    @Override
    public SearchSurchargeActivityDefinitionResponse toSurchargeItems(Page<ActivityDefinition> page) {
        SearchSurchargeActivityDefinitionResponse response = new SearchSurchargeActivityDefinitionResponse();
        response.setItems(new ArrayList<>());
        response.setTotalCount(0L);
        if (page == null) {
            return response;
        }
        response.setTotalCount(page.getTotalElements());
        for (ActivityDefinition def : page.getContent()) {
            if (def == null || def.getId() == null) {
                continue;
            }
            SurchargeActivityDefinitionItemDto item = new SurchargeActivityDefinitionItemDto();
            item.setActivityDefinitionId(def.getId());
            item.setBusNo(def.getBusNo());
            item.setName(def.getName());
            item.setCategoryCode(def.getCategoryCode());
            item.setUnitCode(resolveActivityUnitCode(def));
            response.getItems().add(item);
        }
        return response;
    }

    /**
     * 手术行 → 创建草稿子命令。
     *
     * <p>开立仅术式：{@code activityId=0} + {@code procedureDefinitionId}。加收由护士划价，医生站不再落 SURCHARGE。</p>
     *
     * @param line                         行入参
     * @param context                      就诊上下文
     * @param prescriptionNo               AR 号
     * @param planned                      横表
     * @param nestedAnesthesiaPrescriptionNo 嵌套麻醉号
     * @return 单条 create 子命令
     */
    @Override
    public CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand toCreateDraftItem(
        SurgeryApplyFormLineRequest line,
        InpatientEncounterContextQueryModel context,
        String prescriptionNo,
        PlannedSurgeryFieldsDto planned,
        String nestedAnesthesiaPrescriptionNo) {
        CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand item =
            new CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand();
        item.setEncounterId(context.getEncounterId());
        item.setPatientId(context.getPatientId());
        item.setQuantity(line.getQuantity());
        item.setUnitCode(line.getUnitCode());
        item.setRateCode(trimToNull(line.getRateCode()));
        item.setCategoryEnum(ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE);
        item.setTherapyEnum(TherapyType.TEMPORARY.getCode());
        item.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        item.setOrgId(context.getOrganizationId());
        item.setPrescriptionNo(prescriptionNo);
        item.setSortNumber(line.getSortNumber());
        item.setNote(line.getNote());
        item.setMainProcedureFlag(line.getMainProcedureFlag());
        item.setNestedAnesthesiaPrescriptionNo(trimToNull(nestedAnesthesiaPrescriptionNo));
        applyPlannedFields(item, planned);
        // 术式行：临床真源落 procedure_definition_id，activity_id=0 占位不计费
        item.setActivityId(SurgeryAnesthApplyConstants.ACTIVITY_ID_PLACEHOLDER);
        item.setProcedureDefinitionId(line.getProcedureDefinitionId());
        item.setDetailItems(buildProcedureDetailItems(line.getProcedureDefinitionId()));
        // 术式临嘱默认执行 1 次
        item.setExecutionCount(1);
        return item;
    }

    /**
     * 软删草稿命令。
     *
     * @param id ServiceRequest 主键
     * @return 删除命令（单条 item）
     */
    @Override
    public RemoveServiceRequestDraftCommand toRemoveDraftCommand(Long id) {
        RemoveServiceRequestDraftCommand command = new RemoveServiceRequestDraftCommand();
        RemoveServiceRequestDraftCommand.RemoveServiceRequestDraftItemCommand item =
            new RemoveServiceRequestDraftCommand.RemoveServiceRequestDraftItemCommand();
        item.setId(id);
        command.setItem(item);
        return command;
    }

    /**
     * 麻醉申请单列表头装配。
     *
     * @param rows                      过滤后的行
     * @param requesterNameById         开单人姓名
     * @param activityById              诊疗项目
     * @param linkedSurgeryPrescriptionByAnesthNo 麻醉 AR → 关联手术 AR
     * @return 头列表
     */
    @Override
    public List<AnesthesiaApplyFormHeaderDto> toAnesthesiaFormHeaders(
        List<ServiceRequestListModel> rows,
        Map<Long, String> requesterNameById,
        Map<Long, ActivityDefinition> activityById,
        Map<String, String> linkedSurgeryPrescriptionByAnesthNo) {
        Map<String, List<ServiceRequestListModel>> grouped = groupByPrescriptionNo(rows);
        List<AnesthesiaApplyFormHeaderDto> result = new ArrayList<>();
        for (Map.Entry<String, List<ServiceRequestListModel>> entry : grouped.entrySet()) {
            List<ServiceRequestListModel> groupRows = sortGroupRows(entry.getValue());
            ServiceRequestListModel first = groupRows.get(0);
            AnesthesiaApplyFormHeaderDto header = new AnesthesiaApplyFormHeaderDto();
            header.setPrescriptionNo(entry.getKey());
            header.setStatusEnum(aggregateHeaderStatus(groupRows));
            header.setLineCount(groupRows.size());
            header.setAnesthesiaMethodCode(first.getAnesthesiaMethodCode());
            if (first.getRequesterId() != null && requesterNameById != null) {
                header.setRequesterName(requesterNameById.get(first.getRequesterId()));
            }
            if (linkedSurgeryPrescriptionByAnesthNo != null) {
                header.setLinkedSurgeryPrescriptionNo(linkedSurgeryPrescriptionByAnesthNo.get(entry.getKey()));
            }
            result.add(header);
        }
        return result;
    }

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
    @Override
    public GetAnesthesiaApplyFormDetailResponse toAnesthesiaFormDetail(
        List<ServiceRequestListModel> rows,
        Map<Long, List<ServiceRequestDetailListModel>> detailsByRequestId,
        Map<Long, ActivityDefinition> activityById,
        Map<Long, String> practitionerNameById,
        String linkedSurgeryPrescriptionNo) {
        List<ServiceRequestListModel> groupRows = sortGroupRows(rows);
        GetAnesthesiaApplyFormDetailResponse response = new GetAnesthesiaApplyFormDetailResponse();
        if (groupRows.isEmpty()) {
            return response;
        }
        Map<String, String> linkedMap = new HashMap<>();
        if (isNotEmpty(linkedSurgeryPrescriptionNo) && isNotEmpty(groupRows.get(0).getPrescriptionNo())) {
            linkedMap.put(groupRows.get(0).getPrescriptionNo(), linkedSurgeryPrescriptionNo);
        }
        List<AnesthesiaApplyFormHeaderDto> headers = toAnesthesiaFormHeaders(
            groupRows, practitionerNameById, activityById, linkedMap);
        if (!headers.isEmpty()) {
            response.setHeader(headers.get(0));
        }
        List<AnesthesiaApplyFormLineDto> lines = new ArrayList<>();
        for (ServiceRequestListModel row : groupRows) {
            AnesthesiaApplyFormLineDto lineDto = toAnesthesiaLineDto(row, detailsByRequestId, activityById);
            if (lineDto != null) {
                lines.add(lineDto);
            }
        }
        response.setLines(lines);
        ServiceRequestListModel carrier = resolvePlannedCarrierLine(groupRows);
        if (carrier != null) {
            PlannedSurgeryFieldsDto planned = toPlannedFieldsFromListModel(carrier, practitionerNameById);
            if (planned != null) {
                response.setPlanned(planned);
            }
        }
        return response;
    }

    /**
     * 麻醉行 → 创建草稿子命令（与术式/药品平级，不写 basedOn）。
     *
     * @param line           行入参
     * @param context        就诊上下文
     * @param prescriptionNo AR 号
     * @param planned        横表
     * @return 单条 create 子命令
     */
    @Override
    public CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand toAnesthesiaCreateDraftItem(
        AnesthesiaApplyFormLineRequest line,
        InpatientEncounterContextQueryModel context,
        String prescriptionNo,
        PlannedSurgeryFieldsDto planned) {
        CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand item =
            new CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand();
        item.setEncounterId(context.getEncounterId());
        item.setPatientId(context.getPatientId());
        item.setActivityId(line.getActivityDefinitionId());
        item.setQuantity(line.getQuantity());
        item.setUnitCode(line.getUnitCode());
        item.setRateCode(trimToNull(line.getRateCode()));
        item.setExecutionCount(resolveExecutionCount(line.getQuantity()));
        item.setCategoryEnum(ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA);
        item.setTherapyEnum(TherapyType.TEMPORARY.getCode());
        item.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        item.setOrgId(context.getOrganizationId());
        item.setPrescriptionNo(prescriptionNo);
        item.setSortNumber(line.getSortNumber());
        item.setNote(line.getNote());
        applyPlannedFields(item, planned);
        // 麻醉项目与药品、术式均为平级；关联手术靠 nestedAnesthesiaPrescriptionNo，不写 basedOn
        if (SurgeryAnesthApplyConstants.LINE_KIND_ANESTHESIA.equals(trimToEmpty(line.getLineKindCode()))) {
            item.setDetailItems(buildAnesthesiaDetailItems(line.getActivityDefinitionId()));
        }
        return item;
    }

    /**
     * 术前嘱托 → 创建草稿子命令（basedOn 术式 SR；类目 52）。
     *
     * @param line                 嘱托行
     * @param context              就诊上下文
     * @param entrustActivityId    内置「嘱托」AD
     * @param basedOnProcedureSrId 挂靠术式 SR
     * @param rateCode             频次
     * @return 单条 create 子命令
     */
    @Override
    public CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand toEntrustCreateDraftItem(
        SurgeryApplyFormLineRequest line,
        InpatientEncounterContextQueryModel context,
        Long entrustActivityId,
        Long basedOnProcedureSrId,
        String rateCode) {
        CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand item =
            new CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand();
        item.setEncounterId(context.getEncounterId());
        item.setPatientId(context.getPatientId());
        item.setActivityId(entrustActivityId);
        BigDecimal quantity = line.getQuantity();
        if (quantity == null) {
            quantity = BigDecimal.ONE;
        }
        item.setQuantity(quantity);
        String unit = trimToNull(line.getUnitCode());
        if (unit == null) {
            unit = SurgeryAnesthApplyConstants.NEST_SHELL_DEFAULT_UNIT_CODE;
        }
        item.setUnitCode(unit);
        item.setRateCode(trimToNull(rateCode));
        item.setExecutionCount(1);
        item.setCategoryEnum(ServiceRequestCategoryConstants.SERVICE_CATEGORY_ENTRUST);
        item.setTherapyEnum(TherapyType.TEMPORARY.getCode());
        item.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        item.setOrgId(context.getOrganizationId());
        item.setSortNumber(line.getSortNumber());
        item.setNote(line.getNote());
        item.setEntrustContent(trimToNull(line.getEntrustContent()));
        // 手麻开立唯一 basedOn：术前嘱托挂靠术式 SR
        item.setBasedOnTable(TableNameConstants.WKF_SERVICE_REQUEST);
        item.setBasedOnId(basedOnProcedureSrId);
        return item;
    }

    /**
     * 横表计划字段装配。
     *
     * @param model                主行 QueryModel
     * @param practitionerNameById 人员姓名
     * @return 计划 DTO
     */
    @Override
    public PlannedSurgeryFieldsDto toPlannedFields(
        ServiceRequestQueryModel model,
        Map<Long, String> practitionerNameById) {
        if (model == null) {
            return new PlannedSurgeryFieldsDto();
        }
        PlannedSurgeryFieldsDto dto = new PlannedSurgeryFieldsDto();
        dto.setProcedureTime(model.getProcedureTime());
        dto.setMainProcedureFlag(model.getMainProcedureFlag());
        dto.setProcedureLevelCode(model.getProcedureLevelCode());
        dto.setIncisionTypeCode(model.getIncisionTypeCode());
        dto.setAnesthesiaMethodCode(model.getAnesthesiaMethodCode());
        dto.setNote(model.getRemark());
        dto.setPerformers(buildPerformersFromIds(
            model.getSurgeonId(),
            model.getFirstAssistantId(),
            model.getSecondAssistantId(),
            model.getNurseId(),
            model.getAnesthetistId(),
            practitionerNameById));
        return dto;
    }

    /**
     * 嵌套麻醉壳行 → 创建草稿子命令（不写 basedOn）。
     *
     * @param context                  就诊上下文
     * @param anesthesiaPrescriptionNo 麻醉 AR 号
     * @param activityId               壳行 AD；术式不计费后传占位 0，麻醉页再补真实项目
     * @param unitCode                 单位
     * @param planned                  计划字段（可空）
     * @return 单条 create 子命令
     */
    @Override
    public CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand toNestAnesthesiaShellDraftItem(
        InpatientEncounterContextQueryModel context,
        String anesthesiaPrescriptionNo,
        Long activityId,
        String unitCode,
        PlannedSurgeryFieldsDto planned) {
        CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand item =
            new CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand();
        item.setEncounterId(context.getEncounterId());
        item.setPatientId(context.getPatientId());
        // 空壳占位：真实麻醉项目在麻醉页补开
        if (activityId == null) {
            item.setActivityId(SurgeryAnesthApplyConstants.ACTIVITY_ID_PLACEHOLDER);
        } else {
            item.setActivityId(activityId);
        }
        item.setQuantity(BigDecimal.ONE);
        item.setUnitCode(unitCode);
        item.setCategoryEnum(ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA);
        item.setTherapyEnum(TherapyType.TEMPORARY.getCode());
        item.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        item.setOrgId(context.getOrganizationId());
        item.setPrescriptionNo(anesthesiaPrescriptionNo);
        item.setSortNumber(1);
        applyPlannedFields(item, planned);
        // 与手术关联仅靠手术行 nestedAnesthesiaPrescriptionNo；壳行不写 basedOn
        // 占位壳无 AD 明细；有真实 AD 时才落 detail
        if (activityId != null && activityId != SurgeryAnesthApplyConstants.ACTIVITY_ID_PLACEHOLDER) {
            item.setDetailItems(buildAnesthesiaDetailItems(activityId));
        }
        return item;
    }

    /**
     * 按 prescriptionNo 分组并保持插入顺序。
     *
     * @param rows 行列表
     * @return 分组 map
     */
    private Map<String, List<ServiceRequestListModel>> groupByPrescriptionNo(List<ServiceRequestListModel> rows) {
        Map<String, List<ServiceRequestListModel>> grouped = new LinkedHashMap<>();
        if (rows == null) {
            return grouped;
        }
        for (ServiceRequestListModel row : rows) {
            if (row == null || isEmpty(row.getPrescriptionNo())) {
                continue;
            }
            grouped.computeIfAbsent(row.getPrescriptionNo(), key -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    /**
     * 组内按 sortNumber、id 排序。
     *
     * @param rows 组内行
     * @return 排序后的新列表
     */
    private List<ServiceRequestListModel> sortGroupRows(List<ServiceRequestListModel> rows) {
        List<ServiceRequestListModel> sorted = new ArrayList<>();
        if (rows == null) {
            return sorted;
        }
        sorted.addAll(rows);
        sorted.sort(Comparator
            .comparing(ServiceRequestListModel::getSortNumber, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ServiceRequestListModel::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return sorted;
    }

    /**
     * 聚合头状态：组内行状态全部相同则回该状态码，否则 MIXED。
     *
     * <p>仅认 DRAFT/ACTIVE 会把「全 VERIFIED / 全 COMPLETED」误标成状态不一致；
     * 护士校对后整单一致时须原样回传 RequestStatus code。</p>
     *
     * @param groupRows 组内行
     * @return 统一状态码，或 {@link #HEADER_STATUS_MIXED}
     */
    private String aggregateHeaderStatus(List<ServiceRequestListModel> groupRows) {
        String unifiedStatus = null;
        for (ServiceRequestListModel row : groupRows) {
            if (row == null) {
                continue;
            }
            String status = trimToEmpty(row.getStatusEnum());
            if (unifiedStatus == null) {
                unifiedStatus = status;
                continue;
            }
            if (!unifiedStatus.equals(status)) {
                return HEADER_STATUS_MIXED;
            }
        }
        if (unifiedStatus == null || isEmpty(unifiedStatus)) {
            return RequestStatus.DRAFT.getCode();
        }
        return unifiedStatus;
    }

    /**
     * 选取承载横表字段的主行（主手术标志优先，否则首行）。
     *
     * @param groupRows 组内行
     * @return 主行或 null
     */
    private ServiceRequestListModel resolvePlannedCarrierLine(List<ServiceRequestListModel> groupRows) {
        if (groupRows == null || groupRows.isEmpty()) {
            return null;
        }
        for (ServiceRequestListModel row : groupRows) {
            if (row != null && Boolean.TRUE.equals(row.getMainProcedureFlag())) {
                return row;
            }
        }
        return groupRows.get(0);
    }

    /**
     * 解析主术式展示名（优先主表 procedure_definition_id → PD 名）。
     *
     * @param carrier      主行
     * @param pdById       国临检索项（PD id 为主；兼容旧 AD id）
     * @param activityById AD（加收回落）
     * @return 展示名
     */
    private String resolveMainProcedureName(
        ServiceRequestListModel carrier,
        Map<Long, ProcedureDefinitionSearchItemQueryModel> pdById,
        Map<Long, ActivityDefinition> activityById) {
        if (carrier == null) {
            return null;
        }
        // 术式真源：主表 procedure_definition_id
        if (pdById != null && carrier.getProcedureDefinitionId() != null) {
            ProcedureDefinitionSearchItemQueryModel pd = pdById.get(carrier.getProcedureDefinitionId());
            if (pd != null && isNotEmpty(pd.getName())) {
                return pd.getName();
            }
        }
        // 兼容旧数据：曾用 AD→PD 反查
        if (pdById != null && carrier.getActivityId() != null) {
            ProcedureDefinitionSearchItemQueryModel pdByAd = pdById.get(carrier.getActivityId());
            if (pdByAd != null && isNotEmpty(pdByAd.getName())) {
                return pdByAd.getName();
            }
        }
        if (activityById != null && carrier.getActivityId() != null) {
            ActivityDefinition ad = activityById.get(carrier.getActivityId());
            if (ad != null) {
                return ad.getName();
            }
        }
        return null;
    }

    /**
     * 手术行 DTO 装配（术式名认 PD；加收名认 AD）。
     *
     * @param row                列表行
     * @param detailsByRequestId 明细
     * @param pdByActivityId     国临检索项（PD id 为主；兼容旧 AD id）
     * @param activityById       AD
     * @return 行 DTO
     */
    private SurgeryApplyFormLineDto toSurgeryLineDto(
        ServiceRequestListModel row,
        Map<Long, List<ServiceRequestDetailListModel>> detailsByRequestId,
        Map<Long, ProcedureDefinitionSearchItemQueryModel> pdByActivityId,
        Map<Long, ActivityDefinition> activityById) {
        if (row == null) {
            return null;
        }
        SurgeryApplyFormLineDto dto = new SurgeryApplyFormLineDto();
        dto.setServiceRequestId(row.getId());
        dto.setActivityDefinitionId(row.getActivityId());
        dto.setQuantity(row.getQuantity());
        dto.setUnitCode(row.getUnitCode());
        dto.setStatusEnum(row.getStatusEnum());
        dto.setSortNumber(row.getSortNumber());
        dto.setMainProcedureFlag(row.getMainProcedureFlag());
        if (activityById != null && row.getActivityId() != null
            && row.getActivityId() != SurgeryAnesthApplyConstants.ACTIVITY_ID_PLACEHOLDER) {
            ActivityDefinition ad = activityById.get(row.getActivityId());
            if (ad != null) {
                dto.setActivityName(ad.getName());
            }
        }
        String lineKind = resolveSurgeryLineKind(row, detailsByRequestId, pdByActivityId);
        dto.setLineKindCode(lineKind);
        Long pdId = row.getProcedureDefinitionId();
        if (pdId == null) {
            pdId = resolveProcedureDefinitionIdFromDetails(row.getId(), detailsByRequestId);
        }
        if (pdId != null) {
            dto.setProcedureDefinitionId(pdId);
        }
        // 术式展示名认 PD；加收仍用 AD 名
        if (SurgeryAnesthApplyConstants.LINE_KIND_PROCEDURE.equals(lineKind) && pdByActivityId != null && pdId != null) {
            ProcedureDefinitionSearchItemQueryModel pd = pdByActivityId.get(pdId);
            if (pd != null) {
                dto.setProcedureCode(pd.getProcedureCode());
                dto.setProcedureName(pd.getName());
            }
        }
        return dto;
    }

    /**
     * 麻醉行 DTO 装配。
     *
     * @param row                列表行
     * @param detailsByRequestId 明细
     * @param activityById       AD
     * @return 行 DTO
     */
    private AnesthesiaApplyFormLineDto toAnesthesiaLineDto(
        ServiceRequestListModel row,
        Map<Long, List<ServiceRequestDetailListModel>> detailsByRequestId,
        Map<Long, ActivityDefinition> activityById) {
        if (row == null) {
            return null;
        }
        AnesthesiaApplyFormLineDto dto = new AnesthesiaApplyFormLineDto();
        dto.setServiceRequestId(row.getId());
        dto.setActivityDefinitionId(row.getActivityId());
        dto.setQuantity(row.getQuantity());
        dto.setUnitCode(row.getUnitCode());
        dto.setRateCode(row.getRateCode());
        dto.setStatusEnum(row.getStatusEnum());
        dto.setSortNumber(row.getSortNumber());
        if (activityById != null && row.getActivityId() != null) {
            ActivityDefinition ad = activityById.get(row.getActivityId());
            if (ad != null) {
                dto.setActivityName(ad.getName());
            }
        }
        dto.setLineKindCode(resolveAnesthesiaLineKind(row, detailsByRequestId));
        return dto;
    }

    /**
     * 推断手术行种类（详情回显；开立仅写 PROCEDURE）。
     *
     * <p>无 PD 的历史/划价 AD 行回显 SURCHARGE，不代表医生站可再开附加。</p>
     *
     * @param row                行
     * @param detailsByRequestId 明细
     * @param pdByActivityId     国临反查
     * @return PROCEDURE 或 SURCHARGE（历史/划价）
     */
    private String resolveSurgeryLineKind(
        ServiceRequestListModel row,
        Map<Long, List<ServiceRequestDetailListModel>> detailsByRequestId,
        Map<Long, ProcedureDefinitionSearchItemQueryModel> pdByActivityId) {
        if (row != null && row.getProcedureDefinitionId() != null) {
            return SurgeryAnesthApplyConstants.LINE_KIND_PROCEDURE;
        }
        Long pdId = resolveProcedureDefinitionIdFromDetails(row.getId(), detailsByRequestId);
        if (pdId != null) {
            return SurgeryAnesthApplyConstants.LINE_KIND_PROCEDURE;
        }
        // 兼容旧数据：AD→PD 反查仍命中则视为术式行
        if (pdByActivityId != null && row.getActivityId() != null && pdByActivityId.containsKey(row.getActivityId())) {
            return SurgeryAnesthApplyConstants.LINE_KIND_PROCEDURE;
        }
        return SurgeryAnesthApplyConstants.LINE_KIND_SURCHARGE;
    }

    /**
     * 推断麻醉行种类（详情回显；开立仅写 ANESTHESIA）。
     *
     * <p>无 AD 靶明细的历史行回显 SURCHARGE；开立已禁止附加。</p>
     *
     * @param row                行
     * @param detailsByRequestId 明细
     * @return ANESTHESIA 或 SURCHARGE（历史）
     */
    private String resolveAnesthesiaLineKind(
        ServiceRequestListModel row,
        Map<Long, List<ServiceRequestDetailListModel>> detailsByRequestId) {
        if (detailsByRequestId == null || row.getId() == null) {
            return SurgeryAnesthApplyConstants.LINE_KIND_SURCHARGE;
        }
        List<ServiceRequestDetailListModel> details = detailsByRequestId.get(row.getId());
        if (details == null) {
            return SurgeryAnesthApplyConstants.LINE_KIND_SURCHARGE;
        }
        for (ServiceRequestDetailListModel detail : details) {
            if (detail == null) {
                continue;
            }
            if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(detail.getTargetTable())) {
                return SurgeryAnesthApplyConstants.LINE_KIND_ANESTHESIA;
            }
        }
        return SurgeryAnesthApplyConstants.LINE_KIND_SURCHARGE;
    }

    /**
     * 从明细解析国临 PD 主键。
     *
     * @param serviceRequestId     SR 主键
     * @param detailsByRequestId   明细 map
     * @return PD id 或 null
     */
    private Long resolveProcedureDefinitionIdFromDetails(
        Long serviceRequestId,
        Map<Long, List<ServiceRequestDetailListModel>> detailsByRequestId) {
        if (detailsByRequestId == null || serviceRequestId == null) {
            return null;
        }
        List<ServiceRequestDetailListModel> details = detailsByRequestId.get(serviceRequestId);
        if (details == null) {
            return null;
        }
        for (ServiceRequestDetailListModel detail : details) {
            if (detail == null) {
                continue;
            }
            if (TABLE_CLN_PROCEDURE_DEFINITION.equals(detail.getTargetTable())) {
                return detail.getTargetId();
            }
        }
        return null;
    }

    /**
     * 横表字段写入 create 子命令。
     *
     * @param item    子命令
     * @param planned 计划 DTO
     */
    private void applyPlannedFields(
        CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand item,
        PlannedSurgeryFieldsDto planned) {
        if (planned == null) {
            return;
        }
        item.setProcedureTime(planned.getProcedureTime());
        // 主手术标志认行入参（line.mainProcedureFlag）；横表 planned 不含逐行主术标志，禁止覆盖为 null
        item.setProcedureLevelCode(planned.getProcedureLevelCode());
        item.setIncisionTypeCode(planned.getIncisionTypeCode());
        item.setAnesthesiaMethodCode(planned.getAnesthesiaMethodCode());
        item.setNote(planned.getNote());
        applyPerformerIds(item, planned.getPerformers());
    }

    /**
     * 拟人员 → SR 横表 id 列。
     *
     * @param item       子命令
     * @param performers 拟人员
     */
    private void applyPerformerIds(
        CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand item,
        List<PlannedPerformerDto> performers) {
        if (performers == null) {
            return;
        }
        for (PlannedPerformerDto performer : performers) {
            if (performer == null || performer.getPractitionerId() == null) {
                continue;
            }
            String role = trimToEmpty(performer.getRoleCode());
            if (ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_SURGEON.equals(role)) {
                item.setSurgeonId(performer.getPractitionerId());
            }
            if (ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_FIRST_ASSISTANT.equals(role)) {
                item.setFirstAssistantId(performer.getPractitionerId());
            }
            if (ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_SECOND_ASSISTANT.equals(role)) {
                item.setSecondAssistantId(performer.getPractitionerId());
            }
            if (ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_NURSE.equals(role)) {
                item.setNurseId(performer.getPractitionerId());
            }
            if (ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_ANESTHETIST.equals(role)) {
                item.setAnesthetistId(performer.getPractitionerId());
            }
        }
    }

    /**
     * 列表行 → 计划 DTO（详情用）。
     *
     * @param row                  列表行
     * @param practitionerNameById 姓名
     * @return 计划 DTO
     */
    private PlannedSurgeryFieldsDto toPlannedFieldsFromListModel(
        ServiceRequestListModel row,
        Map<Long, String> practitionerNameById) {
        if (row == null) {
            return null;
        }
        PlannedSurgeryFieldsDto dto = new PlannedSurgeryFieldsDto();
        dto.setProcedureTime(row.getProcedureTime());
        dto.setMainProcedureFlag(row.getMainProcedureFlag());
        dto.setProcedureLevelCode(row.getProcedureLevelCode());
        dto.setIncisionTypeCode(row.getIncisionTypeCode());
        dto.setAnesthesiaMethodCode(row.getAnesthesiaMethodCode());
        dto.setNote(row.getRemark());
        dto.setPerformers(buildPerformersFromIds(
            row.getSurgeonId(),
            row.getFirstAssistantId(),
            row.getSecondAssistantId(),
            row.getNurseId(),
            row.getAnesthetistId(),
            practitionerNameById));
        return dto;
    }

    /**
     * 构建拟人员 DTO 列表。
     *
     * @param surgeonId          主刀
     * @param firstAssistantId   一助
     * @param secondAssistantId  二助
     * @param nurseId            护士
     * @param anesthetistId      麻醉医师
     * @param practitionerNameById 姓名 map
     * @return 列表
     */
    private List<PlannedPerformerDto> buildPerformersFromIds(
        Long surgeonId,
        Long firstAssistantId,
        Long secondAssistantId,
        Long nurseId,
        Long anesthetistId,
        Map<Long, String> practitionerNameById) {
        List<PlannedPerformerDto> performers = new ArrayList<>();
        addPerformer(performers, ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_SURGEON, surgeonId, practitionerNameById);
        addPerformer(performers, ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_FIRST_ASSISTANT, firstAssistantId, practitionerNameById);
        addPerformer(performers, ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_SECOND_ASSISTANT, secondAssistantId, practitionerNameById);
        addPerformer(performers, ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_NURSE, nurseId, practitionerNameById);
        addPerformer(performers, ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_ANESTHETIST, anesthetistId, practitionerNameById);
        return performers;
    }

    /**
     * 追加单条拟人员。
     *
     * @param performers           目标列表
     * @param roleCode             角色码
     * @param practitionerId       人员 id
     * @param practitionerNameById 姓名 map
     */
    private void addPerformer(
        List<PlannedPerformerDto> performers,
        String roleCode,
        Long practitionerId,
        Map<Long, String> practitionerNameById) {
        if (practitionerId == null) {
            return;
        }
        PlannedPerformerDto dto = new PlannedPerformerDto();
        dto.setRoleCode(roleCode);
        dto.setPractitionerId(practitionerId);
        if (practitionerNameById != null) {
            dto.setPractitionerName(practitionerNameById.get(practitionerId));
        }
        performers.add(dto);
    }

    /**
     * 术式行明细：锚定国临 PD。
     *
     * @param procedureDefinitionId PD 主键
     * @return 明细命令列表
     */
    private List<CreateServiceRequestDraftCommand.DetailItemCommand> buildProcedureDetailItems(Long procedureDefinitionId) {
        List<CreateServiceRequestDraftCommand.DetailItemCommand> items = new ArrayList<>();
        if (procedureDefinitionId == null) {
            return items;
        }
        CreateServiceRequestDraftCommand.DetailItemCommand detail = new CreateServiceRequestDraftCommand.DetailItemCommand();
        detail.setTargetTable(TABLE_CLN_PROCEDURE_DEFINITION);
        detail.setTargetId(procedureDefinitionId);
        items.add(detail);
        return items;
    }

    /**
     * 麻醉项目行明细：锚定 AD。
     *
     * @param activityDefinitionId AD 主键
     * @return 明细命令列表
     */
    private List<CreateServiceRequestDraftCommand.DetailItemCommand> buildAnesthesiaDetailItems(Long activityDefinitionId) {
        List<CreateServiceRequestDraftCommand.DetailItemCommand> items = new ArrayList<>();
        if (activityDefinitionId == null) {
            return items;
        }
        CreateServiceRequestDraftCommand.DetailItemCommand detail = new CreateServiceRequestDraftCommand.DetailItemCommand();
        detail.setTargetTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        detail.setTargetId(activityDefinitionId);
        items.add(detail);
        return items;
    }

    /**
     * AD 默认单位：住院单位优先。
     *
     * @param def 诊疗项目
     * @return 单位码
     */
    /**
     * 临嘱计划执行次数：数量取整，至少 1。
     *
     * @param quantity 开立数量
     * @return 执行次数
     */
    private Integer resolveExecutionCount(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return 1;
        }
        return Math.max(1, quantity.intValue());
    }

    private String resolveActivityUnitCode(ActivityDefinition def) {
        if (def == null) {
            return null;
        }
        if (isNotEmpty(def.getDefInhospitalUnitCode())) {
            return def.getDefInhospitalUnitCode();
        }
        if (isNotEmpty(def.getPackageUnitCode())) {
            return def.getPackageUnitCode();
        }
        return def.getDefEncounterUnitCode();
    }

    /**
     * 安全包装 requester 姓名 map（复用 practitioner map）。
     *
     * @param practitionerNameById 姓名 map
     * @return 同一引用或空 map
     */
    private Map<Long, String> requesterNameByIdSafe(Map<Long, String> practitionerNameById) {
        if (practitionerNameById == null) {
            return new HashMap<>();
        }
        return practitionerNameById;
    }
}
