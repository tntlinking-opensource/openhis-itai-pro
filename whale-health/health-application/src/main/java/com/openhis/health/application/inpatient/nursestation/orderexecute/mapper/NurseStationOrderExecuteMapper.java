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

package com.openhis.health.application.inpatient.nursestation.orderexecute.mapper;

import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecutePatientGroupDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteRowDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteWriteSlotDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.response.ExecuteNurseStationOrdersResponse;
import com.openhis.health.application.inpatient.nursestation.orderexecute.response.ListNurseStationOrderExecuteResponse;
import com.openhis.health.application.inpatient.nursestation.orderexecute.response.SkipNurseStationOrderSlotsResponse;
import com.openhis.health.domain.administration.chargeitem.command.ActivateChargeByExeCommand;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.clinical.procedure.command.CompleteProcedureRecordCommand;
import com.openhis.health.domain.clinical.procedure.command.CreateProcedureRecordCommand;
import com.openhis.health.domain.clinical.procedure.command.SkipProcedureRecordCommand;
import com.openhis.health.domain.clinical.procedure.command.StartProcedureExecutionCommand;
import com.openhis.health.domain.medication.medicationdispense.command.GenerateDispenseFromExecutionCommand;
import com.openhis.health.domain.medication.medicationdispense.command.LinkDispenseProcedureCommand;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.application.querysupport.order.dto.InpatientOrderListSkeleton;
import com.openhis.health.domain.workflow.devicedispense.command.GenerateDeviceDispenseFromTemporaryPathCommand;
import com.openhis.health.domain.workflow.devicedispense.command.GenerateIssuedDeviceDispenseFromExecutionCommand;
import com.openhis.health.domain.workflow.devicerequest.command.CreateExecutedDeviceOrderCommand;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 住院护士站医嘱执行 Request/Command/Response 映射（AS-IP-NUR-004）。
 *
 * <p>禁止承载业务流程判断；Tab/长临解析与 Tab 槽位收窄辅助在本接口声明。</p>
 */
public interface NurseStationOrderExecuteMapper {

    /**
     * 可见性查询入参：多选就诊 + 管理病区收窄（BR-IP-EXE-001 / BR-IP-NUR-003）。
     *
     * @param encounterIds   请求就诊主键列表；调用方保证非空
     * @param managedWardIds 登录者管理病区；可空
     * @return 住院信息读模型查询入参
     */
    InpatientEncounterPageQuery toInpatientEncounterPageQuery(List<Long> encounterIds, List<Long> managedWardIds);

    /**
     * therapyType → TherapyType code；all 返回 null。
     *
     * @param therapyType all / long / temp；可空视为 all
     * @return 长临 code；all 时为 null
     */
    String resolveTherapyEnum(String therapyType);

    /**
     * 按 executeTab 收窄槽位：医嘱展示字段不变，仅当前 Tab 对应类别保留 planTime ≤ deadline 的计划点（BR-IP-EXE-010）。
     *
     * @param row                已装配四类槽位的医嘱行；原地改写槽位字段
     * @param executeTab         pending / executed / skipped / cancelled
     * @param executionDeadline  截止时间
     */
    void applyExecuteTabSlotFilter(
        NurseStationOrderExecuteRowDto row,
        String executeTab,
        LocalDateTime executionDeadline);

    /**
     * 分组列表 → 列表响应。
     *
     * @param groups 患者分组；可 null
     * @return 列表响应
     */
    ListNurseStationOrderExecuteResponse toListResponse(List<NurseStationOrderExecutePatientGroupDto> groups);

    /**
     * 批量执行成功响应。
     *
     * @param processedCount 成功槽位数
     * @return 响应 DTO
     */
    ExecuteNurseStationOrdersResponse toExecuteResponse(int processedCount);

    /**
     * 批量不执行成功响应。
     *
     * @param processedCount 成功槽位数
     * @return 响应 DTO
     */
    SkipNurseStationOrderSlotsResponse toSkipResponse(int processedCount);

    /**
     * 写槽位 → 创建 Procedure 命令（BR-IP-EXE-015 skipDuplicate=true）。
     *
     * @param item         写槽位
     * @param requestId    请求主键
     * @param requestTable 请求表
     * @param encounterId  就诊主键
     * @param patientId    患者主键
     * @param categoryEnum 执行类目
     * @param operatorId   操作人
     * @return 创建命令
     */
    CreateProcedureRecordCommand toCreateProcedureRecordCommand(
        NurseStationOrderExecuteWriteSlotDto item,
        Long requestId,
        String requestTable,
        Long encounterId,
        Long patientId,
        String categoryEnum,
        Long operatorId);

    /**
     * 启动 Procedure 执行。
     *
     * @param procedureId 执行记录主键
     * @param operatorId  操作人
     * @return 启动命令
     */
    StartProcedureExecutionCommand toStartProcedureExecutionCommand(Long procedureId, Long operatorId);

    /**
     * 完成 Procedure 执行。
     *
     * @param procedureId 执行记录主键
     * @param operatorId  操作人
     * @return 完成命令
     */
    CompleteProcedureRecordCommand toCompleteProcedureRecordCommand(Long procedureId, Long operatorId);

    /**
     * 登记计划点不执行（BR-IP-EXE-021）。
     *
     * @param item         写槽位
     * @param requestId    请求主键
     * @param requestTable 请求表
     * @param encounterId  就诊主键
     * @param patientId    患者主键
     * @param categoryEnum 执行类目
     * @param operatorId   操作人
     * @param recordedTime 登记时刻
     * @return 不执行命令
     */
    SkipProcedureRecordCommand toSkipProcedureRecordCommand(
        NurseStationOrderExecuteWriteSlotDto item,
        Long requestId,
        String requestTable,
        Long encounterId,
        Long patientId,
        String categoryEnum,
        Long operatorId,
        LocalDateTime recordedTime);

    /**
     * 长期药品执行 → 新建发放单命令。
     *
     * @param medReqId           药品申请主键
     * @param procedureId        执行记录主键
     * @param planTime           计划发药时刻
     * @param locationId         FIFO 库位
     * @param lotNumber          FIFO 批号
     * @param quantity           应发数量（单次量）
     * @param unitCode           单位
     * @param medicationId       药品定义主键
     * @param encounterId        就诊主键
     * @param patientId          患者主键
     * @return 生成发放命令
     */
    GenerateDispenseFromExecutionCommand toGenerateDispenseFromExecutionCommand(
        Long medReqId,
        Long procedureId,
        LocalDateTime planTime,
        Long locationId,
        String lotNumber,
        BigDecimal quantity,
        String unitCode,
        Long medicationId,
        Long encounterId,
        Long patientId);

    /**
     * 临时药品执行 → 挂接已有发放单命令。
     *
     * @param dispenseId         发放单主键
     * @param procedureId        执行记录主键
     * @param plannedDispenseTime 计划发药时刻
     * @param operatorId         操作人
     * @return 挂接命令
     */
    LinkDispenseProcedureCommand toLinkDispenseProcedureCommand(
        Long dispenseId,
        Long procedureId,
        LocalDateTime plannedDispenseTime,
        Long operatorId);

    /**
     * 临时医嘱执行 → 激活已有 DRAFT 收费明细。
     *
     * @param chargeItemId 收费明细主键
     * @param procedureId  执行记录主键
     * @param performedAt  执行发生时刻
     * @param performerId  执行人
     * @return 激活命令
     */
    ActivateChargeByExeCommand toActivateChargeByExeCommand(
        Long chargeItemId,
        Long procedureId,
        LocalDateTime performedAt,
        Long performerId);

    /**
     * 长期医嘱执行 → 新建待结算收费明细（generate_source=ORDER_EXECUTE）。
     *
     * @param order         医嘱读模型行
     * @param template      签发侧费用模板行；可空时由 {@code priceLine} 补全定价
     * @param priceLine     销售定价行；模板缺 {@code definitionId} 时必填
     * @param accountId     就诊账户（IF-IP-EXE-02 由前端从患者列表回传）
     * @param procedureId   执行记录主键
     * @param planTime      计划执行时刻
     * @param quantity           计价数量
     * @param quantityUnitCode   计价单位 code（药嘱须与 {@code med_medication_request.unit_code} 一致）
     * @param medicationProduct  产品定义读模型（药/诊路径用于比例换算单价）
     * @param dispenseId         关联发放主键；非药嘱可空
     * @param dispenseTable      发放表名；可空
     * @param operatorId         操作人
     * @param orgId              机构主键
     * @return 创建命令
     */
    CreateChargeItemCommand toCreatePlannedChargeItemFromOrderExecutionCommand(
        InpatientOrderListSkeleton order,
        ChargeItem template,
        ChargeItemDefinition priceLine,
        Long accountId,
        Long procedureId,
        LocalDateTime planTime,
        BigDecimal quantity,
        String quantityUnitCode,
        ProductDefinitionDisplayDto medicationProduct,
        Long dispenseId,
        String dispenseTable,
        Long operatorId,
        Long orgId);

    /**
     * 绑定项执行 → 新建待结算收费明细（generate_source=ORDER_EXECUTE）。
     *
     * @param patientId     患者主键
     * @param encounterId   就诊主键
     * @param serviceTable  上游服务表
     * @param serviceId     上游服务主键
     * @param productTable  产品表
     * @param productId     产品主键
     * @param priceLine     销售定价行（写入 {@code definitionId} 与金额）
     * @param accountId     就诊账户
     * @param procedureId   执行记录主键
     * @param planTime        计划执行时刻
     * @param quantity        计价数量
     * @param quantityUnitCode 计价单位 code
     * @param productRow      产品定义读模型
     * @param dispenseId      关联发放主键；可空
     * @param dispenseTable   发放表名；可空
     * @param operatorId      操作人
     * @param orgId           机构主键
     * @return 创建命令
     */
    CreateChargeItemCommand toCreatePlannedChargeItemFromBindExecutionCommand(
        Long patientId,
        Long encounterId,
        String serviceTable,
        Long serviceId,
        String productTable,
        Long productId,
        ChargeItemDefinition priceLine,
        Long accountId,
        Long procedureId,
        LocalDateTime planTime,
        BigDecimal quantity,
        String quantityUnitCode,
        ProductDefinitionDisplayDto productRow,
        Long dispenseId,
        String dispenseTable,
        Long operatorId,
        Long orgId);

    /**
     * 绑定耗材执行 → 生成已完成 DeviceRequest 命令。
     *
     * @param deviceDefId     耗材定义主键
     * @param quantity          数量
     * @param performLocation   发放库房；可空
     * @param encounterId       就诊主键
     * @param patientId         患者主键
     * @param operatorId        操作人
     * @return 创建命令
     */
    CreateExecutedDeviceOrderCommand toCreateExecutedDeviceOrderCommand(
        Long deviceDefId,
        BigDecimal quantity,
        Long performLocation,
        Long encounterId,
        Long patientId,
        Long operatorId);

    /**
     * 绑定耗材执行 → 临时路径草稿发放单命令。
     *
     * @param deviceReqId  耗材请求主键
     * @param deviceDefId  耗材定义主键
     * @param encounterId  就诊主键
     * @param patientId    患者主键
     * @param quantity     数量
     * @param unitCode     单位
     * @param limitTime    时限；可空
     * @param operatorId   操作人
     * @return 生成命令
     */
    GenerateDeviceDispenseFromTemporaryPathCommand toGenerateDeviceDispenseFromTemporaryPathCommand(
        Long deviceReqId,
        Long deviceDefId,
        Long encounterId,
        Long patientId,
        BigDecimal quantity,
        String unitCode,
        LocalDateTime limitTime,
        Long operatorId);

    /**
     * 绑定耗材执行 → 执行直发已发放命令。
     *
     * @param deviceReqId  耗材请求主键
     * @param procedureId  主医嘱 Procedure 主键
     * @param lotNumber    批号占位（绑定耗材直发，库存由其他页面处理）
     * @param traceNo      追溯号占位
     * @param quantity     发放数量
     * @param operatorId   操作人
     * @return 直发命令
     */
    GenerateIssuedDeviceDispenseFromExecutionCommand toGenerateIssuedDeviceDispenseFromExecutionCommand(
        Long deviceReqId,
        Long procedureId,
        String lotNumber,
        String traceNo,
        BigDecimal quantity,
        Long operatorId);
}
