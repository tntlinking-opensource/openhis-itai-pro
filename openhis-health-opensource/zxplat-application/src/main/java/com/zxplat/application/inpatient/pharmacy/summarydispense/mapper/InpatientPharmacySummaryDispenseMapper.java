/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.summarydispense.mapper;

import com.zxplat.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummaryDispenseItemDto;
import com.zxplat.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummarySlipHeaderDto;
import com.zxplat.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummarySlipLineDto;
import com.zxplat.application.inpatient.pharmacy.summarydispense.request.PagePendingMedicationSummarySlipsRequest;
import com.zxplat.application.inpatient.pharmacy.summarydispense.response.DispenseSummaryMedicationsResponse;
import com.zxplat.application.inpatient.pharmacy.summarydispense.response.GetMedicationSummaryDispenseDetailResponse;
import com.zxplat.application.inpatient.pharmacy.summarydispense.response.PagePendingMedicationSummarySlipsResponse;
import com.zxplat.application.inpatient.pharmacy.summarydispense.response.RejectSummaryDispenseResponse;
import com.zxplat.domain.medication.medicationdispense.entity.MedicationDispense;
import com.zxplat.domain.medication.medicationdispense.command.CancelMedicationDispenseSummaryCommand;
import com.zxplat.domain.medication.medicationdispense.command.CompleteSummaryDispenseCommand;
import com.zxplat.domain.workflow.supplydelivery.command.CreateSummarySupplyDeliveryCommand;
import com.zxplat.domain.workflow.supplydelivery.command.UpdateCompletedStatusBatchCommand;
import com.zxplat.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.zxplat.domain.workflow.supplyrequest.queries.model.MedicationSummarySlipQueryModel;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 住院药房汇总发药 Request/Query/Command/Response 映射。
 *
 * <p>禁止承载业务流程判断；展示 enrichment 由 Assembler 或本 Mapper 批量索引后完成。</p>
 */
public interface InpatientPharmacySummaryDispenseMapper {

    /**
     * IF-IPS-01：装配供应单据列表查询（固定 type 27 + 审批态，缺省 APPROVAL）。
     *
     * @param request            HTTP 请求
     * @param purposeLocationIds  可操作药房 id 集合
     * @return 域分页查询条件
     */
    SupplyRequestDocumentListQuery toSupplyRequestDocumentListQuery(
        PagePendingMedicationSummarySlipsRequest request,
        List<Long> purposeLocationIds);

    /**
     * IF-IPS-01：域单头分页 → 展示 DTO 列表。
     *
     * @param page 域分页单头
     * @return 单头 DTO 列表
     */
    List<InpatientPharmacySummarySlipHeaderDto> toSummarySlipHeaderDtos(Page<MedicationSummarySlipQueryModel> page);

    /**
     * IF-IPS-01：装配分页响应壳。
     *
     * @param page 域分页
     * @param rows 已映射单头行
     * @return 分页响应
     */
    PagePendingMedicationSummarySlipsResponse toPagePendingMedicationSummarySlipsResponse(
        Page<MedicationSummarySlipQueryModel> page,
        List<InpatientPharmacySummarySlipHeaderDto> rows);

    /**
     * IF-IPS-02：装配详情响应。
     *
     * @param summaryNo     汇总单号
     * @param header        单头
     * @param mergedLines   合并行
     * @param dispenseItems 发放行
     * @return 详情响应
     */
    GetMedicationSummaryDispenseDetailResponse toGetMedicationSummaryDispenseDetailResponse(
        String summaryNo,
        InpatientPharmacySummarySlipHeaderDto header,
        List<InpatientPharmacySummarySlipLineDto> mergedLines,
        List<InpatientPharmacySummaryDispenseItemDto> dispenseItems);

    /**
     * IF-IPS-03：完成汇总发药命令。
     *
     * @param summaryNo      汇总单号
     * @param operatorId     操作人
     * @param practitionerId 发药药师
     * @param preparerId     调配药师
     * @return 领域命令
     */
    CompleteSummaryDispenseCommand toCompleteSummaryDispenseCommand(
        String summaryNo,
        Long operatorId,
        Long practitionerId,
        Long preparerId);

    /**
     * IF-IPS-03：创建汇总 SupplyDelivery 命令。
     *
     * @param receiverId   领药人
     * @param operatorId   操作人
     * @param supplyLines  type 27 Supply 行
     * @param traceBySupplyRequestId 供应行 id → 追溯码（汇总路径落 Delivery）
     * @return 领域命令
     */
    CreateSummarySupplyDeliveryCommand toCreateSummarySupplyDeliveryCommand(
        Long receiverId,
        Long operatorId,
        List<SupplyRequestModel> supplyLines,
        Map<Long, String> traceBySupplyRequestId);

    /**
     * IF-IPS-03：批量推进 Delivery 完成态命令。
     *
     * @param deliveryIds      新建发放 id 列表
     * @param practitionerId   发药药师
     * @param operatorId       操作人
     * @param completedTime    完成时刻
     * @return 领域命令
     */
    UpdateCompletedStatusBatchCommand toUpdateCompletedStatusBatchCommand(
        List<Long> deliveryIds,
        Long practitionerId,
        Long operatorId,
        LocalDateTime completedTime);

    /**
     * IF-IPS-03：Supply 审批同意命令。
     *
     * @param busNo       业务单号
     * @param approverId  审批人
     * @param operatorId  操作人
     * @return 领域命令
     */
    AgreeRequestCommand toAgreeRequestCommand(String busNo, Long approverId, Long operatorId);

    /**
     * IF-IPS-03：核发成功响应。
     *
     * @param summaryNos 已成功单号
     * @return 写响应
     */
    DispenseSummaryMedicationsResponse toDispenseSummaryMedicationsResponse(List<String> summaryNos);

    /**
     * IF-IPS-04：驳回命令。
     *
     * @param busNo             业务单号
     * @param approverId        审批人
     * @param operatorId        操作人
     * @param rejectReasonCode  驳回原因
     * @return 领域命令
     */
    RejectRequestCommand toRejectRequestCommand(
        String busNo,
        Long approverId,
        Long operatorId,
        String rejectReasonCode);

    /**
     * IF-IPS-04：取消汇总发放命令。
     *
     * @param summaryNo  汇总单号
     * @param operatorId 操作人
     * @return 领域命令
     */
    CancelMedicationDispenseSummaryCommand toCancelMedicationDispenseSummaryCommand(
        String summaryNo,
        Long operatorId);

    /**
     * IF-IPS-04：驳回成功响应。
     *
     * @param summaryNos 已驳回单号
     * @return 写响应
     */
    RejectSummaryDispenseResponse toRejectSummaryDispenseResponse(List<String> summaryNos);
}
