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

package com.openhis.health.application.inpatient.financial.registration.mapper;

import com.openhis.health.application.common.assembler.inpatient.AdmissionPrimaryDiagnosisAssembler;
import com.openhis.health.application.common.assembler.inpatient.InpatientInfoQueueDisplayAssembler;
import com.openhis.health.application.common.pagination.PagedResultBuilder;
import com.openhis.health.application.inpatient.financial.registration.dto.AdmissionDiagnosisSubmitDto;
import com.openhis.health.application.inpatient.financial.registration.dto.PaymentDetailDto;
import com.openhis.health.application.inpatient.financial.registration.dto.RegistrationEncounterDto;
import com.openhis.health.application.inpatient.financial.registration.dto.RegistrationPatientDto;
import com.openhis.health.application.inpatient.financial.registration.dto.RegistrationQueueItemDto;
import com.openhis.health.application.inpatient.financial.registration.dto.WardOptionDto;
import com.openhis.health.application.inpatient.financial.registration.request.CancelRegistrationRequest;
import com.openhis.health.application.inpatient.financial.registration.request.SubmitRegistrationRequest;
import com.openhis.health.application.inpatient.financial.registration.response.CancelRegistrationResponse;
import com.openhis.health.application.inpatient.financial.registration.response.ListWardsByDepartmentResponse;
import com.openhis.health.application.inpatient.financial.registration.response.LoadRegistrationContextResponse;
import com.openhis.health.application.inpatient.financial.registration.response.LoadWardBedCapacityResponse;
import com.openhis.health.application.inpatient.financial.registration.response.PageRegistrationQueueResponse;
import com.openhis.health.application.inpatient.financial.registration.response.SubmitRegistrationResponse;
import com.openhis.health.domain.administration.encounter.command.AdmissionConditionItem;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.openhis.health.domain.administration.encounter.command.CancelInpatientAdmissionCommand;
import com.openhis.health.domain.administration.encounter.command.RegisterInpatientEncounterCommand;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.patient.command.ModifyPatientProfileCommand;
import com.openhis.health.domain.administration.patient.command.RegisterPatientCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CollectAdvanceDepositCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.SettleInpatientPaymentCommand;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.whale.core.application.dto.OptionDto;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

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
public class InpatientRegistrationMapperImpl implements InpatientRegistrationMapper {

    private final AdmissionPrimaryDiagnosisAssembler admissionPrimaryDiagnosisAssembler;

    public InpatientRegistrationMapperImpl(AdmissionPrimaryDiagnosisAssembler admissionPrimaryDiagnosisAssembler) {
        this.admissionPrimaryDiagnosisAssembler = admissionPrimaryDiagnosisAssembler;
    }

    /**
     * 读模型列表 → 待办行 DTO 列表。
     *
     * @param rows 读模型行；调用方保证非 null
     * @param ambEncounterIdByEncounterId 住院就诊 ID → 源门诊就诊 ID；可空
     * @return 列表行 DTO
     */
    @Override
    public List<RegistrationQueueItemDto> toRegistrationQueueItemDtoList(
        List<InpatientEncounterContextQueryModel> rows,
        Map<Long, Long> ambEncounterIdByEncounterId,
        Map<Long, InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay> displayOverlayByEncounterId) {
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
            list.add(this.toRegistrationQueueItemDto(row, ambEncounterId, overlay));
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
     * @param contractOptions 费用性质下拉
     * @param readOnly        是否只读查看
     * @return 上下文响应
     */
    @Override
    public LoadRegistrationContextResponse toLoadRegistrationContextResponse(
        RegistrationPatientDto patient,
        RegistrationEncounterDto encounter,
        List<OptionDto> contractOptions,
        boolean readOnly) {
        LoadRegistrationContextResponse response = new LoadRegistrationContextResponse();
        response.setPatient(patient);
        response.setEncounter(encounter);
        response.setContractOptions(contractOptions);
        response.setReadOnly(readOnly);
        return response;
    }

    /**
     * 患者详情 → 登记患者 DTO。
     *
     * @param patientId  患者 ID
     * @param name       姓名
     * @param idCard     身份证
     * @param genderEnum 性别 code
     * @param birthDate  出生日期
     * @param phone      电话
     * @return 患者 DTO；patientId 为空时返回 null
     */
    @Override
    public RegistrationPatientDto toRegistrationPatientDto(
        Long patientId,
        String name,
        String idCard,
        String genderEnum,
        LocalDateTime birthDate,
        String phone) {
        if (patientId == null) {
            return null;
        }
        RegistrationPatientDto dto = new RegistrationPatientDto();
        dto.setPatientId(patientId);
        dto.setName(name);
        dto.setIdCard(idCard);
        dto.setGenderEnum(genderEnum);
        dto.setBirthDate(birthDate);
        dto.setPhone(phone);
        return dto;
    }

    /**
     * 住院读模型 → 登记就诊 DTO。
     *
     * @param row            读模型；调用方保证非 null
     * @param ambEncounterId 源门诊就诊 ID，无申请直登时为 null
     * @return 就诊 DTO
     */
    @Override
    public RegistrationEncounterDto toRegistrationEncounterDto(
        InpatientEncounterContextQueryModel row,
        EncounterDetailQueryModel encounterHead) {
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
        dto.setInWayCode(row.getInWayCode());
        dto.setStartTime(row.getStartTime());
        AdmissionPrimaryDiagnosisAssembler.PrimaryDiagnosisDisplay primaryDiagnosis =
            admissionPrimaryDiagnosisAssembler.resolvePrimaryDiagnosis(row.getDiagnosisJson());
        dto.setPrimaryConditionDefinitionId(primaryDiagnosis.getPrimaryConditionDefinitionId());
        dto.setPrimaryConditionDefinitionName(primaryDiagnosis.getPrimaryConditionDefinitionName());
        dto.setAdmitSourceCode(null);
        dto.setPriorityEnum(null);
        dto.setAmbEncounterId(null);
        if (encounterHead != null) {
            if (StringUtils.isNotBlank(encounterHead.getBusNo())) {
                dto.setEncounterBusNo(encounterHead.getBusNo());
            }
            dto.setAdmitSourceCode(encounterHead.getAdmitSourceCode());
            dto.setPriorityEnum(encounterHead.getPriorityEnum());
            dto.setAmbEncounterId(encounterHead.getAmbEncounterId());
            if (encounterHead.getStartTime() != null) {
                dto.setStartTime(encounterHead.getStartTime());
            }
        }
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
        item.setOrganizationId(request.getOrganizationId());
        item.setTempFlag(Boolean.FALSE);
        command.setItem(item);
        return command;
    }

    /**
     * 提交请求 → 修改患者档案命令。
     *
     * @param request   登记请求
     * @param patientId 既有患者 ID
     * @return 修改命令
     */
    @Override
    public ModifyPatientProfileCommand toModifyPatientProfileCommand(
        SubmitRegistrationRequest request,
        Long patientId) {
        if (request == null || patientId == null) {
            return null;
        }
        ModifyPatientProfileCommand command = new ModifyPatientProfileCommand();
        ModifyPatientProfileCommand.ModifyPatientProfileItemCommand item =
            new ModifyPatientProfileCommand.ModifyPatientProfileItemCommand();
        item.setPatientId(patientId);
        item.setName(request.getPatientName());
        item.setGenderEnum(request.getGenderEnum());
        item.setIdCard(request.getIdCard());
        item.setPhone(request.getPhone());
        item.setBirthDate(this.toLocalDate(request.getBirthDate()));
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
        item.setAdmitSourceCode(request.getAdmitSourceCode());
        item.setInWayCode(request.getInWayCode());
        item.setPriorityCode(request.getPriorityEnum());
        item.setAdmissionDiagnoses(this.toAdmissionConditionItems(request.getAdmissionDiagnoses()));
        command.setItem(item);
        return command;
    }

    /**
     * 预交金收取命令。
     *
     * @param request     登记请求
     * @param patientId   患者 ID
     * @param encounterId 就诊 ID
     * @param accountId   登记账户 ID
     * @return 收取命令
     */
    @Override
    public CollectAdvanceDepositCommand toCollectAdvanceDepositCommand(
        SubmitRegistrationRequest request,
        Long patientId,
        Long encounterId,
        Long accountId) {
        if (request == null) {
            return null;
        }
        CollectAdvanceDepositCommand command = new CollectAdvanceDepositCommand();
        command.setEncounterId(encounterId);
        command.setPatientId(patientId);
        command.setEntererId(request.getOperatorId());
        command.setTenderedAmount(request.getAdvanceDepositAmount());
        command.setDisplayAmount(request.getAdvanceDepositAmount());
        command.setKindEnum(PaymentKind.HOSPITAL_DEPOSIT);
        String paymentEnum = null;
        if (request.getPaymentDetails() != null && !request.getPaymentDetails().isEmpty()) {
            paymentEnum = request.getPaymentDetails().get(0).getPayChannelCode();
        }
        command.setPaymentEnum(paymentEnum);
        command.setPaymentDetails(this.toPaymentDetailItemCommands(request.getPaymentDetails(), accountId));
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
        command.setYbSettleIds(null);
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
        boolean accountOpened) {
        SubmitRegistrationResponse response = new SubmitRegistrationResponse();
        response.setEncounterId(encounterId);
        response.setEncounterBusNo(busNo);
        response.setStatusEnum(EncounterStatus.REGISTERED.getCode());
        response.setPaymentId(paymentId);
        response.setAccountOpened(accountOpened);
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
        InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay overlay) {
        RegistrationQueueItemDto dto = new RegistrationQueueItemDto();
        dto.setEncounterId(row.getEncounterId());
        String overlayBusNo = null;
        String overlayPatientName = null;
        String overlayStatusText = null;
        String overlayOrgName = null;
        String overlayWardName = null;
        String overlayBedName = null;
        if (overlay != null) {
            overlayBusNo = overlay.getEncounterBusNo();
            overlayPatientName = overlay.getPatientName();
            overlayStatusText = overlay.getStatusEnumText();
            overlayOrgName = overlay.getOrganizationName();
            overlayWardName = overlay.getWardName();
            overlayBedName = overlay.getBedName();
        }
        dto.setEncounterBusNo(this.firstNonBlank(row.getEncounterBusNo(), overlayBusNo));
        dto.setPatientId(row.getPatientId());
        dto.setPatientName(this.firstNonBlank(row.getPatientName(), overlayPatientName));
        dto.setStatusEnum(row.getStatusEnum());
        dto.setStatusEnumText(this.firstNonBlank(row.getStatusEnumText(), overlayStatusText));
        dto.setOrganizationId(row.getOrganizationId());
        dto.setOrganizationName(this.firstNonBlank(row.getOrganizationName(), overlayOrgName));
        dto.setWardId(row.getWardId());
        dto.setWardName(this.firstNonBlank(row.getWardName(), overlayWardName));
        dto.setBedId(row.getBedId());
        dto.setBedName(this.firstNonBlank(row.getBedName(), overlayBedName));
        dto.setStartTime(row.getStartTime());
        dto.setInWayCode(row.getInWayCode());
        dto.setAmbEncounterId(ambEncounterId);
        dto.setCancelable(this.resolveCancelable(row));
        return dto;
    }

    /**
     * 读模型字段优先，空白时回落展示覆盖层。
     *
     * @param fromRow    读模型原值
     * @param fromOverlay 覆盖层回填
     * @return 非空白字符串或 null
     */
    private String firstNonBlank(String fromRow, String fromOverlay) {
        if (StringUtils.isNotBlank(fromRow)) {
            return fromRow;
        }
        if (StringUtils.isNotBlank(fromOverlay)) {
            return fromOverlay;
        }
        return null;
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
            item.setConditionId(row.getConditionId());
            item.setConditionDefinitionId(row.getConditionDefinitionId());
            item.setPrimaryFlag(row.getPrimaryFlag());
            item.setDescription(row.getDescription());
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
}
