/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.condition.service;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.domain.share.constants.BusinessSequenceConstants;
import com.zxplat.domain.clinical.condition.command.DelEncounterDiagnosisCommand;
import com.zxplat.domain.clinical.condition.command.SaveDoctorDiagnosisCommand;
import com.zxplat.domain.clinical.condition.command.SaveDoctorDiagnosisTcmCommand;
import com.zxplat.domain.clinical.condition.entity.Condition;
import com.zxplat.domain.clinical.condition.repository.ConditionRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.events.clinical.condition.AdmissionDiagnosisCreatedEvent;
import com.zxplat.domain.share.events.clinical.condition.EncounterDiagnosisDeletedEvent;
import com.zxplat.domain.share.events.clinical.condition.EncounterDiagnosisSavedEvent;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 诊断（Condition）病情事实写侧领域服务。
 *
 * <p><b>DD 退役</b>：禁止业务调用；写路径改经 {@code EncounterDomainService.batchUpsertEncounterDiagnosisAssociation}。</p>
 *
 * @deprecated DD 退役，禁止业务调用
 */
@Deprecated
@Service
@RequiredArgsConstructor
public class ConditionDomainService {

    /** 病情事实持久化与派生查询（含删除前门引用计数占位）。 */
    private final ConditionRepository repository;

    /** 就诊诊断保存/删除的领域事件发布器；须在持久化成功之后调用。 */
    private final IDomainEventPublisher domainEventPublisher;

    private final BusinessSequenceService businessSequenceService;

    /**
     * 保存西医诊断记录（新增或更新）。
     *
     * <p>批量处理本次就诊的全量西医诊断列表；item 携带 id 时走更新路径，不携带 id 时走新增路径。
     * 遵循 A.9.1：循环仅在内存组装实体，最终统一 saveAll 落库，事件在落库完成后逐条发布。</p>
     *
     * <p>当 {@link SaveDoctorDiagnosisCommand#getAdmissionDiagnosisFlow()} 为 true 时，对本次<strong>新增</strong>的病情事实
     * 在落库后额外发布 {@link AdmissionDiagnosisCreatedEvent}（住院申请/登记入院诊断场景）。</p>
     *
     * @param cmd 保存命令，含就诊标识、患者标识、操作人及诊断明细列表
     * @return 本次保存/更新的诊断记录主键集合（顺序与落库后实体迭代一致）
     * @throws BusinessException 记录不存在、患者未绑定、记录元数据刷新规则不满足等业务规则失败时
     */
    public List<Long> saveDoctorDiagnosis(SaveDoctorDiagnosisCommand cmd) {
        List<SaveDoctorDiagnosisCommand.DoctorDiagnosisItemCommand> resolvedItems = cmd.resolveItems();
        int newCount = 0;
        for (SaveDoctorDiagnosisCommand.DoctorDiagnosisItemCommand item : resolvedItems) {
            if (item.getId() == null) {
                newCount++;
            }
        }
        List<String> newBusNos = businessSequenceService.getSeqByDay(AggregateTypeConstants.CONDITION, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH, newCount);
        int newBusIdx = 0;
        List<Condition> toSave = new ArrayList<>();
        // Loop：遍历命令中的全量诊断列表，逐条组装为实体，统一校验后收集到待保存列表。
        // 分支处理：item.id 为空走新增路径创建空实体，非空走更新路径加载既有记录做覆盖写。
        // 约束说明：循环体内仅内存组装，结束后统一 saveAll（A.9.1 批量 I/O 约束），禁止循环内单条 save。
        for (SaveDoctorDiagnosisCommand.DoctorDiagnosisItemCommand item : resolvedItems) {
            Condition entity = this.loadOrCreate(item.getId());
            if (item.getId() == null) {
                entity.setBusNo(newBusNos.get(newBusIdx));
                newBusIdx++;
            }
            // Pre：统一绑定就诊与患者上下文，防止跨就诊串写。
            entity.setEncounterId(cmd.getEncounterId());
            entity.setPatientId(cmd.getPatientId());
            entity.setDefinitionId(item.getDefinitionId());
            entity.setVerificationStatusEnum(item.getVerificationStatusEnum());
            entity.setClinicalStatusEnum(item.getClinicalStatusEnum());
            entity.setCategoryEnum(item.getCategoryEnum());
            entity.setSeverityEnum(item.getSeverityEnum());
            entity.setDescription(item.getDescription());
            entity.setOnsetDatetime(item.getOnsetDatetime());
            entity.setRecordedDatetime(item.getRecordedDatetime());
            entity.setRecorderId(item.getRecorderId());
            entity.setYbNo(item.getYbNo());
            // Assert：BR-CND-001 诊断事实必须归属患者。
            entity.assertPatientBound();
            // Assert：BR-CND-002 保存诊断时必须刷新记录时间与记录人。
            entity.refreshRecordMeta(cmd.getOperatorId());
            toSave.add(entity);
        }
        List<Condition> saved = repository.saveAll(toSave);
        List<Long> ids = new ArrayList<>();
        boolean publishAdmission = Boolean.TRUE.equals(cmd.getAdmissionDiagnosisFlow());
        // Event：落库完成后逐条发布事件，保证事件与数据一致。
        for (int row = 0; row < saved.size(); row++) {
            Condition c = saved.get(row);
            domainEventPublisher.publish(new EncounterDiagnosisSavedEvent(c.getId(), c.getPatientId(), c.getDefinitionId(),
                c.getVerificationStatusEnum(), c.getClinicalStatusEnum()));
            // 入院场景：仅对本次新增病情事实补发 AdmissionDiagnosisCreatedEvent。
            if (publishAdmission && resolvedItems.get(row).getId() == null) {
                domainEventPublisher.publish(new AdmissionDiagnosisCreatedEvent(
                    c.getId(), c.getDefinitionId(), c.getPatientId()));
            }
            ids.add(c.getId());
        }
        return ids;
    }

    /**
     * 保存中医诊断记录（新增或更新）。
     *
     * <p>批量处理本次就诊的全量中医诊断列表；item 携带 id 时走更新路径，不携带 id 时走新增路径。
     * 遵循 A.9.1：循环仅在内存组装实体，最终统一 saveAll 落库，事件在落库完成后逐条发布。</p>
     *
     * @param cmd 保存命令，含就诊标识、患者标识、操作人及中医诊断明细列表
     * @return 本次保存/更新的诊断记录主键集合
     * @throws BusinessException 记录不存在、患者未绑定、记录元数据刷新规则不满足等业务规则失败时
     */
    public List<Long> saveDoctorDiagnosisTcm(SaveDoctorDiagnosisTcmCommand cmd) {
        List<SaveDoctorDiagnosisTcmCommand.DoctorDiagnosisTcmItemCommand> resolvedItems = cmd.resolveItems();
        int newCount = 0;
        for (SaveDoctorDiagnosisTcmCommand.DoctorDiagnosisTcmItemCommand item : resolvedItems) {
            if (item.getId() == null) {
                newCount++;
            }
        }
        List<String> newBusNos = businessSequenceService.getSeqByDay(AggregateTypeConstants.CONDITION, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH, newCount);
        int newBusIdx = 0;
        List<Condition> toSave = new ArrayList<>();
        // Loop：遍历命令项，仅内存组装实体，不触发数据库写操作（A.9.1 批量 I/O 约束）。
        for (SaveDoctorDiagnosisTcmCommand.DoctorDiagnosisTcmItemCommand item : resolvedItems) {
            // 分支处理：item 带 id 走更新路径，不带 id 走新增路径。
            Condition entity = this.loadOrCreate(item.getId());
            if (item.getId() == null) {
                entity.setBusNo(newBusNos.get(newBusIdx));
                newBusIdx++;
            }
            entity.setEncounterId(cmd.getEncounterId());
            entity.setPatientId(cmd.getPatientId());
            entity.setDefinitionId(item.getDefinitionId());
            entity.setClinicalStatusEnum(item.getClinicalStatusEnum());
            entity.setVerificationStatusEnum(item.getVerificationStatusEnum());
            entity.setDescription(item.getDescription());
            entity.setTcmFlag(item.getTcmFlag());
            entity.setRecordedDatetime(item.getRecordedDatetime());
            entity.setRecorderId(item.getRecorderId());
            // Assert：BR-CND-001 诊断事实必须归属患者。
            entity.assertPatientBound();
            // Assert：BR-CND-002 保存诊断时必须刷新记录时间与记录人。
            entity.refreshRecordMeta(cmd.getOperatorId());
            toSave.add(entity);
        }
        List<Condition> saved = repository.saveAll(toSave);
        List<Long> ids = new ArrayList<>();
        // Event：落库完成后逐条发布事件，保证事件与数据一致。
        for (Condition c : saved) {
            domainEventPublisher.publish(new EncounterDiagnosisSavedEvent(c.getId(), c.getPatientId(), c.getDefinitionId(),
                c.getVerificationStatusEnum(), c.getClinicalStatusEnum()));
            ids.add(c.getId());
        }
        return ids;
    }

    /**
     * 软删单条就诊诊断并发布删除事件。
     *
     * <p>步骤：按主键加载事实 → 医嘱引用门控（BR-CND-003）→ 软删元数据 → 单条持久化 →
     * 发布 {@link EncounterDiagnosisDeletedEvent}。持久化须在事件之前完成（B.14）。</p>
     *
     * @param cmd 删除命令，含病情事实主键与操作人
     * @throws BusinessException 记录不存在；或医嘱引用门控未通过（BR-CND-003，依赖
     *         {@link ConditionRepository#countAdviceRefByConditionId} 的真实实现）
     */
    public void delEncounterDiagnosis(DelEncounterDiagnosisCommand cmd) {
        Condition entity = repository.findById(cmd.getConditionId()).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.CONDITION_NOT_FOUND, "诊断记录不存在"));
        entity.assertNoAdviceReferenceBeforeDelete(repository.countAdviceRefByConditionId(cmd.getConditionId()));
        entity.markDeleted(cmd.getOperatorId());
        // 说明：删除路径单实体 save，无批量合并需求；仍须先于事件发布。
        repository.save(entity);
        domainEventPublisher.publish(new EncounterDiagnosisDeletedEvent(entity.getId(), entity.getPatientId()));
    }

    /**
     * 更新路径按主键加载既有实体；新增路径返回空壳实体，由调用方设置 busNo 与业务字段。
     *
     * @param id 病情事实主键；{@code null} 表示新增
     * @return 非 {@code null} 的 {@link Condition}
     * @throws BusinessException 更新路径且主键在库中不存在时
     */
    private Condition loadOrCreate(Long id) {
        if (id == null) {
            return new Condition();
        }
        return repository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.CONDITION_NOT_FOUND, "更新目标诊断不存在"));
    }
}
