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
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import lombok.RequiredArgsConstructor;

/**
 * 医务人员写侧领域服务。
 */
@Service
@RequiredArgsConstructor
public class PractitionerDomainService {

    private final PractitionerRepository practitionerRepository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 工号 busNo：主数据全局递增，前缀 {@link AggregateTypeConstants#PRACTITIONER}；入参非空时沿用外部工号。 */
    private final BusinessSequenceService businessSequenceService;

    /**
     * 批量创建医务人员：校验用户唯一与工号唯一，必要时分配业务工号并发创建事件。
     *
     * @param cmd 创建命令（人员明细列表）
     * @return 首条落库记录主键（与既有批量创建约定一致）
     */
    public Long createPractitioner(CreatePractitionerCommand cmd) {
        List<CreatePractitionerCommand.CreatePractitionerItemCommand> items = cmd.resolveItems();
        int blankBusNoCount = 0;
        for (CreatePractitionerCommand.CreatePractitionerItemCommand item : items) {
            if (StringUtils.isBlank(item.getBusNo())) {
                blankBusNoCount++;
            }
        }
        List<String> allocatedBusNos = businessSequenceService.getSeq(AggregateTypeConstants.PRACTITIONER, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH, blankBusNoCount);
        int allocIdx = 0;
        List<Practitioner> toSave = new ArrayList<>();
        for (CreatePractitionerCommand.CreatePractitionerItemCommand item : items) {
            if (item.getUserId() == null) {
                throw new BusinessException(ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "用户标识不能为空");
            }
            // 业务分支处理（含软删记录，避免误判为可新建）。
            if (practitionerRepository.findFirstByUserIdOrderByIdAsc(item.getUserId()).isPresent()) {
                throw new BusinessException(ErrorCodeConstants.PRACTITIONER_RULE_CONFLICT, "用户已绑定医务人员");
            }
            String busNo = item.getBusNo();
            if (StringUtils.isBlank(busNo)) {
                busNo = allocatedBusNos.get(allocIdx);
                allocIdx++;
            }
            // 业务分支处理。
            if (practitionerRepository.existsByBusNo(busNo)) {
                throw new BusinessException(ErrorCodeConstants.PRACTITIONER_BUS_NO_CONFLICT, "工号已存在");
            }
            Practitioner p = new Practitioner();
            p.bindUser(item.getUserId());
            p.setName(item.getName());
            p.setGenderEnum(item.getGenderEnum());
            p.setBusNo(busNo);
            p.setOrgId(item.getOrgId());
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
     * 批量编辑医务人员基础信息（姓名、电话、工号、签名等），工号变更时防冲突。
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
            // 业务分支处理。
            if (item.getBusNo() != null && practitionerRepository.existsByBusNoAndIdNot(item.getBusNo(), p.getId())) {
                throw new BusinessException(ErrorCodeConstants.PRACTITIONER_BUS_NO_CONFLICT, "工号已存在");
            }
            p.setName(item.getName());
            if (item.getGenderEnum() != null) {
                if (StringUtils.isBlank(item.getGenderEnum())) {
                    throw new BusinessException(ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "人员性别不能为空");
                }
                p.setGenderEnum(item.getGenderEnum().trim());
            }
            p.setPhone(item.getPhone());
            p.setBusNo(item.getBusNo());
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
        if (StringUtils.isBlank(signatureBase64)) {
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
     * 按主键批量加载医务人员并过滤软删记录。
     */
    private Map<Long, Practitioner> loadMap(List<Long> ids) {
        List<Practitioner> list = practitionerRepository.findAllById(ids);
        Map<Long, Practitioner> map = new HashMap<>();
        for (Practitioner item : list) {
            // 业务分支处理。
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
        // 业务分支处理。
        if (p == null) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_NOT_FOUND, "医务人员不存在");
        }
        return p;
    }
}

