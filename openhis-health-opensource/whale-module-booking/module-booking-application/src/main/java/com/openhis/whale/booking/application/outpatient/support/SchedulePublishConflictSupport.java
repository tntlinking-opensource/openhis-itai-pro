/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.outpatient.support;

import com.openhis.health.domain.share.enums.ScheduleActorKind;
import com.openhis.whale.booking.application.outpatient.response.PreviewPublishScheduleConflictsResponse;
import com.openhis.whale.booking.application.outpatient.response.SchedulePublishConflictDto;
import com.openhis.whale.booking.domain.schedule.queries.CalendarScheduleQuery;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleActorModel;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleCalendarItemModel;
import com.openhis.whale.booking.domain.schedule.service.ScheduleQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 发布前冲突检测（07 BR-EXT-005/006）。
 */
@Service
@RequiredArgsConstructor
public class SchedulePublishConflictSupport {

    private final ScheduleQueryService scheduleQueryService;

    /**
     * 检测与同窗已存在日程的医师/诊室冲突。
     */
    public PreviewPublishScheduleConflictsResponse previewConflicts(
        Long scheduleId,
        Long organizationId,
        LocalDateTime planningStart,
        LocalDateTime planningEnd,
        List<ScheduleActorModel> draftActors) {
        PreviewPublishScheduleConflictsResponse response = new PreviewPublishScheduleConflictsResponse();
        if (organizationId == null || planningStart == null || planningEnd == null) {
            return response;
        }
        CalendarScheduleQuery query = new CalendarScheduleQuery();
        query.setOrganizationId(organizationId);
        query.setRangeStart(planningStart.toLocalDate());
        query.setRangeEnd(planningEnd.toLocalDate());
        List<ScheduleCalendarItemModel> existing = scheduleQueryService.listSchedulesForCalendar(query);
        Set<Long> draftPractitioners = this.actorIds(draftActors, ScheduleActorKind.PRACTITIONER);
        Set<Long> draftLocations = this.actorIds(draftActors, ScheduleActorKind.LOCATION);
        for (ScheduleCalendarItemModel other : existing) {
            if (other.getId() == null || Objects.equals(other.getId(), scheduleId)) {
                continue;
            }
            if (!this.horizonOverlaps(planningStart, planningEnd, other.getPlanningHorizonStart(), other.getPlanningHorizonEnd())) {
                continue;
            }
            Set<Long> otherPractitioners = this.actorIds(other.getActors(), ScheduleActorKind.PRACTITIONER);
            for (Long practitionerId : draftPractitioners) {
                if (otherPractitioners.contains(practitionerId)) {
                    response.getConflicts().add(this.buildConflict(
                        "PRACTITIONER", practitionerId, other, "同人同时段存在已发布/草稿日程"));
                }
            }
            Set<Long> otherLocations = this.actorIds(other.getActors(), ScheduleActorKind.LOCATION);
            for (Long locationId : draftLocations) {
                if (otherLocations.contains(locationId)) {
                    response.getConflicts().add(this.buildConflict(
                        "LOCATION", locationId, other, "同诊室同时段存在已发布/草稿日程"));
                }
            }
        }
        response.setHasConflict(!response.getConflicts().isEmpty());
        return response;
    }

    private SchedulePublishConflictDto buildConflict(
        String conflictType, Long resourceId, ScheduleCalendarItemModel other, String message) {
        SchedulePublishConflictDto dto = new SchedulePublishConflictDto();
        dto.setConflictType(conflictType);
        dto.setResourceId(resourceId);
        dto.setConflictScheduleId(other.getId());
        dto.setConflictScheduleName(other.getName());
        dto.setMessage(message);
        return dto;
    }

    private Set<Long> actorIds(List<ScheduleActorModel> actors, ScheduleActorKind kind) {
        Set<Long> ids = new HashSet<>();
        if (actors == null || kind == null) {
            return ids;
        }
        int typeCode = kind.getSortOrder().intValue();
        for (ScheduleActorModel actor : actors) {
            if (actor == null || actor.getActorId() == null) {
                continue;
            }
            if (Objects.equals(typeCode, actor.getActorType())) {
                ids.add(actor.getActorId());
            }
        }
        return ids;
    }

    private boolean horizonOverlaps(
        LocalDateTime startA, LocalDateTime endA, LocalDateTime startB, LocalDateTime endB) {
        if (startA == null || endA == null || startB == null || endB == null) {
            return true;
        }
        return !startA.isAfter(endB) && !endA.isBefore(startB);
    }
}
