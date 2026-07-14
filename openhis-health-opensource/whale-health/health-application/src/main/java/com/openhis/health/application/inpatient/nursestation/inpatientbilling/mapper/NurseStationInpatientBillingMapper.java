/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.mapper;

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingCancelItemDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingParentRequestAnchorDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingWriteItemDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationSupplementParentOrderRowDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.request.PageNurseStationPricingCatalogRequest;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.command.VoidNursePricingChargeItemsByServiceAnchorsCommand;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.clinical.procedure.command.CancelProcedureRecordCommand;
import com.openhis.health.domain.clinical.procedure.command.CompleteProcedureRecordCommand;
import com.openhis.health.domain.clinical.procedure.command.CreateProcedureRecordCommand;
import com.openhis.health.domain.clinical.procedure.command.StartProcedureExecutionCommand;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.application.shared.advice.InpatientOrderListSkeleton;
import com.openhis.health.application.shared.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.workflow.devicerequest.command.CreateExecutedDeviceOrderCommand;
import com.openhis.health.domain.workflow.devicerequest.command.RevokeExecutedDeviceOrderCommand;
import com.openhis.health.domain.workflow.devicedispense.command.GenerateDeviceDispenseFromTemporaryPathCommand;
import com.openhis.health.domain.workflow.devicedispense.command.RemoveUndispensedDeviceDispenseCommand;
import com.openhis.health.domain.workflow.servicerequest.command.CreateFromNursePricingCommand;
import com.openhis.health.domain.workflow.servicerequest.command.RemoveFromNursePricingCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 住院护士站划价 Request/Command 映射（AS-IP-NUR-005）。
 *
 * <p>职责：入参收窄、写行 Gate 校验、父锚点 → based_on、领域 Command 字段搬运；不含跨域编排。</p>
 */
public interface NurseStationInpatientBillingMapper {

    /**
     * 可见性查询入参。
     *
     * @param encounterIds   请求就诊列表
     * @param managedWardIds 管理病区
     * @return 住院信息读模型查询
     */
    InpatientEncounterPageQuery toInpatientEncounterPageQuery(List<Long> encounterIds, List<Long> managedWardIds);

    /**
     * 选品目录 → 产品定义分页查询。
     *
     * @param request 分页请求
     * @return 读模型分页入参
     */
    ProductDefinitionPageQuery toProductDefinitionPageQuery(PageNurseStationPricingCatalogRequest request);

    /**
     * 解析划价产品表集合（默认诊疗+耗材，BR-IP-PRC-002）。
     *
     * @param requestTables 请求表名；可空
     * @return 物理表名列表
     */
    List<String> resolvePricingProductTableNames(List<String> requestTables);

    /**
     * therapyType → TherapyType code；all 返回 null。
     *
     * @param therapyType all/long/temp
     * @return 长临 code 或 null
     */
    String resolveTherapyEnum(String therapyType);

    /**
     * 直接划价：禁止父锚点（BR-IP-PRC-004）。
     *
     * @param items 写行
     */
    void validateDirectPricingItems(List<NurseStationPricingWriteItemDto> items);

    /**
     * 补费：每行须父锚点（BR-IP-PRC-005）。
     *
     * @param items 写行
     */
    void validateSupplementPricingItems(List<NurseStationPricingWriteItemDto> items);

    /**
     * 父锚点 → based_on 表名与 id。
     *
     * @param anchor 父锚点
     * @return 成对字段
     */
    NurseStationPricingBasedOnPair resolveBasedOn(NurseStationPricingParentRequestAnchorDto anchor);

    /**
     * 诊疗划价批量命令。
     *
     * @param serviceRows  诊疗行
     * @param operatorId   操作人
     * @param requesterId  申请人 practitioner
     * @return 领域命令
     */
    CreateFromNursePricingCommand toCreateFromNursePricingCommand(
        List<NurseStationPricingWriteItemDto> serviceRows,
        Long operatorId,
        Long requesterId);

    /**
     * 耗材划价批量命令。
     *
     * @param deviceRows 耗材行
     * @param operatorId 操作人
     * @return 领域命令
     */
    CreateExecutedDeviceOrderCommand toCreateExecutedDeviceOrderCommand(
        List<NurseStationPricingWriteItemDto> deviceRows,
        Long operatorId);

    /**
     * 护士划价费用命令（PENDING_SETTLEMENT）。
     *
     * @param row           写行
     * @param serviceTable  服务表
     * @param serviceId     服务 id
     * @param dispenseTable 发放表；可空
     * @param dispenseId    发放 id；可空
     * @param procedureId   执行记录 id；诊疗划价必填
     * @param operatorId    操作人
     * @param orgId         机构
     * @return 收费命令
     */
    CreateChargeItemCommand toCreateChargeItemCommand(
        NurseStationPricingWriteItemDto row,
        String serviceTable,
        Long serviceId,
        String dispenseTable,
        Long dispenseId,
        Long procedureId,
        Long operatorId,
        Long orgId);

    /**
     * 护士划价诊疗 → 创建 Procedure 命令（category=TEMPORARY，锚定 wkf_service_request）。
     *
     * @param serviceRequestId 诊疗请求主键
     * @param encounterId      就诊主键
     * @param patientId        患者主键
     * @param practitionerId   登录医务人员（{@code adm_practitioner.id}）；写入 Procedure operator/recorder
     * @param occurrenceTime   执行发生时刻
     * @return 创建命令
     */
    CreateProcedureRecordCommand toCreateNursePricingProcedureRecordCommand(
        Long serviceRequestId,
        Long encounterId,
        Long patientId,
        Long practitionerId,
        LocalDateTime occurrenceTime);

    /**
     * 取消划价 CANCEL 对冲 → 创建 Procedure 命令（继承被对冲行锚点与 occurrence）。
     *
     * @param completedRow 被对冲 COMPLETED 行
     * @param operatorId   操作人
     * @param now          当前时刻（occurrence 回落用）
     * @return 创建命令
     */
    CreateProcedureRecordCommand toCreateProcedureCancelOffsetCommand(
        ProcedureRecordQueryModel completedRow,
        Long operatorId,
        LocalDateTime now);

    /**
     * 启动 Procedure 执行。
     *
     * @param procedureId 执行记录主键
     * @param operatorId  操作人
     * @return 启动命令
     */
    StartProcedureExecutionCommand toStartProcedureExecutionCommand(Long procedureId, Long operatorId);

    /**
     * 护士划价诊疗完成 Procedure。
     *
     * @param procedureId 执行记录主键
     * @param operatorId  操作人
     * @return 完成命令
     */
    CompleteProcedureRecordCommand toCompleteNursePricingProcedureRecordCommand(Long procedureId, Long operatorId);

    /**
     * 取消划价 CANCEL 对冲 Procedure。
     *
     * @param newProcedureId             新 CANCEL 行主键
     * @param statusReasonText           取消原因
     * @param offsetCompletedProcedureId 被对冲 COMPLETED 行主键
     * @param operatorId                 操作人
     * @return 取消命令
     */
    CancelProcedureRecordCommand toCancelNursePricingProcedureRecordCommand(
        Long newProcedureId,
        String statusReasonText,
        Long offsetCompletedProcedureId,
        Long operatorId);

    /**
     * 临时路径生成耗材发放草稿。
     *
     * @param deviceReqId 耗材请求 id
     * @param row         写行
     * @param operatorId  操作人
     * @return 命令
     */
    GenerateDeviceDispenseFromTemporaryPathCommand toGenerateDeviceDispenseFromTemporaryPathCommand(
        Long deviceReqId,
        NurseStationPricingWriteItemDto row,
        Long operatorId);

    /**
     * 关键字/科室二次过滤父医嘱行。
     *
     * @param rows    源行
     * @param keyword 关键字
     * @param orgId   科室
     * @return 过滤后
     */
    List<NurseStationSupplementParentOrderRowDto> filterByKeywordAndOrg(
        List<NurseStationSupplementParentOrderRowDto> rows,
        String keyword,
        Long orgId);

    /**
     * 已划价模式：DIRECT / SUPPLEMENT。
     *
     * @param skeleton OrderInfo 行
     * @return 模式 code
     */
    String resolvePricingMode(InpatientOrderListSkeleton skeleton);

    /**
     * 取消划价入参 Gate（BR-IP-PRC-021）。
     *
     * @param items 取消行
     */
    void validateCancelPricingItems(List<NurseStationPricingCancelItemDto> items);

    /**
     * orderType → 上游 serviceTable（wkf_service_request / wkf_device_request）。
     *
     * @param orderType SERVICE_REQUEST / DEVICE_REQUEST
     * @return 物理表名
     */
    String resolveCancelServiceTable(String orderType);

    /**
     * 批量软删护士划价诊疗请求命令。
     *
     * @param serviceRequestIds 诊疗请求 id
     * @param operatorId        操作人
     * @return 领域命令
     */
    RemoveFromNursePricingCommand toRemoveFromNursePricingCommand(List<Long> serviceRequestIds, Long operatorId);

    /**
     * 批量撤销已完成耗材请求命令。
     *
     * @param deviceRequestIds 耗材请求 id
     * @param operatorId       操作人
     * @return 领域命令
     */
    RevokeExecutedDeviceOrderCommand toRevokeExecutedDeviceOrderCommand(List<Long> deviceRequestIds, Long operatorId);

    /**
     * 按单锚点撤销护士划价待结算费用命令。
     *
     * @param encounterId  就诊
     * @param serviceTable 上游表
     * @param serviceId    上游 id
     * @param operatorId   操作人
     * @return 领域命令
     */
    VoidNursePricingChargeItemsByServiceAnchorsCommand toVoidNursePricingChargeItemsByServiceAnchorsCommand(
        Long encounterId,
        String serviceTable,
        Long serviceId,
        Long operatorId);

    /**
     * 软删耗材请求下未发放/已回退待发发放行。
     *
     * @param deviceRequestId 耗材请求 id
     * @param operatorId      操作人
     * @return 领域命令
     */
    RemoveUndispensedDeviceDispenseCommand toRemoveUndispensedDeviceDispenseCommand(
        Long deviceRequestId,
        Long operatorId);

    /**
     * 取消划价库存回补命令（已发 Dispense 路径累计量）。
     *
     * @param operatorId       操作人
     * @param inventoryIdToQty 库存项 → 回补量
     * @return 增加库存命令
     */
    IncreaseQuantityCommand toIncreaseQuantityCommand(Long operatorId, Map<Long, BigDecimal> inventoryIdToQty);
}
