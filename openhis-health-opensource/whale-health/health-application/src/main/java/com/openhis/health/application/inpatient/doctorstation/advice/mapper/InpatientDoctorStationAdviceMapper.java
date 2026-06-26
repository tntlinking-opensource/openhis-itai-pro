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

package com.openhis.health.application.inpatient.doctorstation.advice.mapper;

import com.openhis.health.application.inpatient.doctorstation.advice.dto.CancelStopLongTermAdviceLineRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceDraftLineRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.StopLongTermAdviceLineRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.CreateDischargeInpatientDoctorStationAdviceRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.PageInpatientDoctorStationAdviceCatalogRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.SaveInpatientDoctorStationAdviceDraftBatchRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.response.CreateDischargeInpatientDoctorStationAdviceResponse;
import com.openhis.health.domain.custom.encounterhistory.command.RecordDischargeMilestoneCommand;
import com.openhis.health.domain.medication.medicationrequest.command.AdjustMedicationOrderGroupCommand;
import com.openhis.health.domain.medication.medicationrequest.command.CancelStopLongTermMedicationOrderCommand;
import com.openhis.health.domain.medication.medicationrequest.command.ChangeMedicationOrderDraftCommand;
import com.openhis.health.domain.medication.medicationrequest.command.CreateMedicationOrderDraftCommand;
import com.openhis.health.domain.medication.medicationrequest.command.RemoveMedicationOrderDraftCommand;
import com.openhis.health.domain.medication.medicationrequest.command.SignMedicationOrderCommand;
import com.openhis.health.domain.medication.medicationrequest.command.SignOffMedicationOrderCommand;
import com.openhis.health.domain.medication.medicationrequest.command.StopLongTermMedicationOrderCommand;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.module.querysupport.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.workflow.servicerequest.command.CancelStopServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.CreateDischargeRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.CreateServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.ModifyServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.RemoveServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.SignServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.StopServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.WithdrawSignedServiceRequestCommand;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 住院医生站医嘱开立：DTO 与领域命令之间的装配（不含领域不变量判定）。
 *
 * <p>校验类方法仅做应用层门控；写语义由 {@code *DomainService} 承担。住院场景<strong>不含耗材</strong>分支（BR-IP-ORD-012）。</p>
 */
public interface InpatientDoctorStationAdviceMapper {

    /**
     * Gate：请求患者必须与住院读模型快照患者一致。
     *
     * @param snapshot         住院信息读模型快照
     * @param requestPatientId 请求携带的患者主键
     * @throws com.openhis.whale.core.application.exception.BusinessException 不一致或上下文缺失时抛出
     */
    void validateInpatientEncounterPatientGate(InpatientEncounterContextQueryModel snapshot, Long requestPatientId);

    /**
     * Gate：住院就诊须为 IMP 且医生站可见（与诊断模块门控语义一致）。
     *
     * @param snapshot 住院信息读模型快照
     * @throws com.openhis.whale.core.application.exception.BusinessException 就诊不可见或状态不允许时抛出
     */
    void validateInpatientEncounterClassImp(InpatientEncounterContextQueryModel snapshot);

    /**
     * Gate：住院就诊须处于可写医嘱状态。
     *
     * @param snapshot 住院信息读模型快照
     * @throws com.openhis.whale.core.application.exception.BusinessException 不可写时抛出
     */
    void validateInpatientEncounterWritableGate(InpatientEncounterContextQueryModel snapshot);

    /**
     * Gate：列表请求必填校验。
     *
     * @param encounterId 就诊主键
     * @param patientId   患者主键
     * @throws com.openhis.whale.core.application.exception.BusinessException 必填缺失时抛出
     */
    void validateListRequest(Long encounterId, Long patientId);

    /**
     * 将目录分页请求转为产品定义读模型分页查询命令（仅药品+诊疗表）。
     *
     * @param request 前端分页与筛选条件
     * @return 读模型查询命令
     */
    ProductDefinitionPageQuery toProductDefinitionPageQuery(PageInpatientDoctorStationAdviceCatalogRequest request);

    /**
     * 装配「新建药品医嘱草稿」领域命令。
     *
     * @param parent     批次父上下文
     * @param line       单行请求（含药品载荷与长临）
     * @param operatorId 操作人
     * @param orgId      开立机构
     * @return 领域命令
     */
    CreateMedicationOrderDraftCommand toCreateMedicationOrderDraftCommand(
        SaveInpatientDoctorStationAdviceDraftBatchRequest parent,
        InpatientDoctorStationAdviceDraftLineRequest line,
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
        InpatientDoctorStationAdviceDraftLineRequest line,
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
     * 装配「新建诊疗申请草稿」领域命令。
     *
     * @param parent     批次父上下文
     * @param line       单行请求
     * @param productRm  产品定义读模型行
     * @param operatorId 操作人
     * @return 领域命令
     */
    CreateServiceRequestDraftCommand toCreateServiceRequestDraftCommand(
        SaveInpatientDoctorStationAdviceDraftBatchRequest parent,
        InpatientDoctorStationAdviceDraftLineRequest line,
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
    ModifyServiceRequestDraftCommand toModifyServiceRequestDraftCommand(
        InpatientDoctorStationAdviceDraftLineRequest line,
        Long operatorId
    );

    /**
     * 装配「删除诊疗申请草稿」领域命令。
     *
     * @param requestId  诊疗申请主键
     * @param operatorId 操作人
     * @return 领域命令
     */
    RemoveServiceRequestDraftCommand toRemoveServiceRequestDraftCommand(Long requestId, Long operatorId);

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
     * 装配药品医嘱「调整组号」命令。
     *
     * @param ids        药品申请主键集合
     * @param groupId    目标组号，可为 null 表示拆组
     * @param operatorId 操作人
     * @return 领域命令
     */
    AdjustMedicationOrderGroupCommand toAdjustMedicationGroupCommand(List<Long> ids, Long groupId, Long operatorId);

    /**
     * 装配长期药品停嘱命令（IF-IP-DOR-20）。
     *
     * @param line       停嘱行
     * @param operatorId 操作人
     * @return 领域命令
     */
    StopLongTermMedicationOrderCommand toStopLongTermMedicationOrderCommand(
        StopLongTermAdviceLineRequest line,
        Long operatorId
    );

    /**
     * 装配诊疗停嘱命令（IF-IP-DOR-20）。
     *
     * @param line       停嘱行
     * @param operatorId 操作人
     * @return 领域命令
     */
    StopServiceRequestCommand toStopServiceRequestCommand(StopLongTermAdviceLineRequest line, Long operatorId);

    /**
     * 装配长期药品取消停嘱命令（IF-IP-DOR-25）。
     *
     * @param line       取消停嘱行
     * @param operatorId 操作人
     * @return 领域命令
     */
    CancelStopLongTermMedicationOrderCommand toCancelStopLongTermMedicationOrderCommand(
        CancelStopLongTermAdviceLineRequest line,
        Long operatorId
    );

    /**
     * 装配诊疗取消停嘱命令（IF-IP-DOR-25）。
     *
     * @param line       取消停嘱行
     * @param operatorId 操作人
     * @return 领域命令
     */
    CancelStopServiceRequestCommand toCancelStopServiceRequestCommand(
        CancelStopLongTermAdviceLineRequest line,
        Long operatorId
    );

    /**
     * 装配出院服务申请命令（IF-IP-DOR-21）。
     *
     * @param request    出院请求
     * @param operatorId 操作人
     * @return 领域命令
     */
    CreateDischargeRequestCommand toCreateDischargeRequestCommand(
        CreateDischargeInpatientDoctorStationAdviceRequest request,
        Long operatorId
    );

    /**
     * 装配离院里程碑命令（IF-IP-DOR-21）。
     *
     * @param serviceRequestId 出院 ServiceRequest 主键
     * @param request          出院请求
     * @param operatorId       操作人
     * @return 领域命令
     */
    RecordDischargeMilestoneCommand toRecordDischargeMilestoneCommand(
        Long serviceRequestId,
        CreateDischargeInpatientDoctorStationAdviceRequest request,
        Long operatorId
    );

    /**
     * 装配出院开立响应。
     *
     * @param serviceRequestId   出院申请主键
     * @param encounterHistoryId 里程碑主键
     * @return 响应 DTO
     */
    CreateDischargeInpatientDoctorStationAdviceResponse toCreateDischargeResponse(
        Long serviceRequestId,
        Long encounterHistoryId
    );

    /**
     * 解析医嘱绑定查询用的来源项键（与 {@code cus_service_device_template.item_id} 精确匹配）。
     *
     * <p>用法绑定：{@code itemNo} 为 {@code method_code} 术语 code；诊疗绑定：{@code itemId} 或数字形态
     * {@code itemNo} 为活动定义主键字符串。</p>
     *
     * @param bindType 绑定类型业务码
     * @param itemNo   用法 code 或诊疗主键字符串
     * @param itemId   诊疗活动定义主键（诊疗绑定时可与 {@code itemNo} 二选一）
     * @return 非空来源项键
     */
    String resolveOrderBindSourceItemKey(String bindType, String itemNo, Long itemId);

}
