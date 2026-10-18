/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.summarydispense.mapper;

import com.zxplat.application.shared.pagination.PageQueryBinder;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.application.inpatient.pharmacy.summarydispense.assembler.InpatientPharmacySummaryDispenseAssembler;
import com.zxplat.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummaryDispenseItemDto;
import com.zxplat.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummarySlipHeaderDto;
import com.zxplat.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummarySlipLineDto;
import com.zxplat.application.inpatient.pharmacy.summarydispense.request.PagePendingMedicationSummarySlipsRequest;
import com.zxplat.application.inpatient.pharmacy.summarydispense.response.DispenseSummaryMedicationsResponse;
import com.zxplat.application.inpatient.pharmacy.summarydispense.response.GetMedicationSummaryDispenseDetailResponse;
import com.zxplat.application.inpatient.pharmacy.summarydispense.response.PagePendingMedicationSummarySlipsResponse;
import com.zxplat.application.inpatient.pharmacy.summarydispense.response.RejectSummaryDispenseResponse;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.medication.medicationdispense.entity.MedicationDispense;
import com.zxplat.domain.medication.medicationdispense.command.CancelMedicationDispenseSummaryCommand;
import com.zxplat.domain.medication.medicationdispense.command.CompleteSummaryDispenseCommand;
import com.zxplat.domain.share.constants.InpatientPharmacySummaryDispenseConstants;
import com.zxplat.domain.share.enums.SupplyStatus;
import com.zxplat.domain.share.enums.SupplyType;
import com.zxplat.domain.workflow.supplydelivery.command.CreateSummarySupplyDeliveryCommand;
import com.zxplat.domain.workflow.supplydelivery.command.UpdateCompletedStatusBatchCommand;
import com.zxplat.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.zxplat.domain.workflow.supplyrequest.queries.model.MedicationSummarySlipQueryModel;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import lombok.RequiredArgsConstructor;
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
 * {@link InpatientPharmacySummaryDispenseMapper} 实现：字段搬运与批量展示索引；禁止承载业务流程判断。
 */
@Component
@RequiredArgsConstructor
public class InpatientPharmacySummaryDispenseMapperImpl implements InpatientPharmacySummaryDispenseMapper {

    private final InpatientPharmacySummaryDispenseAssembler inpatientPharmacySummaryDispenseAssembler;
    private final LocationQueryService locationQueryService;
    private final PractitionerQueryService practitionerQueryService;

    /**
     * IF-IPS-01：装配供应单据列表查询（固定 type 27；待发写死 Supply APPROVAL，已发药不设 Supply 态）。
     *
     * @param request            HTTP 请求
     * @param purposeLocationIds  可操作药房 id 集合
     * @return 域分页查询条件
     */
    @Override
    public SupplyRequestDocumentListQuery toSupplyRequestDocumentListQuery(
        PagePendingMedicationSummarySlipsRequest request,
        List<Long> purposeLocationIds) {
        SupplyRequestDocumentListQuery query = new SupplyRequestDocumentListQuery();
        PageQueryBinder.bind(request, query);
        query.setTypeEnum(SupplyType.MEDICATION_SUMMARY_ORDER.getCode());
        // 已发药 Tab：发放表已收窄 busNoIn，不再拼 Supply 审批态
        if (request != null && Boolean.TRUE.equals(request.getSummaryDispenseCompletedOnly())) {
            query.setStatusEnum(null);
        } else {
            // 待发 Tab：服务端写死 APPROVAL，不接受客户端审批态入参
            query.setStatusEnum(SupplyStatus.APPROVAL.getCode());
        }
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
     * IF-IPS-01：域单头分页 → 展示 DTO 列表。
     *
     * @param page 域分页单头
     * @return 单头 DTO 列表
     */
    @Override
    public List<InpatientPharmacySummarySlipHeaderDto> toSummarySlipHeaderDtos(Page<MedicationSummarySlipQueryModel> page) {
        List<InpatientPharmacySummarySlipHeaderDto> rows = new ArrayList<>();
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
        Map<Long, String> locationNameById = locationQueryService.findLocationNamesByIds(locationIds);
        Map<Long, String> practitionerNameById = this.loadPractitionerNames(practitionerIds);
        for (MedicationSummarySlipQueryModel model : page.getContent()) {
            if (model == null) {
                continue;
            }
            InpatientPharmacySummarySlipHeaderDto dto = new InpatientPharmacySummarySlipHeaderDto();
            dto.setBusNo(model.getBusNo());
            dto.setApplyTime(model.getApplyTime());
            dto.setDispenseTime(model.getDispenseTime());
            dto.setPurposeLocationId(model.getPurposeLocationId());
            if (model.getPurposeLocationId() != null) {
                dto.setPurposeLocationName(locationNameById.get(model.getPurposeLocationId()));
            }
            dto.setApplicantId(model.getApplicantId());
            dto.setConsumingOrganizationId(model.getConsumingOrganizationId());
            if (model.getApplicantId() != null) {
                dto.setApplicantName(practitionerNameById.get(model.getApplicantId()));
            }
            dto.setReceiverId(model.getReceiverId());
            if (model.getReceiverId() != null) {
                dto.setReceiverName(practitionerNameById.get(model.getReceiverId()));
            }
            dto.setSupplyStatusEnum(model.getSupplyStatusEnum());
            dto.setSupplyStatusEnumText(
                inpatientPharmacySummaryDispenseAssembler.resolveSupplyStatusText(model.getSupplyStatusEnum()));
            rows.add(dto);
        }
        return rows;
    }

    /**
     * IF-IPS-01：装配分页响应壳。
     *
     * @param page 域分页
     * @param rows 已映射单头行
     * @return 分页响应
     */
    @Override
    public PagePendingMedicationSummarySlipsResponse toPagePendingMedicationSummarySlipsResponse(
        Page<MedicationSummarySlipQueryModel> page,
        List<InpatientPharmacySummarySlipHeaderDto> rows) {
        PagePendingMedicationSummarySlipsResponse response = new PagePendingMedicationSummarySlipsResponse();
        List<InpatientPharmacySummarySlipHeaderDto> safeRows = rows;
        if (safeRows == null) {
            safeRows = new ArrayList<>();
        }
        PagedResultBuilder.build(response, safeRows, page);
        return response;
    }

    /**
     * IF-IPS-02：装配详情响应。
     *
     * @param summaryNo     汇总单号
     * @param header        单头
     * @param mergedLines   合并行
     * @param dispenseItems 发放行
     * @return 详情响应
     */
    @Override
    public GetMedicationSummaryDispenseDetailResponse toGetMedicationSummaryDispenseDetailResponse(
        String summaryNo,
        InpatientPharmacySummarySlipHeaderDto header,
        List<InpatientPharmacySummarySlipLineDto> mergedLines,
        List<InpatientPharmacySummaryDispenseItemDto> dispenseItems) {
        GetMedicationSummaryDispenseDetailResponse response = new GetMedicationSummaryDispenseDetailResponse();
        response.setSummaryNo(summaryNo);
        response.setHeader(header);
        if (mergedLines == null) {
            response.setMergedLines(new ArrayList<>());
        } else {
            response.setMergedLines(mergedLines);
        }
        if (dispenseItems == null) {
            response.setDispenseItems(new ArrayList<>());
        } else {
            response.setDispenseItems(dispenseItems);
        }
        return response;
    }

    /**
     * IF-IPS-03：完成汇总发药命令。
     *
     * @param summaryNo      汇总单号
     * @param operatorId     操作人
     * @param practitionerId 发药药师
     * @param preparerId     调配药师
     * @return 领域命令
     */
    @Override
    public CompleteSummaryDispenseCommand toCompleteSummaryDispenseCommand(
        String summaryNo,
        Long operatorId,
        Long practitionerId,
        Long preparerId) {
        CompleteSummaryDispenseCommand command = new CompleteSummaryDispenseCommand();
        command.setSummaryNo(summaryNo);
        command.setOperatorId(operatorId);
        command.setPractitionerId(practitionerId);
        command.setPreparerId(preparerId);
        return command;
    }

    /**
     * IF-IPS-03：创建汇总 SupplyDelivery 命令。
     *
     * @param receiverId   领药人
     * @param operatorId   操作人
     * @param supplyLines  type 27 Supply 行
     * @return 领域命令
     */
    @Override
    public CreateSummarySupplyDeliveryCommand toCreateSummarySupplyDeliveryCommand(
        Long receiverId,
        Long operatorId,
        List<SupplyRequestModel> supplyLines,
        Map<Long, String> traceBySupplyRequestId) {
        CreateSummarySupplyDeliveryCommand command = new CreateSummarySupplyDeliveryCommand();
        command.setOperatorId(operatorId);
        List<CreateSummarySupplyDeliveryCommand.CreateSummarySupplyDeliveryItemCommand> items = new ArrayList<>();
        if (supplyLines != null) {
            for (SupplyRequestModel line : supplyLines) {
                if (line == null) {
                    continue;
                }
                CreateSummarySupplyDeliveryCommand.CreateSummarySupplyDeliveryItemCommand item =
                    new CreateSummarySupplyDeliveryCommand.CreateSummarySupplyDeliveryItemCommand();
                item.setSupplyRequestId(line.getId());
                item.setSummaryId(line.getSummaryId());
                item.setPatientId(line.getPatientId());
                item.setItemId(line.getItemId());
                item.setQuantity(line.getItemQuantity());
                item.setUnitCode(line.getUnitCode());
                item.setLotNumber(null);
                item.setReceiverId(receiverId);
                if (traceBySupplyRequestId != null && line.getId() != null) {
                    item.setTraceNo(traceBySupplyRequestId.get(line.getId()));
                }
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

    /**
     * IF-IPS-03：批量推进 Delivery 完成态命令。
     *
     * @param deliveryIds      新建发放 id 列表
     * @param practitionerId   发药药师
     * @param operatorId       操作人
     * @param completedTime    完成时刻
     * @return 领域命令
     */
    @Override
    public UpdateCompletedStatusBatchCommand toUpdateCompletedStatusBatchCommand(
        List<Long> deliveryIds,
        Long practitionerId,
        Long operatorId,
        LocalDateTime completedTime) {
        UpdateCompletedStatusBatchCommand command = new UpdateCompletedStatusBatchCommand();
        command.setOperatorId(operatorId);
        UpdateCompletedStatusBatchCommand.UpdateCompletedStatusBatchItemCommand item =
            new UpdateCompletedStatusBatchCommand.UpdateCompletedStatusBatchItemCommand();
        item.setSupplyDeliveryIdList(deliveryIds);
        item.setPractitionerId(practitionerId);
        item.setCompletedTime(completedTime);
        command.setItems(List.of(item));
        return command;
    }

    /**
     * IF-IPS-03：Supply 审批同意命令。
     *
     * @param busNo       业务单号
     * @param approverId  审批人
     * @param operatorId  操作人
     * @return 领域命令
     */
    @Override
    public AgreeRequestCommand toAgreeRequestCommand(String busNo, Long approverId, Long operatorId) {
        AgreeRequestCommand command = new AgreeRequestCommand();
        command.setOperatorId(operatorId);
        AgreeRequestCommand.AgreeRequestItemCommand item = new AgreeRequestCommand.AgreeRequestItemCommand();
        item.setBusNo(busNo);
        item.setApproverId(approverId);
        command.setItems(List.of(item));
        return command;
    }

    /**
     * IF-IPS-03：核发成功响应。
     *
     * @param summaryNos 已成功单号
     * @return 写响应
     */
    @Override
    public DispenseSummaryMedicationsResponse toDispenseSummaryMedicationsResponse(List<String> summaryNos) {
        DispenseSummaryMedicationsResponse response = new DispenseSummaryMedicationsResponse();
        List<String> safeNos = summaryNos;
        if (safeNos == null) {
            safeNos = new ArrayList<>();
        }
        response.setSummaryNos(safeNos);
        if (safeNos.size() > 1) {
            response.setMessage("已成功核发 " + safeNos.size() + " 张汇总单");
        } else if (safeNos.isEmpty()) {
            response.setMessage(InpatientPharmacySummaryDispenseConstants.DISPENSE_SUCCESS_MESSAGE);
        } else {
            response.setMessage(InpatientPharmacySummaryDispenseConstants.DISPENSE_SUCCESS_MESSAGE);
        }
        return response;
    }

    /**
     * IF-IPS-04：驳回命令。
     *
     * @param busNo             业务单号
     * @param approverId        审批人
     * @param operatorId        操作人
     * @param rejectReasonCode  驳回原因
     * @return 领域命令
     */
    @Override
    public RejectRequestCommand toRejectRequestCommand(
        String busNo,
        Long approverId,
        Long operatorId,
        String rejectReasonCode) {
        RejectRequestCommand command = new RejectRequestCommand();
        command.setOperatorId(operatorId);
        RejectRequestCommand.RejectRequestItemCommand item = new RejectRequestCommand.RejectRequestItemCommand();
        item.setBusNo(busNo);
        item.setApproverId(approverId);
        item.setChangeReasonCode(rejectReasonCode);
        command.setItems(List.of(item));
        return command;
    }

    /**
     * IF-IPS-04：取消汇总发放命令。
     *
     * @param summaryNo  汇总单号
     * @param operatorId 操作人
     * @return 领域命令
     */
    @Override
    public CancelMedicationDispenseSummaryCommand toCancelMedicationDispenseSummaryCommand(
        String summaryNo,
        Long operatorId) {
        CancelMedicationDispenseSummaryCommand command = new CancelMedicationDispenseSummaryCommand();
        command.setSummaryNo(summaryNo);
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * IF-IPS-04：驳回成功响应。
     *
     * @param summaryNos 已驳回单号
     * @return 写响应
     */
    @Override
    public RejectSummaryDispenseResponse toRejectSummaryDispenseResponse(List<String> summaryNos) {
        RejectSummaryDispenseResponse response = new RejectSummaryDispenseResponse();
        List<String> safeNos = summaryNos;
        if (safeNos == null) {
            safeNos = new ArrayList<>();
        }
        response.setSummaryNos(safeNos);
        if (safeNos.size() > 1) {
            response.setMessage("已成功驳回 " + safeNos.size() + " 张汇总单");
        } else {
            response.setMessage(InpatientPharmacySummaryDispenseConstants.REJECT_SUCCESS_MESSAGE);
        }
        return response;
    }

    /**
     * 批量索引医务人员 id → 姓名（单头展示用）。
     *
     * @param practitionerIds 医务人员主键集合
     * @return 姓名索引；无命中时返回空 Map
     */
    private Map<Long, String> loadPractitionerNames(Set<Long> practitionerIds) {
        Map<Long, String> nameById = new HashMap<>();
        if (practitionerIds == null) {
            return nameById;
        }
        for (Long practitionerId : practitionerIds) {
            if (practitionerId == null) {
                continue;
            }
            Practitioner practitioner = practitionerQueryService.findById(practitionerId);
            if (practitioner != null) {
                nameById.put(practitionerId, practitioner.getName());
            }
        }
        return nameById;
    }
}
