/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.episodeofcare;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 治疗周期（EpisodeOfCare）领域事件集合（对齐 DM-ADM-013 §5.1）。
 */
public final class EpisodeOfCareDomainEvents {

    /**
     * 工具类私有构造器，禁止实例化。
     */
    private EpisodeOfCareDomainEvents() {
    }

    /**
     * 治疗周期已更新事件（DM-ADM-013 §5.1：变更字段摘要）。
     */
    @Getter
    @Setter
    public static class EpisodeOfCareUpdatedEvent extends DomainEvent {

        /** 变更字段摘要列表（如 ["careTeamId", "careGoal"]）。 */
        private List<String> changedFields;

        /** 默认构造器（框架反序列化用）。 */
        public EpisodeOfCareUpdatedEvent() {
            super();
        }

        /**
         * 全量载荷构造器。
         *
         * @param id            治疗周期主键
         * @param changedFields 本次变更的字段名列表
         */
        public EpisodeOfCareUpdatedEvent(Long id, List<String> changedFields) {
            super(id, AggregateTypeConstants.EPISODE_OF_CARE);
            this.changedFields = changedFields;
        }
    }

    /**
     * 治疗周期已启动事件（DM-ADM-013 §5.1：启动时间）。
     */
    @Getter
    @Setter
    public static class EpisodeOfCareActivatedEvent extends DomainEvent {

        /** 启动时间。 */
        private LocalDateTime activatedAt;

        /** 默认构造器（框架反序列化用）。 */
        public EpisodeOfCareActivatedEvent() {
            super();
        }

        /**
         * 全量载荷构造器。
         *
         * @param id          治疗周期主键
         * @param activatedAt 启动时间
         */
        public EpisodeOfCareActivatedEvent(Long id, LocalDateTime activatedAt) {
            super(id, AggregateTypeConstants.EPISODE_OF_CARE);
            this.activatedAt = activatedAt;
        }
    }

    /**
     * 就诊已加入治疗周期事件（DM-ADM-013 §5.1）。
     */
    @Getter
    @Setter
    public static class EncounterAddedToEpisodeEvent extends DomainEvent {

        /** 加入的就诊标识。 */
        private Long encounterId;

        /** 默认构造器（框架反序列化用）。 */
        public EncounterAddedToEpisodeEvent() {
            super();
        }

        /**
         * 全量载荷构造器。
         *
         * @param id          治疗周期主键
         * @param encounterId 加入的就诊主键
         */
        public EncounterAddedToEpisodeEvent(Long id, Long encounterId) {
            super(id, AggregateTypeConstants.EPISODE_OF_CARE);
            this.encounterId = encounterId;
        }
    }

    /**
     * 治疗周期已暂停事件（DM-ADM-013 §5.1：暂停原因）。
     */
    @Getter
    @Setter
    public static class EpisodeOfCarePausedEvent extends DomainEvent {

        /** 暂停原因描述。 */
        private String pauseReason;

        /** 默认构造器（框架反序列化用）。 */
        public EpisodeOfCarePausedEvent() {
            super();
        }

        /**
         * 全量载荷构造器。
         *
         * @param id          治疗周期主键
         * @param pauseReason 暂停原因（可空）
         */
        public EpisodeOfCarePausedEvent(Long id, String pauseReason) {
            super(id, AggregateTypeConstants.EPISODE_OF_CARE);
            this.pauseReason = pauseReason;
        }
    }

    /**
     * 治疗周期已恢复事件（DM-ADM-013 §5.1：恢复时间）。
     */
    @Getter
    @Setter
    public static class EpisodeOfCareResumedEvent extends DomainEvent {

        /** 恢复时间。 */
        private LocalDateTime resumedAt;

        /** 默认构造器（框架反序列化用）。 */
        public EpisodeOfCareResumedEvent() {
            super();
        }

        /**
         * 全量载荷构造器。
         *
         * @param id        治疗周期主键
         * @param resumedAt 恢复时间
         */
        public EpisodeOfCareResumedEvent(Long id, LocalDateTime resumedAt) {
            super(id, AggregateTypeConstants.EPISODE_OF_CARE);
            this.resumedAt = resumedAt;
        }
    }

    /**
     * 治疗周期已完成事件（DM-ADM-013 §5.1：结束时间、治疗结果摘要）。
     */
    @Getter
    @Setter
    public static class EpisodeOfCareCompletedEvent extends DomainEvent {

        /** 结束时间。 */
        private LocalDateTime endTime;

        /** 治疗结果摘要（可空；如"病情好转"、"转院"等）。 */
        private String outcome;

        /** 默认构造器（框架反序列化用）。 */
        public EpisodeOfCareCompletedEvent() {
            super();
        }

        /**
         * 全量载荷构造器。
         *
         * @param id      治疗周期主键
         * @param endTime 结束时间
         * @param outcome 治疗结果摘要（可空）
         */
        public EpisodeOfCareCompletedEvent(Long id, LocalDateTime endTime, String outcome) {
            super(id, AggregateTypeConstants.EPISODE_OF_CARE);
            this.endTime = endTime;
            this.outcome = outcome;
        }
    }

    /**
     * 治疗周期已取消事件（DM-ADM-013 §5.1：取消原因）。
     */
    @Getter
    @Setter
    public static class EpisodeOfCareCancelledEvent extends DomainEvent {

        /** 取消原因描述。 */
        private String cancelReason;

        /** 默认构造器（框架反序列化用）。 */
        public EpisodeOfCareCancelledEvent() {
            super();
        }

        /**
         * 全量载荷构造器。
         *
         * @param id           治疗周期主键
         * @param cancelReason 取消原因（可空）
         */
        public EpisodeOfCareCancelledEvent(Long id, String cancelReason) {
            super(id, AggregateTypeConstants.EPISODE_OF_CARE);
            this.cancelReason = cancelReason;
        }
    }

    /**
     * 治疗周期已标记错误录入事件（DM-ADM-013 §5.1：原因）。
     */
    @Getter
    @Setter
    public static class EpisodeOfCareEnteredInErrorEvent extends DomainEvent {

        /** 标记错误录入的原因描述。 */
        private String reason;

        /** 默认构造器（框架反序列化用）。 */
        public EpisodeOfCareEnteredInErrorEvent() {
            super();
        }

        /**
         * 全量载荷构造器。
         *
         * @param id     治疗周期主键
         * @param reason 错误录入原因（可空）
         */
        public EpisodeOfCareEnteredInErrorEvent(Long id, String reason) {
            super(id, AggregateTypeConstants.EPISODE_OF_CARE);
            this.reason = reason;
        }
    }
}
