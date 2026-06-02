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

package com.openhis.health.domain.workflow.schedule.service;


import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.ScheduleStatus;
import com.openhis.health.domain.workflow.schedule.entity.Schedule;
import com.openhis.health.domain.workflow.schedule.entity.ScheduleActor;
import com.openhis.health.domain.workflow.schedule.queries.SchedulePageQuery;
import com.openhis.health.domain.workflow.schedule.queries.model.ScheduleActorDateModel;
import com.openhis.health.domain.workflow.schedule.queries.model.ScheduleActorModel;
import com.openhis.health.domain.workflow.schedule.queries.model.ScheduleDetailModel;
import com.openhis.health.domain.workflow.schedule.repository.ScheduleActorRepository;
import com.openhis.health.domain.workflow.schedule.repository.ScheduleRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 号源日程读侧查询服务（DM-WKF-009）。
 *
 * <p>职责：加载日程详情（含参与人）、按执业者与日期解析当日可用日程、机构下启用日程分页；只读不写库。</p>
 */
@Service
@RequiredArgsConstructor
public class ScheduleQueryService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleActorRepository scheduleActorRepository;


    /**
     * 按主键加载日程及参与人列表；不存在抛错。
     *
     * @param id 日程主键
     * @return 详情模型
     * @throws BusinessException 日程不存在
     */
    public ScheduleDetailModel loadScheduleById(Long id) {
        Schedule schedule = scheduleRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.SCHEDULE_NOT_FOUND, "号源日程不存在"));
        List<ScheduleActor> actors = scheduleActorRepository.findByScheduleIdOrderBySortNoAsc(schedule.getId());
        return this.toDetailModel(schedule, actors);
    }

    /**
     * 解析某执业者在指定日历日可排班的日程集合（计划窗口需覆盖该日全天）。
     *
     * @param actorType 参与者类型编码
     * @param actorId 参与者主键
     * @param date 日历日
     * @return 日程摘要列表；入参不完整或无参与关系时为空
     */
    public List<ScheduleActorDateModel> findSchedulesByActorAndDate(Integer actorType, Long actorId, LocalDate date) {
        if (actorType == null || actorId == null || date == null) {
            return new ArrayList<>();
        }
        List<ScheduleActor> actorRows = scheduleActorRepository.findByActorTypeAndActorIdOrderByScheduleIdAsc(actorType, actorId);
        if (actorRows.isEmpty()) {
            return new ArrayList<>();
        }

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        List<Long> scheduleIds = new ArrayList<>();
        for (ScheduleActor row : actorRows) {
            if (row.getScheduleId() != null) {
                scheduleIds.add(row.getScheduleId());
            }
        }
        if (scheduleIds.isEmpty()) {
            return new ArrayList<>();
        }

        Specification<Schedule> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(root.get(FieldNameConstants.ID).in(scheduleIds));
            jakarta.persistence.criteria.Predicate windowContains = cb.and(
                cb.or(cb.isNull(root.get(FieldNameConstants.PLANNING_HORIZON_START)), cb.lessThanOrEqualTo(root.get(FieldNameConstants.PLANNING_HORIZON_START), dayStart)),
                cb.or(cb.isNull(root.get(FieldNameConstants.PLANNING_HORIZON_END)), cb.greaterThan(root.get(FieldNameConstants.PLANNING_HORIZON_END), dayEnd))
            );
            predicates.add(windowContains);
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        List<Schedule> schedules = scheduleRepository.findAll(specification);

        List<ScheduleActorDateModel> result = new ArrayList<>();
        for (Schedule schedule : schedules) {
            ScheduleActorDateModel model = new ScheduleActorDateModel();
            model.setScheduleId(schedule.getId());
            model.setActorType(String.valueOf(actorType));
            model.setActorId(actorId);
            result.add(model);
        }
        return result;
    }

    /**
     * 机构维度分页查询「启用」状态号源日程，可按名称关键字过滤。
     *
     * @param organizationId 机构标识
     * @param query 可选关键字条件
     * @param pageable 分页
     * @return 分页详情（每条带参与人）
     */
    public Page<ScheduleDetailModel> pageActiveSchedulesByOrganization(Long organizationId, SchedulePageQuery query, Pageable pageable) {
        if (organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        Specification<Schedule> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId));
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), ScheduleStatus.ACTIVE.getCode()));
            if (query != null && StringUtils.isNotBlank(query.getKeyword())) {
                String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
                predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return scheduleRepository.findAll(specification, pageable).map(s -> {
            List<ScheduleActor> actors = scheduleActorRepository.findByScheduleIdOrderBySortNoAsc(s.getId());
            return this.toDetailModel(s, actors);
        });
    }

    /**
     * 组装日程详情：头信息 + 有序参与人列表。
     *
     * @param schedule 日程实体
     * @param actorRows 参与人行
     * @return 详情模型
     */
    private ScheduleDetailModel toDetailModel(Schedule schedule, List<ScheduleActor> actorRows) {
        ScheduleDetailModel model = new ScheduleDetailModel();
        model.setId(schedule.getId());
        model.setStatusEnum(schedule.getStatusEnum());
        List<ScheduleActorModel> actors = new ArrayList<>();
        for (ScheduleActor row : actorRows) {
            ScheduleActorModel a = new ScheduleActorModel();
            a.setActorType(row.getActorType());
            a.setActorId(row.getActorId());
            a.setSortNo(row.getSortNo());
            actors.add(a);
        }
        model.setActors(actors);
        return model;
    }
}
