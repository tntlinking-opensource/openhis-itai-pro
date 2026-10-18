/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.registration.mapper;

import static com.zxplat.framework.core.application.util.StringUtils.firstNonBlank;

import com.zxplat.application.shared.inpatient.AdmissionPrimaryDiagnosisAssembler;
import com.zxplat.application.shared.inpatient.InpatientInfoQueueDisplayAssembler;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.application.inpatient.financial.registration.dto.AdmissionDiagnosisSubmitDto;
import com.zxplat.application.inpatient.financial.registration.dto.PaymentDetailDto;
import com.zxplat.application.inpatient.financial.registration.dto.RegistrationEncounterDto;
import com.zxplat.application.inpatient.financial.registration.dto.RegistrationPatientDto;
import com.zxplat.application.inpatient.financial.registration.dto.RegistrationQueueItemDto;
import com.zxplat.application.inpatient.financial.registration.dto.WardOptionDto;
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
import com.zxplat.domain.administration.encounter.command.AdmissionConditionItem;
import com.zxplat.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.zxplat.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.zxplat.domain.administration.encounter.command.CancelInpatientAdmissionCommand;
import com.zxplat.domain.administration.encounter.command.RegisterInpatientEncounterCommand;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.patient.command.ModifyPatientProfileCommand;
import com.zxplat.domain.administration.patient.command.RegisterPatientCommand;
import com.zxplat.domain.administration.patient.queries.model.PatientCoreQueryModel;
import com.zxplat.domain.financial.paymentreconciliation.command.CollectAdvanceDepositCommand;
import com.zxplat.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.zxplat.domain.financial.paymentreconciliation.command.SettleInpatientPaymentCommand;
import com.zxplat.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.zxplat.domain.share.enums.ChargeIntent;
import com.zxplat.domain.share.enums.EncounterStatus;
import com.zxplat.domain.share.enums.PaymentKind;
import com.zxplat.domain.share.enums.PaymentStatus;
import com.zxplat.domain.share.enums.PaymentType;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link InpatientRegistrationMapper} 的 Spring 实现：纯字段映射，不含业务分支。
 */
@Component
@RequiredArgsConstructor
public class InpatientRegistrationMapperImpl implements InpatientRegistrationMapper {

    private final AdmissionPrimaryDiagnosisAssembler admissionPrimaryDiagnosisAssembler;

    /**
     * 装配住院登记页初始化响应。
     *
     * @param paymentChannelOptions 用户可选支付渠道下拉
     * @return 初始化响应
     */
    @Override
    public LoadInpatientRegistrationPageInitResponse toLoadInpatientRegistrationPageInitResponse(
        List<OptionDto> paymentChannelOptions) {
        LoadInpatientRegistrationPageInitResponse response = new LoadInpatientRegistrationPageInitResponse();
        response.setPaymentChannelOptions(paymentChannelOptions);
        return response;
    }

    /**
     * 读模型列表 → 待办行 DTO 列表。
     *
     * @param rows 读模型行；调用方保证非 null
     * @param ambEncounterIdByEncounterId 住院就诊 ID → 源门诊就诊 ID；可空
     * @param inWayLabelByCode            入院方式 code → 展示文案；可空
     * @return 列表行 DTO
     */
    @Override
    public List<RegistrationQueueItemDto> toRegistrationQueueItemDtoList(
        List<InpatientEncounterContextQueryModel> rows,
        Map<Long, Long> ambEncounterIdByEncounterId,
        Map<Long, InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay> displayOverlayByEncounterId,
        Map<String, String> inWayLabelByCode) {
        List<RegistrationQueueItemDto> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        Map<Long, Long> ambIndex = ambEncounterIdByEncounterId;
        if (ambIndex == null) {
            ambIndex = new HashMap<>();
        }
        Map<Long, InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay> overlayIndex =
            displayOverlayByEncounterId;
        if (overlayIndex == null) {
            overlayIndex = new HashMap<>();
        }
        for (InpatientEncounterContextQueryModel row : rows) {
            Long ambEncounterId = null;
            InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay overlay = null;
            if (row.getEncounterId() != null) {
                ambEncounterId = ambIndex.get(row.getEncounterId());
                overlay = overlayIndex.get(row.getEncounterId());
            }
            list.add(this.toRegistrationQueueItemDto(row, ambEncounterId, overlay, inWayLabelByCode));
        }
        return list;
    }

    /**
     * 列表行 + Spring 分页壳 → 分页响应（经 PagedResultBuilder）。
     *
     * @param rows 已映射列表行
     * @param page 读模型分页结果
     * @return 分页响应
     */
    @Override
    public PageRegistrationQueueResponse toPageRegistrationQueueResponse(
        List<RegistrationQueueItemDto> rows,
        Page<InpatientEncounterContextQueryModel> page) {
        return PagedResultBuilder.build(new PageRegistrationQueueResponse(), rows, page);
    }

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
    @Override
    public LoadRegistrationContextResponse toLoadRegistrationContextResponse(
        RegistrationPatientDto patient,
        RegistrationEncounterDto encounter,
        List<OptionDto> contractOptions,
        List<OptionDto> organizationOptions,
        List<OptionDto> inWayCodeOptions,
        List<OptionDto> admitSourceCodeOptions,
        List<OptionDto> admCondCodeOptions,
        boolean readOnly,
        boolean switchToInsuranceEnabled,
        boolean switchToSelfPayEnabled) {
        LoadRegistrationContextResponse response = new LoadRegistrationContextResponse();
        response.setPatient(patient);
        response.setEncounter(encounter);
        response.setContractOptions(contractOptions);
        response.setOrganizationOptions(organizationOptions);
        response.setInWayCodeOptions(inWayCodeOptions);
        response.setAdmitSourceCodeOptions(admitSourceCodeOptions);
        response.setAdmCondCodeOptions(admCondCodeOptions);
        response.setReadOnly(readOnly);
        response.setSwitchToInsuranceEnabled(switchToInsuranceEnabled);
        response.setSwitchToSelfPayEnabled(switchToSelfPayEnabled);
        return response;
    }

    /**
     * 患者核心读模型 → 登记弹窗患者主档（含婚姻/职业/现住址回显）。
     *
     * @param core 患者核心；null 或无 id 时返回 null
     * @return 登记患者 DTO
     */
    @Override
    public RegistrationPatientDto toRegistrationPatientDto(PatientCoreQueryModel core) {
        if (core == null || core.getId() == null) {
            return null;
        }
        RegistrationPatientDto dto = new RegistrationPatientDto();
        dto.setPatientId(core.getId());
        dto.setName(core.getName());
        dto.setIdCard(core.getIdCard());
        dto.setGenderEnum(core.getGenderEnum());
        // Asm：读模型生日为 LocalDate，弹窗展示统一为当日 00:00:00
        if (core.getBirthDate() != null) {
            dto.setBirthDate(core.getBirthDate().atStartOfDay());
        }
        dto.setPhone(core.getPhone());
        dto.setMaritalStatusEnum(core.getMaritalStatusEnum());
        dto.setPrfsEnum(core.getPrfsEnum());
        dto.setAddress(core.getAddress());
        dto.setAddressProvince(core.getAddressProvince());
        dto.setAddressCity(core.getAddressCity());
        dto.setAddressDistrict(core.getAddressDistrict());
        dto.setAddressStreet(core.getAddressStreet());
        dto.setCountryCode(core.getCountryCode());
        dto.setNationalityCode(core.getNationalityCode());
        dto.setNativePlace(core.getNativePlace());
        dto.setWorkCompany(core.getWorkCompany());
        dto.setLinkName(core.getLinkName());
        dto.setLinkRelationCode(core.getLinkRelationCode());
        dto.setLinkTelcom(core.getLinkTelcom());
        return dto;
    }

    /**
     * 住院读模型 → 登记就诊 DTO（入院主诊名称走登记专用解析：国临行回落医保名）。
     *
     * @param row                     住院就诊上下文读模型；null 时返回 null
     * @param encounterHead           就诊头表补充（入院类型等）；可为 null
     * @param inWayLabelByCode        入院途径术语码→文案
     * @param admitSourceLabelByCode  入院类型术语码→文案
     * @return 就诊 DTO
     */
    @Override
    public RegistrationEncounterDto toRegistrationEncounterDto(
        InpatientEncounterContextQueryModel row,
        EncounterDetailQueryModel encounterHead,
        Map<String, String> inWayLabelByCode,
        Map<String, String> admitSourceLabelByCode) {
        // Gate：无读模型则无登记就诊 DTO
        if (row == null) {
            return null;
        }
        RegistrationEncounterDto dto = new RegistrationEncounterDto();
        dto.setEncounterId(row.getEncounterId());
        dto.setEncounterBusNo(row.getEncounterBusNo());
        dto.setStatusEnum(row.getStatusEnum());
        dto.setOrganizationId(row.getOrganizationId());
        dto.setOrganizationName(row.getOrganizationName());
        dto.setWardId(row.getWardId());
        dto.setWardName(row.getWardName());
        dto.setBedId(row.getBedId());
        dto.setBedName(row.getBedName());
        dto.setInWayCode(row.getInWayCode());
        dto.setInWayCodeText(this.resolveTerminologyLabel(inWayLabelByCode, row.getInWayCode()));
        dto.setStartTime(row.getStartTime());
        // Asm：登记阶段展示医保名；国临名延后到住院医生站（AS-IP-FIN-001）
        AdmissionPrimaryDiagnosisAssembler.PrimaryDiagnosisDisplay primaryDiagnosis =
            admissionPrimaryDiagnosisAssembler.resolvePrimaryDiagnosisForRegistration(row.getDiagnosisJson());
        dto.setPrimaryConditionDefinitionId(primaryDiagnosis.getPrimaryConditionDefinitionId());
        dto.setPrimaryConditionDefinitionName(primaryDiagnosis.getPrimaryConditionDefinitionName());
        dto.setAdmitSourceCode(null);
        dto.setAdmitSourceCodeText(null);
        dto.setPriorityEnum(null);
        dto.setAmbEncounterId(null);
        dto.setAttendingDoctorId(row.getAttendingDoctorId());
        dto.setAttendingDoctorName(row.getAttendingDoctorName());
        if (encounterHead != null) {
            if (StringUtils.isNotEmpty(encounterHead.getBusNo())) {
                dto.setEncounterBusNo(encounterHead.getBusNo());
            }
            dto.setAdmitSourceCode(encounterHead.getAdmitSourceCode());
            dto.setAdmitSourceCodeText(
                this.resolveTerminologyLabel(admitSourceLabelByCode, encounterHead.getAdmitSourceCode()));
            dto.setPriorityEnum(encounterHead.getPriorityEnum());
            dto.setAmbEncounterId(encounterHead.getAmbEncounterId());
            if (encounterHead.getStartTime() != null) {
                dto.setStartTime(encounterHead.getStartTime());
            }
        }
        // Asm：办理意向优先头表；无头表时回落住院上下文（与队列同口径）
        String chargeIntent = null;
        if (encounterHead != null) {
            chargeIntent = encounterHead.getChargeIntent();
        }
        if (StringUtils.isEmpty(chargeIntent)) {
            chargeIntent = row.getChargeIntent();
        }
        dto.setChargeIntent(chargeIntent);
        dto.setChargeIntentText(this.resolveChargeIntentText(chargeIntent));
        return dto;
    }

    /**
     * 费用性质列表 → 下拉选项。
     *
     * @param contracts 合同列表项
     * @return 下拉选项
     */
    @Override
    public List<OptionDto> toContractOptionList(List<ContractListItemQueryModel> contracts) {
        List<OptionDto> list = new ArrayList<>();
        if (contracts == null) {
            return list;
        }
        for (ContractListItemQueryModel contract : contracts) {
            list.add(new OptionDto(contract.getId(), String.valueOf(contract.getId()), contract.getName()));
        }
        return list;
    }

    /**
     * 提交请求 → 新建患者命令。
     *
     * @param request 登记请求
     * @return 注册命令
     */
    @Override
    public RegisterPatientCommand toRegisterPatientCommand(SubmitRegistrationRequest request) {
        if (request == null) {
            return null;
        }
        RegisterPatientCommand command = new RegisterPatientCommand();
        RegisterPatientCommand.RegisterPatientItemCommand item =
            new RegisterPatientCommand.RegisterPatientItemCommand();
        item.setName(request.getPatientName());
        item.setGenderEnum(request.getGenderEnum());
        item.setIdCard(request.getIdCard());
        item.setPhone(request.getPhone());
        item.setBirthDate(this.toLocalDate(request.getBirthDate()));
        // 新建仅落地址全文；婚姻/职业/省市区在随后 modify 中叠写（供病案 bootstrap）
        item.setAddress(trimToNull(request.getAddress()));
        item.setOrganizationId(request.getOrganizationId());
        item.setTempFlag(Boolean.FALSE);
        command.setItem(item);
        return command;
    }

    /**
     * 提交请求 → 修改患者档案命令（既有主档为底，叠写登记可编辑字段）。
     *
     * <p>叠写字段对齐病案首页病人基本信息可入院采集项：国籍/民族/籍贯/婚姻/职业/现住址/
     * 工作单位/联系人；血型等登记页未编辑字段自 {@code existing} 保留，避免空值覆盖。</p>
     *
     * @param request   登记请求
     * @param patientId 既有患者 ID
     * @param existing  既有主档；可为 null（新建后首更）
     * @return 修改命令；入参缺失时 null
     */
    @Override
    public ModifyPatientProfileCommand toModifyPatientProfileCommand(
        SubmitRegistrationRequest request,
        Long patientId,
        PatientCoreQueryModel existing) {
        // Gate：缺请求或患者主键则无法组装改档命令
        if (request == null || patientId == null) {
            return null;
        }
        ModifyPatientProfileCommand command = new ModifyPatientProfileCommand();
        ModifyPatientProfileCommand.ModifyPatientProfileItemCommand item =
            new ModifyPatientProfileCommand.ModifyPatientProfileItemCommand();
        item.setPatientId(patientId);
        // Pre：有既有主档时先全量拷贝，登记页未展示字段（血型/联系人扩展等）不因 null 被清空
        if (existing != null) {
            item.setNameJson(existing.getNameJson());
            item.setMaritalStatusEnum(existing.getMaritalStatusEnum());
            item.setPrfsEnum(existing.getPrfsEnum());
            item.setAddress(existing.getAddress());
            item.setAddressProvince(existing.getAddressProvince());
            item.setAddressCity(existing.getAddressCity());
            item.setAddressDistrict(existing.getAddressDistrict());
            item.setAddressStreet(existing.getAddressStreet());
            item.setAddressExtendJson(existing.getAddressExtendJson());
            item.setNationalityCode(existing.getNationalityCode());
            item.setEducationLevelCode(existing.getEducationLevelCode());
            item.setWorkCompany(existing.getWorkCompany());
            item.setNativePlace(existing.getNativePlace());
            item.setCountryCode(existing.getCountryCode());
            item.setLinkName(existing.getLinkName());
            item.setLinkRelationCode(existing.getLinkRelationCode());
            item.setLinkTelcom(existing.getLinkTelcom());
            item.setLinkJsons(existing.getLinkJsons());
            item.setBloodTypeAbo(existing.getBloodTypeAbo());
            item.setBloodTypeRh(existing.getBloodTypeRh());
            item.setName(existing.getName());
            item.setGenderEnum(existing.getGenderEnum());
            item.setIdCard(existing.getIdCard());
            item.setPhone(existing.getPhone());
            item.setBirthDate(existing.getBirthDate());
        }
        // Cmd：登记表单可编辑字段叠写；请求字段为 null 表示「未传」、空串经 trimToNull 表示清空
        if (StringUtils.isNotEmpty(request.getPatientName())) {
            item.setName(request.getPatientName());
        }
        if (StringUtils.isNotEmpty(request.getGenderEnum())) {
            item.setGenderEnum(request.getGenderEnum());
        }
        // 证件号允许传空串清空假证（与建档可空口径一致）
        if (request.getIdCard() != null) {
            item.setIdCard(request.getIdCard());
        }
        if (request.getPhone() != null) {
            item.setPhone(request.getPhone());
        }
        if (request.getBirthDate() != null) {
            item.setBirthDate(this.toLocalDate(request.getBirthDate()));
        }
        if (request.getMaritalStatusEnum() != null) {
            item.setMaritalStatusEnum(trimToNull(request.getMaritalStatusEnum()));
        }
        if (request.getPrfsEnum() != null) {
            item.setPrfsEnum(trimToNull(request.getPrfsEnum()));
        }
        // 现住址任一分量非 null 即整组叠写，避免省市区与详址半更新
        if (request.getAddress() != null
            || request.getAddressProvince() != null
            || request.getAddressCity() != null
            || request.getAddressDistrict() != null
            || request.getAddressStreet() != null) {
            item.setAddress(trimToNull(request.getAddress()));
            item.setAddressProvince(trimToNull(request.getAddressProvince()));
            item.setAddressCity(trimToNull(request.getAddressCity()));
            item.setAddressDistrict(trimToNull(request.getAddressDistrict()));
            item.setAddressStreet(trimToNull(request.getAddressStreet()));
        }
        if (request.getCountryCode() != null) {
            item.setCountryCode(trimToNull(request.getCountryCode()));
        }
        if (request.getNationalityCode() != null) {
            item.setNationalityCode(trimToNull(request.getNationalityCode()));
        }
        if (request.getNativePlace() != null) {
            item.setNativePlace(trimToNull(request.getNativePlace()));
        }
        if (request.getWorkCompany() != null) {
            item.setWorkCompany(trimToNull(request.getWorkCompany()));
        }
        if (request.getLinkName() != null) {
            item.setLinkName(trimToNull(request.getLinkName()));
        }
        if (request.getLinkRelationCode() != null) {
            item.setLinkRelationCode(request.getLinkRelationCode());
        }
        if (request.getLinkTelcom() != null) {
            item.setLinkTelcom(trimToNull(request.getLinkTelcom()));
        }
        command.setItem(item);
        return command;
    }

    /**
     * 提交请求 → 住院登记命令。
     *
     * @param request     登记请求
     * @param encounterId 住院就诊 ID
     * @param registrarId 登记员 ID
     * @param contractNo  费用性质 bus_no
     * @return 登记命令
     */
    @Override
    public RegisterInpatientEncounterCommand toRegisterInpatientEncounterCommand(
        SubmitRegistrationRequest request,
        Long encounterId,
        Long registrarId,
        String contractNo) {
        if (request == null) {
            return null;
        }
        RegisterInpatientEncounterCommand command = new RegisterInpatientEncounterCommand();
        command.setOperatorId(registrarId);
        RegisterInpatientEncounterCommand.RegisterInpatientEncounterItemCommand item =
            new RegisterInpatientEncounterCommand.RegisterInpatientEncounterItemCommand();
        item.setEncounterId(encounterId);
        item.setDepartmentId(request.getOrganizationId());
        item.setWardId(request.getWardId());
        item.setRegistrarId(registrarId);
        item.setRegisterTime(request.getStartTime());
        item.setContractNo(contractNo);
        if (StringUtils.isNotEmpty(request.getInsutype())) {
            item.setInsutype(request.getInsutype().trim());
        }
        item.setAdmitSourceCode(request.getAdmitSourceCode());
        item.setInWayCode(request.getInWayCode());
        item.setPriorityCode(request.getPriorityEnum());
        item.setAdmissionDiagnoses(this.toAdmissionConditionItems(request.getAdmissionDiagnoses()));
        command.setItem(item);
        return command;
    }

    /**
     * 登记请求 → 预交金收取命令（头 {@code paymentEnum=PAY}，渠道在明细）。
     *
     * @param request     登记请求
     * @param patientId   患者 ID
     * @param encounterId 就诊 ID
     * @param accountId   登记账户 ID（写入明细挂账）
     * @return 收取命令；请求为空时 null
     */
    @Override
    public CollectAdvanceDepositCommand toCollectAdvanceDepositCommand(
        SubmitRegistrationRequest request,
        Long patientId,
        Long encounterId,
        Long accountId) {
        // 空请求无法装配命令，交由调用方跳过收款
        if (request == null) {
            return null;
        }
        CollectAdvanceDepositCommand command = new CollectAdvanceDepositCommand();
        command.setEncounterId(encounterId);
        command.setPatientId(patientId);
        // entererId 由 AppService 写入 practitioner.id（对齐门诊登记 BR-PRICE 写路径）
        command.setTenderedAmount(request.getAdvanceDepositAmount());
        command.setDisplayAmount(request.getAdvanceDepositAmount());
        command.setKindEnum(PaymentKind.HOSPITAL_DEPOSIT);
        // 头表 payment_enum 为支付方向 PAY；渠道落在明细 payChannelCode（与预交金页一致）
        command.setPaymentEnum(PaymentType.PAY.getCode());
        command.setPaymentDetails(this.toPaymentDetailItemCommands(request.getPaymentDetails(), accountId));
        return command;
    }

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
    @Override
    public CollectAdvanceDepositCommand toCollectAdvanceDepositCommandForChannel(
        Long patientId,
        Long encounterId,
        Long accountId,
        BigDecimal amount,
        PaymentDetailDto detail) {
        // 无渠道明细时无法装配，交由调用方跳过
        if (detail == null) {
            return null;
        }
        CollectAdvanceDepositCommand command = new CollectAdvanceDepositCommand();
        command.setEncounterId(encounterId);
        command.setPatientId(patientId);
        // 头金额与本渠道金额一致，保证一渠道一流水
        command.setTenderedAmount(amount);
        command.setDisplayAmount(amount);
        command.setKindEnum(PaymentKind.HOSPITAL_DEPOSIT);
        command.setPaymentEnum(PaymentType.PAY.getCode());
        command.setPaymentDetails(this.toPaymentDetailItemCommands(List.of(detail), accountId));
        return command;
    }

    /**
     * 住院支付结算命令。
     *
     * @param paymentId 支付草稿 ID
     * @return 结算命令
     */
    @Override
    public SettleInpatientPaymentCommand toSettleInpatientPaymentCommand(Long paymentId) {
        SettleInpatientPaymentCommand command = new SettleInpatientPaymentCommand();
        command.setPaymentId(paymentId);
        command.setOutcomeEnum(PaymentStatus.SUCCESS.getCode());
        command.setBillDate(LocalDateTime.now());
        return command;
    }

    /**
     * 登记结果 → 提交响应。
     *
     * @param encounterId   就诊 ID
     * @param busNo         住院号
     * @param paymentId     支付单 ID，可 null
     * @param accountOpened 是否新开账户
     * @return 提交响应
     */
    @Override
    public SubmitRegistrationResponse toSubmitRegistrationResponse(
        Long encounterId,
        String busNo,
        Long paymentId,
        boolean accountOpened,
        String ybMdtrtId) {
        SubmitRegistrationResponse response = new SubmitRegistrationResponse();
        response.setEncounterId(encounterId);
        response.setEncounterBusNo(busNo);
        response.setStatusEnum(EncounterStatus.REGISTERED.getCode());
        response.setPaymentId(paymentId);
        response.setAccountOpened(accountOpened);
        response.setYbMdtrtId(ybMdtrtId);
        return response;
    }

    /**
     * 取消请求 → 取消住院登记命令。
     *
     * @param request                取消请求
     * @param accountBalanceSnapshot 账户余额快照
     * @return 取消命令
     */
    @Override
    public CancelInpatientAdmissionCommand toCancelInpatientAdmissionCommand(
        CancelRegistrationRequest request,
        Long accountBalanceSnapshot) {
        if (request == null) {
            return null;
        }
        CancelInpatientAdmissionCommand command = new CancelInpatientAdmissionCommand();
        command.setOperatorId(request.getOperatorId());
        CancelInpatientAdmissionCommand.CancelInpatientAdmissionItemCommand item =
            new CancelInpatientAdmissionCommand.CancelInpatientAdmissionItemCommand();
        item.setEncounterId(request.getEncounterId());
        item.setCancelReason(request.getCancelReason());
        item.setCancelAt(LocalDateTime.now());
        item.setAccountBalance(accountBalanceSnapshot);
        command.setItem(item);
        return command;
    }

    /**
     * 取消后 → 取消响应。
     *
     * @param encounterId 就诊 ID
     * @return 取消响应
     */
    @Override
    public CancelRegistrationResponse toCancelRegistrationResponse(Long encounterId) {
        CancelRegistrationResponse response = new CancelRegistrationResponse();
        response.setEncounterId(encounterId);
        response.setStatusEnum(EncounterStatus.CANCELLED.getCode());
        response.setSuccess(Boolean.TRUE);
        return response;
    }

    /**
     * 病区位置列表 → 病区下拉响应。
     *
     * @param wards 病区位置实体列表
     * @return 响应
     */
    @Override
    public ListWardsByDepartmentResponse toListWardsByDepartmentResponse(List<Location> wards) {
        ListWardsByDepartmentResponse response = new ListWardsByDepartmentResponse();
        List<WardOptionDto> options = new ArrayList<>();
        if (wards != null) {
            for (Location ward : wards) {
                WardOptionDto option = new WardOptionDto();
                option.setWardId(ward.getId());
                option.setWardCode(ward.getBusNo());
                option.setWardName(ward.getName());
                options.add(option);
            }
        }
        response.setWards(options);
        return response;
    }

    /**
     * 床位统计 → 余量响应。
     *
     * @param wardId 病区 ID
     * @param totalBedCount  总床
     * @param vacantBedCount 空床
     * @return 响应
     */
    @Override
    public LoadWardBedCapacityResponse toLoadWardBedCapacityResponse(
        Long wardId,
        Long totalBedCount,
        Long vacantBedCount) {
        LoadWardBedCapacityResponse response = new LoadWardBedCapacityResponse();
        response.setWardId(wardId);
        response.setTotalBedCount(totalBedCount);
        response.setVacantBedCount(vacantBedCount);
        return response;
    }

    /**
     * 单条读模型 → 列表行（含可取消标记）。
     *
     * @param row 读模型行
     * @return 列表行 DTO
     */
    private RegistrationQueueItemDto toRegistrationQueueItemDto(
        InpatientEncounterContextQueryModel row,
        Long ambEncounterId,
        InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay overlay,
        Map<String, String> inWayLabelByCode) {
        RegistrationQueueItemDto dto = new RegistrationQueueItemDto();
        dto.setEncounterId(row.getEncounterId());
        String overlayBusNo = null;
        String overlayPatientName = null;
        String overlayStatusText = null;
        String overlayOrgName = null;
        String overlayWardName = null;
        String overlayBedName = null;
        String overlayContractDisplay = null;
        if (overlay != null) {
            overlayBusNo = overlay.getEncounterBusNo();
            overlayPatientName = overlay.getPatientName();
            overlayStatusText = overlay.getStatusEnumText();
            overlayOrgName = overlay.getOrganizationName();
            overlayWardName = overlay.getWardName();
            overlayBedName = overlay.getBedName();
            overlayContractDisplay = overlay.getContractDisplay();
        }
        dto.setEncounterBusNo(firstNonBlank(row.getEncounterBusNo(), overlayBusNo));
        dto.setPatientId(row.getPatientId());
        dto.setPatientName(firstNonBlank(row.getPatientName(), overlayPatientName));
        dto.setContractDisplay(overlayContractDisplay);
        dto.setStatusEnum(row.getStatusEnum());
        dto.setStatusEnumText(firstNonBlank(row.getStatusEnumText(), overlayStatusText));
        dto.setOrganizationId(row.getOrganizationId());
        dto.setOrganizationName(firstNonBlank(row.getOrganizationName(), overlayOrgName));
        dto.setWardId(row.getWardId());
        dto.setWardName(firstNonBlank(row.getWardName(), overlayWardName));
        dto.setBedId(row.getBedId());
        dto.setBedName(firstNonBlank(row.getBedName(), overlayBedName));
        dto.setStartTime(row.getStartTime());
        dto.setInWayCode(row.getInWayCode());
        dto.setInWayCodeText(this.resolveTerminologyLabel(inWayLabelByCode, row.getInWayCode()));
        // Asm：医生办理意向提示款台（待登记时常无合同展示）
        dto.setChargeIntent(row.getChargeIntent());
        dto.setChargeIntentText(this.resolveChargeIntentText(row.getChargeIntent()));
        dto.setAmbEncounterId(ambEncounterId);
        dto.setCancelable(this.resolveCancelable(row));
        return dto;
    }

    /**
     * 将费用性质意向 code 解析为展示文案（医保/自费）。
     *
     * @param chargeIntent {@link ChargeIntent#getCode()}；空或非法返回 null
     * @return 展示文案；无法识别时为 null
     */
    private String resolveChargeIntentText(String chargeIntent) {
        // 未指定意向：不展示文案，避免款台误读
        if (StringUtils.isEmpty(chargeIntent)) {
            return null;
        }
        ChargeIntent intent = EnumUtil.fromCode(ChargeIntent.class, chargeIntent.trim());
        // 非法存量 code：不拼假文案
        if (intent == null) {
            return null;
        }
        return intent.getDisplay();
    }

    /**
     * 术语 code → 展示文案；未命中时返回 null。
     *
     * @param labelByCode 术语标签索引
     * @param code        业务码
     * @return 展示文案或 null
     */
    private String resolveTerminologyLabel(Map<String, String> labelByCode, String code) {
        if (labelByCode == null || StringUtils.isEmpty(code)) {
            return null;
        }
        return labelByCode.get(code.trim());
    }

    /**
     * 列表行是否允许取消：待登记或待入科（REGISTERED）。
     *
     * @param row 读模型行
     * @return 可取消则为 true
     */
    private boolean resolveCancelable(InpatientEncounterContextQueryModel row) {
        String status = row.getStatusEnum();
        if (EncounterStatus.PLANNED.getCode().equals(status)) {
            return true;
        }
        if (EncounterStatus.REGISTERED.getCode().equals(status)) {
            return true;
        }
        return false;
    }

    /**
     * 入院诊断提交行 → 领域诊断项。
     *
     * @param rows 提交行
     * @return 领域项列表
     */
    private List<AdmissionConditionItem> toAdmissionConditionItems(List<AdmissionDiagnosisSubmitDto> rows) {
        List<AdmissionConditionItem> items = new ArrayList<>();
        if (rows == null) {
            return items;
        }
        for (AdmissionDiagnosisSubmitDto row : rows) {
            AdmissionConditionItem item = new AdmissionConditionItem();
            item.setEncounterDiagnosisId(this.resolveAdmissionEncounterDiagnosisId(row));
            item.setConditionDefinitionId(row.getConditionDefinitionId());
            item.setPrimaryFlag(row.getPrimaryFlag());
            item.setDescription(row.getDescription());
            item.setTcmFlag(row.getTcmFlag());
            item.setTcmSyndromeGroup(row.getTcmSyndromeGroup());
            items.add(item);
        }
        return items;
    }

    /**
     * 支付明细 DTO → 领域支付明细命令。
     *
     * @param rows      前端明细
     * @param accountId 登记账户 ID
     * @return 明细命令列表
     */
    private List<PaymentDetailItemCommand> toPaymentDetailItemCommands(
        List<PaymentDetailDto> rows,
        Long accountId) {
        List<PaymentDetailItemCommand> items = new ArrayList<>();
        if (rows == null) {
            return items;
        }
        for (PaymentDetailDto row : rows) {
            PaymentDetailItemCommand item = new PaymentDetailItemCommand();
            item.setPayEnum(row.getPayChannelCode());
            item.setAmount(row.getAmount());
            Long detailAccountId = row.getAccountId();
            if (detailAccountId == null) {
                detailAccountId = accountId;
            }
            item.setAccountId(detailAccountId);
            items.add(item);
        }
        return items;
    }

    /**
     * LocalDateTime → LocalDate（患者主档出生日用）。
     *
     * @param dateTime 日期时间
     * @return 日期；入参 null 时返回 null
     */
    private LocalDate toLocalDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.toLocalDate();
    }

    /**
     * 解析入院诊断行主键。
     *
     * @param row 提交行
     * @return 就诊诊断行主键；新建时为 null
     */
    private Long resolveAdmissionEncounterDiagnosisId(AdmissionDiagnosisSubmitDto row) {
        return row.getEncounterDiagnosisId();
    }

    /**
     * 自费转医保成功响应。
     *
     * @param ybMdtrtId               医保就诊 ID
     * @param switchedChargeItemCount 切换行数
     * @param insuranceAccountId      医保账户 ID
     * @return 响应
     */
    @Override
    public SwitchInpatientToInsuranceResponse toSwitchInpatientToInsuranceResponse(
        String ybMdtrtId,
        int switchedChargeItemCount,
        Long insuranceAccountId) {
        SwitchInpatientToInsuranceResponse response = new SwitchInpatientToInsuranceResponse();
        response.setSuccess(Boolean.TRUE);
        response.setYbMdtrtId(ybMdtrtId);
        response.setSwitchedChargeItemCount(switchedChargeItemCount);
        response.setInsuranceAccountId(insuranceAccountId);
        return response;
    }

    /**
     * 医保转自费成功响应。
     *
     * @param switchedChargeItemCount 切换到个人现金户的行数
     * @param selfPayAccountId        个人现金账户主键
     * @return 响应
     */
    @Override
    public SwitchInpatientToSelfPayResponse toSwitchInpatientToSelfPayResponse(
        int switchedChargeItemCount,
        Long selfPayAccountId) {
        SwitchInpatientToSelfPayResponse response = new SwitchInpatientToSelfPayResponse();
        response.setSuccess(Boolean.TRUE);
        response.setSwitchedChargeItemCount(switchedChargeItemCount);
        response.setSelfPayAccountId(selfPayAccountId);
        return response;
    }
}
