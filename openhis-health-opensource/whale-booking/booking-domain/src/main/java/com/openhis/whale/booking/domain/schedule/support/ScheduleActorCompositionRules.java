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

package com.openhis.whale.booking.domain.schedule.support;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.ScheduleActorKind;
import com.openhis.whale.core.application.exception.BusinessException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 门诊 Schedule Actor 组合规则（07 §2.2 v2）。
 *
 * <p>校验参与者非空、唯一性及 MODE-A/B/C 最小组合；LOCATION 可为空。</p>
 */
public final class ScheduleActorCompositionRules {

    private static final int PRACTITIONER_TYPE = ScheduleActorKind.PRACTITIONER.getSortOrder().intValue();
    private static final int LOCATION_TYPE = ScheduleActorKind.LOCATION.getSortOrder().intValue();
    private static final int HEALTHCARE_SERVICE_TYPE = ScheduleActorKind.HEALTHCARE_SERVICE.getSortOrder().intValue();

    private ScheduleActorCompositionRules() {
    }

    /**
     * 门诊 O 线参与者组合断言（创建/替换/发布共用）。
     *
     * @param actors 参与者类型与主键列表；每项须含 actorType、actorId
     * @throws BusinessException 列表为空、字段缺失、重复或组合不满足 v2 规则时
     */
    public static void assertOutpatientComposition(List<ScheduleActorRef> actors) {
        if (actors == null || actors.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "actors 不能为空");
        }
        Set<String> uniqueKeys = new HashSet<>();
        boolean hasPractitioner = false;
        boolean hasLocation = false;
        boolean hasHealthcareService = false;
        for (ScheduleActorRef actor : actors) {
            if (actor == null || actor.getActorType() == null || actor.getActorId() == null) {
                throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "actorType/actorId 不能为空");
            }
            if (!ScheduleActorCompositionRules.isOutpatientActorType(actor.getActorType())) {
                throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "门诊排班不支持的参与者类型");
            }
            String uniqueKey = actor.getActorType() + "\u0001" + actor.getActorId();
            if (uniqueKeys.contains(uniqueKey)) {
                throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "actor 组合不允许重复");
            }
            uniqueKeys.add(uniqueKey);
            if (PRACTITIONER_TYPE == actor.getActorType()) {
                hasPractitioner = true;
            }
            if (LOCATION_TYPE == actor.getActorType()) {
                hasLocation = true;
            }
            if (HEALTHCARE_SERVICE_TYPE == actor.getActorType()) {
                hasHealthcareService = true;
            }
        }
        if (!hasHealthcareService) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "须选择号源类型（HEALTHCARE_SERVICE）");
        }
    }

    /**
     * 推断门诊 Schedule 模式码（07 §2.1）。
     *
     * @param actors 已通过 {@link #assertOutpatientComposition} 的参与者列表
     * @return MODE-A / MODE-B / MODE-C
     */
    public static String resolveScheduleMode(List<ScheduleActorRef> actors) {
        boolean hasPractitioner = false;
        boolean hasLocation = false;
        for (ScheduleActorRef actor : actors) {
            if (actor.getActorType() == PRACTITIONER_TYPE) {
                hasPractitioner = true;
            }
            if (actor.getActorType() == LOCATION_TYPE) {
                hasLocation = true;
            }
        }
        if (hasLocation && !hasPractitioner) {
            return "MODE-C";
        }
        if (hasPractitioner) {
            return "MODE-A";
        }
        return "MODE-B";
    }

    private static boolean isOutpatientActorType(Integer actorType) {
        if (actorType == null) {
            return false;
        }
        if (actorType == PRACTITIONER_TYPE) {
            return true;
        }
        if (actorType == LOCATION_TYPE) {
            return true;
        }
        if (actorType == HEALTHCARE_SERVICE_TYPE) {
            return true;
        }
        return false;
    }

    /**
     * 参与者类型与主键引用（领域校验入参）。
     */
    public static final class ScheduleActorRef {

        private final Integer actorType;
        private final Long actorId;

        /**
         * 构造参与者引用。
         *
         * @param actorType 参与者类型编码（对齐 {@link ScheduleActorKind#sortOrder}）
         * @param actorId   参与者主键
         */
        public ScheduleActorRef(Integer actorType, Long actorId) {
            this.actorType = actorType;
            this.actorId = actorId;
        }

        public Integer getActorType() {
            return actorType;
        }

        public Long getActorId() {
            return actorId;
        }
    }
}
