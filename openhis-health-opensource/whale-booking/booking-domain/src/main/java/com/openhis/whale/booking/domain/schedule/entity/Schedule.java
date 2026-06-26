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

package com.openhis.whale.booking.domain.schedule.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.ScheduleStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 号源日程聚合根（DM-WKF-009）。
 *
 * <p>对应表：wkf_schedule。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "wkf_schedule")
public class Schedule extends BaseAuditedEntity {

    /** 机构标识。 */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /** 显示名称。 */
    @Column(name = "name")
    private String name;

    /** 业务状态（draft/active/inactive/retired）。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /** 启用标记（与 FHIR Schedule.active 对齐）。 */
    @Column(name = "active_flag", nullable = false)
    private Boolean activeFlag;

    /** 规划窗口起。 */
    @Column(name = "planning_horizon_start")
    private LocalDateTime planningHorizonStart;

    /** 规划窗口止。 */
    @Column(name = "planning_horizon_end")
    private LocalDateTime planningHorizonEnd;

    /** 服务类型概念快照 JSON。 */
    @Column(name = "service_type_concept_json")
    private String serviceTypeConceptJson;

    /** 医疗服务引用。 */
    @Column(name = "healthcare_service_id")
    private Long healthcareServiceId;

    /** 服务大类快照 JSON。 */
    @Column(name = "service_category_json")
    private String serviceCategoryJson;

    /** 专科快照 JSON。 */
    @Column(name = "specialty_json")
    private String specialtyJson;

    /** 可用性说明 markdown。 */
    @Column(name = "comment_markdown")
    private String commentMarkdown;

    /** 替诊医师（顶班 B；原医师保留在 Actor）。 */
    @Column(name = "substitute_practitioner_id")
    private Long substitutePractitionerId;

    /** 替诊原因。 */
    @Column(name = "substitute_reason")
    private String substituteReason;

    /**
     * 参与者集合非空断言（BR-SCH-001）。
     *
     * @param actorCount 参与者数量
     */
    public void assertActorNotEmpty(Integer actorCount) {
        if (actorCount == null || actorCount <= 0) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "actors 不能为空");
        }
    }

    /**
     * 初始化草稿（DRAFT，activeFlag=false）。
     */
    public void initDraft() {
        this.statusEnum = ScheduleStatus.DRAFT.getCode();
        this.activeFlag = false;
    }

    /**
     * 发布前断言（BR-SCH-002/003/004）。
     */
    public void assertPublishable(Integer actorCount, LocalDateTime start, LocalDateTime end) {
        if (!ScheduleStatus.DRAFT.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "仅草稿状态允许发布");
        }
        this.assertActorNotEmpty(actorCount);
        this.assertPlanningWindowValid(start, end);
    }

    /**
     * 规划窗口合法性断言（BR-SCH-002）。
     */
    public void assertPlanningWindowValid(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null) {
            if (!end.isAfter(start)) {
                throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "planningHorizonEnd 必须大于 planningHorizonStart");
            }
        }
    }

    /**
     * 草稿 -> 已启用（BR-SCH-003）。
     */
    public void draftToActive(LocalDateTime start, LocalDateTime end) {
        this.statusEnum = ScheduleStatus.ACTIVE.getCode();
        this.activeFlag = true;
        this.planningHorizonStart = start;
        this.planningHorizonEnd = end;
    }

    /**
     * 退役终态保护（BR-SCH-004）。
     */
    public void assertNotRetired() {
        if (ScheduleStatus.RETIRED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "已废弃日程不允许修改");
        }
    }

    /**
     * 调整规划窗口前断言。
     */
    public void assertPlanningHorizonAdjustable(LocalDateTime newStart, LocalDateTime newEnd) {
        this.assertNotRetired();
        this.assertPlanningWindowValid(newStart, newEnd);
    }

    /**
     * 调整规划窗口（不改变业务状态）。
     */
    public void adjustPlanningHorizon(LocalDateTime newStart, LocalDateTime newEnd) {
        this.planningHorizonStart = newStart;
        this.planningHorizonEnd = newEnd;
    }

    /**
     * 更新服务口径前断言。
     */
    public void assertServiceProfileValid() {
        this.assertNotRetired();
    }

    /**
     * 更新日程关联的服务口径（JSON 快照字段 + 卫生服务主键）。
     *
     * @param serviceCategoryJson 服务分类 JSON
     * @param specialtyJson 专科 JSON
     * @param serviceTypeConceptJson 服务类型概念 JSON
     * @param healthcareServiceId 卫生服务主键
     */
    public void updateServiceProfile(String serviceCategoryJson, String specialtyJson, String serviceTypeConceptJson, Long healthcareServiceId) {
        this.serviceCategoryJson = serviceCategoryJson;
        this.specialtyJson = specialtyJson;
        this.serviceTypeConceptJson = serviceTypeConceptJson;
        this.healthcareServiceId = healthcareServiceId;
    }

    /**
     * 更新说明前断言。
     */
    public void assertCommentLength(String commentMarkdown) {
        this.assertNotRetired();
        if (commentMarkdown != null && commentMarkdown.length() > 20000) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "commentMarkdown 过长");
        }
    }

    /**
     * 更新日程说明（Markdown）。
     *
     * @param commentMarkdown 说明正文
     */
    public void updateComment(String commentMarkdown) {
        this.commentMarkdown = commentMarkdown;
    }

    /**
     * 更新日程显示名称。
     *
     * @param name 名称（可为空表示清除自定义名）
     */
    public void updateName(String name) {
        this.assertNotRetired();
        this.name = name;
    }

    /**
     * 停用前断言。
     */
    public void assertDeactivatable() {
        if (!ScheduleStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "仅已启用日程允许停用");
        }
    }

    /**
     * 已启用 -> 已停用。
     */
    public void activeToInactive() {
        this.statusEnum = ScheduleStatus.INACTIVE.getCode();
        this.activeFlag = false;
    }

    /**
     * 替诊前断言：仅已发布日程可设置/撤销替诊（BR-SUB-001）。
     */
    public void assertSubstitutable() {
        this.assertNotRetired();
        if (!ScheduleStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "仅已发布日程允许替诊");
        }
    }

    /**
     * 设置替诊医师（不改 Actor、不迁移预约）。
     */
    public void applySubstitute(Long substitutePractitionerId, String reason) {
        this.assertSubstitutable();
        if (substitutePractitionerId == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "substitutePractitionerId 不能为空");
        }
        this.substitutePractitionerId = substitutePractitionerId;
        this.substituteReason = reason;
    }

    /**
     * 撤销替诊。
     */
    public void clearSubstitute() {
        this.assertSubstitutable();
        this.substitutePractitionerId = null;
        this.substituteReason = null;
    }

    /**
     * 恢复前断言。
     */
    public void assertReactivatable() {
        if (!ScheduleStatus.INACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "仅已停用日程允许恢复");
        }
    }

    /**
     * 已停用 -> 已启用。
     */
    public void inactiveToActive() {
        this.statusEnum = ScheduleStatus.ACTIVE.getCode();
        this.activeFlag = true;
    }

    /**
     * 废弃前断言。
     */
    public void assertRetirable() {
        if (ScheduleStatus.RETIRED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "已废弃日程不允许重复废弃");
        }
    }

    /**
     * -> 已废弃。
     */
    public void toRetired() {
        this.statusEnum = ScheduleStatus.RETIRED.getCode();
        this.activeFlag = false;
    }
}

