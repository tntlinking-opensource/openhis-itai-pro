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

package com.openhis.health.application.outpatient.financial.registration.mapper;

import static com.openhis.whale.core.application.util.StringUtils.firstNonBlank;

import com.openhis.health.module.common.pagination.PageQueryBinder;
import com.openhis.health.module.common.pagination.PagedResultBuilder;
import com.openhis.health.application.outpatient.financial.registration.dto.*;
import com.openhis.health.application.outpatient.financial.registration.request.*;
import com.openhis.health.application.outpatient.financial.registration.response.*;
import com.openhis.health.domain.share.enums.AccountTypeCode;
import com.openhis.health.domain.share.constants.ChargeItemDmConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.openhis.health.domain.share.utils.AgeCalculatorUtil;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.openhis.health.domain.administration.account.command.OpenEncounterAccountsCommand;
import com.openhis.health.domain.administration.chargeitem.command.BatchChargeItemIdsCommand;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
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
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.queries.DuplicatePatientCheckQuery;
import com.openhis.health.domain.administration.patient.queries.model.PatientCoreQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientPageItemQueryModel;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.command.*;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.ClinicRegisterQueryModel;
import com.openhis.health.domain.administration.encounter.queries.ClinicRegisterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.RegistrationSlipQueryModel;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.enums.PaymentType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.dto.OptionDto;

/**
 * {@link OutpatientRegistrationMapper} 实现：门诊挂号应用层字段映射器。
 *
 * <p><b>职责</b>：将挂号相关 HTTP 请求（建档、预结算、支付、退号等）装配为患者/就诊/收费/支付领域命令，
 * 将领域查询模型/实体装配为 HTTP 响应；负责挂号费计算、性别年龄展示、收费明细聚合等挂号专属字段映射。</p>
 *
 * <p><b>对应接口规格</b>：IF-OPR-01 ～ IF-OPR-09（挂号初始化、患者检索、建档、预结算、支付、退号等）。</p>
 *
 * <p><b>边界假设</b>：
 * <ul>
 *   <li>金额字段统一使用 {@link BigDecimal}，挂号费计算以定价行 {@code priceValue} 为准，数量默认为 1。</li>
 *   <li>性别枚举码→展示文本转换优先使用 {@link EnumUtil}，与前端码表约定一致。</li>
 *   <li>年龄计算以自然周岁为准，出生日期为空时年龄字段为 null。</li>
 *   <li>搜索码（拼音/五笔）生成使用 {@link SearchCodeUtil}，主档缺失时由请求侧兜底。</li>
 * </ul></p>
 */
@Component
public class OutpatientRegistrationMapperImpl implements OutpatientRegistrationMapper {

    /**
     * 组装挂号初始化响应。
     *
     * @param departments 科室选项
     * @param practitioners 医生选项
     * @param contracts 费用性质选项
     * @param serviceTypes 号源类型选项
     * @param genderOptions 性别选项
     * @return 挂号初始化响应
     */
    @Override
    public LoadRegisterFormInitResponse toLoadRegisterFormInitResponse(List<OptionDto> departments,
                                                                       List<OptionDto> practitioners, List<OptionDto> contracts, List<OptionDto> serviceTypes,
                                                                       Map<String, BigDecimal> serviceTypeFeeById,
                                                                       List<OptionDto> genderOptions) {
        return new LoadRegisterFormInitResponse().setDepartmentOptions(departments)
            .setPractitionerOptions(practitioners).setContractOptions(contracts).setServiceTypeOptions(serviceTypes)
            .setServiceTypeFeeById(serviceTypeFeeById)
            .setGenderOptions(genderOptions);
    }

    /**
     * 组装患者分页检索响应（IF-OPR-02；1-based 页码，B.10.11）。
     *
     * <p>分页字段遵循 PagedResult 规范（B.10.11）：content 承载列表数据，totalCount 承载总条数，
     * pageNo/pageSize 为 1-based 自然数；Spring Data 页码换算由 {@link PagedResultBuilder} 完成。</p>
     *
     * @param page 分页对象
     * @param records 患者记录
     * @return 患者分页响应
     */
    @Override
    public PagePatientsForSelectResponse toPagePatientsForSelectResponse(Page<PatientPageItemQueryModel> page,
                                                                         List<PatientItemDto> records) {
        return PagedResultBuilder.build(new PagePatientsForSelectResponse(), records, page);
    }

    /**
     * 组装首次建档返回结果。
     *
     * @param patient 患者实体
     * @return 首次建档响应
     */
    @Override
    public SavePatientForFirstRegisterResponse toSavePatientForFirstRegisterResponse(Patient patient) {
        return new SavePatientForFirstRegisterResponse().setPatientId(patient.getId())
            .setPatientBusNo(patient.getBusNo());
    }

    /**
     * 组装科室下拉响应。
     *
     * @param options 科室选项
     * @return 科室响应
     */
    @Override
    public ListDepartmentsResponse toListDepartmentsResponse(List<OptionDto> options) {
        return new ListDepartmentsResponse().setDepartmentOptions(options);
    }

    /**
     * 组装医生下拉响应。
     *
     * @param options 医生选项
     * @return 医生响应
     */
    @Override
    public ListPractitionersResponse toListPractitionersResponse(List<OptionDto> options) {
        return new ListPractitionersResponse().setPractitionerOptions(options);
    }

    /**
     * 组装挂号预结算响应。
     *
     * @param encounterId    就诊主键
     * @param paymentDraftId 支付草稿 ID
     * @param totalAmount    总金额
     * @param chargeItems    收费项预览
     * @return 预结算响应
     */
    @Override
    public PreRegisterSettlementResponse toPreRegisterSettlementResponse(Long encounterId, Long paymentDraftId, BigDecimal totalAmount,
        List<ChargeItemPreviewDto> chargeItems, Long appointmentId, Long slotId) {
        return new PreRegisterSettlementResponse().setEncounterId(encounterId).setPaymentDraftId(paymentDraftId).setTotalAmount(totalAmount)
            .setChargeItems(chargeItems).setAppointmentId(appointmentId).setSlotId(slotId);
    }

    /**
     * 预约快照 → 挂号台加载预约响应（IF-REG-12）。
     *
     * @param snapshot booking 模块预约快照；为 null 时返回 null
     * @return 加载预约响应
     */
    @Override
    public LoadAppointmentForRegisterResponse toLoadAppointmentForRegisterResponse(
        com.openhis.health.integration.booking.result.OutpatientBookingSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new LoadAppointmentForRegisterResponse()
            .setAppointmentId(snapshot.getAppointmentId())
            .setAppointmentBusNo(snapshot.getAppointmentBusNo())
            .setSlotId(snapshot.getSlotId())
            .setOrganizationId(snapshot.getOrganizationId())
            .setPractitionerId(snapshot.getPractitionerId())
            .setHealthcareServiceId(snapshot.getHealthcareServiceId())
            .setSlotStart(snapshot.getSlotStart())
            .setSlotEnd(snapshot.getSlotEnd())
            .setOrganizationName(snapshot.getOrganizationName())
            .setPractitionerName(snapshot.getPractitionerName())
            .setServiceTypeName(snapshot.getServiceTypeName())
            .setStatusEnum(snapshot.getStatusEnum());
    }

    /**
     * 可约时段摘要列表 → 响应 DTO（IF-REG-14）。
     *
     * @param hints booking 模块时段摘要；可为 null
     * @return 时段提示响应
     */
    @Override
    public ListBookableSlotHintsResponse toListBookableSlotHintsResponse(
        java.util.List<com.openhis.health.integration.booking.result.BookableSlotHintSnapshot> hints) {
        ListBookableSlotHintsResponse response = new ListBookableSlotHintsResponse();
        java.util.List<BookableSlotHintDto> rows = new java.util.ArrayList<>();
        if (hints != null) {
            for (com.openhis.health.integration.booking.result.BookableSlotHintSnapshot hint : hints) {
                if (hint == null) {
                    continue;
                }
                BookableSlotHintDto dto = new BookableSlotHintDto();
                dto.setSlotId(hint.getSlotId());
                dto.setSlotStart(hint.getSlotStart());
                dto.setSlotEnd(hint.getSlotEnd());
                dto.setRemainingCount(hint.getRemainingCount());
                rows.add(dto);
            }
        }
        response.setHints(rows);
        return response;
    }

    /**
     * 组装挂号支付响应。
     *
     * @param payment 支付记录
     * @param invoice 发票记录
     * @return 支付响应
     */
    @Override
    public PayRegisterFeeResponse toPayRegisterFeeResponse(PaymentReconciliationQueryModel payment, Invoice invoice) {
        Long invoiceId = null;
        if (invoice != null) {
            invoiceId = invoice.getId();
        }

        String status = null;
        if (payment.getStatusEnum() != null) {
            status = payment.getStatusEnum().getCode();
        }

        String invoiceStatus = null;
        if (invoice != null) {
            invoiceStatus = invoice.getStatusEnum();
        }

        return new PayRegisterFeeResponse()
                .setPaymentId(payment.getId())
                .setEncounterId(payment.getEncounterId())
                .setInvoiceId(invoiceId)
                .setStatus(status)
                .setInvoiceStatus(invoiceStatus);
    }

    /**
     * 组装挂号分页响应（IF-OPR-06；1-based 页码，B.10.11）。
     *
     * <p>分页字段遵循 PagedResult 规范（B.10.11）：content 承载列表数据，totalCount 承载总条数，
     * pageNo/pageSize 为 1-based 自然数；Spring Data 页码换算由 {@link PagedResultBuilder} 完成。</p>
     *
     * @param page 分页对象
     * @param records 挂号记录
     * @return 挂号分页响应
     */
    @Override
    public PageClinicRegistersResponse toPageClinicRegistersResponse(Page<ClinicRegisterQueryModel> page,
        List<ClinicRegisterItemDto> records) {
        return PagedResultBuilder.build(new PageClinicRegistersResponse(), records, page);
    }

    /**
     * 组装退号可行性响应。
     *
     * @param cancelable 是否可退
     * @param reasonCode 原因码
     * @param reasonText 原因说明
     * @return 退号校验响应
     */
    @Override
    public ValidateCancelableResponse toValidateCancelableResponse(Boolean cancelable, String reasonCode,
        String reasonText) {
        return new ValidateCancelableResponse().setCancelable(cancelable).setReasonCode(reasonCode)
            .setReasonText(reasonText);
    }

    /**
     * 组装退号退费响应。
     *
     * @param encounter 就诊详情
     * @param payment 退费支付记录
     * @param redInvoice 红票记录
     * @return 退号退费响应
     */
    @Override
    public CancelWithRefundResponse toCancelWithRefundResponse(EncounterDetailQueryModel encounter,
        PaymentReconciliationQueryModel payment, Invoice redInvoice) {
        String refundStatus = null;
        if (payment != null && payment.getStatusEnum() != null) {
            refundStatus = payment.getStatusEnum().getCode();
        }

        Long redInvoiceId = null;
        if (redInvoice != null) {
            redInvoiceId = redInvoice.getId();
        }

        return new CancelWithRefundResponse().setEncounterStatus(encounter.getStatusEnum())
            .setRefundStatus(refundStatus).setRedInvoiceId(redInvoiceId);
    }

    /**
     * 组装补打挂号单响应。
     *
     * @param slip 挂号单读模型
     * @return 补打响应
     */
    @Override
    public PrintRegisterTicketResponse toPrintRegisterTicketResponse(RegistrationSlipQueryModel slip) {
        Integer printCount = 0;
        if (slip != null && slip.getPrintCount() != null) {
            printCount = slip.getPrintCount();
        }
        return new PrintRegisterTicketResponse().setSlip(toRegistrationSlipDto(slip)).setPrintCount(printCount);
    }

    /**
     * 将科室实体转换为下拉选项。
     *
     * @param organizations 科室实体列表
     * @return 下拉选项列表
     */
    @Override
    public List<OptionDto> toOrganizationOptionList(List<Organization> organizations) {
        List<OptionDto> list = new ArrayList<>();
        for (Organization organization : organizations) {
            list.add(new OptionDto(organization.getId(), String.valueOf(organization.getId()), organization.getName()));
        }
        return list;
    }

    /**
     * 将医生实体转换为下拉选项。
     *
     * @param practitioners 医生实体列表
     * @return 下拉选项列表
     */
    @Override
    public List<OptionDto> toPractitionerOptionList(List<Practitioner> practitioners) {
        List<OptionDto> list = new ArrayList<>();
        for (Practitioner practitioner : practitioners) {
            list.add(new OptionDto(practitioner.getId(), String.valueOf(practitioner.getId()), practitioner.getName()));
        }
        return list;
    }

    /**
     * 将费用性质列表转换为下拉选项。
     *
     * @param contracts 合同列表
     * @return 下拉选项列表
     */
    @Override
    public List<OptionDto> toContractOptionList(List<ContractListItemQueryModel> contracts) {
        List<OptionDto> list = new ArrayList<>();
        for (ContractListItemQueryModel contract : contracts) {
            list.add(new OptionDto(contract.getId(), String.valueOf(contract.getId()), contract.getName()));
        }
        return list;
    }

    /**
     * 将诊疗服务列表转换为下拉选项。
     *
     * @param services 诊疗服务列表
     * @return 下拉选项列表
     */
    @Override
    public List<OptionDto> toHealthcareServiceOptionList(List<HealthcareService> services) {
        List<OptionDto> list = new ArrayList<>();
        for (HealthcareService service : services) {
            list.add(new OptionDto(service.getId(), String.valueOf(service.getId()), service.getName()));
        }
        return list;
    }

    /**
     * 将患者分页内容转换为患者列表 DTO。
     *
     * @param page 患者分页对象
     * @return 患者列表 DTO
     */
    @Override
    public List<PatientItemDto> toPatientItemDtoList(Page<PatientPageItemQueryModel> page) {
        List<PatientItemDto> list = new ArrayList<>();
        for (PatientPageItemQueryModel patient : page.getContent()) {
            // 生日与周岁：主档已有出生日期时回填，年龄按自然周岁推算（与患者分页列表提示口径一致）。
            LocalDate birthDate = patient.getBirthDate();
            Integer ageYears = AgeCalculatorUtil.calculateAge(birthDate);
            list.add(new PatientItemDto().setPatientId(patient.getId()).setPatientBusNo(patient.getBusNo())
                .setName(patient.getName()).setGenderCode(patient.getGenderEnum())
                .setGenderText(EnumUtil.fromCode(AdministrativeGender.class, patient.getGenderEnum()).getDisplay())
                .setBirthDate(birthDate).setAge(ageYears).setIdCardNoMasked(patient.getIdCard())
                .setPhoneMasked(patient.getPhone()));
        }
        return list;
    }

    /**
     * 将挂号分页内容转换为挂号列表 DTO。
     *
     * @param page                         挂号分页对象
     * @param registrationFeeTotalByAnchor 挂号费锚点键 → 收费明细总价
     * @param paymentDraftIdByEncounterId  就诊主键 → 支付草稿主键（仅待结算挂号行有值）
     * @param paymentIdByEncounterId       就诊主键 → 已成功支付流水主键（供补打小票）
     * @return 挂号列表 DTO
     */
    @Override
    public List<ClinicRegisterItemDto> toClinicRegisterItemDtoList(Page<ClinicRegisterQueryModel> page,
        Map<String, BigDecimal> registrationFeeTotalByAnchor,
        Map<Long, Long> paymentDraftIdByEncounterId,
        Map<Long, Long> paymentIdByEncounterId) {
        return page.getContent().stream().map(item -> {
            // 列表金额以账单事实（挂号费收费明细）为准；读模型未投影挂号费，不能用账户余额冒充。
            BigDecimal rowAmount = null;
            String feeKey = null;
            Long eid = item.getEncounterId();
            Long stid = item.getServiceTypeId();
            if (eid != null && stid != null) {
                // 复合键与 OutpatientRegistrationAppService#buildRegistrationFeeTotalsForClinicRegisterPage 汇总侧一致。
                String sep = "\u0001";
                feeKey = eid + sep + TableNameConstants.ADM_HEALTHCARE_SERVICE + sep + stid;
            }
            if (feeKey != null && registrationFeeTotalByAnchor != null) {
                rowAmount = registrationFeeTotalByAnchor.get(feeKey);
            }
            // 性别展示：读模型有文案则用；否则用 EnumUtil.fromCode / fromSortOrder 解析 AdministrativeGender。
            String genderRowText = item.getPatientGenderText();
            if (genderRowText == null || genderRowText.isBlank()) {
                genderRowText = null;
                String gc = item.getPatientGenderCode();
                if (gc != null && !gc.isBlank()) {
                    String t = gc.trim();
                    AdministrativeGender ag = EnumUtil.fromCode(AdministrativeGender.class, t);
                    if (ag == null) {
                        try {
                            ag = EnumUtil.fromSortOrder(AdministrativeGender.class, Long.parseLong(t));
                        } catch (NumberFormatException ex) {
                            ag = null;
                        }
                    }
                    if (ag != null) {
                        genderRowText = ag.getDisplay();
                    }
                }
            }
            Long draftId = null;
            if (paymentDraftIdByEncounterId != null && item.getEncounterId() != null) {
                draftId = paymentDraftIdByEncounterId.get(item.getEncounterId());
            }
            Long paymentId = null;
            if (paymentIdByEncounterId != null && item.getEncounterId() != null) {
                paymentId = paymentIdByEncounterId.get(item.getEncounterId());
            }
            return new ClinicRegisterItemDto().setEncounterId(item.getEncounterId())
                .setEncounterBusNo(item.getEncounterBusNo()).setPatientName(item.getPatientName())
                .setPatientGenderCode(item.getPatientGenderCode()).setPatientGenderText(genderRowText)
                .setContactPhone(item.getContactPhone()).setOrganizationName(item.getOrganizationName())
                .setDoctorName(item.getDoctorName()).setStatusEnum(item.getStatusEnum())
                .setStatusText(item.getStatusEnumText()).setStartTime(item.getStartTime())
                .setServiceTypeId(item.getServiceTypeId()).setServiceTypeName(item.getServiceTypeName())
                .setAmount(rowAmount).setPrintCount(item.getPrintCount()).setPaymentDraftId(draftId)
                .setPaymentId(paymentId);
        }).collect(Collectors.toList());
    }

    /**
     * 根据收费定义生成收费预览。
     *
     * @param saleLine 收费定义
     * @return 收费预览列表
     */
    @Override
    public List<ChargeItemPreviewDto> toChargeItemPreviewDtoListBySaleLine(ChargeItemDefinition saleLine) {
        List<ChargeItemPreviewDto> list = new ArrayList<>();
        if (saleLine != null) {
            BigDecimal price = saleLine.getPriceValue();
            if (price == null) {
                price = BigDecimal.ZERO;
            }
            list.add(new ChargeItemPreviewDto().setChargeItemCode(String.valueOf(saleLine.getId()))
                .setChargeItemName(saleLine.getChargeName()).setUnitPrice(price).setQuantity(BigDecimal.ONE)
                .setAmount(price));
        }
        return list;
    }

    /**
     * 将挂号单读模型转换为挂号单 DTO。
     *
     * @param model 挂号单读模型
     * @return 挂号单 DTO，若入参为空返回 null
     */
    @Override
    public RegistrationSlipDto toRegistrationSlipDto(RegistrationSlipQueryModel model) {
        if (model == null) {
            return null;
        }
        return new RegistrationSlipDto().setSlipNo(model.getSlipNo()).setEncounterId(model.getEncounterId())
            .setPatientName(model.getPatientName()).setOrganizationName(model.getOrganizationName())
            .setDoctorName(model.getDoctorName()).setServiceTypeName(model.getServiceTypeName())
            .setStatusText(model.getStatusEnumText()).setAmount(model.getAmount()).setPrintCount(model.getPrintCount());
    }

    /**
     * 将首次建档请求转换为重号校验查询命令。
     *
     * @param request 首次建档请求
     * @return 重号校验查询命令
     */
    @Override
    public DuplicatePatientCheckQuery toDuplicatePatientCheckQuery(SavePatientForFirstRegisterRequest request) {
        DuplicatePatientCheckQuery query = new DuplicatePatientCheckQuery();
        query.setName(request.getName());
        query.setGenderEnum(request.getGender());
        query.setIdCard(request.getIdCardNo());
        query.setBirthDate(request.getBirthDate());
        return query;
    }

    /**
     * 将首次建档请求转换为患者注册命令。
     *
     * @param request 首次建档请求
     * @return 患者注册命令
     */
    @Override
    public RegisterPatientCommand toRegisterPatientCommand(SavePatientForFirstRegisterRequest request) {
        RegisterPatientCommand command = new RegisterPatientCommand();
        RegisterPatientCommand.RegisterPatientItemCommand item =
            new RegisterPatientCommand.RegisterPatientItemCommand();
        item.setName(request.getName());
        item.setGenderEnum(request.getGender());
        item.setIdCard(request.getIdCardNo());
        item.setPhone(request.getPhone());
        item.setBirthDate(request.getBirthDate());
        item.setTempFlag(Boolean.FALSE);
        command.setItem(item);
        return command;
    }

    /**
     * 将预结算请求中的患者建档字段转换为患者注册命令。
     *
     * @param request 预结算请求
     * @return 患者注册命令
     */
    @Override
    public RegisterPatientCommand toRegisterPatientCommand(PreRegisterSettlementRequest request) {
        RegisterPatientCommand command = new RegisterPatientCommand();
        RegisterPatientCommand.RegisterPatientItemCommand item =
            new RegisterPatientCommand.RegisterPatientItemCommand();
        item.setName(request.getName());
        item.setGenderEnum(request.getGender());
        item.setIdCard(request.getIdCardNo());
        item.setPhone(request.getPhone());
        item.setBirthDate(request.getBirthDate());
        item.setOrganizationId(request.getOrganizationId());
        item.setTempFlag(Boolean.FALSE);
        command.setItem(item);
        return command;
    }

    /**
     * 将预结算请求与挂号费定价行装配为「创建收费项」命令：金额取自已解析的定价行，服务锚定为号源类型（healthcare_service），
     * 并写入就诊、账户、机构等必填外键以满足挂号读模型与 BR-CHG-001 追溯。
     *
     * @param request     预结算请求（患者、号源类型等）
     * @param saleLine    已选中的销售价定价行
     * @param encounterId 已开立就诊主键
     * @param occurredAt  费用发生时刻，空则落库为当前时刻
     * @param accountId   挂号结算账户主键
     * @param orgId       机构主键
     * @return 收费项创建命令
     */
    @Override
    public CreateChargeItemCommand toCreateChargeItemCommand(PreRegisterSettlementRequest request,
        ChargeItemDefinition saleLine, Long encounterId, LocalDateTime occurredAt, Long accountId, Long orgId) {
        CreateChargeItemCommand command = new CreateChargeItemCommand();
        CreateChargeItemCommand.Item item = new CreateChargeItemCommand.Item();
        item.setPatientId(request.getPatientId());
        item.setEncounterId(encounterId);
        item.setAccountId(accountId);
        item.setDefinitionId(saleLine.getId());
        BigDecimal price = saleLine.getPriceValue();
        if (price == null) {
            price = BigDecimal.ZERO;
        }
        item.setTotalPrice(price);
        item.setUnitPrice(price);
        item.setBaseAmount(price);
        item.setDiscountAmount(price);
        item.setSurchargeAmount(BigDecimal.ZERO);
        item.setQuantityValue(BigDecimal.ONE);
        item.setQuantityUnit(ChargeItemDmConstants.DEFAULT_QUANTITY_UNIT);
        LocalDateTime occurrenceAt = occurredAt;
        if (occurrenceAt == null) {
            occurrenceAt = LocalDateTime.now();
        }
        item.setOccurrenceTime(occurrenceAt);
        // 挂号费锚定号源类型定义行（与定价 healthcare_service 实例一致），满足 BR-CHG-001 可追溯。
        item.setServiceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        item.setServiceId(request.getServiceTypeId());
        item.setOrgId(orgId);
        item.setRequestingOrgId(orgId);
        command.setItem(item);
        return command;
    }

    /**
     * 预结算阶段开立门诊就诊默认个人现金账户命令：与缴费前开户语义一致，供幂等探测后补开账户使用。
     *
     * @param request     预结算请求（含患者主键）
     * @param encounterId 就诊主键
     * @return 开立账户命令，仅含一条默认账户项
     */
    @Override
    public OpenEncounterAccountsCommand
        toOpenEncounterAccountsCommandForPreRegister(
            PreRegisterSettlementRequest request,
            Long encounterId,
            String contractBusNo) {
        OpenEncounterAccountsCommand command = new OpenEncounterAccountsCommand();
        // 门诊就诊仅开立默认个人现金账户（AccountTypeCode.PERSONAL_CASH_ACCOUNT）：挂号费与医生站医嘱费用草稿共用 account_id。
        OpenEncounterAccountsCommand.Item item = new OpenEncounterAccountsCommand.Item();
        item.setEncounterId(encounterId);
        item.setPatientId(request.getPatientId());
        item.setDefaultFlag(Boolean.TRUE);
        item.setTypeCode(AccountTypeCode.PERSONAL_CASH_ACCOUNT.getCode());
        if (StringUtils.isNotBlank(contractBusNo)) {
            item.setContractNo(contractBusNo.trim());
        }
        command.setItem(item);
        return command;
    }

    /**
     * 将单个收费项 ID 转换为批量命令。
     *
     * @param chargeItemId 收费项 ID
     * @return 批量收费项命令
     */
    @Override
    public BatchChargeItemIdsCommand toSingleChargeItemBatchCommand(Long chargeItemId) {
        BatchChargeItemIdsCommand command = new BatchChargeItemIdsCommand();
        BatchChargeItemIdsCommand.Item item = new BatchChargeItemIdsCommand.Item();
        item.setChargeItemId(chargeItemId);
        command.setItem(item);
        return command;
    }

    /**
     * 将预结算请求转换为门诊挂号命令，并装配读模型投影所需的展示快照字段。
     *
     * @param request 预结算请求（含患者主键及建档场景下的姓名等）
     * @param organization 已校验存在的挂号科室
     * @param healthcareService 已校验存在的号源类型
     * @param patientCore 患者主档核心字段，可为空（走请求侧兜底）
     * @param operatorId 当前挂号员主键
     * @param operatorName 当前挂号员展示名
     * @param occurredAt 挂号发生时刻，写入就诊命令与快照 startTime
     * @param registrationPractitionerDisplayName 挂号所选医师展示名；未选时为 {@code null}
     * @return 可下推到 {@code EncounterDomainService#registerOutpatientEncounter} 的命令
     */
    @Override
    public RegisterOutpatientEncounterCommand toRegisterOutpatientEncounterCommand(PreRegisterSettlementRequest request,
        Organization organization, HealthcareService healthcareService, PatientCoreQueryModel patientCore,
        Long operatorId, String operatorName, LocalDateTime occurredAt, String registrationPractitionerDisplayName) {
        // 命令头：操作者与发生时刻供写侧与审计使用。
        RegisterOutpatientEncounterCommand command = new RegisterOutpatientEncounterCommand();
        command.setOperatorId(operatorId);
        command.setOccurredAt(occurredAt);
        RegisterOutpatientEncounterCommand.RegisterOutpatientEncounterItemCommand item =
            new RegisterOutpatientEncounterCommand.RegisterOutpatientEncounterItemCommand();
        // 主键与业务关联：患者、科室、号源、接诊医生（若有）。
        item.setPatientId(request.getPatientId());
        item.setServiceTypeId(request.getServiceTypeId());
        item.setOrganizationId(request.getOrganizationId());
        item.setPractitionerId(request.getPractitionerId());
        item.setPractitionerName(registrationPractitionerDisplayName);
        // 患者展示字段：主档优先，预结算表单（如新患者最小字段）兜底。
        String primaryPatientName = null;
        if (patientCore != null) {
            primaryPatientName = patientCore.getName();
        }
        item.setPatientName(firstNonBlank(primaryPatientName, request.getName()));
        String pyStr = null;
        String wbStr = null;
        if (patientCore != null) {
            pyStr = patientCore.getPyStr();
            wbStr = patientCore.getWbStr();
        }
        if (StringUtils.isBlank(pyStr)) {
            String generatedPy = SearchCodeUtil.toPyStr(StringUtils.defaultString(item.getPatientName()));
            if (StringUtils.isBlank(generatedPy)) {
                pyStr = null;
            } else {
                // 由患者姓名生成拼音检索码，供队列关键字匹配。
                pyStr = generatedPy;
            }
        }
        if (StringUtils.isBlank(wbStr)) {
            String generatedWb = SearchCodeUtil.toWbStr(StringUtils.defaultString(item.getPatientName()));
            if (StringUtils.isBlank(generatedWb)) {
                wbStr = null;
            } else {
                wbStr = generatedWb;
            }
        }
        item.setPyStr(pyStr);
        item.setWbStr(wbStr);
        if (patientCore != null) {
            item.setPatientBusNo(patientCore.getBusNo());
        } else {
            item.setPatientBusNo(null);
        }
        item.setGenderEnum(request.getGender());
        item.setGenderEnumText(EnumUtil.fromCode(AdministrativeGender.class, request.getGender()).getDisplay());
        String primaryPhone = null;
        if (patientCore != null) {
            primaryPhone = patientCore.getPhone();
        }
        item.setPhone(firstNonBlank(primaryPhone, request.getPhone()));
        LocalDate birthDate = request.getBirthDate();
        if (patientCore != null && patientCore.getBirthDate() != null) {
            birthDate = patientCore.getBirthDate();
        }
        item.setBirthDate(birthDate);
        item.setPatientAgeText(buildPatientAgeText(birthDate));
        String primaryIdCard = null;
        if (patientCore != null) {
            primaryIdCard = patientCore.getIdCard();
        }
        item.setIdCard(firstNonBlank(primaryIdCard, request.getIdCardNo()));
        // 科室与号源名称：来自已加载主数据，供挂号列表/挂号单直接展示。
        if (organization != null) {
            item.setOrganizationName(organization.getName());
        } else {
            item.setOrganizationName(null);
        }
        if (healthcareService != null) {
            item.setServiceTypeName(healthcareService.getName());
        } else {
            item.setServiceTypeName(null);
        }
        LocalDateTime startTime = LocalDateTime.now();
        if (occurredAt != null) {
            startTime = occurredAt;
        }
        item.setStartTime(startTime);
        item.setRegistrarId(operatorId);
        item.setRegistrarName(operatorName);
        command.setItem(item);
        return command;
    }

    /**
     * 构建刷新支付草稿命令。
     *
     * @param paymentId 支付草稿 ID
     * @param chargeItemId 收费项 ID
     * @param chargeItemAmount 已落库收费明细金额（与 {@code ChargeItem.totalPrice} 一致）
     * @param details 支付明细
     * @return 刷新支付草稿命令
     */
    @Override
    public RefreshRegistrationPaymentDraftCommand toRefreshRegistrationPaymentDraftCommand(Long paymentId,
        Long chargeItemId, BigDecimal chargeItemAmount, List<PaymentDetailItemCommand> details) {
        RefreshRegistrationPaymentDraftCommand command = new RefreshRegistrationPaymentDraftCommand();
        // 金额口径与预结算一致：以已落库收费明细为准，避免与定价表二次取值漂移。
        BigDecimal amount = chargeItemAmount;
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        command.setPaymentId(paymentId);
        command.setChargeItemIds(String.valueOf(chargeItemId));
        command.setDisplayAmount(amount);
        command.setTenderedAmount(amount);
        command.setPaymentDetails(details);
        return command;
    }

    /**
     * 构建创建支付草稿命令。
     *
     * @param request 预结算请求
     * @param encounterId 就诊 ID
     * @param chargeItemId 收费项 ID
     * @param chargeItemAmount 已落库收费明细金额（与 {@code ChargeItem.totalPrice} 一致）
     * @param details 支付明细
     * @return 创建支付草稿命令
     */
    @Override
    public CreatePaymentDraftFromPreSettleCommand toCreatePaymentDraftFromPreSettleCommand(
        PreRegisterSettlementRequest request,
        Long encounterId,
        Long chargeItemId,
        BigDecimal chargeItemAmount,
        List<PaymentDetailItemCommand> details,
        String contractBusNo) {
        CreatePaymentDraftFromPreSettleCommand command = new CreatePaymentDraftFromPreSettleCommand();
        // display/tendered 与 ChargeItem.totalPrice 对齐：支付草稿跟随已生成的收费事实，不回头取 ChargeItemDefinition。
        BigDecimal amount = chargeItemAmount;
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        command.setEncounterId(encounterId);
        command.setPatientId(request.getPatientId());
        command.setChargeItemIds(String.valueOf(chargeItemId));
        command.setDisplayAmount(amount);
        command.setTenderedAmount(amount);
        // 门诊挂号预结算阶段统一按收款方向落草稿，支付方式在后续支付回执阶段补充明细。
        command.setPaymentEnum(PaymentType.PAY.getCode());
        // 预结算创建支付主记录时显式落草稿态，避免状态默认值由下游隐式推断。
        command.setStatusEnum(PaymentStatus.DRAFT);
        // 门诊挂号收费属于门诊收费大类，避免 kind_enum 为空触发约束异常。
        command.setKindEnum(PaymentKind.OUTPATIENT_CLINIC);
        command.setPaymentDetails(details);
        if (StringUtils.isNotBlank(contractBusNo)) {
            command.setContractNo(contractBusNo.trim());
        }
        return command;
    }

    /**
     * 将支付草稿转换为开户命令。
     *
     * @param paymentDraft 支付草稿
     * @return 开户命令
     */
    @Override
    public OpenEncounterAccountsCommand toOpenEncounterAccountsCommand(PaymentReconciliationQueryModel paymentDraft) {
        OpenEncounterAccountsCommand command = new OpenEncounterAccountsCommand();
        OpenEncounterAccountsCommand.Item item = new OpenEncounterAccountsCommand.Item();
        item.setEncounterId(paymentDraft.getEncounterId());
        item.setPatientId(paymentDraft.getPatientId());
        item.setDefaultFlag(Boolean.TRUE);
        item.setTypeCode(AccountTypeCode.PERSONAL_CASH_ACCOUNT.getCode());
        if (StringUtils.isNotBlank(paymentDraft.getContractNo())) {
            item.setContractNo(paymentDraft.getContractNo().trim());
        }
        command.setItem(item);
        return command;
    }

    /**
     * 将支付请求转换为支付结算命令。
     *
     * @param request 挂号支付请求
     * @return 支付结算命令
     */
    @Override
    public SettleOutpatientPaymentCommand toSettleOutpatientPaymentCommand(PayRegisterFeeRequest request) {
        SettleOutpatientPaymentCommand command = new SettleOutpatientPaymentCommand();
        command.setPaymentId(request.getPaymentDraftId());
        command.setBillDate(LocalDateTime.now());
        command.setPayReceiptDetails(toPaymentDetailItemCommands(request.getPaymentDetails()));
        return command;
    }

    /**
     * 将支付信息转换为开票草稿命令。
     *
     * @param paymentDraft 支付草稿
     * @param paymentId 支付 ID
     * @return 开票草稿命令
     */
    @Override
    public CreateDraftInvoiceCommand toCreateDraftInvoiceCommand(PaymentReconciliationQueryModel paymentDraft,
        Long paymentId) {
        CreateDraftInvoiceCommand command = new CreateDraftInvoiceCommand();
        command.setPatientId(paymentDraft.getPatientId());
        command.setPaymentId(paymentId);
        command.setAmount(paymentDraft.getDisplayAmount());
        command.setTypeCode(AccountTypeCode.REG.getCode());
        return command;
    }

    /**
     * 构建电子发票开具命令。
     *
     * @param invoiceId 发票 ID
     * @return 电子发票开具命令
     */
    @Override
    public IssueElectronicInvoiceCommand toIssueElectronicInvoiceCommand(Long invoiceId) {
        IssueElectronicInvoiceCommand command = new IssueElectronicInvoiceCommand();
        command.setInvoiceId(invoiceId);
        command.setBillNo("INV-" + invoiceId);
        command.setBillCreateTime(LocalDateTime.now());
        command.setBillBatchCode("REG-BATCH");
        return command;
    }

    /**
     * 构建发票查询命令。
     *
     * @param invoiceId 发票 ID
     * @return 发票查询命令
     */
    @Override
    public InvoiceQueryCommand toInvoiceQueryCommand(Long invoiceId) {
        InvoiceQueryCommand command = new InvoiceQueryCommand();
        command.setInvoiceId(invoiceId);
        return command;
    }

    /**
     * 将挂号分页请求转换为读模型查询命令。
     *
     * @param request 挂号分页请求
     * @return 读模型查询命令
     */
    @Override
    public ClinicRegisterPageQuery toClinicRegisterPageQuery(PageClinicRegistersRequest request) {
        ClinicRegisterPageQuery query = new ClinicRegisterPageQuery();
        PageQueryBinder.bind(request, query);
        // 挂号列表业务窗使用 dateFrom/dateTo → startTime/endTime；不沿用通用 AbstractPageQuery 日期列以免与读模型谓词语义混用。
        query.setStartDate(null);
        query.setEndDate(null);
        query.setStartTime(request.getDateFrom().atStartOfDay());
        query.setEndTime(request.getDateTo().atTime(23, 59, 59));
        query.setPatientId(request.getPatientId());
        return query;
    }

    /**
     * 将退号退费请求转换为退号命令。
     *
     * @param request 退号退费请求
     * @return 退号命令
     */
    @Override
    public CancelOutpatientRegistrationCommand toCancelOutpatientRegistrationCommand(CancelWithRefundRequest request) {
        CancelOutpatientRegistrationCommand command = new CancelOutpatientRegistrationCommand();
        CancelOutpatientRegistrationCommand.CancelOutpatientRegistrationItemCommand item =
            new CancelOutpatientRegistrationCommand.CancelOutpatientRegistrationItemCommand();
        item.setEncounterId(request.getEncounterId());
        item.setCancelReason(request.getCancelReason());
        item.setCancelAt(LocalDateTime.now());
        command.setItem(item);
        return command;
    }

    /**
     * 构建门诊退费申请命令。
     *
     * @param request 退号退费请求
     * @param payment 支付记录
     * @return 退费申请命令
     */
    @Override
    public RequestOutpatientRefundCommand toRequestOutpatientRefundCommand(CancelWithRefundRequest request,
        PaymentReconciliationQueryModel payment) {
        RequestOutpatientRefundCommand command = new RequestOutpatientRefundCommand();
        command.setRelationId(payment.getId());
        command.setEncounterId(request.getEncounterId());
        command.setPatientId(payment.getPatientId());
        command.setEntererId(payment.getEntererId());
        command.setKindEnum(payment.getKindEnum());
        command.setPaymentEnum(PaymentType.UN_PAY.getCode());
        command.setDisplayAmount(payment.getDisplayAmount());
        command.setTenderedAmount(payment.getDisplayAmount());
        command.setChargeItemIds(payment.getChargeItemIds());
        return command;
    }

    /**
     * 构建退号联动退费完成命令。
     *
     * @param originalPaymentId 原支付主单主键
     * @param reversePaymentId  逆向退款单主键
     * @return 退号退费完成命令
     */
    @Override
    public CompleteRegistrationCancelRefundCommand toCompleteRegistrationCancelRefundCommand(
        Long originalPaymentId,
        Long reversePaymentId
    ) {
        CompleteRegistrationCancelRefundCommand command = new CompleteRegistrationCancelRefundCommand();
        command.setOriginalPaymentId(originalPaymentId);
        command.setReversePaymentId(reversePaymentId);
        command.setBillDate(LocalDateTime.now());
        return command;
    }

    /**
     * 按就诊 ID 聚合收费项并构建批量命令。
     *
     * @param encounterId 就诊 ID
     * @param chargeItemQueryService 收费项查询服务
     * @return 批量收费项命令
     */
    @Override
    public BatchChargeItemIdsCommand toBatchChargeItemIdsCommandFromEncounter(Long encounterId,
        ChargeItemQueryService chargeItemQueryService) {
        ChargeItemQueryCommand query = new ChargeItemQueryCommand();
        query.setEncounterId(encounterId);
        List<ChargeItem> chargeItems = chargeItemQueryService.listChargeItemsByEncounterId(query);

        BatchChargeItemIdsCommand command = new BatchChargeItemIdsCommand();
        List<BatchChargeItemIdsCommand.Item> items = new ArrayList<>();
        for (ChargeItem chargeItem : chargeItems) {
            BatchChargeItemIdsCommand.Item item = new BatchChargeItemIdsCommand.Item();
            item.setChargeItemId(chargeItem.getId());
            items.add(item);
        }
        command.setItems(items);
        return command;
    }

    /**
     * 构建发票冲红命令。
     *
     * @param invoiceId 发票 ID
     * @param reason 冲红原因
     * @return 冲红命令
     */
    @Override
    public CancelInvoiceCommand toCancelInvoiceCommand(Long invoiceId, String reason) {
        CancelInvoiceCommand command = new CancelInvoiceCommand();
        command.setInvoiceId(invoiceId);
        command.setReason(reason);
        return command;
    }

    /**
     * 将应用层支付明细转换为领域支付明细命令列表。
     *
     * <p>实现要点：逐条搬运支付渠道、金额、流水号等字段，账户信息随渠道一并写入，
     * 用于后续支付结算命令组装。</p>
     *
     * @param details 应用层支付明细；不为 null（调用方已校验）
     * @return 领域支付明细命令列表；空入参时返回空列表
     */
    private List<PaymentDetailItemCommand> toPaymentDetailItemCommands(List<PaymentDetailDto> details) {
        List<PaymentDetailItemCommand> list = new ArrayList<>();
        // 逐条转换支付明细：账户、渠道、金额、流水号。
        for (PaymentDetailDto detail : details) {
            PaymentDetailItemCommand item = new PaymentDetailItemCommand();
            item.setAccountId(detail.getAccountId());
            item.setAccountCode(detail.getAccountCode());
            item.setPayEnum(detail.getPayChannelCode());
            item.setAmount(detail.getPayAmount());
            item.setPayTransNo(detail.getTransactionNo());
            list.add(item);
        }
        return list;
    }

    /**
     * 由出生日期推算整岁年龄展示文本，供挂号单/列表快照使用。
     *
     * <p>实现要点：使用 {@link Period#between} 计算自然周岁，负值（未来日期）视为无效返回 null，
     * 文案后缀使用 {@link GeneralSharedConstants#PATIENT_AGE_YEAR_SUFFIX} 与系统统一年龄展示口径对齐。</p>
     *
     * @param birthDate 出生日期，可为空
     * @return 形如「35岁」的文本；无法计算时为 {@code null}
     */
    private static String buildPatientAgeText(LocalDate birthDate) {
        // 出生日期缺失时无法计算年龄，返回 null。
        if (birthDate == null) {
            return null;
        }
        // 计算自然周岁：当前日期与出生日期之间的完整年数。
        int years = Period.between(birthDate, LocalDate.now()).getYears();
        // 负值表示出生日期在未来（数据异常），视为无效返回 null。
        if (years < 0) {
            return null;
        }
        return years + GeneralSharedConstants.PATIENT_AGE_YEAR_SUFFIX;
    }
}
