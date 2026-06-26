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

package com.openhis.health.domain.workflow.servicerequest.service;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.events.workflow.servicerequest.AutoRollServiceRequestCreatedEvent;
import com.openhis.health.domain.share.events.workflow.servicerequest.DischargeRequestCreatedEvent;
import com.openhis.health.domain.share.events.workflow.servicerequest.NursePricingServiceRequestCreatedEvent;
import com.openhis.health.domain.share.events.workflow.servicerequest.NursePricingServiceRequestRemovedEvent;
import com.openhis.health.domain.share.events.workflow.servicerequest.NursingOrderUpsertedEvent;
import com.openhis.health.domain.share.events.workflow.servicerequest.OutpatientRegistrationServiceRequestCreatedEvent;
import com.openhis.health.domain.share.events.workflow.servicerequest.ServiceRequestCancelledEvent;
import com.openhis.health.domain.share.events.workflow.servicerequest.ServiceRequestDraftCreatedEvent;
import com.openhis.health.domain.share.events.workflow.servicerequest.ServiceRequestDraftEditedEvent;
import com.openhis.health.domain.share.events.workflow.servicerequest.ServiceRequestDraftRemovedEvent;
import com.openhis.health.domain.share.events.workflow.servicerequest.ServiceRequestExecutedCreatedEvent;
import com.openhis.health.domain.share.events.workflow.servicerequest.ServiceRequestExecutedRemovedEvent;
import com.openhis.health.domain.share.events.workflow.servicerequest.ServiceRequestRejectedToDraftEvent;
import com.openhis.health.domain.share.events.workflow.servicerequest.ServiceRequestSignedEvent;
import com.openhis.health.domain.share.events.workflow.servicerequest.ServiceRequestStopCancelledEvent;
import com.openhis.health.domain.share.events.workflow.servicerequest.ServiceRequestStoppedEvent;
import com.openhis.health.domain.share.events.workflow.servicerequest.ServiceRequestVerifiedEvent;
import com.openhis.health.domain.share.events.workflow.servicerequest.ServiceRequestWithdrawnEvent;
import com.openhis.health.domain.share.events.workflow.servicerequest.TransferDepartmentRequestCreatedEvent;
import com.openhis.health.domain.workflow.servicerequest.command.CancelServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.CloneServiceRequestForPreservedChargeRegenerationCommand;
import com.openhis.health.domain.workflow.servicerequest.command.CreateDischargeRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.CreateExecutedServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.CreateFromAutoRollCommand;
import com.openhis.health.domain.workflow.servicerequest.command.CreateFromNursePricingCommand;
import com.openhis.health.domain.workflow.servicerequest.command.CreateFromOutpatientRegistrationCommand;
import com.openhis.health.domain.workflow.servicerequest.command.CreateServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.CreateTransferDepartmentRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.IncreasePrintCountCommand;
import com.openhis.health.domain.workflow.servicerequest.command.ModifyServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.RejectServiceRequestToDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.RemoveExecutedServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.RemoveFromNursePricingCommand;
import com.openhis.health.domain.workflow.servicerequest.command.RemoveServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.SignServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.CancelStopServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.StopServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.UpsertNursingOrderCommand;
import com.openhis.health.domain.workflow.servicerequest.command.VerifyServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.WithdrawSignedServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequest;
import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequestDetail;
import com.openhis.health.domain.workflow.servicerequest.repository.ServiceRequestDetailRepository;
import com.openhis.health.domain.workflow.servicerequest.repository.ServiceRequestRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import org.apache.commons.lang3.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 服务申请写侧领域服务（非药品医嘱）。
 *
 * <p>覆盖草稿维护、签发/校对/停嘱/取消、住院转科与出院申请、护理医嘱、执行回写建单及门诊挂号/护士划价来源建单等。</p>
 */
@Service
@RequiredArgsConstructor
public class ServiceRequestDomainService {

    private final ServiceRequestRepository serviceRequestRepository;
    private final ServiceRequestDetailRepository serviceRequestDetailRepository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 服务申请 busNo：业务流水按日递增，前缀 {@link AggregateTypeConstants#SERVICE_REQUEST}。 */
    private final BusinessSequenceService businessSequenceService;

    /**
     * 创建服务申请草稿：分配 busNo，可选持久化明细行，发布草稿创建事件。
     *
     * @param command 批次内一条头记录 + 明细项
     * @return 新申请主键
     * @throws BusinessException 批次条数不为 1 或不变式失败时
     */
    public Long createServiceRequestDraft(CreateServiceRequestDraftCommand command) {
        List<CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "创建服务申请草稿一次仅允许提交一条记录");
        }

        CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand item = items.get(0);
        ServiceRequest request = new ServiceRequest();
        request.setEncounterId(item.getEncounterId());
        request.setPatientId(item.getPatientId());
        request.setActivityId(item.getActivityId());
        request.setQuantity(item.getQuantity());
        request.setUnitCode(item.getUnitCode());
        request.setExecutionCount(item.getExecutionCount());
        request.setCategoryEnum(item.getCategoryEnum());
        request.setTherapyEnum(item.getTherapyEnum());
        request.setRateCode(this.resolveRateCodeForTherapy(item.getTherapyEnum(), item.getRateCode()));
        // 开嘱人以后端当前操作人为兜底，避免调用方漏传 requesterId 导致列表无开嘱人。
        if (item.getRequesterId() != null) {
            request.setRequesterId(item.getRequesterId());
        } else {
            request.setRequesterId(command.getOperatorId());
        }
        request.setRemark(item.getNote());
        // Pre：诊疗草稿须落库生成来源；医生站未传时回落医生开立。
        String generateSourceCode = item.getGenerateSourceEnum();
        if (generateSourceCode == null) {
            generateSourceCode = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        }
        request.setGenerateSourceEnum(generateSourceCode);
        if (item.getSortNumber() != null) {
            request.setSortNumber(item.getSortNumber());
        }
        // 医生站：可选绑定诊断定义，与药品/耗材医嘱口径一致（单列 condition_definition_id）。
        if (item.getConditionDefinitionId() != null) {
            request.setConditionDefinitionId(item.getConditionDefinitionId());
        }
        // Pre：绑定就诊诊断行，供收费/医保按 encounter_diagnosis_id 解析医疗类别。
        if (item.getEncounterDiagnosisId() != null) {
            request.setEncounterDiagnosisId(item.getEncounterDiagnosisId());
        }
        if (item.getOrgId() != null) {
            request.setOrgId(item.getOrgId());
        }
        if (item.getLocationId() != null) {
            request.setLocationId(item.getLocationId());
        }
        if (item.getContractId() != null) {
            request.setContractId(item.getContractId());
        }
        if (StringUtils.isNotBlank(item.getPrescriptionNo())) {
            request.setPrescriptionNo(item.getPrescriptionNo().trim());
        }
        // Pre：皮试 companion 等派生场景须落库 based_on，供护士皮试 Tab 反查皮试药。
        if (StringUtils.isNotBlank(item.getBasedOnTable()) && item.getBasedOnId() != null) {
            request.setBasedOnTable(item.getBasedOnTable().trim());
            request.setBasedOnId(item.getBasedOnId());
        }
        request.initDraft();
        request.setIsDeleted(Boolean.FALSE);
        request.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.SERVICE_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        request.assertDraftCreatable();

        ServiceRequest saved = serviceRequestRepository.save(request);

        List<ServiceRequestDetail> details = this.buildDetails(saved.getId(), item.getDetailItems());
        // 业务分支处理。
        if (!details.isEmpty()) {
            serviceRequestDetailRepository.saveAll(details);
        }

        domainEventPublisher.publish(new ServiceRequestDraftCreatedEvent(
            saved.getId(), saved.getEncounterId(), saved.getPatientId()));
        return saved.getId();
    }

    /**
     * 修改草稿头字段并可全量替换明细（先软删旧明细）；有字段变更时发布编辑事件。
     *
     * @param command 批次内一条，须含申请主键
     * @throws BusinessException 非草稿可编辑或参数非法时
     */
    public void modifyServiceRequestDraft(ModifyServiceRequestDraftCommand command) {
        List<ModifyServiceRequestDraftCommand.ModifyServiceRequestDraftItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "修改服务申请草稿一次仅允许提交一条记录");
        }

        ModifyServiceRequestDraftCommand.ModifyServiceRequestDraftItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "修改服务申请草稿时 id 不能为空");
        }

        ServiceRequest request = this.requireServiceRequest(item.getId());
        request.assertDraftEditable();

        List<String> changedFields = new ArrayList<>();

        // 业务分支处理。
        if (item.getQuantity() != null) {
            if (request.getQuantity() == null || !item.getQuantity().equals(request.getQuantity())) {
                changedFields.add(FieldNameConstants.QUANTITY);
            }
            request.setQuantity(item.getQuantity());
        }
        // 业务分支处理。
        if (item.getUnitCode() != null) {
            if (!item.getUnitCode().equals(request.getUnitCode())) {
                changedFields.add(FieldNameConstants.UNIT_CODE);
            }
            request.setUnitCode(item.getUnitCode());
        }
        // 业务分支处理：临嘱可更新计划执行次数；长嘱传空不覆盖。
        if (item.getExecutionCount() != null) {
            if (!item.getExecutionCount().equals(request.getExecutionCount())) {
                changedFields.add(FieldNameConstants.EXECUTION_COUNT);
            }
            request.setExecutionCount(item.getExecutionCount());
        }
        // 业务分支处理。
        if (item.getCategoryEnum() != null) {
            if (!item.getCategoryEnum().equals(request.getCategoryEnum())) {
                changedFields.add(FieldNameConstants.CATEGORY_ENUM);
            }
            request.setCategoryEnum(item.getCategoryEnum());
        }
        // 业务分支处理。
        if (item.getTherapyEnum() != null) {
            if (!item.getTherapyEnum().equals(request.getTherapyEnum())) {
                changedFields.add(FieldNameConstants.THERAPY_ENUM);
            }
            request.setTherapyEnum(item.getTherapyEnum());
        }
        if (item.getRateCode() != null) {
            String therapyForRate = item.getTherapyEnum() != null ? item.getTherapyEnum() : request.getTherapyEnum();
            String normalizedRate = this.resolveRateCodeForTherapy(therapyForRate, item.getRateCode());
            if (!Objects.equals(normalizedRate, request.getRateCode())) {
                changedFields.add(FieldNameConstants.RATE_CODE);
            }
            request.setRateCode(normalizedRate);
        }
        // 业务分支处理。
        if (item.getRequesterId() != null) {
            if (!item.getRequesterId().equals(request.getRequesterId())) {
                changedFields.add(FieldNameConstants.REQUESTER_ID);
            }
            request.setRequesterId(item.getRequesterId());
        }
        // 业务分支处理。
        if (item.getReasonText() != null) {
            if (!item.getReasonText().equals(request.getReasonText())) {
                changedFields.add(FieldNameConstants.REASON_TEXT);
            }
            request.setReasonText(item.getReasonText());
        }
        // 业务分支处理。
        if (item.getPerformerId() != null) {
            if (!item.getPerformerId().equals(request.getPerformerId())) {
                changedFields.add(FieldNameConstants.PERFORMER_ID);
            }
            request.setPerformerId(item.getPerformerId());
        }
        // 业务分支处理。
        if (item.getLocationId() != null) {
            if (!item.getLocationId().equals(request.getLocationId())) {
                changedFields.add(FieldNameConstants.LOCATION_ID);
            }
            request.setLocationId(item.getLocationId());
        }
        // 业务分支处理。
        if (item.getOrgId() != null) {
            if (!item.getOrgId().equals(request.getOrgId())) {
                changedFields.add(FieldNameConstants.ORG_ID);
            }
            request.setOrgId(item.getOrgId());
        }
        // 业务分支处理。
        if (item.getRemark() != null) {
            if (!item.getRemark().equals(request.getRemark())) {
                changedFields.add(FieldNameConstants.REMARK);
            }
            request.setRemark(item.getRemark());
        }
        // 业务分支处理。
        if (item.getSortNumber() != null && !Objects.equals(item.getSortNumber(), request.getSortNumber())) {
            changedFields.add(FieldNameConstants.SORT_NUMBER);
            request.setSortNumber(item.getSortNumber());
        }
        // 业务分支处理。
        if (item.getEntrustContent() != null) {
            if (!item.getEntrustContent().equals(request.getEntrustContent())) {
                changedFields.add(FieldNameConstants.ENTRUST_CONTENT);
            }
            request.setEntrustContent(item.getEntrustContent());
        }
        // 业务分支处理。
        if (item.getOccurrenceStartTime() != null) {
            if (!item.getOccurrenceStartTime().equals(request.getOccurrenceStartTime())) {
                changedFields.add(FieldNameConstants.OCCURRENCE_START_TIME);
            }
            request.setOccurrenceStartTime(item.getOccurrenceStartTime());
        }
        // 业务分支处理。
        if (item.getOccurrenceEndTime() != null) {
            if (!item.getOccurrenceEndTime().equals(request.getOccurrenceEndTime())) {
                changedFields.add(FieldNameConstants.OCCURRENCE_END_TIME);
            }
            request.setOccurrenceEndTime(item.getOccurrenceEndTime());
        }
        // 业务分支处理：诊断定义绑定与药品医嘱一致，仅当命令显式传入且值变化时落库并记入变更摘要。
        if (item.getConditionDefinitionId() != null
            && !Objects.equals(item.getConditionDefinitionId(), request.getConditionDefinitionId())) {
            request.setConditionDefinitionId(item.getConditionDefinitionId());
            changedFields.add(FieldNameConstants.CONDITION_DEFINITION_ID);
        }
        // 业务分支处理：就诊诊断绑定与药品医嘱一致，仅当命令显式传入且值变化时落库并记入变更摘要。
        if (item.getEncounterDiagnosisId() != null
            && !Objects.equals(item.getEncounterDiagnosisId(), request.getEncounterDiagnosisId())) {
            request.setEncounterDiagnosisId(item.getEncounterDiagnosisId());
            changedFields.add(FieldNameConstants.ENCOUNTER_DIAGNOSIS_ID);
        }

        request.assertDraftCreatable();

        serviceRequestRepository.save(request);

        // 业务分支处理。
        if (item.getDetailItems() != null) {
            List<ServiceRequestDetail> existed = serviceRequestDetailRepository.findByServiceReqId(request.getId());
            for (ServiceRequestDetail detail : existed) {
                detail.setIsDeleted(Boolean.TRUE);
            }
            // 业务分支处理。
            if (!existed.isEmpty()) {
                serviceRequestDetailRepository.saveAll(existed);
            }
            List<ServiceRequestDetail> details = this.buildDetails(request.getId(), item.getDetailItems());
            // 业务分支处理。
            if (!details.isEmpty()) {
                serviceRequestDetailRepository.saveAll(details);
            }
        }

        domainEventPublisher.publish(new ServiceRequestDraftEditedEvent(
            request.getId(), String.join(",", changedFields)));
    }

    /**
     * 软删草稿主档及全部明细并发布删除事件。
     *
     * @param command 批次内一条，须含申请主键
     * @throws BusinessException 不可删除或不存在时
     */
    public void removeServiceRequestDraft(RemoveServiceRequestDraftCommand command) {
        List<RemoveServiceRequestDraftCommand.RemoveServiceRequestDraftItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "删除服务申请草稿一次仅允许提交一条记录");
        }
        RemoveServiceRequestDraftCommand.RemoveServiceRequestDraftItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "删除服务申请草稿时 id 不能为空");
        }

        ServiceRequest request = this.requireServiceRequest(item.getId());
        request.assertDraftRemovable();

        // BR-SRQ-004：主档与明细同事务清理。此处采用软删主档与明细。
        request.setIsDeleted(Boolean.TRUE);
        List<ServiceRequestDetail> details = serviceRequestDetailRepository.findByServiceReqId(request.getId());
        for (ServiceRequestDetail detail : details) {
            detail.setIsDeleted(Boolean.TRUE);
        }

        serviceRequestRepository.save(request);
        // 业务分支处理。
        if (!details.isEmpty()) {
            serviceRequestDetailRepository.saveAll(details);
        }

        domainEventPublisher.publish(new ServiceRequestDraftRemovedEvent(request.getId(), request.getEncounterId()));
    }

    /**
     * 签发：草稿转生效，写入签发信息与时间，发布签发事件。
     *
     * @param command 批次内一条，须含 id、signerId 等
     * @throws BusinessException 不可签发或参数缺失时
     */
    public void signServiceRequest(SignServiceRequestCommand command) {
        List<SignServiceRequestCommand.SignServiceRequestItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "签发服务申请一次仅允许提交一条记录");
        }

        SignServiceRequestCommand.SignServiceRequestItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "签发服务申请时 id 不能为空");
        }
        // 业务分支处理。
        if (item.getSignerId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "签发服务申请时 signerId 不能为空");
        }

        ServiceRequest request = this.requireServiceRequest(item.getId());
        request.assertSignable();

        // 签发时间未传则默认为当前系统时间。
        if (item.getSignTime() == null) {
            request.setAuthoredTime(LocalDateTime.now());
        } else {
            request.setAuthoredTime(item.getSignTime());
        }
        request.draftToActive();
        serviceRequestRepository.save(request);

        // Pub：载荷携带 save 后 status_enum，供 OrderInfo 投影 ACTIVE 供护士站待校对列表查询。
        // BR-SRQ-013：草稿保存不得触发跨系统发送；签发事件作为后置编排锚点。
        domainEventPublisher.publish(new ServiceRequestSignedEvent(
            request.getId(), request.getCategoryEnum(), request.getStatusEnum()));
    }

    /**
     * 签退：生效申请退回草稿，发布撤回事件。
     *
     * @param command 批次内一条，须含 id
     * @throws BusinessException 不可撤回时
     */
    public void withdrawSignedServiceRequest(WithdrawSignedServiceRequestCommand command) {
        List<WithdrawSignedServiceRequestCommand.WithdrawSignedServiceRequestItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "签退服务申请一次仅允许提交一条记录");
        }
        WithdrawSignedServiceRequestCommand.WithdrawSignedServiceRequestItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "签退服务申请时 id 不能为空");
        }

        ServiceRequest request = this.requireServiceRequest(item.getId());
        request.assertWithdrawable();

        request.activeToDraft();
        serviceRequestRepository.save(request);
        domainEventPublisher.publish(new ServiceRequestWithdrawnEvent(request.getId()));
    }

    /**
     * 校对核销：生效转已校对（VERIFIED），记录核对人与时间，发布校对事件。
     *
     * @param command 批次内一条，须含 id 与 performerCheckId
     * @throws BusinessException 不可校对时
     */
    public void verifyServiceRequest(VerifyServiceRequestCommand command) {
        List<VerifyServiceRequestCommand.VerifyServiceRequestItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "校对服务申请一次仅允许提交一条记录");
        }
        VerifyServiceRequestCommand.VerifyServiceRequestItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "校对服务申请时 id 不能为空");
        }
        // 业务分支处理。
        if (item.getPerformerCheckId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "校对服务申请时 performerCheckId 不能为空");
        }

        ServiceRequest request = this.requireServiceRequest(item.getId());
        request.assertVerifiable();

        request.setPerformerCheckId(item.getPerformerCheckId());
        // 核销时间未传则默认为当前系统时间。
        if (item.getCheckTime() == null) {
            request.setCheckTime(LocalDateTime.now());
        } else {
            request.setCheckTime(item.getCheckTime());
        }
        request.activeToVerified();
        serviceRequestRepository.save(request);
        domainEventPublisher.publish(new ServiceRequestVerifiedEvent(
            request.getId(), request.getPerformerCheckId(), request.getStatusEnum()));
    }

    /**
     * 门诊收费完成：已发送 → 已完成（COMPLETED），核对人记为收银操作人；与住院校对 VERIFIED 分离。
     *
     * @param serviceRequestId 服务申请主键
     * @param operatorId       收费操作人，写入 performerCheckId
     */
    public void completeServiceRequestAfterOutpatientCharge(Long serviceRequestId, Long operatorId) {
        ServiceRequest request = this.requireServiceRequest(serviceRequestId);
        if (RequestStatus.COMPLETED.getCode().equals(request.getStatusEnum())) {
            return;
        }
        request.ensureCompletableAfterOutpatientCharge();
        request.setPerformerCheckId(operatorId);
        request.setCheckTime(LocalDateTime.now());
        request.activeToCompleted();
        serviceRequestRepository.save(request);
        domainEventPublisher.publish(new ServiceRequestVerifiedEvent(
            request.getId(), request.getPerformerCheckId(), request.getStatusEnum()));
    }

    /**
     * 退回草稿：由生效或完成态回到草稿，发布退回事件。
     *
     * @param command 批次内一条，须含 id
     * @throws BusinessException 不可退回时
     */
    public void rejectServiceRequestToDraft(RejectServiceRequestToDraftCommand command) {
        List<RejectServiceRequestToDraftCommand.RejectServiceRequestToDraftItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "退回草稿一次仅允许提交一条记录");
        }
        RejectServiceRequestToDraftCommand.RejectServiceRequestToDraftItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "退回草稿时 id 不能为空");
        }

        ServiceRequest request = this.requireServiceRequest(item.getId());
        request.assertRejectable();
        if (command.getPerformerCheckId() != null) {
            request.setPerformerCheckId(command.getPerformerCheckId());
        }
        if (command.getCheckTime() != null) {
            request.setCheckTime(command.getCheckTime());
        } else {
            request.setCheckTime(LocalDateTime.now());
        }
        request.activeOrCompletedToDraft();
        serviceRequestRepository.save(request);
        domainEventPublisher.publish(new ServiceRequestRejectedToDraftEvent(request.getId(), request.getStatusEnum()));
    }

    /**
     * 停嘱：写入结束时间并置为已停止，发布停止事件。
     *
     * @param command 批次内一条，须含 id；stopTime 可空则取当前时间
     * @throws BusinessException 不可停嘱时
     */
    public void stopServiceRequest(StopServiceRequestCommand command) {
        List<StopServiceRequestCommand.StopServiceRequestItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "停嘱一次仅允许提交一条记录");
        }
        StopServiceRequestCommand.StopServiceRequestItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "停嘱时 id 不能为空");
        }

        ServiceRequest request = this.requireServiceRequest(item.getId());
        request.assertStoppable();

        // 停止时间未传则默认为当前系统时间。
        LocalDateTime stopTime;
        if (item.getStopTime() == null) {
            stopTime = LocalDateTime.now();
        } else {
            stopTime = item.getStopTime();
        }
        request.setOccurrenceEndTime(stopTime);
        request.toStopped();
        serviceRequestRepository.save(request);
        domainEventPublisher.publish(new ServiceRequestStoppedEvent(request.getId(), stopTime, request.getStatusEnum()));
    }

    /**
     * 取消停嘱：恢复长期诊疗申请为已校对或已发送，并清空结束时间（IF-IP-DOR-25 / BR-SRQ-016a）。
     *
     * @param command 取消停嘱命令，须含 id
     * @throws BusinessException 命令为空、非长期或非 STOPPED 时
     */
    public void cancelStopServiceRequest(CancelStopServiceRequestCommand command) {
        if (command == null || command.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "取消停嘱时 id 不能为空");
        }
        // 加载：按主键取诊疗申请聚合根。
        ServiceRequest request = this.requireServiceRequest(command.getId());
        // 前置校验：须为长期且当前 STOPPED。
        request.assertCancelStopAllowed();
        // 状态迁移：performer_check_id 非空→VERIFIED，否则→ACTIVE，并清空 occurrence_end_time。
        request.fromStopped();
        // 持久化：先落库再发事件，避免投影读到旧态。
        serviceRequestRepository.save(request);
        domainEventPublisher.publish(new ServiceRequestStopCancelledEvent(request.getId(), request.getStatusEnum()));
    }

    /**
     * 取消申请：写入取消原因并置为已取消，发布取消事件。
     *
     * @param command 批次内一条，须含 id 与取消原因
     * @throws BusinessException 不可取消时
     */
    public void cancelServiceRequest(CancelServiceRequestCommand command) {
        List<CancelServiceRequestCommand.CancelServiceRequestItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "取消一次仅允许提交一条记录");
        }
        CancelServiceRequestCommand.CancelServiceRequestItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "取消时 id 不能为空");
        }

        ServiceRequest request = this.requireServiceRequest(item.getId());
        request.assertCancelable();
        request.setReasonText(item.getCancelReason());
        request.toCancelled();
        serviceRequestRepository.save(request);
        domainEventPublisher.publish(new ServiceRequestCancelledEvent(request.getId(), request.getReasonText()));
    }

    /**
     * 开立住院转科申请草稿：校验同就诊无未完成转科/出院申请，分配 busNo 并发布创建事件。
     *
     * @param command 批次内一条，须含 encounterId、原因等
     * @return 新申请主键
     * @throws BusinessException 重复申请或参数非法时
     */
    public Long createTransferDepartmentRequest(CreateTransferDepartmentRequestCommand command) {
        List<CreateTransferDepartmentRequestCommand.CreateTransferDepartmentRequestItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "开立转科申请一次仅允许提交一条记录");
        }
        CreateTransferDepartmentRequestCommand.CreateTransferDepartmentRequestItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "开立转科申请时 encounterId 不能为空");
        }
        // 业务分支处理。
        if (this.checkDuplicateTransferOrDischargeRequest(item.getEncounterId())) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "同一就诊存在未完成的转科/出院申请，禁止重复开立");
        }

        ServiceRequest request = new ServiceRequest();
        request.setEncounterId(item.getEncounterId());
        request.setCategoryEnum("32");
        request.setTherapyEnum(TherapyType.TEMPORARY.getCode());
        request.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        request.setReasonText(item.getReasonText());
        request.initDraft();
        request.setIsDeleted(Boolean.FALSE);
        request.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.SERVICE_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        request.setQuantity(BigDecimal.ONE);
        request.setUnitCode("EA");
        request.setActivityId(0L);
        if (item.getPatientId() != null) {
            request.setPatientId(item.getPatientId());
        } else {
            request.setPatientId(0L);
        }
        request.setRequesterId(command.getOperatorId());

        ServiceRequest saved = serviceRequestRepository.save(request);
        domainEventPublisher.publish(new TransferDepartmentRequestCreatedEvent(saved.getId(), saved.getEncounterId()));
        return saved.getId();
    }

    /**
     * 开立出院申请草稿：同就诊转科/出院互斥校验，写入计划出院时间，发布创建事件。
     *
     * @param command 批次内一条，须含 encounterId、出院时间与原因
     * @return 新申请主键
     * @throws BusinessException 重复申请或参数非法时
     */
    public Long createDischargeRequest(CreateDischargeRequestCommand command) {
        List<CreateDischargeRequestCommand.CreateDischargeRequestItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "开立出院申请一次仅允许提交一条记录");
        }
        CreateDischargeRequestCommand.CreateDischargeRequestItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "开立出院申请时 encounterId 不能为空");
        }
        // 业务分支处理。
        if (this.checkDuplicateTransferOrDischargeRequest(item.getEncounterId())) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "同一就诊存在未完成的转科/出院申请，禁止重复开立");
        }

        ServiceRequest request = new ServiceRequest();
        request.setEncounterId(item.getEncounterId());
        request.setCategoryEnum("34");
        request.setTherapyEnum(TherapyType.TEMPORARY.getCode());
        request.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        request.setReasonText(item.getReasonText());
        request.setOccurrenceStartTime(item.getDischargeTime());
        request.initDraft();
        request.setIsDeleted(Boolean.FALSE);
        request.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.SERVICE_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        request.setQuantity(BigDecimal.ONE);
        request.setUnitCode("EA");
        request.setActivityId(0L);
        if (item.getPatientId() != null) {
            request.setPatientId(item.getPatientId());
        } else {
            request.setPatientId(0L);
        }
        request.setRequesterId(command.getOperatorId());

        ServiceRequest saved = serviceRequestRepository.save(request);
        domainEventPublisher.publish(new DischargeRequestCreatedEvent(saved.getId(), saved.getEncounterId()));
        return saved.getId();
    }

    /**
     * 护理医嘱 upsert：同就诊同类别下非终态旧单停嘱后新建一条草稿并发布事件。
     *
     * @param command 批次内一条，须含 encounterId、categoryEnum 与内容 JSON
     * @return 新申请主键
     * @throws BusinessException 参数非法时
     */
    public Long upsertNursingOrder(UpsertNursingOrderCommand command) {
        List<UpsertNursingOrderCommand.UpsertNursingOrderItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "维护护理医嘱一次仅允许提交一条记录");
        }
        UpsertNursingOrderCommand.UpsertNursingOrderItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getEncounterId() == null || item.getCategoryEnum() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "维护护理医嘱时 encounterId/categoryEnum 不能为空");
        }

        List<ServiceRequest> existed = serviceRequestRepository.findByEncounterIdAndCategoryEnumIn(
            item.getEncounterId(), List.of(String.valueOf(item.getCategoryEnum())));
        for (ServiceRequest request : existed) {
            // 业务分支处理。
            if (request.getStatusEnum() != null && !RequestStatus.STOPPED.getCode().equals(request.getStatusEnum())) {
                request.toStopped();
            }
        }
        // 业务分支处理。
        if (!existed.isEmpty()) {
            serviceRequestRepository.saveAll(existed);
        }

        ServiceRequest request = new ServiceRequest();
        request.setEncounterId(item.getEncounterId());
        request.setCategoryEnum(String.valueOf(item.getCategoryEnum()));
        request.setTherapyEnum("2");
        request.setContentJson(item.getContent());
        request.initDraft();
        request.setIsDeleted(Boolean.FALSE);
        request.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.SERVICE_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        request.setQuantity(BigDecimal.ONE);
        request.setUnitCode("EA");
        request.setActivityId(0L);
        request.setPatientId(0L);
        request.setRequesterId(command.getOperatorId());

        ServiceRequest saved = serviceRequestRepository.save(request);
        domainEventPublisher.publish(new NursingOrderUpsertedEvent(saved.getId(), saved.getCategoryEnum()));
        return saved.getId();
    }

    /**
     * 由执行上下文派生「已完成」服务申请：基于 basedOn 计步生成 busNo，类别固定为执行衍生场景，发布执行创建事件。
     *
     * @param command 批次内一条，须含 basedOnTable/basedOnId 及就诊等活动上下文
     * @return 新申请主键
     * @throws BusinessException 参数非法或不可创建时
     */
    public Long createExecutedServiceRequest(CreateExecutedServiceRequestCommand command) {
        List<CreateExecutedServiceRequestCommand.CreateExecutedServiceRequestItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "创建执行完成申请一次仅允许提交一条记录");
        }
        CreateExecutedServiceRequestCommand.CreateExecutedServiceRequestItemCommand item = items.get(0);
        // 业务分支处理。
        if (StringUtils.isBlank(item.getBasedOnTable()) || item.getBasedOnId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "创建执行完成申请时 basedOnTable/basedOnId 不能为空");
        }
        ServiceRequest request = new ServiceRequest();
        request.setBasedOnTable(item.getBasedOnTable());
        request.setBasedOnId(item.getBasedOnId());
        request.setGenerateSourceEnum(item.getGenerateSourceEnum());
        request.setActivityId(item.getActivityId());
        request.setEncounterId(item.getEncounterId());
        // 患者标识未传时用 0 兜底（适用于非就诊场景的服务申请）。
        if (item.getPatientId() == null) {
            request.setPatientId(0L);
        } else {
            request.setPatientId(item.getPatientId());
        }
        // 申请人未指定时默认为当前操作人。
        if (item.getRequesterId() == null) {
            request.setRequesterId(command.getOperatorId());
        } else {
            request.setRequesterId(item.getRequesterId());
        }
        request.setBusNo(businessSequenceService.getSeqByDay(
            AggregateTypeConstants.SERVICE_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        request.initCompleted();
        request.setIsDeleted(Boolean.FALSE);
        request.setQuantity(BigDecimal.ONE);
        request.setUnitCode("EA");
        request.setCategoryEnum("21");
        request.setTherapyEnum("2");
        request.assertExecutionDerivedCreatable();

        ServiceRequest saved = serviceRequestRepository.save(request);
        domainEventPublisher.publish(new ServiceRequestExecutedCreatedEvent(
            saved.getId(), saved.getBasedOnTable(), saved.getBasedOnId()));
        return saved.getId();
    }

    /**
     * 逻辑删除执行派生服务申请主档及明细（与 createExecutedServiceRequest 对称，AS-IP-NUR-012 BR-IP-CXL-013）。
     *
     * @param command 批次内多条，每条须含申请主键
     * @throws BusinessException 不可删除或不存在时
     */
    public void removeExecutedServiceRequest(RemoveExecutedServiceRequestCommand command) {
        List<RemoveExecutedServiceRequestCommand.Item> items = command.resolveItems();
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "删除执行派生服务申请入参不能为空");
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (RemoveExecutedServiceRequestCommand.Item item : items) {
            if (item.getId() == null) {
                throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "删除执行派生服务申请时 id 不能为空");
            }
            distinctIds.add(item.getId());
        }
        List<Long> idList = new ArrayList<>(distinctIds);
        List<ServiceRequest> requests = serviceRequestRepository.findAllById(idList);
        Map<Long, ServiceRequest> requestMap = new HashMap<>();
        for (ServiceRequest request : requests) {
            requestMap.put(request.getId(), request);
        }
        List<ServiceRequest> toSaveRequests = new ArrayList<>();
        List<ServiceRequestDetail> toSaveDetails = new ArrayList<>();
        for (Long id : idList) {
            ServiceRequest request = requestMap.get(id);
            if (request == null) {
                throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND, "服务申请不存在");
            }
            request.assertExecutionDerivedRemovable();
            // 业务分支处理：主档与明细同事务软删，与 removeServiceRequestDraft 口径一致。
            request.setIsDeleted(Boolean.TRUE);
            toSaveRequests.add(request);
            List<ServiceRequestDetail> details = serviceRequestDetailRepository.findByServiceReqId(request.getId());
            for (ServiceRequestDetail detail : details) {
                detail.setIsDeleted(Boolean.TRUE);
                toSaveDetails.add(detail);
            }
        }
        serviceRequestRepository.saveAll(toSaveRequests);
        if (!toSaveDetails.isEmpty()) {
            serviceRequestDetailRepository.saveAll(toSaveDetails);
        }
        for (ServiceRequest request : toSaveRequests) {
            domainEventPublisher.publish(new ServiceRequestExecutedRemovedEvent(
                request.getId(), request.getBasedOnTable(), request.getBasedOnId()));
        }
    }

    /**
     * 护士划价来源建单：批量置为生效态并分配 busNo，发布护士划价建单事件。
     *
     * <p>支持直接划价（无 {@code based_on_*}）与医嘱补费（须成对携带 {@code basedOnTable}/{@code basedOnId}）。</p>
     *
     * @param command 批次内多条，含就诊、患者、数量单位及可选父锚点
     * @return 新申请主键列表（顺序与入参一致）
     * @throws BusinessException 不变式失败或父锚点字段不成对时
     */
    public List<Long> createFromNursePricing(CreateFromNursePricingCommand command) {
        List<CreateFromNursePricingCommand.CreateFromNursePricingItemCommand> items = command.resolveItems();
        // 批量划价须至少一行，由应用层整批事务收口。
        if (items.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "护士划价建单至少须提交一条记录");
        }
        List<ServiceRequest> toSave = new ArrayList<>();
        for (CreateFromNursePricingCommand.CreateFromNursePricingItemCommand item : items) {
            boolean hasBasedOnTable = StringUtils.isNotBlank(item.getBasedOnTable());
            boolean hasBasedOnId = item.getBasedOnId() != null;
            // 父锚点表名与主键须成对出现，避免半填导致追溯歧义。
            if (hasBasedOnTable != hasBasedOnId) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    "护士划价建单时 basedOnTable 与 basedOnId 须同时为空或同时有值");
            }
            ServiceRequest request = new ServiceRequest();
            request.setEncounterId(item.getEncounterId());
            request.setPatientId(item.getPatientId());
            request.setActivityId(item.getActivityId());
            request.setQuantity(item.getQuantity());
            request.setUnitCode(item.getUnitCode());
            request.setRequesterId(item.getRequesterId());
            request.setGenerateSourceEnum(GenerateSource.NURSE_PRICING.getCode());
            if (hasBasedOnTable) {
                request.setBasedOnTable(item.getBasedOnTable());
                request.setBasedOnId(item.getBasedOnId());
            }
            request.setIsDeleted(Boolean.FALSE);
            request.setBusNo(businessSequenceService.getSeqByDay(
                AggregateTypeConstants.SERVICE_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
            request.setCategoryEnum("21");
            request.setTherapyEnum(TherapyType.TEMPORARY.getCode());
            // 护士划价不经校对 Tab，创建即 VERIFIED，避免计入待校对统计（BR-IP-NUR-004）。
            request.initVerified();
            request.setPerformerCheckId(command.getOperatorId());
            request.setCheckTime(LocalDateTime.now());
            request.assertDraftCreatable();
            toSave.add(request);
        }
        List<ServiceRequest> saved = serviceRequestRepository.saveAll(toSave);
        List<Long> ids = new ArrayList<>();
        for (ServiceRequest row : saved) {
            ids.add(row.getId());
            domainEventPublisher.publish(new NursePricingServiceRequestCreatedEvent(
                row.getId(),
                row.getEncounterId(),
                row.getPatientId(),
                row.getActivityId(),
                row.getQuantity(),
                row.getUnitCode(),
                row.getBasedOnTable(),
                row.getBasedOnId(),
                row.getStatusEnum()));
        }
        return ids;
    }

    /**
     * 住院滚费来源建单：批量置为已校对并分配 busNo，发布滚费建单事件（AS-IP-NUR-013 IF-IP-ARG-08；DM-WKF-004 §6.1.20）。
     *
     * <p>与 {@link #createFromNursePricing} 同构，但 {@code generate_source_enum=auto-roll-fees}，且须携带诊疗类目与费用发生时刻。</p>
     *
     * @param command 批次内多条滚费落账项
     * @return 新申请主键列表（顺序与入参一致）
     * @throws BusinessException 不变式失败时
     */
    public List<Long> createFromAutoRoll(CreateFromAutoRollCommand command) {
        List<CreateFromAutoRollCommand.CreateFromAutoRollItemCommand> items = command.resolveItems();
        // Pre：滚费批次须至少一行，由应用层整批事务收口。
        if (items.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "住院滚费建单至少须提交一条记录");
        }
        List<ServiceRequest> toSave = new ArrayList<>();
        // Loop：逐条校验滚费落账必填项并组装 VERIFIED 诊疗申请。
        for (CreateFromAutoRollCommand.CreateFromAutoRollItemCommand item : items) {
            if (item.getEncounterId() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    "住院滚费建单时 encounterId 不能为空");
            }
            if (item.getPatientId() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    "住院滚费建单时 patientId 不能为空");
            }
            if (item.getActivityId() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    "住院滚费建单时 activityId 不能为空");
            }
            if (StringUtils.isBlank(item.getCategoryEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    "住院滚费建单时 categoryEnum 不能为空");
            }
            if (item.getOccurrenceTime() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    "住院滚费建单时 occurrenceTime 不能为空");
            }
            ServiceRequest request = new ServiceRequest();
            request.setEncounterId(item.getEncounterId());
            request.setPatientId(item.getPatientId());
            request.setActivityId(item.getActivityId());
            request.setQuantity(item.getQuantity());
            request.setUnitCode(item.getUnitCode());
            request.setRequesterId(item.getRequesterId());
            request.setGenerateSourceEnum(GenerateSource.AUTO_ROLL_FEES.getCode());
            request.setCategoryEnum(item.getCategoryEnum());
            request.setTherapyEnum(TherapyType.TEMPORARY.getCode());
            request.setIsDeleted(Boolean.FALSE);
            request.setBusNo(businessSequenceService.getSeqByDay(
                AggregateTypeConstants.SERVICE_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
            // 滚费不经校对 Tab，创建即 VERIFIED，与护士划价同构（BR-IP-ARG-015）。
            request.initVerified();
            request.setPerformerCheckId(command.getOperatorId());
            request.setCheckTime(item.getOccurrenceTime());
            request.assertDraftCreatable();
            toSave.add(request);
        }
        List<ServiceRequest> saved = serviceRequestRepository.saveAll(toSave);
        List<Long> ids = new ArrayList<>();
        // Event：save 完成后逐条发布滚费建单事件，供费用读侧区分 auto-roll-fees 来源。
        for (ServiceRequest row : saved) {
            ids.add(row.getId());
            domainEventPublisher.publish(new AutoRollServiceRequestCreatedEvent(
                row.getId(),
                row.getEncounterId(),
                row.getPatientId(),
                row.getActivityId(),
                row.getQuantity(),
                row.getUnitCode(),
                row.getCategoryEnum(),
                row.getStatusEnum()));
        }
        return ids;
    }

    /**
     * 逻辑删除护士划价诊疗服务申请（与 {@link #createFromNursePricing} 对称，AS-IP-NUR-005 IF-IP-PRC-06）。
     *
     * @param command 批次内多条，每条须为 nurse-pricing + VERIFIED 且未软删
     * @throws BusinessException 来源/状态不允许或不存在时
     */
    public void removeFromNursePricing(RemoveFromNursePricingCommand command) {
        List<RemoveFromNursePricingCommand.Item> items = command.resolveItems();
        // 前置校验：批量撤销须至少一行，由应用层整批事务收口。
        if (items.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "撤销护士划价诊疗申请入参不能为空");
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (RemoveFromNursePricingCommand.Item item : items) {
            if (item.getId() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    "撤销护士划价诊疗申请时 id 不能为空");
            }
            distinctIds.add(item.getId());
        }
        List<Long> idList = new ArrayList<>(distinctIds);
        List<ServiceRequest> requests = serviceRequestRepository.findAllById(idList);
        Map<Long, ServiceRequest> requestMap = new HashMap<>();
        for (ServiceRequest request : requests) {
            requestMap.put(request.getId(), request);
        }
        List<ServiceRequest> toSaveRequests = new ArrayList<>();
        List<ServiceRequestDetail> toSaveDetails = new ArrayList<>();
        for (Long id : idList) {
            ServiceRequest request = requestMap.get(id);
            if (request == null) {
                throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND, "服务申请不存在");
            }
            // 前置校验：仅 nurse-pricing + VERIFIED 允许撤销，防止误删医生医嘱。
            request.assertNursePricingRemovable();
            request.setIsDeleted(Boolean.TRUE);
            toSaveRequests.add(request);
            List<ServiceRequestDetail> details = serviceRequestDetailRepository.findByServiceReqId(request.getId());
            for (ServiceRequestDetail detail : details) {
                detail.setIsDeleted(Boolean.TRUE);
                toSaveDetails.add(detail);
            }
        }
        // 持久化：主档与明细同事务软删。
        serviceRequestRepository.saveAll(toSaveRequests);
        if (!toSaveDetails.isEmpty()) {
            serviceRequestDetailRepository.saveAll(toSaveDetails);
        }
        // 发布事件：save 完成后逐条通知读侧剔除已划价行。
        for (ServiceRequest request : toSaveRequests) {
            domainEventPublisher.publish(new NursePricingServiceRequestRemovedEvent(
                request.getId(),
                request.getEncounterId(),
                request.getBasedOnTable(),
                request.getBasedOnId()));
        }
    }

    /**
     * 门诊挂号来源建单：生效态、生成来源为挂号，单位/类别有默认值兜底，发布挂号建单事件。
     *
     * @param command 批次内一条，含就诊、患者、活动与申请人等
     * @return 新申请主键
     * @throws BusinessException 不变式失败时
     */
    public Long createFromOutpatientRegistration(CreateFromOutpatientRegistrationCommand command) {
        List<CreateFromOutpatientRegistrationCommand.CreateFromOutpatientRegistrationItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "挂号登记建单一次仅允许提交一条记录");
        }
        CreateFromOutpatientRegistrationCommand.CreateFromOutpatientRegistrationItemCommand item = items.get(0);
        ServiceRequest request = new ServiceRequest();
        request.setEncounterId(item.getEncounterId());
        request.setPatientId(item.getPatientId());
        request.setActivityId(item.getActivityId());
        request.setRequesterId(item.getRequesterId());
        request.setGenerateSourceEnum(GenerateSource.REGISTRATION_REGISTER.getCode());
        request.initActive();
        request.setIsDeleted(Boolean.FALSE);
        request.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.SERVICE_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        request.setQuantity(BigDecimal.ONE);
        // 单位码未传时默认 EA（each，件）。
        if (item.getUnitCode() == null) {
            request.setUnitCode("EA");
        } else {
            request.setUnitCode(item.getUnitCode());
        }
        // 类别枚举未传时默认 21（护理项目兜底值）。
        if (item.getCategoryEnum() == null) {
            request.setCategoryEnum("21");
        } else {
            request.setCategoryEnum(String.valueOf(item.getCategoryEnum()));
        }
        request.setTherapyEnum("2");
        request.assertDraftCreatable();

        ServiceRequest saved = serviceRequestRepository.save(request);
        domainEventPublisher.publish(new OutpatientRegistrationServiceRequestCreatedEvent(saved.getId(), saved.getEncounterId()));
        return saved.getId();
    }

    /**
     * 累加申请打印次数（从 null 视为 0）。
     *
     * @param command 批次内一条，须含 id 与 increaseBy
     * @throws BusinessException 记录不存在或不允许累加时
     */
    public void increasePrintCount(IncreasePrintCountCommand command) {
        List<IncreasePrintCountCommand.IncreasePrintCountItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "增加打印次数一次仅允许提交一条记录");
        }
        IncreasePrintCountCommand.IncreasePrintCountItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "增加打印次数时 id 不能为空");
        }

        ServiceRequest request = this.requireServiceRequest(item.getId());
        request.assertPrintCountIncreasable(item.getIncreaseBy());
        // 打印次数为 null 时从 0 开始累加。
        Integer origin;
        if (request.getPrintCount() == null) {
            origin = 0;
        } else {
            origin = request.getPrintCount();
        }
        request.setPrintCount(origin + item.getIncreaseBy());
        serviceRequestRepository.save(request);
    }

    /**
     * 半退保留链：按源服务申请事实在本聚合内正向克隆新申请（DM-WKF-004 §6.1.17）。
     *
     * <p>头表复刻可对齐字段；签发与退费链字段清空；状态回落草稿；{@code based_on_table}/{@code based_on_id} 指向源头表行；
     * 未软删的明细按 {@link ServiceRequestDetailRepository#findByServiceReqId} 批量复制到新 {@code service_req_id}；
     * 新 {@code bus_no} 按日取号；明细与头均持久化后再发布 {@link ServiceRequestDraftCreatedEvent}（B.14）。</p>
     *
     * @param command 克隆入参，须含源主键与操作人
     * @return 新服务申请主键
     * @throws BusinessException 入参非法、源不可克隆或不变式失败时
     */
    public Long cloneServiceRequestForPreservedChargeRegeneration(CloneServiceRequestForPreservedChargeRegenerationCommand command) {
        if (command == null || command.getSourceServiceRequestId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "半退保留链克隆入参不能为空");
        }
        if (command.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "半退保留链克隆操作人不能为空");
        }
        ServiceRequest source = this.requireServiceRequest(command.getSourceServiceRequestId());
        this.assertServiceRequestCloneableForPreservedChargeRegeneration(source);
        ServiceRequest cloned = this.buildServiceRequestCloneFromSourceForPreservedChargeRegeneration(source, command.getOperatorId());
        cloned.assertDraftCreatable();
        ServiceRequest saved = serviceRequestRepository.save(cloned);
        List<ServiceRequestDetail> sourceDetails = serviceRequestDetailRepository.findByServiceReqId(source.getId());
        List<ServiceRequestDetail> newDetails = new ArrayList<>();
        for (ServiceRequestDetail row : sourceDetails) {
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            ServiceRequestDetail copy = new ServiceRequestDetail();
            copy.setServiceReqId(saved.getId());
            copy.setTargetTable(row.getTargetTable());
            copy.setTargetId(row.getTargetId());
            copy.setRemark(row.getRemark());
            copy.setIsDeleted(Boolean.FALSE);
            copy.assertPersistable();
            newDetails.add(copy);
        }
        if (!newDetails.isEmpty()) {
            serviceRequestDetailRepository.saveAll(newDetails);
        }
        domainEventPublisher.publish(new ServiceRequestDraftCreatedEvent(
            saved.getId(), saved.getEncounterId(), saved.getPatientId()));
        return saved.getId();
    }

    /**
     * 校验源服务申请是否允许作为半退保留链正向克隆源。
     *
     * @param source 源服务申请
     * @throws BusinessException 软删或取消/暂挂时拒绝
     */
    private void assertServiceRequestCloneableForPreservedChargeRegeneration(ServiceRequest source) {
        if (Boolean.TRUE.equals(source.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "已删除服务申请不允许半退保留链克隆");
        }
        String statusEnum = source.getStatusEnum();
        if (statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "源服务申请状态为空，不允许半退保留链克隆");
        }
        if (RequestStatus.CANCELLED.getCode().equals(statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "已取消服务申请不允许半退保留链克隆");
        }
        if (RequestStatus.HOLD.getCode().equals(statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "暂挂服务申请不允许半退保留链克隆");
        }
    }

    /**
     * 由源服务申请组装半退保留链用的新头表行：字段复刻、草稿初始化、追溯锚点与新业务号。
     *
     * @param source     源头表
     * @param operatorId 操作人，写入 requester
     * @return 未持久化的新实体
     */
    private ServiceRequest buildServiceRequestCloneFromSourceForPreservedChargeRegeneration(ServiceRequest source, Long operatorId) {
        ServiceRequest out = new ServiceRequest();
        out.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.SERVICE_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        out.setEncounterId(source.getEncounterId());
        out.setPatientId(source.getPatientId());
        out.setActivityId(source.getActivityId());
        out.setQuantity(source.getQuantity());
        out.setUnitCode(source.getUnitCode());
        out.setExecutionCount(source.getExecutionCount());
        out.setCategoryEnum(source.getCategoryEnum());
        out.setTherapyEnum(source.getTherapyEnum());
        out.setIntentEnum(source.getIntentEnum());
        out.setGroupId(source.getGroupId());
        out.setPriorityEnum(source.getPriorityEnum());
        out.setPerformFlag(source.getPerformFlag());
        out.setPerformerTypeCode(source.getPerformerTypeCode());
        out.setPerformerId(source.getPerformerId());
        out.setLocationId(source.getLocationId());
        out.setOrgId(source.getOrgId());
        out.setReasonText(source.getReasonText());
        out.setContractId(source.getContractId());
        out.setOccurrenceStartTime(source.getOccurrenceStartTime());
        out.setOccurrenceEndTime(source.getOccurrenceEndTime());
        out.setRelevantHistoryId(source.getRelevantHistoryId());
        out.setContentJson(source.getContentJson());
        out.setYbClassEnum(source.getYbClassEnum());
        out.setConditionDefinitionId(source.getConditionDefinitionId());
        out.setEncounterDiagnosisId(source.getEncounterDiagnosisId());
        out.setRateCode(source.getRateCode());
        out.setGenerateSourceEnum(source.getGenerateSourceEnum());
        out.setSortNumber(source.getSortNumber());
        out.setRemark(source.getRemark());
        out.setEntrustContent(source.getEntrustContent());
        out.setPrescriptionNo(null);
        out.setRefundServiceId(null);
        out.setPerformerCheckId(null);
        out.setCheckTime(null);
        out.setRequisitionId(null);
        out.setParentId(null);
        out.setPrintCount(null);
        out.setBasedOnTable(TableNameConstants.WKF_SERVICE_REQUEST);
        out.setBasedOnId(source.getId());
        out.setRequesterId(operatorId);
        out.setAuthoredTime(LocalDateTime.now());
        out.setIsDeleted(Boolean.FALSE);
        out.initDraft();
        return out;
    }

    /**
     * 按主键加载服务申请，不存在则抛出业务异常。
     *
     * @param id 服务申请主键
     * @return 服务申请实体
     * @throws BusinessException 服务申请不存在时
     */
    private ServiceRequest requireServiceRequest(Long id) {
        return serviceRequestRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND, "服务申请不存在"));
    }

    /**
     * 私有方法：buildDetails。
     *
     * <p>根据明细项命令列表构建服务申请明细实体列表。</p>
     *
     * @param serviceRequestId 服务申请主键
     * @param detailItems      明细项命令列表
     * @return 明细实体列表
     */
    private List<ServiceRequestDetail> buildDetails(Long serviceRequestId, List<?> detailItems) {
        List<ServiceRequestDetail> details = new ArrayList<>();
        // 业务分支处理：空列表直接返回。
        if (detailItems == null || detailItems.isEmpty()) {
            return details;
        }
        // Loop：遍历命令列表构建明细实体。
        for (int i = 0; i < detailItems.size(); i++) {
            Object item = detailItems.get(i);
            ServiceRequestDetail detail = new ServiceRequestDetail();
            detail.setServiceReqId(serviceRequestId);
            if (item instanceof CreateServiceRequestDraftCommand.DetailItemCommand createItem) {
                this.applyDetailItemCommand(detail, createItem.getTargetTable(), createItem.getTargetId(), createItem.getRemark());
            } else if (item instanceof ModifyServiceRequestDraftCommand.DetailItemCommand modifyItem) {
                this.applyDetailItemCommand(detail, modifyItem.getTargetTable(), modifyItem.getTargetId(), modifyItem.getRemark());
            }
            detail.assertPersistable();
            details.add(detail);
        }
        return details;
    }

    /**
     * 将明细行命令字段写入实体。
     *
     * @param detail      明细实体
     * @param targetTable 目标表名
     * @param targetId    目标主键
     * @param remark      备注
     */
    private void applyDetailItemCommand(
        ServiceRequestDetail detail, String targetTable, Long targetId, String remark) {
        detail.setTargetTable(targetTable);
        detail.setTargetId(targetId);
        detail.setRemark(remark);
    }

    /**
     * 长期须非空频次 code；临时医嘱清空 {@code rate_code}。
     */
    private String resolveRateCodeForTherapy(String therapyEnum, String rateCode) {
        if (TherapyType.LONG_TERM.getCode().equals(therapyEnum)) {
            if (StringUtils.isBlank(rateCode)) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    "长期诊疗医嘱须指定频次");
            }
            return rateCode.trim();
        }
        return null;
    }

    /**
     * 同就诊是否存在未完成的转科/出院申请（仅本聚合仓储判定）。
     */
    private boolean checkDuplicateTransferOrDischargeRequest(Long encounterId) {
        if (encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "校验转科/出院重复时 encounterId 不能为空");
        }
        List<String> categories = new ArrayList<>();
        categories.add("32");
        categories.add("34");
        List<ServiceRequest> requests = serviceRequestRepository.findByEncounterIdAndCategoryEnumIn(encounterId, categories);
        for (ServiceRequest request : requests) {
            if (request.getStatusEnum() == null) {
                continue;
            }
            if (request.getStatusEnum().equals(RequestStatus.DRAFT.getCode())
                || request.getStatusEnum().equals(RequestStatus.ACTIVE.getCode())) {
                return true;
            }
        }
        return false;
    }
}
