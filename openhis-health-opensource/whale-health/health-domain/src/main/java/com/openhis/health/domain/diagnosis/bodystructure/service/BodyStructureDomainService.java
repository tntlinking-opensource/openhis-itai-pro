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

package com.openhis.health.domain.diagnosis.bodystructure.service;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.diagnosis.bodystructure.command.ActiveBodyStructureCommand;
import com.openhis.health.domain.diagnosis.bodystructure.command.AddOrEditBodyStructureCommand;
import com.openhis.health.domain.diagnosis.bodystructure.command.DeleteBodyStructureCommand;
import com.openhis.health.domain.diagnosis.bodystructure.command.InactiveBodyStructureCommand;
import com.openhis.health.domain.diagnosis.bodystructure.entity.BodyStructure;
import com.openhis.health.domain.diagnosis.bodystructure.repository.BodyStructureRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.events.diagnosis.bodystructure.BodyStructureActivatedEvent;
import com.openhis.health.domain.share.events.diagnosis.bodystructure.BodyStructureCreatedEvent;
import com.openhis.health.domain.share.events.diagnosis.bodystructure.BodyStructureDeletedEvent;
import com.openhis.health.domain.share.events.diagnosis.bodystructure.BodyStructureInactivatedEvent;
import com.openhis.health.domain.share.events.diagnosis.bodystructure.BodyStructureModifiedEvent;
import com.openhis.health.domain.share.utils.CommandBatchUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 身体部位（解剖部位）写侧领域服务。
 *
 * <p>批量新增或修改部位树节点、软删、启停；新建行分配业务编码并校验同编码不冲突，成功后发布对应领域事件。</p>
 */
@Service
@RequiredArgsConstructor
public class BodyStructureDomainService {
    private final BodyStructureRepository repository;
    private final IDomainEventPublisher domainEventPublisher;
    private final BusinessSequenceService businessSequenceService;


    /**
     * 批量新增或编辑身体部位：无 id 则分配 busNo 并视为新建；有 id 则加载后更新。首条保存结果的主键作为返回值（与批量保存语义一致）。
     *
     * @param command 批量明细命令
     * @return 保存集合中第一条的主键；无保存行时为 {@code null}
     * @throws BusinessException 名称/编码等业务不变式失败或编码冲突时
     */
    public Long addOrEditBodyStructure(AddOrEditBodyStructureCommand command) {
        // Pre：解析批量命令项，获取待处理的身体部位明细列表
        List<AddOrEditBodyStructureCommand.AddOrEditBodyStructureItemCommand> items = command.resolveItems();
        List<BodyStructure> toSave = new ArrayList<>();
        List<BodyStructure> created = new ArrayList<>();
        List<BodyStructure> updated = new ArrayList<>();
        // Loop：遍历命令项，逐条处理新增或编辑逻辑
        for (AddOrEditBodyStructureCommand.AddOrEditBodyStructureItemCommand item : items) {
            BodyStructure bodyStructure;
            // 业务分支处理：无 id 视为新增，分配业务编码并初始化启用状态
            if (item.getId() == null) {
                bodyStructure = new BodyStructure();
                bodyStructure.setBusNo(businessSequenceService.getSeq(AggregateTypeConstants.BODY_STRUCTURE, BusinessSequenceConstants.MASTER_DATA_2_PREFIX_SEQ_LENGTH));
                bodyStructure.setStatusEnum(PublicationStatus.ACTIVE.getCode());
                created.add(bodyStructure);
            } else {
                // 业务分支处理：有 id 视为编辑，按主键加载现有实体
                bodyStructure = this.loadBodyStructure(item.getId());
                updated.add(bodyStructure);
            }
            bodyStructure.setName(item.getName());
            bodyStructure.setParentId(item.getParentId());
            // 业务分支处理：命令显式传入状态时覆盖，否则保持既有状态
            if (item.getStatusEnum() != null) {
                bodyStructure.setStatusEnum(item.getStatusEnum());
            }
            bodyStructure.assertNameNotBlank();
            bodyStructure.assertBusNoGenerated();
            bodyStructure.refreshSearchCodeFromName();
            this.assertBusNoNotConflict(bodyStructure);
            toSave.add(bodyStructure);
        }
        List<BodyStructure> saved = repository.saveAll(toSave);
        // Event：先发布创建事件，再发布修改事件；事件在 saveAll 后投递保证事务内一致性
        for (BodyStructure bodyStructure : created) {
            domainEventPublisher.publish(new BodyStructureCreatedEvent(
                bodyStructure.getId(), bodyStructure.getBusNo(), bodyStructure.getName(), bodyStructure.getStatusEnum()));
        }
        for (BodyStructure bodyStructure : updated) {
            domainEventPublisher.publish(new BodyStructureModifiedEvent(
                bodyStructure.getId(), List.of("name", "statusEnum", "pyStr", "wbStr")));
        }
        // Asm：返回首条记录主键，与批量保存语义保持一致
        if (saved.isEmpty()) {
            return null;
        }
        return saved.get(0).getId();
    }

    /**
     * 批量软删身体部位。
     *
     * @param command 须含待删主键与操作人
     * @throws BusinessException id 为空、记录缺失或主键集合不完整时
     */
    public void deleteBodyStructure(DeleteBodyStructureCommand command) {
        // Pre：解析批量命令项，提取待删除主键列表
        List<DeleteBodyStructureCommand.DeleteBodyStructureItemCommand> items = command.resolveItems();
        List<Long> ids = CommandBatchUtil.extractIds(items, DeleteBodyStructureCommand.DeleteBodyStructureItemCommand::getId);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.BODY_STRUCTURE_INVALID_ARGUMENT, "删除身体部位时 id 不能为空");
        }
        // Pre：批量查询待删除实体，按主键建索引加速后续比对
        List<BodyStructure> bodyStructures = repository.findByIdIn(ids);
        if (bodyStructures.size() != ids.size()) {
            throw new BusinessException(ErrorCodeConstants.BODY_STRUCTURE_NOT_FOUND, "删除身体部位时存在无效主键");
        }
        // Loop：遍历实体逐条标记软删，注入操作人审计字段
        for (BodyStructure bodyStructure : bodyStructures) {
            bodyStructure.markDeleted(command.getOperatorId());
        }
        repository.saveAll(bodyStructures);
        // Event：软删完成后发布删除事件，供下游投影器同步读模型
        domainEventPublisher.publish(new BodyStructureDeletedEvent(ids));
    }

    /**
     * 批量启用身体部位（发布启用事件）。
     *
     * @param command 批量主键明细
     * @throws BusinessException 记录不存在时
     */
    public void activeBodyStructure(ActiveBodyStructureCommand command) {
        // Pre：解析批量命令项，获取待启用主键列表
        List<ActiveBodyStructureCommand.ActiveBodyStructureItemCommand> items = command.resolveItems();
        List<BodyStructure> toSave = new ArrayList<>();
        // Loop：遍历命令项，逐条加载并执行启用状态迁移
        for (ActiveBodyStructureCommand.ActiveBodyStructureItemCommand item : items) {
            // Pre：按主键加载部位实体，不存在则抛出业务异常
            BodyStructure bodyStructure = this.loadBodyStructure(item.getId());
            bodyStructure.activate();
            toSave.add(bodyStructure);
        }
        repository.saveAll(toSave);
        // Event：saveAll 完成后发布启用事件，供下游读模型同步
        for (BodyStructure bodyStructure : toSave) {
            domainEventPublisher.publish(new BodyStructureActivatedEvent(bodyStructure.getId(), bodyStructure.getStatusEnum()));
        }
    }

    /**
     * 批量停用身体部位：仅当前为启用态的可停用（由聚合根断言）。
     *
     * @param command 批量主键明细
     * @throws BusinessException 记录不存在或状态不允许停用时
     */
    public void inactiveBodyStructure(InactiveBodyStructureCommand command) {
        // Pre：解析批量命令项，获取待停用主键列表
        List<InactiveBodyStructureCommand.InactiveBodyStructureItemCommand> items = command.resolveItems();
        List<BodyStructure> toSave = new ArrayList<>();
        // Loop：遍历命令项，逐条加载并执行停用状态迁移
        for (InactiveBodyStructureCommand.InactiveBodyStructureItemCommand item : items) {
            // Pre：按主键加载部位实体，不存在则抛出业务异常
            BodyStructure bodyStructure = this.loadBodyStructure(item.getId());
            bodyStructure.assertActive();
            bodyStructure.inactivate();
            toSave.add(bodyStructure);
        }
        repository.saveAll(toSave);
        // Event：saveAll 完成后发布停用事件，供下游读模型同步
        for (BodyStructure bodyStructure : toSave) {
            domainEventPublisher.publish(new BodyStructureInactivatedEvent(bodyStructure.getId(), bodyStructure.getStatusEnum()));
        }
    }

    /**
     * 按主键加载部位实体，不存在则抛出业务异常。
     *
     * @param id 身体部位主键
     * @return 身体部位聚合根实体
     * @throws BusinessException 当记录不存在时
     */
    private BodyStructure loadBodyStructure(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.BODY_STRUCTURE_NOT_FOUND, "身体部位不存在"));
    }

    /**
     * 新建或更新时校验 busNo 不与其它行冲突。
     *
     * @param bodyStructure 待校验的身体部位实体
     * @throws BusinessException 当业务编码与现有记录冲突时
     */
    private void assertBusNoNotConflict(BodyStructure bodyStructure) {
        // 业务分支处理：新建场景，仅校验全局唯一性
        if (bodyStructure.getId() == null) {
            if (repository.existsByBusNo(bodyStructure.getBusNo())) {
                throw new BusinessException(ErrorCodeConstants.BODY_STRUCTURE_BUS_NO_CONFLICT, "身体部位业务编码冲突");
            }
            return;
        }
        // 业务分支处理：更新场景，排除自身后校验唯一性
        if (repository.existsByBusNoAndIdNot(bodyStructure.getBusNo(), bodyStructure.getId())) {
            throw new BusinessException(ErrorCodeConstants.BODY_STRUCTURE_BUS_NO_CONFLICT, "身体部位业务编码冲突");
        }
    }

}
