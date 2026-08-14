/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.mapper;

import com.openhis.health.application.outpatient.financial.registration.dto.ChargeItemPreviewDto;
import com.openhis.health.application.outpatient.financial.registration.dto.ClinicRegisterItemDto;
import com.openhis.health.application.outpatient.financial.registration.dto.PatientItemDto;
import com.openhis.health.application.outpatient.financial.registration.dto.RegistrationSlipDto;
import com.openhis.health.application.outpatient.financial.registration.request.CancelWithRefundRequest;
import com.openhis.health.application.outpatient.financial.registration.request.PageClinicRegistersRequest;
import com.openhis.health.application.outpatient.financial.registration.request.PayRegisterFeeRequest;
import com.openhis.health.application.outpatient.financial.registration.request.PreRegisterSettlementRequest;
import com.openhis.health.application.outpatient.financial.registration.request.SavePatientForFirstRegisterRequest;
import com.openhis.health.application.outpatient.financial.registration.response.CancelWithRefundResponse;
import com.openhis.health.application.outpatient.financial.registration.response.ListBookableSlotHintsResponse;
import com.openhis.health.application.outpatient.financial.registration.response.LoadAppointmentForRegisterResponse;
import com.openhis.health.application.outpatient.financial.registration.response.ListDepartmentsResponse;
import com.openhis.health.application.outpatient.financial.registration.response.ListPractitionersResponse;
import com.openhis.health.application.outpatient.financial.registration.response.LoadRegisterFormInitResponse;
import com.openhis.health.application.outpatient.financial.registration.response.PageClinicRegistersResponse;
import com.openhis.health.application.outpatient.financial.registration.response.PagePatientsForSelectResponse;
import com.openhis.health.application.outpatient.financial.registration.response.PayRegisterFeeResponse;
import com.openhis.health.application.outpatient.financial.registration.response.PreRegisterSettlementResponse;
import com.openhis.health.application.outpatient.financial.registration.response.PrintRegisterTicketResponse;
import com.openhis.health.application.outpatient.financial.registration.response.SavePatientForFirstRegisterResponse;
import com.openhis.health.application.outpatient.financial.registration.response.ValidateCancelableResponse;
import com.openhis.health.domain.administration.account.command.OpenEncounterAccountsCommand;
import com.openhis.health.domain.administration.chargeitem.command.BatchChargeItemIdsCommand;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.encounter.command.CancelOutpatientRegistrationCommand;
import com.openhis.health.domain.administration.encounter.command.RegisterOutpatientEncounterCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.invoice.command.CancelInvoiceCommand;
import com.openhis.health.domain.administration.invoice.command.CreateDraftInvoiceCommand;
import com.openhis.health.domain.administration.invoice.command.IssueElectronicInvoiceCommand;
import com.openhis.health.domain.administration.invoice.entity.Invoice;
import com.openhis.health.domain.administration.invoice.query.InvoiceQueryCommand;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.patient.command.RegisterPatientCommand;
import com.openhis.health.domain.administration.patient.command.UpdatePatientAddressCommand;
import com.openhis.health.domain.administration.patient.command.UpdatePatientPhoneCommand;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.queries.DuplicatePatientCheckQuery;
import com.openhis.health.domain.administration.patient.queries.model.PatientCoreQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientPageItemQueryModel;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.command.CompleteRegistrationCancelRefundCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CreatePaymentDraftFromPreSettleCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.RefreshRegistrationPaymentDraftCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.RequestOutpatientRefundCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.SettleOutpatientPaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.ClinicRegisterQueryModel;
import com.openhis.health.domain.administration.encounter.queries.ClinicRegisterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.RegistrationSlipQueryModel;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 门诊挂号应用层映射器。
 * <p>负责 Request/Response/DTO 与领域 Command/QueryModel 之间的结构转换，不承载业务判断。</p>
 */
public interface OutpatientRegistrationMapper {
    /**
     * 组装挂号初始化响应。
     *
     * @param departments 科室选项
     * @param practitioners 医生选项
     * @param contracts 合同选项
     * @param serviceTypes 号源类型选项
     * @param genderOptions 性别选项（AdministrativeGender）
     * @return 初始化响应
     */
    LoadRegisterFormInitResponse toLoadRegisterFormInitResponse(
        List<OptionDto> departments,
        List<OptionDto> practitioners,
        List<OptionDto> contracts,
        List<OptionDto> serviceTypes,
        Map<String, BigDecimal> serviceTypeFeeById,
        List<OptionDto> genderOptions
    );

    /**
     * 组装患者分页响应。
     *
     * @param page 原始分页对象
     * @param records 患者记录列表
     * @return 患者分页响应
     */
    PagePatientsForSelectResponse toPagePatientsForSelectResponse(
        Page<PatientPageItemQueryModel> page,
        List<PatientItemDto> records
    );

    /**
     * 组装首次建档响应。
     *
     * @param patient 患者实体
     * @return 建档响应
     */
    SavePatientForFirstRegisterResponse toSavePatientForFirstRegisterResponse(Patient patient);

    /**
     * 组装科室列表响应。
     *
     * @param options 科室选项列表
     * @return 科室列表响应
     */
    ListDepartmentsResponse toListDepartmentsResponse(List<OptionDto> options);

    /**
     * 组装医生列表响应。
     *
     * @param options 医生选项列表
     * @return 医生列表响应
     */
    ListPractitionersResponse toListPractitionersResponse(List<OptionDto> options);

    /**
     * 组装挂号预结算响应。
     *
     * @param encounterId    就诊主键（预结算落账后的 Encounter 标识）
     * @param paymentDraftId   支付草稿 ID
     * @param totalAmount      总金额
     * @param chargeItems      收费项预览
     * @return 预结算响应
     */
    PreRegisterSettlementResponse toPreRegisterSettlementResponse(
        Long encounterId,
        Long paymentDraftId,
        BigDecimal totalAmount,
        List<ChargeItemPreviewDto> chargeItems,
        Long appointmentId,
        Long slotId
    );

    /**
     * IF-REG-12：预约快照转挂号台响应。
     */
    LoadAppointmentForRegisterResponse toLoadAppointmentForRegisterResponse(
        com.openhis.health.infrastructure.integration.booking.ExtensionOut.OutpatientBookingQueryExtensionOut snapshot);

    /**
     * IF-REG-14：可约时段摘要映射。
     */
    ListBookableSlotHintsResponse toListBookableSlotHintsResponse(
        java.util.List<com.openhis.health.infrastructure.integration.booking.ExtensionOut.BookableSlotHintQueryExtensionOut> hints);

    /**
     * 组装挂号支付响应。
     *
     * @param payment 支付记录
     * @param invoice 发票记录
     * @return 支付响应
     */
    PayRegisterFeeResponse toPayRegisterFeeResponse(PaymentReconciliationQueryModel payment, Invoice invoice);

    /**
     * 组装门诊挂号分页响应。
     *
     * @param page 原始分页对象
     * @param records 挂号记录列表
     * @return 挂号分页响应
     */
    PageClinicRegistersResponse toPageClinicRegistersResponse(
        Page<ClinicRegisterQueryModel> page,
        List<ClinicRegisterItemDto> records
    );

    /**
     * 组装退号可行性响应。
     *
     * @param cancelable 是否可退
     * @param reasonCode 原因码
     * @param reasonText 原因说明
     * @return 退号校验响应
     */
    ValidateCancelableResponse toValidateCancelableResponse(Boolean cancelable, String reasonCode, String reasonText);

    /**
     * 组装退号退费结果响应。
     *
     * @param encounter 就诊详情
     * @param payment 退费支付记录
     * @param redInvoice 红票记录
     * @return 退号退费响应
     */
    CancelWithRefundResponse toCancelWithRefundResponse(
        EncounterDetailQueryModel encounter,
        PaymentReconciliationQueryModel payment,
        Invoice redInvoice
    );

    /**
     * 组装补打挂号单响应。
     *
     * @param slip 挂号单读模型
     * @return 补打响应
     */
    PrintRegisterTicketResponse toPrintRegisterTicketResponse(RegistrationSlipQueryModel slip);

    /**
     * 将科室实体映射为下拉选项。
     *
     * @param organizations 科室实体列表
     * @return 下拉选项列表
     */
    List<OptionDto> toOrganizationOptionList(List<Organization> organizations);

    /**
     * 将医生实体映射为下拉选项。
     *
     * @param practitioners 医生实体列表
     * @return 下拉选项列表
     */
    List<OptionDto> toPractitionerOptionList(List<Practitioner> practitioners);

    /**
     * 将合同列表映射为下拉选项。
     *
     * @param contracts 合同列表
     * @return 下拉选项列表
     */
    List<OptionDto> toContractOptionList(List<ContractListItemQueryModel> contracts);

    /**
     * 将诊疗服务映射为下拉选项。
     *
     * @param services 诊疗服务列表
     * @return 下拉选项列表
     */
    List<OptionDto> toHealthcareServiceOptionList(List<com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService> services);

    /**
     * 将患者分页结果映射为 DTO 列表。
     *
     * @param page 患者分页
     * @return 患者 DTO 列表
     */
    List<PatientItemDto> toPatientItemDtoList(Page<PatientPageItemQueryModel> page);

    /**
     * 将挂号分页结果映射为 DTO 列表（含性别编码/展示、就诊状态码与状态文案、挂号费回填）。
     *
     * @param page                         挂号分页
     * @param registrationFeeTotalByAnchor 挂号费锚点键 → 收费明细总价（{@code ChargeItem.totalPrice} 汇总）
     * @return 挂号 DTO 列表
     */
    List<ClinicRegisterItemDto> toClinicRegisterItemDtoList(
        Page<ClinicRegisterQueryModel> page,
        Map<String, BigDecimal> registrationFeeTotalByAnchor,
        Map<Long, Long> paymentDraftIdByEncounterId,
        Map<Long, Long> paymentIdByEncounterId
    );

    /**
     * 根据收费定义构建预结算收费项预览。
     *
     * @param saleLine 收费定义
     * @return 收费项预览列表
     */
    List<ChargeItemPreviewDto> toChargeItemPreviewDtoListBySaleLine(ChargeItemDefinition saleLine);

    /**
     * 将挂号单读模型映射为挂号单 DTO。
     *
     * @param model 挂号单读模型
     * @return 挂号单 DTO
     */
    RegistrationSlipDto toRegistrationSlipDto(RegistrationSlipQueryModel model);

    /**
     * 将首次建档请求映射为重号校验查询命令。
     *
     * @param request 首次建档请求
     * @return 重号校验查询命令
     */
    DuplicatePatientCheckQuery toDuplicatePatientCheckQuery(SavePatientForFirstRegisterRequest request);

    /**
     * 将首次建档请求映射为患者注册命令。
     *
     * @param request 首次建档请求
     * @return 患者注册命令
     */
    RegisterPatientCommand toRegisterPatientCommand(SavePatientForFirstRegisterRequest request);

    /**
     * 将预结算请求中的患者建档字段映射为患者注册命令。
     *
     * @param request 预结算请求
     * @return 患者注册命令
     */
    RegisterPatientCommand toRegisterPatientCommand(PreRegisterSettlementRequest request);

    /**
     * 将复用患者主键与表单联系电话映射为更新手机号命令。
     *
     * @param patientId 既有患者主键
     * @param phone 挂号表单联系电话（须非空，领域层再校验格式）
     * @return 更新手机号命令
     */
    UpdatePatientPhoneCommand toUpdatePatientPhoneCommand(Long patientId, String phone);

    /**
     * 将复用患者主键与读卡/表单地址全文映射为更新地址命令。
     *
     * @param patientId 既有患者主键
     * @param address 地址全文（须非空）
     * @return 更新地址命令
     */
    UpdatePatientAddressCommand toUpdatePatientAddressCommand(Long patientId, String address);

    /**
     * 将预结算请求映射为收费项创建命令。
     *
     * @param request 预结算请求
     * @param saleLine 收费定义
     * @param encounterId 已开立就诊主键，须非空且非 0，写入收费明细以便挂号读模型投影累加金额
     * @param occurredAt 费用发生时刻（与就诊登记时刻对齐）
     * @param accountId 挂号结算账户主键（DM 必填 account_id）
     * @param orgId 机构主键（DM 必填 org_id）
     * @return 收费项创建命令
     */
    CreateChargeItemCommand toCreateChargeItemCommand(
        PreRegisterSettlementRequest request,
        ChargeItemDefinition saleLine,
        Long encounterId,
        LocalDateTime occurredAt,
        Long accountId,
        Long orgId
    );

    /**
     * 将单收费项 ID 封装为批量命令。
     *
     * @param chargeItemId 收费项 ID
     * @return 批量收费项命令
     */
    BatchChargeItemIdsCommand toSingleChargeItemBatchCommand(Long chargeItemId);

    /**
     * 将预结算请求映射为门诊挂号命令（含读模型投影所需的展示快照）。
     *
     * @param request 预结算请求
     * @param organization 挂号科室（名称快照来源）
     * @param healthcareService 号源类型（名称快照来源）
     * @param patientCore 患者主档快照；可为 {@code null}，此时由请求侧字段兜底
     * @param operatorId 挂号员主键
     * @param operatorName 挂号员展示名
     * @param occurredAt 挂号发生时刻（写入 startTime）
     * @param registrationPractitionerDisplayName 挂号所选医师展示名；未选医师时为 {@code null}
     * @return 门诊挂号命令
     */
    RegisterOutpatientEncounterCommand toRegisterOutpatientEncounterCommand(
        PreRegisterSettlementRequest request,
        Organization organization,
        HealthcareService healthcareService,
        PatientCoreQueryModel patientCore,
        Long operatorId,
        String operatorName,
        LocalDateTime occurredAt,
        String registrationPractitionerDisplayName
    );

    /**
     * 构建刷新支付草稿命令。
     *
     * @param paymentId 支付草稿 ID
     * @param chargeItemId 收费项 ID
     * @param chargeItemAmount 已落库收费明细金额（与 {@code ChargeItem.totalPrice} 一致）
     * @param details 支付明细
     * @return 刷新支付草稿命令
     */
    RefreshRegistrationPaymentDraftCommand toRefreshRegistrationPaymentDraftCommand(
        Long paymentId,
        Long chargeItemId,
        java.math.BigDecimal chargeItemAmount,
        List<PaymentDetailItemCommand> details
    );

    /**
     * 构建创建支付草稿命令。
     *
     * @param request 预结算请求
     * @param encounterId 就诊 ID
     * @param chargeItemId 收费项 ID
     * @param chargeItemAmount 已落库的挂号收费明细金额（与 {@code ChargeItem.totalPrice} 一致，不得再取自定价定义）
     * @param details 支付明细
     * @return 创建支付草稿命令
     */
    CreatePaymentDraftFromPreSettleCommand toCreatePaymentDraftFromPreSettleCommand(
        PreRegisterSettlementRequest request,
        Long encounterId,
        Long chargeItemId,
        java.math.BigDecimal chargeItemAmount,
        List<PaymentDetailItemCommand> details,
        String contractBusNo
    );

    /**
     * 构建开立就诊账户命令。
     *
     * @param paymentDraft 支付草稿
     * @return 开立账户命令
     */
    OpenEncounterAccountsCommand toOpenEncounterAccountsCommand(PaymentReconciliationQueryModel paymentDraft);

    /**
     * 预结算阶段开立挂号（REG）账户命令，与缴费前开户语义一致，用于幂等探测后补开。
     */
    OpenEncounterAccountsCommand toOpenEncounterAccountsCommandForPreRegister(
        PreRegisterSettlementRequest request,
        Long encounterId,
        String contractBusNo
    );

    /**
     * 将挂号支付请求映射为支付结算命令。
     *
     * @param request 支付请求
     * @return 支付结算命令
     */
    SettleOutpatientPaymentCommand toSettleOutpatientPaymentCommand(PayRegisterFeeRequest request);

    /**
     * 构建支付后开票草稿命令。
     *
     * @param paymentDraft      支付草稿
     * @param paymentId         支付记录 ID
     * @param invoiceFaceAmount 票面金额（收费项合计结算刻度；含医保已付）
     * @return 开票草稿命令
     */
    CreateDraftInvoiceCommand toCreateDraftInvoiceCommand(
        PaymentReconciliationQueryModel paymentDraft,
        Long paymentId,
        BigDecimal invoiceFaceAmount);

    /**
     * 构建电子发票开具命令。
     *
     * @param invoiceId 发票 ID
     * @return 电子发票开具命令
     */
    IssueElectronicInvoiceCommand toIssueElectronicInvoiceCommand(Long invoiceId);

    /**
     * 构建发票查询命令。
     *
     * @param invoiceId 发票 ID
     * @return 发票查询命令
     */
    InvoiceQueryCommand toInvoiceQueryCommand(Long invoiceId);

    /**
     * 将挂号分页请求映射为读模型分页查询命令。
     *
     * @param request 分页请求
     * @return 读模型分页查询命令
     */
    ClinicRegisterPageQuery toClinicRegisterPageQuery(PageClinicRegistersRequest request);

    /**
     * 将退号退费请求映射为退号命令。
     *
     * @param request 退号退费请求
     * @return 退号命令
     */
    CancelOutpatientRegistrationCommand toCancelOutpatientRegistrationCommand(CancelWithRefundRequest request);

    /**
     * 构建门诊退号联动退费申请命令（原路返回：须带原单合同号与渠道明细）。
     *
     * @param request         退号退费请求
     * @param payment         原支付记录
     * @param paymentDetails  原单渠道明细拷贝（含统筹/个账/自付）；禁止传空导致日结支付方式缺失
     * @return 退费申请命令
     */
    RequestOutpatientRefundCommand toRequestOutpatientRefundCommand(
        CancelWithRefundRequest request,
        PaymentReconciliationQueryModel payment,
        List<PaymentDetailItemCommand> paymentDetails);

    /**
     * 原挂号支付明细 → 退号逆向单渠道明细（整单原路返回，对齐 IF-REF-05 / BR-REF-011 口径）。
     *
     * @param originalPaymentDetails 原支付 {@code fin_payment_rec_detail} 读模型
     * @return 领域明细命令；入参空时返回空列表（由调用方门禁）
     */
    List<PaymentDetailItemCommand> toRegistrationCancelRefundPaymentDetails(
        List<PaymentRecDetailQueryModel> originalPaymentDetails);

    /**
     * 构建退号联动退费完成命令（原单 + 逆向单一并收口）。
     *
     * @param originalPaymentId 原挂号支付主单主键
     * @param reversePaymentId  退号逆向退款单主键
     * @return 退号退费完成命令
     */
    CompleteRegistrationCancelRefundCommand toCompleteRegistrationCancelRefundCommand(
        Long originalPaymentId,
        Long reversePaymentId
    );

    /**
     * 根据就诊 ID 组装批量收费项命令。
     *
     * @param encounterId 就诊 ID
     * @param chargeItemQueryService 收费项查询服务
     * @return 批量收费项命令
     */
    BatchChargeItemIdsCommand toBatchChargeItemIdsCommandFromEncounter(Long encounterId, ChargeItemQueryService chargeItemQueryService);

    /**
     * 构建冲红发票命令。
     *
     * @param invoiceId 发票 ID
     * @param reason 冲红原因
     * @return 冲红发票命令
     */
    CancelInvoiceCommand toCancelInvoiceCommand(Long invoiceId, String reason);
}
