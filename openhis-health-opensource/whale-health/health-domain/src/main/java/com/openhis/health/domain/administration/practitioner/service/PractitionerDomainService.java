/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.practitioner.service;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.administration.practitioner.command.*;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.repository.PractitionerRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.administration.practitioner.*;
import com.openhis.health.domain.share.utils.CommandBatchUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import lombok.RequiredArgsConstructor;

/**
 * 医务人员（{@code adm_practitioner}）写侧领域服务（DM-ADM-005）。
 *
 * <p>不变量：一用户至多一条有效医务人员；工号由领域层采番不可外部注入；注销/恢复须发领域事件。</p>
 */
@Service
@RequiredArgsConstructor
public class PractitionerDomainService {

    private final PractitionerRepository practitionerRepository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 工号 busNo：主数据全局递增，前缀 {@link AggregateTypeConstants#PRACTITIONER}；新建禁止外部注入（B.10.9）。 */
    private final BusinessSequenceService businessSequenceService;

    /** 采番撞号时单条最大重试次数（对齐序号后仍冲突多为并发占用）。 */
    private static final int BUS_NO_ALLOCATE_MAX_ATTEMPTS = 32;

    /**
     * 批量创建医务人员：校验用户唯一与工号唯一，必要时分配业务工号并发创建事件。
     *
     * @param cmd 创建命令（人员明细列表）
     * @return 首条落库记录主键（与既有批量创建约定一致）
     */
    public Long createPractitioner(CreatePractitionerCommand cmd) {
        List<CreatePractitionerCommand.CreatePractitionerItemCommand> items = cmd.resolveItems();
        // 前置：将 PR 序号抬到库内已有最大后缀，避免迁移/导数据后 whale_sequence 落后撞号。
        alignPrSequenceWithExistingBusNo();
        List<Practitioner> toSave = new ArrayList<>();
        for (CreatePractitionerCommand.CreatePractitionerItemCommand item : items) {
            if (item.getUserId() == null) {
                throw new BusinessException(ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "用户标识不能为空");
            }
            // 同一 userId 已存在有效绑定则拒绝新建，含软删记录亦视为占用（避免重复建档）
            if (practitionerRepository.findFirstByUserIdOrderByIdAsc(item.getUserId()).isPresent()) {
                throw new BusinessException(ErrorCodeConstants.PRACTITIONER_RULE_CONFLICT, "用户已绑定医务人员");
            }
            // 工号一律领域层采番，禁止命令行注入（B.10.9）；撞号则跳号重试。
            String busNo = allocateUniqueBusNo();
            Practitioner p = new Practitioner();
            p.bindUser(item.getUserId());
            p.setName(item.getName());
            p.setGenderEnum(item.getGenderEnum());
            p.setBusNo(busNo);
            p.setOrgId(item.getOrgId());
            p.setYbNo(item.getYbNo());
            p.setDrProfttlCode(item.getDrProfttlCode());
            p.setPharPracCertNo(item.getPharPracCertNo());
            p.assertCreateAllowed();
            p.initAsActive();
            toSave.add(p);
        }
        List<Practitioner> saved = practitionerRepository.saveAll(toSave);
        for (Practitioner p : saved) {
            domainEventPublisher.publish(new PractitionerCreatedEvent(p.getId(), p.getUserId(), p.getName(), p.getBusNo()));
        }
        return saved.get(0).getId();
    }

    /**
     * 按库内已占用的 {@code PR} 工号后缀抬升序号水位。
     */
    private void alignPrSequenceWithExistingBusNo() {
        Long maxSuffix = practitionerRepository.findMaxPrBusNoSuffix();
        if (maxSuffix == null || maxSuffix <= 0L) {
            return;
        }
        businessSequenceService.ensureAtLeast(AggregateTypeConstants.PRACTITIONER, maxSuffix);
    }

    /**
     * 采番一条未被占用的医务人员工号；对齐后仍冲突则继续取下一号。
     */
    private String allocateUniqueBusNo() {
        for (int attempt = 0; attempt < BUS_NO_ALLOCATE_MAX_ATTEMPTS; attempt++) {
            String busNo = businessSequenceService.getSeq(
                AggregateTypeConstants.PRACTITIONER, BusinessSequenceConstants.MASTER_DATA_2_PREFIX_SEQ_LENGTH);
            if (!practitionerRepository.existsByBusNo(busNo)) {
                return busNo;
            }
        }
        throw new BusinessException(ErrorCodeConstants.PRACTITIONER_BUS_NO_CONFLICT, "工号采番冲突，请稍后重试");
    }

    /**
     * 批量编辑医务人员基础信息（姓名、电话、签名等）；工号创建后不可通过命令修改（B.10.9）。
     *
     * @param cmd 编辑命令（含变更字段清单供事件携带）
     */
    public void editPractitioner(EditPractitionerCommand cmd) {
        List<EditPractitionerCommand.EditPractitionerItemCommand> items = cmd.resolveItems();
        Map<Long, Practitioner> map = this.loadMap(CommandBatchUtil.extractIds(items, EditPractitionerCommand.EditPractitionerItemCommand::getPractitionerId));
        List<Practitioner> toSave = new ArrayList<>();
        for (EditPractitionerCommand.EditPractitionerItemCommand item : items) {
            Practitioner p = this.require(map, item.getPractitionerId());
            p.assertEditable();
            p.setName(item.getName());
            if (item.getGenderEnum() != null) {
                if (StringUtils.isEmpty(item.getGenderEnum())) {
                    throw new BusinessException(ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "人员性别不能为空");
                }
                p.setGenderEnum(item.getGenderEnum().trim());
            }
            p.setPhone(item.getPhone());
            p.setYbNo(item.getYbNo());
            p.setDrProfttlCode(item.getDrProfttlCode());
            p.setPharPracCertNo(item.getPharPracCertNo());
            p.setSignature(item.getSignature());
            toSave.add(p);
        }
        practitionerRepository.saveAll(toSave);
        for (EditPractitionerCommand.EditPractitionerItemCommand item : items) {
            domainEventPublisher.publish(new PractitionerEditedEvent(item.getPractitionerId(), item.getChangedFields()));
        }
    }

    /**
     * 更新医务人员签名图片（Base64 或 data URL）；传空白则清空签名。
     *
     * @param practitionerId 医务人员主键
     * @param signatureBase64 签名载荷；可为 {@code null} 表示清空
     * @param operatorId 操作人
     */
    public void updateSignature(Long practitionerId, String signatureBase64, Long operatorId) {
        Practitioner p = practitionerRepository.findById(practitionerId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.PRACTITIONER_NOT_FOUND, "医务人员不存在"));
        p.assertEditable();
        if (StringUtils.isEmpty(signatureBase64)) {
            // 空串视为清除签名，不写占位 Base64。
            p.setSignature(null);
        } else {
            p.setSignature(signatureBase64.trim());
        }
        practitionerRepository.save(p);
        domainEventPublisher.publish(new PractitionerEditedEvent(practitionerId, List.of("signature")));
    }

    /**
     * 批量停用医务人员（状态迁至非活跃），逐条发布停用事件。
     *
     * @param cmd 停用命令（含原因、操作人）
     */
    public void deactivatePractitioner(DeactivatePractitionerCommand cmd) {
        List<DeactivatePractitionerCommand.DeactivatePractitionerItemCommand> items = cmd.resolveItems();
        Map<Long, Practitioner> map = this.loadMap(CommandBatchUtil.extractIds(items, DeactivatePractitionerCommand.DeactivatePractitionerItemCommand::getPractitionerId));
        List<Practitioner> toSave = new ArrayList<>();
        for (DeactivatePractitionerCommand.DeactivatePractitionerItemCommand item : items) {
            Practitioner p = this.require(map, item.getPractitionerId());
            p.assertDeactivatable();
            p.transitToInactive();
            toSave.add(p);
        }
        practitionerRepository.saveAll(toSave);
        for (DeactivatePractitionerCommand.DeactivatePractitionerItemCommand item : items) {
            domainEventPublisher.publish(new PractitionerDeactivatedEvent(item.getPractitionerId(), item.getReason(), cmd.getOperatorId()));
        }
    }

    /**
     * 批量重新激活已停用的医务人员。
     *
     * @param cmd 激活命令（含操作人）
     */
    public void activatePractitioner(ActivatePractitionerCommand cmd) {
        List<ActivatePractitionerCommand.ActivatePractitionerItemCommand> items = cmd.resolveItems();
        Map<Long, Practitioner> map = this.loadMap(CommandBatchUtil.extractIds(items, ActivatePractitionerCommand.ActivatePractitionerItemCommand::getPractitionerId));
        List<Practitioner> toSave = new ArrayList<>();
        for (ActivatePractitionerCommand.ActivatePractitionerItemCommand item : items) {
            Practitioner p = this.require(map, item.getPractitionerId());
            p.assertActivatable();
            p.transitToActive();
            toSave.add(p);
        }
        practitionerRepository.saveAll(toSave);
        for (ActivatePractitionerCommand.ActivatePractitionerItemCommand item : items) {
            domainEventPublisher.publish(new PractitionerActivatedEvent(item.getPractitionerId(), cmd.getOperatorId()));
        }
    }

    /**
     * 批量退休/注销医务人员（软删语义由实体 {@code transitToDeleted} 表达）。
     *
     * @param cmd 退休命令（含原因、操作人）
     */
    public void retirePractitioner(RetirePractitionerCommand cmd) {
        List<RetirePractitionerCommand.RetirePractitionerItemCommand> items = cmd.resolveItems();
        Map<Long, Practitioner> map = this.loadMap(CommandBatchUtil.extractIds(items, RetirePractitionerCommand.RetirePractitionerItemCommand::getPractitionerId));
        List<Practitioner> toSave = new ArrayList<>();
        for (RetirePractitionerCommand.RetirePractitionerItemCommand item : items) {
            Practitioner p = this.require(map, item.getPractitionerId());
            p.assertRetirable();
            p.transitToDeleted(cmd.getOperatorId());
            toSave.add(p);
        }
        practitionerRepository.saveAll(toSave);
        for (RetirePractitionerCommand.RetirePractitionerItemCommand item : items) {
            domainEventPublisher.publish(new PractitionerRetiredEvent(item.getPractitionerId(), item.getReason(), cmd.getOperatorId()));
        }
    }

    /**
     * 批量切换医务人员所属组织/科室，校验目标机构存在并发切换事件。
     *
     * @param cmd 切换命令（目标机构主键等；目标机构存在性由应用层前置校验）
     */
    public void switchOrganization(SwitchOrganizationCommand cmd) {
        List<SwitchOrganizationCommand.SwitchOrganizationItemCommand> items = cmd.resolveItems();
        Map<Long, Practitioner> map = this.loadMap(CommandBatchUtil.extractIds(items, SwitchOrganizationCommand.SwitchOrganizationItemCommand::getPractitionerId));
        List<Practitioner> toSave = new ArrayList<>();
        for (SwitchOrganizationCommand.SwitchOrganizationItemCommand item : items) {
            Practitioner p = this.require(map, item.getPractitionerId());
            p.assertSwitchOrgAllowed();
            Long oldOrgId = p.getOrgId();
            p.setOrgId(item.getTargetOrgId());
            toSave.add(p);
            domainEventPublisher.publish(new PractitionerOrgSwitchedEvent(p.getId(), oldOrgId, item.getTargetOrgId()));
        }
        practitionerRepository.saveAll(toSave);
    }

    /**
     * 系统用户软删联动：注销所有仍绑定该用户的未删医务人员。
     *
     * @param userId     平台用户主键
     * @param operatorId 操作人主键
     * @param reason     注销原因
     */
    public void retirePractitionerByBoundUserId(Long userId, Long operatorId, String reason) {
        // userId 缺失时无绑定关系可注销，静默返回
        if (userId == null) {
            return;
        }
        // Pre：按绑定 user_id 加载仍未逻辑删除的医务人员
        List<Practitioner> practitioners =
            practitionerRepository.findAllByUserIdInAndIsDeletedFalse(Collections.singleton(userId));
        // 无绑定 practitioner 时无需联动注销
        if (practitioners.isEmpty()) {
            return;
        }
        RetirePractitionerCommand command = new RetirePractitionerCommand();
        command.setOperatorId(operatorId);
        List<RetirePractitionerCommand.RetirePractitionerItemCommand> items = new ArrayList<>();
        for (Practitioner practitioner : practitioners) {
            // 脏数据跳过，避免空 id 进入 retire 命令
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            RetirePractitionerCommand.RetirePractitionerItemCommand item =
                new RetirePractitionerCommand.RetirePractitionerItemCommand();
            item.setPractitionerId(practitioner.getId());
            item.setReason(reason);
            items.add(item);
        }
        if (items.isEmpty()) {
            // 有效项均被过滤时不再调用 retire，避免空命令
            return;
        }
        command.setItems(items);
        // Cmd：复用 retirePractitioner 统一状态迁移与 PractitionerRetiredEvent
        this.retirePractitioner(command);
    }

    /**
     * 按主键批量加载医务人员并过滤软删记录。
     */
    private Map<Long, Practitioner> loadMap(List<Long> ids) {
        List<Practitioner> list = practitionerRepository.findAllById(ids);
        Map<Long, Practitioner> map = new HashMap<>();
        for (Practitioner item : list) {
            // 软删记录不参与后续编辑/注销，与 require 口径一致
            if (!Boolean.TRUE.equals(item.getIsDeleted())) {
                map.put(item.getId(), item);
            }
        }
        return map;
    }

    /**
     * 从预加载映射中取实体，缺失则视为不存在并抛业务异常。
     */
    private Practitioner require(Map<Long, Practitioner> map, Long id) {
        Practitioner p = map.get(id);
        // 主键不在有效映射中视为不存在或已软删，拒绝继续写路径
        if (p == null) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_NOT_FOUND, "医务人员不存在");
        }
        return p;
    }
}
