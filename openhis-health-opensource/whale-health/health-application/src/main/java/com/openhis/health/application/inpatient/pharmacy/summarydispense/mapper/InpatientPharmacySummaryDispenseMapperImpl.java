/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inpatient.pharmacy.summarydispense.mapper;

import com.openhis.health.module.common.pagination.PageQueryBinder;
import com.openhis.health.module.common.pagination.PagedResultBuilder;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.assembler.InpatientPharmacySummaryDispenseAssembler;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummaryDispenseItemDto;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummarySlipHeaderDto;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummarySlipLineDto;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.request.PagePendingMedicationSummarySlipsRequest;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.response.DispenseSummaryMedicationsResponse;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.response.GetMedicationSummaryDispenseDetailResponse;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.response.PagePendingMedicationSummarySlipsResponse;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.response.RejectSummaryDispenseResponse;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.custom.tracenumberrecord.command.AppendTraceRecordsCommand;
import com.openhis.health.domain.medication.medicationdispense.command.CancelMedicationDispenseSummaryCommand;
import com.openhis.health.domain.medication.medicationdispense.command.CompleteSummaryDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientPharmacySummaryDispenseConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.health.domain.share.enums.TraceNumberRecordOperationType;
import com.openhis.health.domain.workflow.supplydelivery.command.CreateSummarySupplyDeliveryCommand;
import com.openhis.health.domain.workflow.supplydelivery.command.UpdateCompletedStatusBatchCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.MedicationSummarySlipQueryModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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
     * IF-IPS-01：装配供应单据列表查询（固定 type 27 + 审批态，缺省 APPROVAL）。
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
        // 列表态：缺省待核发 APPROVAL；已核发列表传 AGREE（BR-IPS-013）
        query.setStatusEnum(this.resolveListSupplyStatusEnum(request));
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
     * 解析 IF-IPS-01 列表供应审批态：取请求中首个合法 APPROVAL/AGREE，缺省 APPROVAL。
     *
     * @param request HTTP 请求
     * @return 持久化 status_enum
     */
    private String resolveListSupplyStatusEnum(PagePendingMedicationSummarySlipsRequest request) {
        if (request == null || request.getSupplyStatusEnumIn() == null || request.getSupplyStatusEnumIn().isEmpty()) {
            return SupplyStatus.APPROVAL.getCode();
        }
        String approvalCode = SupplyStatus.APPROVAL.getCode();
        String agreeCode = SupplyStatus.AGREE.getCode();
        for (String raw : request.getSupplyStatusEnumIn()) {
            if (StringUtils.isBlank(raw)) {
                continue;
            }
            String code = raw.trim();
            if (approvalCode.equals(code) || agreeCode.equals(code)) {
                return code;
            }
        }
        return approvalCode;
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
     * @return 领域命令
     */
    @Override
    public CompleteSummaryDispenseCommand toCompleteSummaryDispenseCommand(
        String summaryNo,
        Long operatorId,
        Long practitionerId) {
        CompleteSummaryDispenseCommand command = new CompleteSummaryDispenseCommand();
        command.setSummaryNo(summaryNo);
        command.setOperatorId(operatorId);
        command.setPractitionerId(practitionerId);
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
        List<SupplyRequestModel> supplyLines) {
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
     * IF-IPS-03：追溯出库命令。
     *
     * @param dispenses  已完成汇总发放行（追溯码已合并）
     * @param summaryNo  汇总单号
     * @param operatorId 操作人
     * @return 领域命令；无追溯码时 items 可为空
     */
    @Override
    public AppendTraceRecordsCommand toAppendTraceRecordsCommand(
        List<MedicationDispense> dispenses,
        String summaryNo,
        Long operatorId) {
        AppendTraceRecordsCommand command = new AppendTraceRecordsCommand();
        command.setOperatorId(operatorId);
        List<AppendTraceRecordsCommand.AppendTraceRecordItemCommand> items = new ArrayList<>();
        String traceOperationType = TraceNumberRecordOperationType.MEDICATION_DISPENSE_OUT.getCode();
        if (dispenses != null) {
            for (MedicationDispense dispense : dispenses) {
                if (dispense == null || StringUtils.isBlank(dispense.getTraceNo())) {
                    continue;
                }
                String locationTypeCode = locationQueryService.requireSupplyRequestTypeForLocationId(
                    dispense.getLocationId()).getCode();
                for (String part : dispense.getTraceNo().split(GeneralSharedConstants.COMMA)) {
                    String traceNo = part.trim();
                    if (traceNo.isEmpty()) {
                        continue;
                    }
                    AppendTraceRecordsCommand.AppendTraceRecordItemCommand item =
                        new AppendTraceRecordsCommand.AppendTraceRecordItemCommand();
                    item.setItemTable(TableNameConstants.MED_MEDICATION_DEFINITION);
                    item.setItemId(dispense.getMedicationId());
                    item.setLocationTypeEnum(locationTypeCode);
                    item.setLocationId(dispense.getLocationId());
                    item.setLotNumber(dispense.getLotNumber());
                    item.setTraceNo(traceNo);
                    item.setStatusEnum(TraceDirection.OUT.getCode());
                    item.setUnitCode(dispense.getUnitCode());
                    item.setOperationType(traceOperationType);
                    item.setBusNo(summaryNo);
                    items.add(item);
                }
            }
        }
        command.setItems(items);
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
