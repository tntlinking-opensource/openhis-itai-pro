/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.registration.mapper;

import com.zxplat.application.inpatient.financial.registration.dto.PaymentDetailDto;
import com.zxplat.application.inpatient.financial.registration.dto.RegistrationEncounterDto;
import com.zxplat.application.inpatient.financial.registration.dto.RegistrationPatientDto;
import com.zxplat.application.inpatient.financial.registration.dto.RegistrationQueueItemDto;
import com.zxplat.application.inpatient.financial.registration.request.CancelRegistrationRequest;
import com.zxplat.application.inpatient.financial.registration.request.SubmitRegistrationRequest;
import com.zxplat.application.inpatient.financial.registration.response.CancelRegistrationResponse;
import com.zxplat.application.inpatient.financial.registration.response.ListWardsByDepartmentResponse;
import com.zxplat.application.inpatient.financial.registration.response.LoadInpatientRegistrationPageInitResponse;
import com.zxplat.application.inpatient.financial.registration.response.LoadRegistrationContextResponse;
import com.zxplat.application.inpatient.financial.registration.response.LoadWardBedCapacityResponse;
import com.zxplat.application.inpatient.financial.registration.response.PageRegistrationQueueResponse;
import com.zxplat.application.inpatient.financial.registration.response.SubmitRegistrationResponse;
import com.zxplat.application.inpatient.financial.registration.response.SwitchInpatientToInsuranceResponse;
import com.zxplat.application.inpatient.financial.registration.response.SwitchInpatientToSelfPayResponse;
import com.zxplat.domain.administration.encounter.command.CancelInpatientAdmissionCommand;
import com.zxplat.domain.administration.encounter.command.RegisterInpatientEncounterCommand;
import com.zxplat.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.zxplat.domain.administration.patient.command.ModifyPatientProfileCommand;
import com.zxplat.domain.administration.patient.command.RegisterPatientCommand;
import com.zxplat.domain.administration.patient.queries.model.PatientCoreQueryModel;
import com.zxplat.domain.financial.paymentreconciliation.command.CollectAdvanceDepositCommand;
import com.zxplat.domain.financial.paymentreconciliation.command.SettleInpatientPaymentCommand;
import com.zxplat.application.shared.inpatient.InpatientInfoQueueDisplayAssembler;
import com.zxplat.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.zxplat.framework.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 住院登记应用层映射：读模型/请求与领域命令、响应 DTO 之间的字段搬运。
 *
 * <p>禁止承载业务流程判断与不变式校验。</p>
 */
public interface InpatientRegistrationMapper {

    /**
     * 装配住院登记页初始化响应。
     *
     * @param paymentChannelOptions 用户可选支付渠道下拉
     * @return 初始化响应
     */
    LoadInpatientRegistrationPageInitResponse toLoadInpatientRegistrationPageInitResponse(
        List<OptionDto> paymentChannelOptions);

    /**
     * 读模型列表 → 待办行 DTO 列表。
     *
     * @param rows                          住院上下文行；调用方保证非 null
     * @param ambEncounterIdByEncounterId 住院就诊 ID → 源门诊就诊 ID；可空
     * @param inWayLabelByCode            入院方式 code → 展示文案；可空
     * @return 列表行 DTO
     */
    List<RegistrationQueueItemDto> toRegistrationQueueItemDtoList(
        List<InpatientEncounterContextQueryModel> rows,
        Map<Long, Long> ambEncounterIdByEncounterId,
        Map<Long, InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay> displayOverlayByEncounterId,
        Map<String, String> inWayLabelByCode);

    /**
     * 列表行 + Spring 分页壳 → 分页响应（经 PagedResultBuilder）。
     *
     * @param rows 已映射列表行
     * @param page 住院上下文分页结果
     * @return 分页响应
     */
    PageRegistrationQueueResponse toPageRegistrationQueueResponse(
        List<RegistrationQueueItemDto> rows,
        Page<InpatientEncounterContextQueryModel> page);

    /**
     * 登记上下文装配为响应。
     *
     * @param patient         患者 DTO，可 null
     * @param encounter       就诊 DTO，可 null
     * @param contractOptions     费用性质下拉
     * @param organizationOptions 入院科室下拉
     * @param inWayCodeOptions    入院方式下拉
     * @param admitSourceCodeOptions 入院类型下拉
     * @param admCondCodeOptions  患者病情下拉
     * @param readOnly            是否只读查看
     * @param switchToInsuranceEnabled 是否允许自费转医保
     * @param switchToSelfPayEnabled   是否允许医保转自费
     * @return 上下文响应
     */
    LoadRegistrationContextResponse toLoadRegistrationContextResponse(
        RegistrationPatientDto patient,
        RegistrationEncounterDto encounter,
        List<OptionDto> contractOptions,
        List<OptionDto> organizationOptions,
        List<OptionDto> inWayCodeOptions,
        List<OptionDto> admitSourceCodeOptions,
        List<OptionDto> admCondCodeOptions,
        boolean readOnly,
        boolean switchToInsuranceEnabled,
        boolean switchToSelfPayEnabled);

    /**
     * 患者核心读模型 → 登记患者 DTO（含婚姻/职业/现住址）。
     *
     * @param core 患者核心读模型；null 或无 id 时返回 null
     * @return 登记弹窗患者主档
     */
    RegistrationPatientDto toRegistrationPatientDto(PatientCoreQueryModel core);

    /**
     * 住院就诊上下文 → 登记就诊 DTO。
     *
     * @param row           上下文行；调用方保证非 null
     * @param encounterHead 就诊主档（补 busNo/入院来源/优先级/ambEncounterId）；可 null
     * @param inWayLabelByCode 入院方式 code → 展示文案；可 null
     * @param admitSourceLabelByCode 入院类型 code → 展示文案；可 null
     * @return 就诊 DTO
     */
    RegistrationEncounterDto toRegistrationEncounterDto(
        InpatientEncounterContextQueryModel row,
        EncounterDetailQueryModel encounterHead,
        Map<String, String> inWayLabelByCode,
        Map<String, String> admitSourceLabelByCode);

    /**
     * 费用性质列表 → 下拉选项（与门诊挂号口径一致）。
     *
     * @param contracts 合同列表项
     * @return 下拉选项
     */
    List<OptionDto> toContractOptionList(List<ContractListItemQueryModel> contracts);

    /**
     * 提交请求 → 新建患者命令。
     *
     * @param request 登记请求
     * @return 注册命令
     */
    RegisterPatientCommand toRegisterPatientCommand(SubmitRegistrationRequest request);

    /**
     * 提交请求 → 修改患者档案命令（同身份证更新主档）。
     *
     * <p>以既有主档为底，叠写登记表单可编辑字段（姓名/性别/证件/电话/生日/婚姻/职业/现住址），
     * 避免未在登记页展示的字段（血型、联系人等）被空值覆盖。</p>
     *
     * @param request   登记请求
     * @param patientId 既有患者 ID
     * @param existing  既有患者核心读模型；可为 null（仅写请求字段）
     * @return 修改命令
     */
    ModifyPatientProfileCommand toModifyPatientProfileCommand(
        SubmitRegistrationRequest request,
        Long patientId,
        PatientCoreQueryModel existing);

    /**
     * 提交请求 → 住院登记命令。
     *
     * @param request      登记请求
     * @param encounterId  住院就诊 ID
     * @param registrarId  登记员 ID
     * @param contractNo   费用性质 bus_no
     * @return 登记命令
     */
    RegisterInpatientEncounterCommand toRegisterInpatientEncounterCommand(
        SubmitRegistrationRequest request,
        Long encounterId,
        Long registrarId,
        String contractNo);

    /**
     * 登记请求 → 预交金收取命令（头 {@code paymentEnum=PAY}，渠道在明细）。
     *
     * @param request     登记请求
     * @param patientId   患者 ID
     * @param encounterId 就诊 ID
     * @param accountId   登记账户 ID
     * @return 收取命令
     */
    CollectAdvanceDepositCommand toCollectAdvanceDepositCommand(
        SubmitRegistrationRequest request,
        Long patientId,
        Long encounterId,
        Long accountId);

    /**
     * 单渠道预交金收取命令（一渠道一笔流水）。
     *
     * @param patientId   患者 ID
     * @param encounterId 就诊 ID
     * @param accountId   登记账户 ID
     * @param amount      本渠道金额
     * @param detail      单条支付渠道明细
     * @return 收取命令；明细空时 null
     */
    CollectAdvanceDepositCommand toCollectAdvanceDepositCommandForChannel(
        Long patientId,
        Long encounterId,
        Long accountId,
        BigDecimal amount,
        PaymentDetailDto detail);

    /**
     * 住院支付结算命令。
     *
     * @param paymentId 支付草稿 ID
     * @return 结算命令
     */
    SettleInpatientPaymentCommand toSettleInpatientPaymentCommand(Long paymentId);

    /**
     * 登记结果 → 提交响应。
     *
     * @param encounterId   就诊 ID
     * @param busNo         住院号
     * @param paymentId     支付单 ID，可 null
     * @param accountOpened 是否新开账户
     * @param ybMdtrtId     医保就诊 ID；无医保时为 null
     * @return 提交响应
     */
    SubmitRegistrationResponse toSubmitRegistrationResponse(
        Long encounterId,
        String busNo,
        Long paymentId,
        boolean accountOpened,
        String ybMdtrtId);

    /**
     * 取消请求 → 取消住院登记命令。
     *
     * @param request               取消请求
     * @param accountBalanceSnapshot 账户余额快照（元取整，无户为 0）
     * @return 取消命令
     */
    CancelInpatientAdmissionCommand toCancelInpatientAdmissionCommand(
        CancelRegistrationRequest request,
        Long accountBalanceSnapshot);

    /**
     * 取消后 → 取消响应。
     *
     * @param encounterId 就诊 ID
     * @return 取消响应
     */
    CancelRegistrationResponse toCancelRegistrationResponse(Long encounterId);

    /**
     * 病区位置列表 → 病区下拉响应。
     *
     * @param wards 病区位置实体列表
     * @return 响应
     */
    ListWardsByDepartmentResponse toListWardsByDepartmentResponse(List<Location> wards);

    /**
     * 床位统计 → 余量响应。
     *
     * @param wardId  病区 ID
     * @param totalBedCount   总床
     * @param vacantBedCount  空床
     * @return 响应
     */
    LoadWardBedCapacityResponse toLoadWardBedCapacityResponse(
        Long wardId,
        Long totalBedCount,
        Long vacantBedCount);

    /**
     * 自费转医保成功响应。
     *
     * @param ybMdtrtId               医保就诊 ID
     * @param switchedChargeItemCount 切换行数
     * @param insuranceAccountId      医保账户 ID
     * @return 响应
     */
    SwitchInpatientToInsuranceResponse toSwitchInpatientToInsuranceResponse(
        String ybMdtrtId,
        int switchedChargeItemCount,
        Long insuranceAccountId);

    /**
     * 医保转自费成功响应。
     *
     * @param switchedChargeItemCount 切换到个人现金户的行数
     * @param selfPayAccountId        个人现金账户主键
     * @return 响应
     */
    SwitchInpatientToSelfPayResponse toSwitchInpatientToSelfPayResponse(
        int switchedChargeItemCount,
        Long selfPayAccountId);
}
