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

package com.openhis.health.application.outpatient.doctorstation.advice.mapper;

import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceDraftLineRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.PageDoctorStationAdviceCatalogRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.SaveDoctorStationAdviceDraftBatchRequest;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.command.VoidUnbilledDraftChargeItemsByServiceAnchorCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.medication.medicationrequest.command.AdjustMedicationOrderGroupCommand;
import com.openhis.health.domain.medication.medicationrequest.command.ChangeMedicationOrderDraftCommand;
import com.openhis.health.domain.medication.medicationrequest.command.CreateMedicationOrderDraftCommand;
import com.openhis.health.domain.medication.medicationrequest.command.RemoveMedicationOrderDraftCommand;
import com.openhis.health.domain.medication.medicationrequest.command.SignMedicationOrderCommand;
import com.openhis.health.domain.medication.medicationrequest.command.SignOffMedicationOrderCommand;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.module.querysupport.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.workflow.devicerequest.command.ChangeDeviceOrderDraftCommand;
import com.openhis.health.domain.workflow.devicerequest.command.CreateDeviceOrderDraftCommand;
import com.openhis.health.domain.workflow.devicerequest.command.RemoveDeviceOrderDraftCommand;
import com.openhis.health.domain.workflow.devicerequest.command.SignDeviceOrderCommand;
import com.openhis.health.domain.workflow.devicerequest.command.SignOffDeviceOrderCommand;
import com.openhis.health.domain.workflow.servicerequest.command.CreateServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.ModifyServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.RemoveServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.SignServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.WithdrawSignedServiceRequestCommand;

import java.math.BigDecimal;
import java.util.List;

/**
 * 医生站医嘱开立：DTO 与领域命令之间的装配（不含领域不变量判定）。
 *
 * <p>校验类方法仅做应用层门控（患者/就诊一致性等）；写语义由 {@code *DomainService} 承担。</p>
 */
public interface DoctorStationAdviceMapper {

    /**
     * Gate：请求患者必须与门诊读模型中的患者一致。
     *
     * @param patientId          请求携带的患者主键
     * @param encounterPatientId 就诊快照上的患者主键
     * @throws com.openhis.whale.core.application.exception.BusinessException 不一致时抛出
     */
    void validatePatientEncounter(Long patientId, Long encounterPatientId);

    /**
     * 将目录分页请求转为产品定义读模型分页查询命令。
     *
     * @param request             前端分页与筛选条件
     * @return 读模型查询命令
     */
    ProductDefinitionPageQuery toProductDefinitionPageQuery(PageDoctorStationAdviceCatalogRequest request);

    /**
     * 装配「新建药品医嘱草稿」领域命令。
     *
     * @param parent     批次父上下文（就诊、患者）
     * @param line       单行请求（含药品载荷）
     * @param operatorId 操作人
     * @param orgId      开立机构
     * @return 领域命令
     */
    CreateMedicationOrderDraftCommand toCreateMedicationOrderDraftCommand(
        SaveDoctorStationAdviceDraftBatchRequest parent,
        DoctorStationAdviceDraftLineRequest line,
        Long operatorId,
        Long orgId
    );

    /**
     * 装配「修改药品医嘱草稿」领域命令。
     *
     * @param line       单行请求
     * @param operatorId 操作人
     * @return 领域命令
     */
    ChangeMedicationOrderDraftCommand toChangeMedicationOrderDraftCommand(
        DoctorStationAdviceDraftLineRequest line,
        Long operatorId
    );

    /**
     * 装配「删除药品医嘱草稿」领域命令。
     *
     * @param requestId  药品申请主键
     * @param operatorId 操作人
     * @return 领域命令
     */
    RemoveMedicationOrderDraftCommand toRemoveMedicationOrderDraftCommand(Long requestId, Long operatorId);

    /**
     * 装配「新建耗材医嘱草稿」领域命令。
     *
     * @param parent     批次父上下文
     * @param line       单行请求（含耗材载荷）
     * @param operatorId 操作人
     * @return 领域命令
     */
    CreateDeviceOrderDraftCommand toCreateDeviceOrderDraftCommand(
        SaveDoctorStationAdviceDraftBatchRequest parent,
        DoctorStationAdviceDraftLineRequest line,
        Long operatorId
    );

    /**
     * 装配「修改耗材医嘱草稿」领域命令。
     *
     * @param line       单行请求
     * @param operatorId 操作人
     * @return 领域命令
     */
    ChangeDeviceOrderDraftCommand toChangeDeviceOrderDraftCommand(DoctorStationAdviceDraftLineRequest line, Long operatorId);

    /**
     * 装配「删除耗材医嘱草稿」领域命令。
     *
     * @param requestId  耗材申请主键
     * @param operatorId 操作人
     * @return 领域命令
     */
    RemoveDeviceOrderDraftCommand toRemoveDeviceOrderDraftCommand(Long requestId, Long operatorId);

    /**
     * 装配「新建诊疗申请草稿」领域命令（依赖活动定义对应的产品读模型行）。
     *
     * @param parent     批次父上下文
     * @param line       单行请求（含诊疗载荷）
     * @param productRm  产品定义读模型行
     * @param operatorId 操作人
     * @return 领域命令
     */
    CreateServiceRequestDraftCommand toCreateServiceRequestDraftCommand(
        SaveDoctorStationAdviceDraftBatchRequest parent,
        DoctorStationAdviceDraftLineRequest line,
        ProductDefinitionDisplayDto productRm,
        Long operatorId
    );

    /**
     * 装配「修改诊疗申请草稿」领域命令。
     *
     * @param line       单行请求
     * @param operatorId 操作人
     * @return 领域命令
     */
    ModifyServiceRequestDraftCommand toModifyServiceRequestDraftCommand(DoctorStationAdviceDraftLineRequest line, Long operatorId);

    /**
     * 装配「删除诊疗申请草稿」领域命令。
     *
     * @param requestId  诊疗申请主键
     * @param operatorId 操作人
     * @return 领域命令
     */
    RemoveServiceRequestDraftCommand toRemoveServiceRequestDraftCommand(Long requestId, Long operatorId);

    /**
     * 装配「按服务锚点作废未计费草稿费用」命令。
     *
     * @param encounterId 就诊主键
     * @param serviceTable 服务物理表名（与收费锚点一致）
     * @param serviceId    服务主键
     * @param operatorId   操作人
     * @return 领域命令
     */
    VoidUnbilledDraftChargeItemsByServiceAnchorCommand toVoidDraftChargeAnchor(Long encounterId, String serviceTable, Long serviceId, Long operatorId);

    /**
     * 装配「由医嘱申请生成就诊费用草稿」命令。
     *
     * @param encounterId   就诊
     * @param patientId     患者
     * @param accountId     自费账户
     * @param orgId         机构
     * @param operatorId    操作人
     * @param requestId              医嘱申请主键
     * @param serviceTable           医嘱申请表名
     * @param productDefinitionTable 产品定义表名（三大目录之一），写入 {@code product_table}
     * @param productDefinitionId    产品定义主键，写入 {@code product_id}
     * @param priceLine              定价行（单价来源）
     * @param quantity               计价数量（与医嘱请求 quantity 同语义，须 &gt; 0）
     * @param quantityUnit           计价单位 code（与医嘱请求 unit_code 同语义）
     * @param productRow             产品定义读模型，用于目录单位槽比例换算
     * @return 领域命令
     */
    CreateChargeItemCommand toCreateChargeItemDraftFromOrder(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long operatorId,
        Long requestId,
        String serviceTable,
        String productDefinitionTable,
        Long productDefinitionId,
        ChargeItemDefinition priceLine,
        BigDecimal quantity,
        String quantityUnit,
        ProductDefinitionDisplayDto productRow
    );

    /**
     * 装配单条「医嘱/发放锚点费用草稿」命令项（与 {@link #toCreateChargeItemDraftFromOrder} 金额与上下文口径一致），供签发 FIFO 多段合并为一次领域写入。
     *
     * @param encounterId   就诊主键
     * @param patientId     患者主键
     * @param accountId     自费账户主键
     * @param orgId         机构主键
     * @param serviceId              上游业务主键（暂存草稿多为医嘱请求 id；签发 FIFO 分段路径请改用 {@link #toCreateChargeItemDraftItemForFifoDispenseSegment}）
     * @param serviceTable           上游物理表名（与 {@code service_id} 同行一致）
     * @param productDefinitionTable 产品定义表名（三大目录之一），写入 {@code product_table}
     * @param productDefinitionId    产品定义主键，写入 {@code product_id}
     * @param priceLine              定价行，不得为 {@code null}
     * @param quantity               计价数量（须 &gt; 0）
     * @param quantityUnit           计价单位 code
     * @param productRow             产品定义读模型
     * @return 费用创建项
     * @throws com.openhis.whale.core.application.exception.BusinessException 定价行为 {@code null} 时抛出
     */
    CreateChargeItemCommand.Item toCreateChargeItemDraftItemFromOrder(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long serviceId,
        String serviceTable,
        String productDefinitionTable,
        Long productDefinitionId,
        ChargeItemDefinition priceLine,
        BigDecimal quantity,
        String quantityUnit,
        ProductDefinitionDisplayDto productRow
    );

    /**
     * 装配签发 FIFO 单段费用草稿命令项（DM-ADM-011：{@code service} 锚医嘱请求，{@code product} 锚定义，{@code dispense} 锚发放分段）。
     *
     * <p>在 {@link #toCreateChargeItemDraftItemFromOrder} 的金额与上下文口径基础上，写入 {@code product_*} 与 {@code dispense_*}，
     * {@code service_*} 仍为医嘱请求表名与申请主键。</p>
     *
     * @param encounterId           就诊主键
     * @param patientId             患者主键
     * @param accountId             自费账户主键
     * @param orgId                 开立机构主键
     * @param requestId             药品或耗材医嘱申请主键，写入 {@code service_id}
     * @param requestServiceTable   医嘱请求物理表名，写入 {@code service_table}
     * @param productDefinitionTable 药品定义表或耗材定义表名，写入 {@code product_table}
     * @param productDefinitionId   定义主键，写入 {@code product_id}
     * @param dispenseTable         发放物理表名，写入 {@code dispense_table}
     * @param dispenseId            本条 FIFO 分段对应的发放主键，写入 {@code dispense_id}
     * @param priceLine             定价行，不得为 {@code null}
     * @param quantity              本分段的计价数量（须 &gt; 0）
     * @param quantityUnit          计价单位 code（与医嘱开立单位一致）
     * @param productRow            产品定义读模型
     * @return 单条费用创建项，可直接并入批量 {@link com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand}
     * @throws com.openhis.whale.core.application.exception.BusinessException 定价行为为空等与 {@link #toCreateChargeItemDraftItemFromOrder} 一致
     */
    CreateChargeItemCommand.Item toCreateChargeItemDraftItemForFifoDispenseSegment(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long requestId,
        String requestServiceTable,
        String productDefinitionTable,
        Long productDefinitionId,
        String dispenseTable,
        Long dispenseId,
        ChargeItemDefinition priceLine,
        BigDecimal quantity,
        String quantityUnit,
        ProductDefinitionDisplayDto productRow
    );

    /**
     * 装配药品医嘱「签发」命令。
     *
     * @param id             药品申请主键
     * @param prescriptionNo 处方号
     * @param operatorId     操作人
     * @return 领域命令
     */
    SignMedicationOrderCommand toSignMedicationOrderCommand(Long id, String prescriptionNo, Long operatorId);

    /**
     * 装配药品医嘱「签退」命令。
     *
     * @param id         药品申请主键
     * @param operatorId 操作人
     * @return 领域命令
     */
    SignOffMedicationOrderCommand toSignOffMedicationOrderCommand(Long id, Long operatorId);

    /**
     * 装配诊疗申请「签发」命令。
     *
     * @param id         诊疗申请主键
     * @param operatorId 操作人
     * @return 领域命令
     */
    SignServiceRequestCommand toSignServiceRequestCommand(Long id, Long operatorId);

    /**
     * 装配诊疗申请「签退已签发」命令。
     *
     * @param id         诊疗申请主键
     * @param operatorId 操作人
     * @return 领域命令
     */
    WithdrawSignedServiceRequestCommand toWithdrawServiceRequestCommand(Long id, Long operatorId);

    /**
     * 装配耗材医嘱「签发」命令。
     *
     * @param id         耗材申请主键
     * @param operatorId 操作人
     * @return 领域命令
     */
    SignDeviceOrderCommand toSignDeviceOrderCommand(Long id, Long operatorId);

    /**
     * 装配耗材医嘱「签退」命令。
     *
     * @param id         耗材申请主键
     * @param operatorId 操作人
     * @return 领域命令
     */
    SignOffDeviceOrderCommand toSignOffDeviceOrderCommand(Long id, Long operatorId);

    /**
     * 装配药品医嘱「调整组号」命令（清空传 {@code null} groupId）。
     *
     * @param ids        药品申请主键集合
     * @param groupId    目标组号，可为 {@code null} 表示清空成组
     * @param operatorId 操作人
     * @return 领域命令
     */
    AdjustMedicationOrderGroupCommand toAdjustMedicationGroupCommand(List<Long> ids, Long groupId, Long operatorId);

    /**
     * 解析医嘱绑定查询用的项目主键：{@code itemId} 非空时直接使用；否则将 {@code itemNo} trim 后解析为 {@link Long}。
     *
     * @param itemNo 业务号形式的数字主键；在 {@code itemId} 为空时必填
     * @param itemId   数字主键，优先
     * @return 非空主键
     * @throws com.openhis.whale.core.application.exception.BusinessException 二者皆空、或 {@code itemNo} 非数值
     */
    /**
     * 解析医嘱绑定查询用的来源项键（与 {@code cus_service_device_template.item_id} 精确匹配）。
     *
     * @param bindType 绑定类型业务码
     * @param itemNo   用法 {@code method_code} 或诊疗主键字符串
     * @param itemId   诊疗活动定义主键（诊疗绑定时可选）
     * @return 非空来源项键
     */
    String resolveOrderBindSourceItemKey(String bindType, String itemNo, Long itemId);

}
