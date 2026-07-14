/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.dispensesummary.mapper;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.assembler.NurseStationDispenseSummaryListAssembler;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.assembler.NurseStationDispenseSummaryWriteAssembler.SummaryMergeGroup;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummaryPatientGroupDto;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummarySlipHeaderDto;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummarySlipLineDto;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.request.PageMedicationSummarySlipHeadersRequest;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.request.PagePendingDispenseSummaryLinesRequest;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.response.CancelMedicationSummaryResponse;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.response.CreateMedicationSummaryResponse;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.response.GetMedicationSummarySlipDetailResponse;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.response.PageMedicationSummarySlipHeadersResponse;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.response.PagePendingDispenseSummaryLinesResponse;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.response.SubmitMedicationSummaryResponse;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.medication.medicationdispense.command.CancelMedicationDispenseSummaryCommand;
import com.openhis.health.domain.medication.medicationdispense.command.SummarizeMedicationDispensesCommand;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispensePickupPendingQueryModel;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispensePickupPendingPageQuery;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.SupplyCategory;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateSummarySupplyRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.MedicationSummarySlipQueryModel;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link NurseStationDispenseSummaryMapper} 实现：Request/Query/Command/Response 字段搬运；禁止承载业务流程判断。
 */
@Component
@RequiredArgsConstructor
public class NurseStationDispenseSummaryMapperImpl implements NurseStationDispenseSummaryMapper {

    private final NurseStationDispenseSummaryListAssembler nurseStationDispenseSummaryListAssembler;
    private final LocationQueryService locationQueryService;
    private final PractitionerQueryService practitionerQueryService;

    /**
     * 将护士站就诊 id 与管辖病区 id 装配为住院就诊分页查询条件。
     *
     * @param encounterIds   AS-IP-NUR-001 已选就诊 id 列表
     * @param managedWardIds 当前用户管辖病区 location id 列表
     * @return 住院护士站队列查询条件
     */
    @Override
    public InpatientEncounterPageQuery toInpatientEncounterPageQuery(List<Long> encounterIds, List<Long> managedWardIds) {
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        query.setNurseStationQueue(Boolean.TRUE);
        if (encounterIds != null && !encounterIds.isEmpty()) {
            query.setEncounterIdsIn(encounterIds);
        }
        if (managedWardIds != null && !managedWardIds.isEmpty()) {
            query.setManagedWardIds(managedWardIds);
        }
        return query;
    }

    /**
     * 将明细 Tab 长临嘱筛选文案转为 {@link TherapyType} code；{@code all} 或空表示不过滤。
     *
     * @param therapyFilter 前端长临嘱筛选（all/long/temp）
     * @return 领域 therapy code，或 {@code null} 表示全量
     */
    @Override
    public String resolveTherapyEnum(String therapyFilter) {
        String normalized = trimToEmpty(therapyFilter);
        if (StringUtils.isEmpty(normalized)
            || InpatientNurseStationConstants.THERAPY_FILTER_ALL.equals(normalized)) {
            return null;
        }
        if (InpatientNurseStationConstants.THERAPY_FILTER_LONG.equals(normalized)) {
            return TherapyType.LONG_TERM.getCode();
        }
        if (InpatientNurseStationConstants.THERAPY_FILTER_TEMP.equals(normalized)) {
            return TherapyType.TEMPORARY.getCode();
        }
        return null;
    }

    /**
     * 装配 IF-IP-SUM-01 待汇总明细分页 Query：分页窗口、可见就诊、预解析药嘱 id、截止时间。
     *
     * @param request              前端请求
     * @param visibleEncounterIds  可见就诊 id（BR-IP-SUM-001 收窄后）
     * @param resolvedMedReqIds    中西药/长临嘱过滤后的药嘱 id
     * @return 域分页查询对象
     */
    @Override
    public MedicationDispensePickupPendingPageQuery toPickupPendingPageQuery(
        PagePendingDispenseSummaryLinesRequest request,
        List<Long> visibleEncounterIds,
        List<Long> resolvedMedReqIds) {
        MedicationDispensePickupPendingPageQuery query = new MedicationDispensePickupPendingPageQuery();
        PageQueryBinder.bind(request, query);
        query.setEncounterIds(visibleEncounterIds);
        query.setResolvedMedReqIds(resolvedMedReqIds);
        if (request != null) {
            query.setQueryDeadline(request.getQueryDeadline());
        }
        return query;
    }

    /**
     * 将域分页结果与按就诊分组后的 patientGroups 装配为 IF-IP-SUM-01 响应壳。
     *
     * @param page   发放行扁平行分页（totalCount 为行维度）
     * @param groups ListAssembler 聚合后的就诊分组
     * @return 含 {@code pageNo}/{@code pageSize}/{@code totalCount} 与 {@code patientGroups} 的响应
     */
    @Override
    public PagePendingDispenseSummaryLinesResponse toPagePendingDispenseSummaryLinesResponse(
        Page<MedicationDispensePickupPendingQueryModel> page,
        List<NurseStationDispenseSummaryPatientGroupDto> groups) {
        PagePendingDispenseSummaryLinesResponse response = new PagePendingDispenseSummaryLinesResponse();
        List<NurseStationDispenseSummaryPatientGroupDto> safeGroups = groups;
        if (safeGroups == null) {
            safeGroups = new ArrayList<>();
        }
        response.setPatientGroups(safeGroups);
        PagedResultBuilder.build(response, new ArrayList<>(), page);
        return response;
    }

    /**
     * 装配 IF-IP-SUM-02 汇总单头分页 Query：type 27、时间窗、关键字与药房门控。
     *
     * @param request            前端请求
     * @param purposeLocationIds  当前用户可操作药房 id（未指定单药房时使用）
     * @return 供应单据列表查询条件
     */
    @Override
    public SupplyRequestDocumentListQuery toSupplyRequestDocumentListQuery(
        PageMedicationSummarySlipHeadersRequest request,
        List<Long> purposeLocationIds) {
        SupplyRequestDocumentListQuery query = new SupplyRequestDocumentListQuery();
        PageQueryBinder.bind(request, query);
        query.setTypeEnum(SupplyType.MEDICATION_SUMMARY_ORDER.getCode());
        if (request != null) {
            query.setStartDate(request.getStartDate());
            query.setEndDate(request.getEndDate());
            Long purposeLocationId = request.getPurposeLocationId();
            if (purposeLocationId != null && purposeLocationId.longValue() != 0L) {
                query.setPurposeLocationId(purposeLocationId);
            } else if (purposeLocationIds != null && !purposeLocationIds.isEmpty()) {
                query.setPurposeLocationIds(purposeLocationIds);
            }
        }
        return query;
    }

    /**
     * 将域汇总单头 QueryModel 批量映射为 slipHeader DTO，并批量索引药房/人员展示名。
     *
     * @param page 域分页单头
     * @return 汇总 Tab 单头行列表
     */
    @Override
    public List<NurseStationDispenseSummarySlipHeaderDto> toSlipHeaderDtos(Page<MedicationSummarySlipQueryModel> page) {
        List<NurseStationDispenseSummarySlipHeaderDto> rows = new ArrayList<>();
        if (page == null || page.getContent() == null) {
            return rows;
        }
        Set<Long> locationIds = new HashSet<>();
        Set<Long> practitionerIds = new HashSet<>();
        for (MedicationSummarySlipQueryModel model : page.getContent()) {
            if (model == null) {
                continue;
            }
            if (model.getPurposeLocationId() != null) {
                locationIds.add(model.getPurposeLocationId());
            }
            if (model.getApplicantId() != null) {
                practitionerIds.add(model.getApplicantId());
            }
            if (model.getReceiverId() != null) {
                practitionerIds.add(model.getReceiverId());
            }
        }
        Map<Long, Location> locationById = this.loadLocationsById(locationIds);
        Map<Long, String> practitionerNameById = this.loadPractitionerNames(practitionerIds);
        for (MedicationSummarySlipQueryModel model : page.getContent()) {
            NurseStationDispenseSummarySlipHeaderDto dto = nurseStationDispenseSummaryListAssembler.toSlipHeaderDto(
                model, locationById, practitionerNameById);
            if (dto != null) {
                rows.add(dto);
            }
        }
        return rows;
    }

    /**
     * 装配 IF-IP-SUM-02 分页响应：{@code rows} 与父类 {@code content} 同源。
     *
     * @param page 域分页单头
     * @param rows 已映射的单头 DTO
     * @return 含分页元数据与 {@code rows} 的响应
     */
    @Override
    public PageMedicationSummarySlipHeadersResponse toPageMedicationSummarySlipHeadersResponse(
        Page<MedicationSummarySlipQueryModel> page,
        List<NurseStationDispenseSummarySlipHeaderDto> rows) {
        PageMedicationSummarySlipHeadersResponse response = new PageMedicationSummarySlipHeadersResponse();
        List<NurseStationDispenseSummarySlipHeaderDto> safeRows = rows;
        if (safeRows == null) {
            safeRows = new ArrayList<>();
        }
        response.setRows(safeRows);
        PagedResultBuilder.build(response, safeRows, page);
        return response;
    }

    /**
     * 装配 IF-IP-SUM-03 汇总单详情响应（单头 + 合并明细，不含批号）。
     *
     * @param summaryNo 汇总单号
     * @param header    单头展示
     * @param lines     合并后明细行
     * @return 详情响应
     */
    @Override
    public GetMedicationSummarySlipDetailResponse toGetMedicationSummarySlipDetailResponse(
        String summaryNo,
        NurseStationDispenseSummarySlipHeaderDto header,
        List<NurseStationDispenseSummarySlipLineDto> lines) {
        GetMedicationSummarySlipDetailResponse response = new GetMedicationSummarySlipDetailResponse();
        response.setSummaryNo(summaryNo);
        response.setHeader(header);
        if (lines == null) {
            response.setLines(new ArrayList<>());
        } else {
            response.setLines(lines);
        }
        return response;
    }

    /**
     * 装配 IF-IP-SUM-04 发放行汇总写 Command（按药房一组）。
     *
     * @param locationId  发放药房
     * @param dispenseIds 本药房待汇总发放行 id
     * @param operatorId  操作人
     * @return 领域 summarize 命令
     */
    @Override
    public SummarizeMedicationDispensesCommand toSummarizeMedicationDispensesCommand(
        Long locationId,
        List<Long> dispenseIds,
        Long operatorId,
        String summaryNo) {
        SummarizeMedicationDispensesCommand command = new SummarizeMedicationDispensesCommand();
        command.setLocationId(locationId);
        command.setDispenseIds(dispenseIds);
        command.setOperatorId(operatorId);
        command.setSummaryNo(summaryNo);
        return command;
    }

    /**
     * 将合并组列表装配为 type 27 汇总 SupplyRequest 写 Command（初态 PENDING_APPROVAL；applyTime 在发送时写入）。
     */
    @Override
    public CreateSummarySupplyRequestCommand toCreateSummarySupplyRequestCommand(
        String summaryNo,
        List<SummaryMergeGroup> groups,
        Long applicantId,
        Long operatorId) {
        CreateSummarySupplyRequestCommand command = new CreateSummarySupplyRequestCommand();
        command.setOperatorId(operatorId);
        LocalDateTime occurrenceTime = LocalDateTime.now();
        List<CreateSummarySupplyRequestCommand.CreateSummarySupplyRequestItemCommand> items = new ArrayList<>();
        if (groups != null) {
            for (SummaryMergeGroup group : groups) {
                if (group == null) {
                    continue;
                }
                CreateSummarySupplyRequestCommand.CreateSummarySupplyRequestItemCommand item =
                    new CreateSummarySupplyRequestCommand.CreateSummarySupplyRequestItemCommand();
                item.setBusNo(summaryNo);
                item.setTypeEnum(SupplyType.MEDICATION_SUMMARY_ORDER.getCode());
                item.setItemTable(TableNameConstants.MED_MEDICATION_DEFINITION);
                item.setItemId(group.getMedicationId());
                item.setItemQuantity(group.getSplitQuantitySum());
                item.setUnitCode(group.getSplitUnitCode());
                item.setPatientId(group.getPatientId());
                item.setLotNumber(null);
                item.setApplicantId(applicantId);
                item.setApplyTime(null);
                item.setOccurrenceTime(occurrenceTime);
                Long dispensingPharmacyId = group.getLocationId();
                // 业务含义：单端点 purpose 真源，仅写发放药房 purpose_*。
                String locationTypeCode = locationQueryService.requireSupplyRequestTypeForLocationId(
                    dispensingPharmacyId).getCode();
                item.setPurposeLocationId(dispensingPharmacyId);
                item.setPurposeTypeEnum(locationTypeCode);
                item.setCategoryEnum(SupplyCategory.STOCK_SUPPLY.getCode());
                if (group.getDispenseIds() != null && !group.getDispenseIds().isEmpty()) {
                    item.setSummaryId(group.getDispenseIds().get(0));
                }
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

    /**
     * 装配 IF-IP-SUM-05 提交审批命令。
     */
    @Override
    public SubmitApprovalCommand toSubmitApprovalCommand(String summaryNo, Long applicantId, Long operatorId) {
        SubmitApprovalCommand command = new SubmitApprovalCommand();
        command.setOperatorId(operatorId);
        SubmitApprovalCommand.SubmitApprovalItemCommand item = new SubmitApprovalCommand.SubmitApprovalItemCommand();
        item.setBusNo(summaryNo);
        item.setApplicantId(applicantId);
        command.setItems(List.of(item));
        return command;
    }

    /**
     * 装配 IF-IP-SUM-04 写成功响应。
     */
    @Override
    public CreateMedicationSummaryResponse toCreateMedicationSummaryResponse(List<String> summaryNos) {
        CreateMedicationSummaryResponse response = new CreateMedicationSummaryResponse();
        List<String> safeNos = summaryNos;
        if (safeNos == null) {
            safeNos = new ArrayList<>();
        }
        response.setSummaryNos(safeNos);
        if (safeNos.size() > 1) {
            response.setMessage("已按药房拆分保存汇总单");
        } else if (safeNos.isEmpty()) {
            response.setMessage("汇总保存失败，请检查所选明细状态");
        } else {
            response.setMessage("汇总保存成功，请发送后等待药房核发");
        }
        return response;
    }

    /**
     * 装配 IF-IP-SUM-05 写成功响应。
     */
    @Override
    public SubmitMedicationSummaryResponse toSubmitMedicationSummaryResponse(List<String> summaryNos) {
        SubmitMedicationSummaryResponse response = new SubmitMedicationSummaryResponse();
        List<String> safeNos = summaryNos;
        if (safeNos == null) {
            safeNos = new ArrayList<>();
        }
        response.setSummaryNos(safeNos);
        if (safeNos.size() > 1) {
            response.setMessage("已发送 " + safeNos.size() + " 张汇总单");
        } else if (safeNos.isEmpty()) {
            response.setMessage("发送失败，请检查汇总单状态");
        } else {
            response.setMessage("汇总单已发送，等待药房核发");
        }
        return response;
    }

    /**
     * 装配 IF-IP-SUM-08 取消发放汇总命令。
     */
    @Override
    public CancelMedicationDispenseSummaryCommand toCancelMedicationDispenseSummaryCommand(
        String summaryNo,
        Long operatorId) {
        CancelMedicationDispenseSummaryCommand command = new CancelMedicationDispenseSummaryCommand();
        command.setOperatorId(operatorId);
        command.setSummaryNo(summaryNo);
        return command;
    }

    /**
     * 装配 IF-IP-SUM-08 逻辑删除汇总供应单命令。
     */
    @Override
    public RemoveByBusNoCommand toRemoveByBusNoCommand(String summaryNo, Long operatorId) {
        RemoveByBusNoCommand command = new RemoveByBusNoCommand();
        command.setOperatorId(operatorId);
        RemoveByBusNoCommand.RemoveByBusNoItemCommand item = new RemoveByBusNoCommand.RemoveByBusNoItemCommand();
        item.setBusNo(summaryNo);
        command.setItem(item);
        return command;
    }

    /**
     * 装配 IF-IP-SUM-08 写成功响应。
     */
    @Override
    public CancelMedicationSummaryResponse toCancelMedicationSummaryResponse(List<String> summaryNos) {
        CancelMedicationSummaryResponse response = new CancelMedicationSummaryResponse();
        List<String> safeNos = summaryNos;
        if (safeNos == null) {
            safeNos = new ArrayList<>();
        }
        response.setSummaryNos(safeNos);
        if (safeNos.size() > 1) {
            response.setMessage("已撤销 " + safeNos.size() + " 张汇总单，请到明细 Tab 重新勾选");
        } else if (safeNos.isEmpty()) {
            response.setMessage("撤销失败，请检查汇总单状态");
        } else {
            response.setMessage("已撤销汇总，请到明细 Tab 重新勾选");
        }
        return response;
    }

    /**
     * 按 id 批量加载 Location 实体 Map（缺失 id 跳过，不抛错）。
     *
     * @param locationIds 位置 id 集合
     * @return id → Location
     */
    private Map<Long, Location> loadLocationsById(Set<Long> locationIds) {
        Map<Long, Location> map = new HashMap<>();
        if (locationIds == null) {
            return map;
        }
        for (Long locationId : locationIds) {
            if (locationId == null) {
                continue;
            }
            locationQueryService.findOptionalById(locationId).ifPresent(location -> map.put(locationId, location));
        }
        return map;
    }

    /**
     * 按 id 批量解析药师/practitioner 展示名 Map。
     *
     * @param practitionerIds 人员 id 集合
     * @return id → 姓名；不存在则不含该键
     */
    private Map<Long, String> loadPractitionerNames(Set<Long> practitionerIds) {
        Map<Long, String> map = new HashMap<>();
        if (practitionerIds == null) {
            return map;
        }
        for (Long practitionerId : practitionerIds) {
            if (practitionerId == null) {
                continue;
            }
            Practitioner practitioner = practitionerQueryService.findById(practitionerId);
            if (practitioner != null) {
                map.put(practitionerId, practitioner.getName());
            }
        }
        return map;
    }
}
