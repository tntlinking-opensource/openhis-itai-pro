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

package com.openhis.health.application.inpatient.financial.registration.service;

import com.openhis.health.application.common.pagination.PageQueryBinder;
import com.openhis.health.application.common.pagination.PagedResultBuilder;
import com.openhis.health.application.common.validator.CurrentOperatorResolver;
import com.openhis.health.application.common.validator.PageWindowGate;
import com.openhis.health.application.common.validator.PaymentChannelGate;
import com.openhis.health.application.inpatient.financial.registration.dto.AdmissionDiagnosisSubmitDto;
import com.openhis.health.application.inpatient.financial.registration.dto.PaymentDetailDto;
import com.openhis.health.application.inpatient.financial.registration.dto.RegistrationQueueItemDto;
import com.openhis.health.application.inpatient.financial.registration.dto.RegistrationEncounterDto;
import com.openhis.health.application.inpatient.financial.registration.dto.RegistrationPatientDto;
import com.openhis.health.application.common.assembler.inpatient.InpatientInfoQueueDisplayAssembler;
import com.openhis.health.application.inpatient.financial.registration.mapper.InpatientRegistrationMapper;
import com.openhis.health.domain.administration.account.command.AdjustAdvanceDepositBalanceCommand;
import com.openhis.health.domain.administration.account.service.AccountDomainService;
import com.openhis.health.application.inpatient.financial.registration.request.CancelRegistrationRequest;
import com.openhis.health.application.inpatient.financial.registration.request.ListWardsByDepartmentRequest;
import com.openhis.health.application.inpatient.financial.registration.request.LoadRegistrationContextRequest;
import com.openhis.health.application.inpatient.financial.registration.request.LoadWardBedCapacityRequest;
import com.openhis.health.application.inpatient.financial.registration.request.PageRegistrationQueueRequest;
import com.openhis.health.application.inpatient.financial.registration.request.SubmitRegistrationRequest;
import com.openhis.health.application.inpatient.financial.registration.response.CancelRegistrationResponse;
import com.openhis.health.application.inpatient.financial.registration.response.ListWardsByDepartmentResponse;
import com.openhis.health.application.inpatient.financial.registration.response.LoadRegistrationContextResponse;
import com.openhis.health.application.inpatient.financial.registration.response.LoadWardBedCapacityResponse;
import com.openhis.health.application.inpatient.financial.registration.response.PageRegistrationQueueResponse;
import com.openhis.health.application.inpatient.financial.registration.response.SubmitRegistrationResponse;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.encounter.command.CancelInpatientAdmissionCommand;
import com.openhis.health.domain.administration.encounter.command.RegisterInpatientEncounterCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.command.ModifyPatientProfileCommand;
import com.openhis.health.domain.administration.patient.command.RegisterPatientCommand;
import com.openhis.health.domain.administration.patient.queries.model.PatientCoreQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientDetailQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientSimpleQueryModel;
import com.openhis.health.domain.administration.patient.service.PatientDomainService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.financial.contract.queries.model.ContractDetailQueryModel;
import com.openhis.health.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.command.CollectAdvanceDepositCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.SettleInpatientPaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.constants.InpatientRegistrationConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院登记应用编排。
 *
 * <p>待办队列：按登记状态/身份证筛选在院上下文 → 加载登记上下文与病区床位容量。</p>
 * <p>正式登记：患者解析/建档 → 开立住院就诊与账户 → 可选收取预交金并结算。</p>
 * <p>取消登记：校验可取消 → 撤销住院就诊及相关收款。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpatientRegistrationAppService {

    private final PatientDomainService patientDomainService;
    private final PatientQueryService patientQueryService;
    private final ICurrentUser currentUser;
    private final CurrentOperatorResolver currentOperatorResolver;
    private final PageWindowGate pageWindowGate;
    private final PaymentChannelGate paymentChannelGate;
    private final OrganizationQueryService organizationQueryService;
    private final LocationQueryService locationQueryService;
    private final ContractQueryService contractQueryService;
    private final EncounterDomainService encounterDomainService;
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextFacade encounterContextFacade;
    private final AccountQueryService accountQueryService;
    private final AccountDomainService accountDomainService;
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;
    private final InpatientRegistrationMapper inpatientRegistrationMapper;
    private final InpatientInfoQueueDisplayAssembler inpatientInfoQueueDisplayAssembler;
    /**
     *
     * @param request 分页与筛选条件
     * @return 分页列表
     * @throws BusinessException 分页或筛选参数非法时抛出（EX-IP-REG-101）
     */
    public PageRegistrationQueueResponse pageInpatientRegistrationQueue(PageRegistrationQueueRequest request) {
        pageWindowGate.requirePositivePageWindow(
            request,
            InpatientRegistrationConstants.EX_IP_REG_101,
            "分页参数不合法，请检查页码与每页条数");
        this.validateRegistrationListStatus(request.getRegistrationListStatus());
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        PageQueryBinder.bind(request, query);
        if (StringUtils.isNotBlank(request.getRegistrationListStatus())) {
            query.setRegistrationListStatus(request.getRegistrationListStatus().trim());
        } else {
            query.setRegistrationListStatus(InpatientRegistrationConstants.DEFAULT_LIST_STATUS);
        }
        if (StringUtils.isNotBlank(request.getIdCard())) {
            PatientSimpleQueryModel matched = patientQueryService.findPatientByIdCard(request.getIdCard().trim());
            if (matched == null || matched.getId() == null) {
                return PagedResultBuilder.build(
                    new PageRegistrationQueueResponse(),
                    new ArrayList<>(),
                    0L,
                    request.getPageNo(),
                    request.getPageSize());
            }
            query.setPatientId(matched.getId());
        }
        Page<InpatientEncounterContextQueryModel> page = encounterContextFacade.pageInpatientRegQueue(query);
        Map<Long, Long> ambEncounterIdByEncounterId =
            this.loadAmbEncounterIdIndex(page.getContent());
        Map<Long, InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay> displayOverlay =
            inpatientInfoQueueDisplayAssembler.buildOverlay(page.getContent());
        List<RegistrationQueueItemDto> rows =
            inpatientRegistrationMapper.toRegistrationQueueItemDtoList(
                page.getContent(),
                ambEncounterIdByEncounterId,
                displayOverlay);
        return inpatientRegistrationMapper.toPageRegistrationQueueResponse(rows, page);
    }

    /**
     *
     * @param request 就诊 ID 与科室（可空）
     * @return 患者、就诊与费用性质下拉
     * @throws BusinessException 就诊不存在或非住院类时抛出（EX-IP-REG-102）
     */
    public LoadRegistrationContextResponse loadInpatientRegistrationContext(LoadRegistrationContextRequest request) {
        RegistrationPatientDto patientDto = null;
        RegistrationEncounterDto encounterDto = null;
        if (request.getEncounterId() != null) {
            InpatientEncounterContextQueryModel inpatientRow =
                this.mustGetInpatientContextForRegistration(request.getEncounterId());
            EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
                request.getEncounterId(),
                EncounterClass.IMP.getCode(),
                InpatientRegistrationConstants.EX_IP_REG_102,
                "就诊记录不是住院类别，无法办理住院登记");
            encounterDto = inpatientRegistrationMapper.toRegistrationEncounterDto(inpatientRow, encounter);
            PatientDetailQueryModel patientDetail =
                patientQueryService.loadPatientDetail(inpatientRow.getPatientId());
            if (patientDetail != null && patientDetail.getPatient() != null) {
                PatientCoreQueryModel core = patientDetail.getPatient();
                LocalDateTime birthDateTime = null;
                if (core.getBirthDate() != null) {
                    birthDateTime = core.getBirthDate().atStartOfDay();
                }
                patientDto = inpatientRegistrationMapper.toRegistrationPatientDto(
                    core.getId(),
                    core.getName(),
                    core.getIdCard(),
                    core.getGenderEnum(),
                    birthDateTime,
                    core.getPhone());
            }
        }
        Page<ContractListItemQueryModel> contractPage =
            contractQueryService.listContracts(request.getOrganizationId(), Pageable.unpaged());
        List<OptionDto> contractOptions =
            inpatientRegistrationMapper.toContractOptionList(contractPage.getContent());
        boolean readOnly = false;
        if (encounterDto != null && EncounterStatus.IN_PROGRESS.getCode().equals(encounterDto.getStatusEnum())) {
            readOnly = true;
        }
        return inpatientRegistrationMapper.toLoadRegistrationContextResponse(
            patientDto,
            encounterDto,
            contractOptions,
            readOnly);
    }

    /**
     *
     * @param request 登记表单
     * @return 登记结果（含可选预交金支付单）
     * @throws BusinessException 必填缺失、主数据无效、状态不允许或收款失败时抛出
     */
    public SubmitRegistrationResponse submitInpatientRegistration(SubmitRegistrationRequest request) {
        this.validateSubmitRegistrationRequest(request);
        ContractDetailQueryModel contract = contractQueryService.findContractById(request.getContractId());
        if (contract == null || StringUtils.isBlank(contract.getBusNo())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_002,
                "费用性质无效，请重新选择");
        }
        Organization organization =
            organizationQueryService.loadOrganizationByIdForDisplay(request.getOrganizationId());
        if (organization == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_002,
                "住院科室无效，请重新选择");
        }
        this.assertWardBelongsToOrganization(request.getOrganizationId(), request.getWardId());
        Long registrarId = this.resolveRegistrarId(request.getOperatorId());
        Long patientId = this.resolvePatientIdForSubmit(request);
        this.updatePatientProfileIfRequested(request, patientId);
        Long encounterId = request.getEncounterId();
        RegisterInpatientEncounterCommand registerCommand =
            inpatientRegistrationMapper.toRegisterInpatientEncounterCommand(
                request, encounterId, registrarId, contract.getBusNo());
        if (encounterId == null) {
            registerCommand.setPatientId(patientId);
            encounterId = encounterDomainService.registerDirectInpatientEncounter(registerCommand);
        } else {
            // Pre 9：有申请时登记前须仍为 PLANNED。
            EncounterDetailQueryModel encounterBeforeRegister = encounterQueryService.mustGetEncounterOfClass(
                encounterId,
                EncounterClass.IMP.getCode(),
                InpatientRegistrationConstants.EX_IP_REG_003,
                "就诊类别不是住院，不能登记");
            if (!EncounterStatus.PLANNED.getCode().equals(encounterBeforeRegister.getStatusEnum())) {
                throw new BusinessException(
                    InpatientRegistrationConstants.EX_IP_REG_003,
                    "当前就诊状态不允许登记，请刷新列表");
            }
            this.assertNoAdmissionDiagnosesOnPlannedRegistration(request);
            this.clearAdmissionDiagnosesOnRegisterCommand(registerCommand);
            encounterDomainService.registerInpatientEncounter(registerCommand);
        }
        boolean accountOpened = true;
        Long paymentId = this.collectAdvanceDepositIfRequired(request, patientId, encounterId);
        EncounterDetailQueryModel registeredEncounter = encounterQueryService.mustGetEncounterOfClass(
            encounterId,
            EncounterClass.IMP.getCode(),
            InpatientRegistrationConstants.EX_IP_REG_003,
            "就诊类别不是住院，不能登记");
        String busNo = registeredEncounter.getBusNo();
        return inpatientRegistrationMapper.toSubmitRegistrationResponse(
            encounterId, busNo, paymentId, accountOpened);
    }

    /**
     *
     * @param request 取消请求
     * @return 取消结果
     * @throws BusinessException 余额大于 0 或已分床等不允许取消时抛出
     */
    public CancelRegistrationResponse cancelInpatientRegistration(CancelRegistrationRequest request) {
        if (request.getEncounterId() == null || request.getOperatorId() == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "取消登记缺少就诊或操作人");
        }
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientRegistrationConstants.EX_IP_REG_006,
            "就诊记录不是住院类别，不能取消住院登记");
        Long patientId = encounter.getPatientId();
        Account account = accountQueryService.getRegistrationAccount(patientId, request.getEncounterId());
        long balanceSnapshot = 0L;
        if (account != null && account.getBalance() != null) {
            if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException(
                    InpatientRegistrationConstants.EX_IP_REG_005,
                    "预交金余额不为 0，请先退费后再取消登记");
            }
            balanceSnapshot = account.getBalance().longValue();
        }
        CancelInpatientAdmissionCommand cancelCommand =
            inpatientRegistrationMapper.toCancelInpatientAdmissionCommand(request, balanceSnapshot);
        try {
            encounterDomainService.cancelInpatientAdmission(cancelCommand);
        } catch (BusinessException ex) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_006,
                ex.getMessage());
        }
        return inpatientRegistrationMapper.toCancelRegistrationResponse(request.getEncounterId());
    }

    /**
     *
     * @param request 科室 ID
     * @return 病区选项
     * @throws BusinessException 科室 ID 为空时抛出
     */
    public ListWardsByDepartmentResponse listWardsByDepartment(ListWardsByDepartmentRequest request) {
        if (request.getOrganizationId() == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "住院科室不能为空");
        }
        List<Location> wards = locationQueryService.listWardsByOrganization(request.getOrganizationId());
        return inpatientRegistrationMapper.toListWardsByDepartmentResponse(wards);
    }

    /**
     *
     * @param request 病区位置 ID
     * @return 总床与空床数
     * @throws BusinessException 病区 ID 为空时抛出
     */
    public LoadWardBedCapacityResponse loadWardBedCapacity(LoadWardBedCapacityRequest request) {
        if (request.getWardId() == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "病区不能为空");
        }
        this.requireActiveWard(request.getWardId());
        Long total = locationQueryService.countTotalBedsByWard(request.getWardId());
        Long vacant = locationQueryService.countBedsByWard(request.getWardId());
        return inpatientRegistrationMapper.toLoadWardBedCapacityResponse(
            request.getWardId(), total, vacant);
    }

    /**
     * 校验登记列表状态筛选码。
     *
     * @param registrationListStatus 筛选码，可空
     * @throws BusinessException 非法筛选码
     */
    private void validateRegistrationListStatus(String registrationListStatus) {
        if (StringUtils.isBlank(registrationListStatus)) {
            return;
        }
        String code = registrationListStatus.trim();
        boolean valid = InpatientRegistrationConstants.LIST_STATUS_PLANNED.equals(code)
            || InpatientRegistrationConstants.LIST_STATUS_WAIT_ADMISSION.equals(code)
            || InpatientRegistrationConstants.LIST_STATUS_ADMITTED.equals(code);
        if (!valid) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_101,
                "登记状态筛选无效，请重新选择");
        }
    }

    /**
     * 提交登记请求门控。
     *
     * @param request 登记请求
     * @throws BusinessException 必填项缺失
     */
    private void validateSubmitRegistrationRequest(SubmitRegistrationRequest request) {
        boolean missingCore = request.getOrganizationId() == null
            || request.getWardId() == null
            || request.getStartTime() == null
            || request.getContractId() == null
            || request.getOperatorId() == null;
        if (missingCore) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "请补齐住院科室、病区、入院时间、费用性质与操作人");
        }
        if (request.getPatientId() == null && StringUtils.isBlank(request.getIdCard())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "请选择患者或填写身份证号");
        }
        if (request.getPatientId() == null && StringUtils.isBlank(request.getPatientName())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "新建患者时姓名不能为空");
        }
        if (request.getEncounterId() == null) {
            this.assertDirectRegistrationAdmissionDiagnoses(request);
        } else {
            this.assertNoAdmissionDiagnosesOnPlannedRegistration(request);
        }
    }

    /**
     * 有医生申请登记时禁止在请求体中提交入院诊断（诊断已在申请阶段写入住院线）。
     *
     * @param request 登记请求
     * @throws BusinessException 携带入院诊断列表时抛出
     */
    private void assertNoAdmissionDiagnosesOnPlannedRegistration(SubmitRegistrationRequest request) {
        List<AdmissionDiagnosisSubmitDto> rows = request.getAdmissionDiagnoses();
        if (rows == null || rows.isEmpty()) {
            return;
        }
        throw new BusinessException(
            InpatientRegistrationConstants.EX_IP_REG_003,
            "有医生申请时入院诊断以申请为准，登记不可提交或修改入院诊断");
    }

    /**
     * 直登登记须携带可编辑入院诊断且至少一条主诊断（BR-IP-REG-007）。
     *
     * @param request 登记请求
     * @throws BusinessException 列表为空或结构不合法时抛出
     */
    private void assertDirectRegistrationAdmissionDiagnoses(SubmitRegistrationRequest request) {
        List<AdmissionDiagnosisSubmitDto> rows = request.getAdmissionDiagnoses();
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "直登登记须填写入院诊断");
        }
        boolean hasPrimary = false;
        for (AdmissionDiagnosisSubmitDto row : rows) {
            if (row == null) {
                throw new BusinessException(
                    InpatientRegistrationConstants.EX_IP_REG_001,
                    "入院诊断行不能为空");
            }
            if (row.getPrimaryFlag() == null) {
                throw new BusinessException(
                    InpatientRegistrationConstants.EX_IP_REG_001,
                    "是否主诊断不能为空");
            }
            if (Boolean.TRUE.equals(row.getPrimaryFlag())) {
                hasPrimary = true;
            }
            if (row.getConditionId() == null && row.getConditionDefinitionId() == null) {
                throw new BusinessException(
                    InpatientRegistrationConstants.EX_IP_REG_001,
                    "入院诊断须指定病情或诊断目录");
            }
        }
        if (!hasPrimary) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "入院诊断须至少一条主诊断");
        }
    }

    /**
     * 有申请登记不向领域命令传递入院诊断，避免登记侧误触发写诊断。
     *
     * @param registerCommand 住院登记命令
     */
    private void clearAdmissionDiagnosesOnRegisterCommand(RegisterInpatientEncounterCommand registerCommand) {
        for (RegisterInpatientEncounterCommand.RegisterInpatientEncounterItemCommand item
            : registerCommand.resolveItems()) {
            item.setAdmissionDiagnoses(null);
        }
    }

    /**
     * 断言病区属于科室且为 WARD 形态。
     *
     * @param organizationId  科室 ID
     * @param wardId  病区位置 ID
     * @throws BusinessException 病区无效
     */
    private void assertWardBelongsToOrganization(Long organizationId, Long wardId) {
        Location ward = this.requireActiveWard(wardId);
        if (ward.getOrganizationId() == null || !ward.getOrganizationId().equals(organizationId)) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_002,
                "病区不属于所选住院科室，请重新选择");
        }
    }

    /**
     * 加载并校验启用中的病区位置。
     *
     * @param wardId 病区位置 ID
     * @return 病区实体
     * @throws BusinessException 不存在、非病区或已停用时抛出
     */
    private Location requireActiveWard(Long wardId) {
        Location ward = locationQueryService.findOptionalById(wardId).orElse(null);
        if (ward == null || Boolean.TRUE.equals(ward.getIsDeleted())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_002,
                "入院病区不存在，请重新选择");
        }
        if (!LocationForm.WARD.getCode().equals(ward.getFormEnum())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_002,
                "所选位置不是病区，请重新选择");
        }
        if (!LocationStatus.ACTIVE.getCode().equals(ward.getStatusEnum())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_002,
                "入院病区已停用，请重新选择");
        }
        return ward;
    }

    /**
     * 解析登记员主键：请求优先，否则回落当前登录用户。
     *
     * @param operatorIdFromRequest 请求中的操作人
     * @return 登记员 ID
     * @throws BusinessException 无法解析操作人时抛出
     */
    private Long resolveRegistrarId(Long operatorIdFromRequest) {
        Long registrarId = operatorIdFromRequest;
        if (registrarId == null) {
            registrarId = currentOperatorResolver.requireOperatorId(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "操作人不能为空");
        }
        return registrarId;
    }

    /**
     * 患者分流（SDD §3.4 步骤 5～6）：复用 patientId、身份证命中或新建患者。
     *
     * @param request 登记请求
     * @return 患者 ID
     * @throws BusinessException 新建患者字段不足时抛出
     */
    private Long resolvePatientIdForSubmit(SubmitRegistrationRequest request) {
        if (request.getPatientId() != null) {
            return request.getPatientId();
        }
        if (StringUtils.isNotBlank(request.getIdCard())) {
            PatientSimpleQueryModel matched =
                patientQueryService.findPatientByIdCard(request.getIdCard().trim());
            if (matched != null && matched.getId() != null) {
                return matched.getId();
            }
        }
        RegisterPatientCommand registerCommand =
            inpatientRegistrationMapper.toRegisterPatientCommand(request);
        Long orgId = request.getOrganizationId();
        if (orgId == null) {
            orgId = currentUser.getCurrentOrganizationUnitId();
        }
        if (registerCommand.getItem() != null) {
            registerCommand.getItem().setOrganizationId(orgId);
        }
        return patientDomainService.registerPatient(registerCommand);
    }

    /**
     * 同身份证更新主档（SDD §3.4 步骤 7）。
     *
     * @param request   登记请求
     * @param patientId 已解析患者 ID
     */
    private void updatePatientProfileIfRequested(SubmitRegistrationRequest request, Long patientId) {
        if (!Boolean.TRUE.equals(request.getUpdatePatientFromIdCard())) {
            return;
        }
        if (patientId == null) {
            return;
        }
        ModifyPatientProfileCommand profileCommand =
            inpatientRegistrationMapper.toModifyPatientProfileCommand(request, patientId);
        patientDomainService.modifyPatientProfile(profileCommand);
    }

    /**
     * 预交金收取与结算（SDD §3.4 步骤 12～14）；金额≤0 时跳过。
     *
     * @param request     登记请求
     * @param patientId   患者 ID
     * @param encounterId 住院就诊 ID
     * @return 支付单 ID；未收款时为 null
     * @throws BusinessException 明细缺失、账户不存在或收款失败时抛出
     */
    private Long collectAdvanceDepositIfRequired(
        SubmitRegistrationRequest request,
        Long patientId,
        Long encounterId) {
        BigDecimal advanceAmount = request.getAdvanceDepositAmount();
        if (advanceAmount == null || advanceAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        this.validateAdvanceDepositPaymentDetails(request, advanceAmount);
        Account registrationAccount =
            accountQueryService.getRegistrationAccount(patientId, encounterId);
        if (registrationAccount == null || registrationAccount.getId() == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_004,
                "登记账户不存在，无法收取预交金");
        }
        CollectAdvanceDepositCommand collectCommand =
            inpatientRegistrationMapper.toCollectAdvanceDepositCommand(
                request, patientId, encounterId, registrationAccount.getId());
        try {
            Long paymentId = paymentReconciliationDomainService.collectAdvanceDeposit(collectCommand);
            SettleInpatientPaymentCommand settleCommand =
                inpatientRegistrationMapper.toSettleInpatientPaymentCommand(paymentId);
            paymentReconciliationDomainService.settleInpatientPayment(settleCommand);
            AdjustAdvanceDepositBalanceCommand balanceCmd = new AdjustAdvanceDepositBalanceCommand();
            balanceCmd.setPatientId(patientId);
            balanceCmd.setEncounterId(encounterId);
            balanceCmd.setBalanceDelta(advanceAmount);
            balanceCmd.setOperatorId(request.getOperatorId());
            accountDomainService.adjustAdvanceDepositBalance(balanceCmd);
            return paymentId;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_004,
                "预交金收款失败，请重试");
        }
    }

    /**
     *
     * @param request       登记请求
     * @param advanceAmount 预交金主金额
     * @throws BusinessException 明细缺失或合计不等时抛 EX-IP-REG-001
     */
    private void validateAdvanceDepositPaymentDetails(
        SubmitRegistrationRequest request,
        BigDecimal advanceAmount) {
        if (advanceAmount == null || advanceAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (request.getPaymentDetails() == null || request.getPaymentDetails().isEmpty()) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "收取预交金时必须填写支付明细");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (PaymentDetailDto row : request.getPaymentDetails()) {
            if (row == null || row.getAmount() == null) {
                throw new BusinessException(
                    InpatientRegistrationConstants.EX_IP_REG_001,
                    "支付明细合计与预交金金额不一致");
            }
            if (row.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(
                    InpatientRegistrationConstants.EX_IP_REG_001,
                    "支付明细合计与预交金金额不一致");
            }
            row.setPayChannelCode(paymentChannelGate.requireUserSelectablePayChannelCode(
                row.getPayChannelCode(),
                InpatientRegistrationConstants.EX_IP_REG_001,
                "支付方式不合法"));
            sum = sum.add(row.getAmount());
        }
        BigDecimal normalizedSum = sum.setScale(2, RoundingMode.HALF_UP);
        BigDecimal normalizedMain = advanceAmount.setScale(2, RoundingMode.HALF_UP);
        if (normalizedSum.compareTo(normalizedMain) != 0) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "支付明细合计与预交金金额不一致");
        }
    }

    /**
     * 加载住院就诊上下文；不存在时统一抛 EX-IP-REG-102。
     */
    private InpatientEncounterContextQueryModel mustGetInpatientContextForRegistration(Long encounterId) {
        try {
            return encounterContextFacade.mustGetInpatientContextByEncounterId(encounterId);
        } catch (BusinessException ex) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_102,
                "住院登记信息不存在或尚未同步，请稍后重试");
        }
    }

    /**
     * 为本页住院上下文行批量加载源门诊就诊 ID 索引。
     */
    private Map<Long, Long> loadAmbEncounterIdIndex(List<InpatientEncounterContextQueryModel> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Set<Long> encounterIds = new LinkedHashSet<>();
        for (InpatientEncounterContextQueryModel row : rows) {
            if (row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        if (encounterIds.isEmpty()) {
            return Map.of();
        }
        return encounterQueryService.mapAmbEncounterIdByEncounterIds(encounterIds);
    }

}
