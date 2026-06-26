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

package com.openhis.health.domain.administration.episodeofcare.service;

import com.openhis.health.domain.administration.episodeofcare.command.ActivateEpisodeOfCareCommand;
import com.openhis.health.domain.administration.episodeofcare.command.AddEncounterToEpisodeCommand;
import com.openhis.health.domain.administration.episodeofcare.command.ChangeEpisodeOfCareStatusCommand;
import com.openhis.health.domain.administration.episodeofcare.command.CreateEpisodeOfCareCommand;
import com.openhis.health.domain.administration.episodeofcare.command.UpdateEpisodeOfCareProfileCommand;
import com.openhis.health.domain.administration.episodeofcare.entity.EpisodeOfCare;
import com.openhis.health.domain.administration.episodeofcare.entity.EpisodeOfCareEncounter;
import com.openhis.health.domain.administration.episodeofcare.repository.EpisodeOfCareEncounterRepository;
import com.openhis.health.domain.administration.episodeofcare.repository.EpisodeOfCareRepository;
import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.EpisodeOfCareStatus;
import com.openhis.health.domain.share.events.administration.episodeofcare.EpisodeOfCareCreatedEvent;
import com.openhis.health.domain.share.events.administration.episodeofcare.EpisodeOfCareDomainEvents;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 治疗周期（Episode of Care）聚合写侧领域服务：创建与激活、照护资料维护、就诊纳入及暂停/恢复/完成/取消/误录等状态流转。
 */
@Service
@RequiredArgsConstructor
public class EpisodeOfCareDomainService {
    private final EpisodeOfCareRepository repository;
    private final EpisodeOfCareEncounterRepository encounterRepository;
    private final IDomainEventPublisher domainEventPublisher;
    private final BusinessSequenceService businessSequenceService;


    /**
     * 新建治疗周期（初始为未激活，状态为「计划中」语义）。
     *
     * @param cmd 创建命令（患者、机构等；业务号由领域层按日采番）
     * @return 新建聚合主键
     */
    public Long createEpisodeOfCare(CreateEpisodeOfCareCommand cmd) {
        EpisodeOfCare row = new EpisodeOfCare();
        row.setPatientId(cmd.getPatientId());
        row.setOrganizationId(cmd.getOrganizationId());
        // 业务分支：治疗周期号为业务流水，按 EC 前缀与当日序号生成（B.10.9）。
        row.setBusNo(businessSequenceService.getSeqByDay(
            AggregateTypeConstants.EPISODE_OF_CARE, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        row.setStatusEnum(EpisodeOfCareStatus.PLANNED.getCode());
        row.setActiveFlag(Boolean.FALSE);
        row = repository.save(row);
        domainEventPublisher.publish(new EpisodeOfCareCreatedEvent(
            row.getId(), row.getPatientId(), row.getBusNo(), row.getOrganizationId(), row.getStatusEnum()));
        return row.getId();
    }

    /**
     * 激活治疗周期：置为进行中并记录实际开始时间，发布激活领域事件。
     *
     * @param cmd 激活命令（治疗周期主键、激活时间）
     */
    public void activateEpisodeOfCare(ActivateEpisodeOfCareCommand cmd) {
        EpisodeOfCare row = this.requireById(cmd.getEpisodeOfCareId());
        row.setStatusEnum(EpisodeOfCareStatus.ACTIVE.getCode());
        row.setActiveFlag(Boolean.TRUE);
        row.setStartTime(cmd.getActivatedAt());
        repository.save(row);
        // 携带启动时间（DM-ADM-013 §5.1）。
        domainEventPublisher.publish(new EpisodeOfCareDomainEvents.EpisodeOfCareActivatedEvent(row.getId(), cmd.getActivatedAt()));
    }

    /**
     * 更新治疗周期照护资料（团队、类型、目标等），先落库再发更新事件。
     *
     * @param cmd 资料更新命令
     */
    public void updateEpisodeOfCareProfile(UpdateEpisodeOfCareProfileCommand cmd) {
        // 先加载聚合，确保在有效治疗周期上执行资料更新。
        EpisodeOfCare row = this.requireById(cmd.getEpisodeOfCareId());
        // 更新照护资料字段，避免出现“发事件但实体未变更”的空更新。
        row.setCareTeamId(cmd.getCareTeamId());
        row.setCareType(cmd.getCareType());
        row.setCareGoal(cmd.getCareGoal());

        // 先落库后发事件，避免 save 失败时产生幽灵事件。
        repository.save(row);
        // 携带本次变更字段摘要（DM-ADM-013 §5.1）。
        List<String> changedFields = Arrays.asList("careTeamId", "careType", "careGoal");
        domainEventPublisher.publish(new EpisodeOfCareDomainEvents.EpisodeOfCareUpdatedEvent(row.getId(), changedFields));
    }
    /**
     * 将一次就诊纳入本治疗周期（写入关联表并发布事件）。
     *
     * @param cmd 纳入命令（治疗周期主键、就诊主键）
     */
    public void addEncounterToEpisode(AddEncounterToEpisodeCommand cmd) {
        EpisodeOfCare row = this.requireById(cmd.getEpisodeOfCareId());
        EpisodeOfCareEncounter relation = new EpisodeOfCareEncounter();
        relation.setEpisodeOfCareId(row.getId());
        relation.setEncounterId(cmd.getEncounterId());
        relation.setJoinTime(LocalDateTime.now());
        encounterRepository.save(relation);
        domainEventPublisher.publish(new EpisodeOfCareDomainEvents.EncounterAddedToEpisodeEvent(row.getId(), cmd.getEncounterId()));
    }
    /**
     * 暂停治疗周期，携带暂停原因发布领域事件。
     *
     * @param cmd 状态变更命令（主键、原因等）
     */
    public void pauseEpisodeOfCare(ChangeEpisodeOfCareStatusCommand cmd) {
        EpisodeOfCare row = this.requireById(cmd.getEpisodeOfCareId());
        row.setStatusEnum(EpisodeOfCareStatus.ON_HOLD.getCode());
        repository.save(row);
        // 携带暂停原因（DM-ADM-013 §5.1）。
        domainEventPublisher.publish(new EpisodeOfCareDomainEvents.EpisodeOfCarePausedEvent(row.getId(), cmd.getReason()));
    }
    /**
     * 恢复已暂停的治疗周期为进行中，携带恢复时间发布领域事件。
     *
     * @param cmd 状态变更命令（主键、发生时间等）
     */
    public void resumeEpisodeOfCare(ChangeEpisodeOfCareStatusCommand cmd) {
        EpisodeOfCare row = this.requireById(cmd.getEpisodeOfCareId());
        row.setStatusEnum(EpisodeOfCareStatus.ACTIVE.getCode());
        repository.save(row);
        // 携带恢复时间（DM-ADM-013 §5.1）。
        domainEventPublisher.publish(new EpisodeOfCareDomainEvents.EpisodeOfCareResumedEvent(row.getId(), cmd.getOccurredAt()));
    }
    /**
     * 正常结束治疗周期：置为非活跃并写入结束时间；结果摘要经 {@code reason} 传入并随事件下发。
     *
     * @param cmd 状态变更命令（主键、结束时间、结果/原因摘要）
     */
    public void completeEpisodeOfCare(ChangeEpisodeOfCareStatusCommand cmd) {
        EpisodeOfCare row = this.requireById(cmd.getEpisodeOfCareId());
        row.setStatusEnum(EpisodeOfCareStatus.COMPLETED.getCode());
        row.setActiveFlag(Boolean.FALSE);
        row.setEndTime(cmd.getOccurredAt());
        repository.save(row);
        // 携带结束时间与治疗结果摘要（DM-ADM-013 §5.1；outcome 由应用服务通过 reason 字段传入）。
        domainEventPublisher.publish(new EpisodeOfCareDomainEvents.EpisodeOfCareCompletedEvent(row.getId(), cmd.getOccurredAt(), cmd.getReason()));
    }
    /**
     * 取消治疗周期（非完成路径），携带取消原因发布领域事件。
     *
     * @param cmd 状态变更命令（主键、原因等）
     */
    public void cancelEpisodeOfCare(ChangeEpisodeOfCareStatusCommand cmd) {
        EpisodeOfCare row = this.requireById(cmd.getEpisodeOfCareId());
        row.setStatusEnum(EpisodeOfCareStatus.CANCELLED.getCode());
        row.setActiveFlag(Boolean.FALSE);
        repository.save(row);
        // 携带取消原因（DM-ADM-013 §5.1）。
        domainEventPublisher.publish(new EpisodeOfCareDomainEvents.EpisodeOfCareCancelledEvent(row.getId(), cmd.getReason()));
    }
    /**
     * 将治疗周期标记为误录/错误建档终态，携带原因发布领域事件。
     *
     * @param cmd 状态变更命令（主键、原因等）
     */
    public void markEpisodeOfCareEnteredInError(ChangeEpisodeOfCareStatusCommand cmd) {
        EpisodeOfCare row = this.requireById(cmd.getEpisodeOfCareId());
        row.setStatusEnum(EpisodeOfCareStatus.ENTERED_IN_ERROR.getCode());
        row.setActiveFlag(Boolean.FALSE);
        repository.save(row);
        // 携带错误录入原因（DM-ADM-013 §5.1）。
        domainEventPublisher.publish(new EpisodeOfCareDomainEvents.EpisodeOfCareEnteredInErrorEvent(row.getId(), cmd.getReason()));
    }

    /**
     * 按主键加载治疗周期，不存在则抛出业务异常。
     *
     * @param id 治疗周期主键
     * @return 聚合根实体
     * @throws BusinessException 主键不存在时 {@link ErrorCodeConstants#EPISODE_OF_CARE_NOT_FOUND}
     */
    private EpisodeOfCare requireById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.EPISODE_OF_CARE_NOT_FOUND, "治疗周期不存在"));
    }
}

