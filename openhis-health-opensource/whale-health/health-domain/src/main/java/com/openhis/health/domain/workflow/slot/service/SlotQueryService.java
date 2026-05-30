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

package com.openhis.health.domain.workflow.slot.service;


import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.SlotStatus;
import com.openhis.health.domain.workflow.slot.entity.Slot;
import com.openhis.health.domain.workflow.slot.queries.FindBookableSlotsQuery;
import com.openhis.health.domain.workflow.slot.queries.FindSlotsByScheduleAndTimeRangeQuery;
import com.openhis.health.domain.workflow.slot.queries.PageSlotsByOrganizationAndDateQuery;
import com.openhis.health.domain.workflow.slot.queries.model.BookableSlotModel;
import com.openhis.health.domain.workflow.slot.queries.model.SlotDetailModel;
import com.openhis.health.domain.workflow.slot.repository.SlotRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 号源时段读侧查询服务（DM-WKF-010）。
 *
 * <p>职责：日程下时段检索、可预约筛选（状态与容量）、机构某日分页排班；参数校验失败抛业务异常，不写库。</p>
 */
@Service
@RequiredArgsConstructor
public class SlotQueryService {

    private final SlotRepository slotRepository;

    /**
     * 按主键加载时段。
     *
     * @param id 时段标识
     * @return 明细模型
     * @throws BusinessException id 为空或时段不存在
     */
    public SlotDetailModel loadSlotById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "id 不能为空");
        }
        Slot slot = slotRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SLOT_NOT_FOUND, "Slot 不存在"));
        return this.toDetailModel(slot);
    }

    /**
     * 按日程与时间窗查询时段。
     *
     * @param query 查询入参
     * @return 列表
     * @throws BusinessException 必填字段缺失
     */
    public List<SlotDetailModel> findSlotsByScheduleAndTimeRange(FindSlotsByScheduleAndTimeRangeQuery query) {
        if (query == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "query 不能为空");
        }
        if (query.getScheduleId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "scheduleId 不能为空");
        }
        if (query.getStartTime() == null || query.getEndTime() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "startTime/endTime 不能为空");
        }

        List<Slot> slots;
        if (query.getStatusCodeList() != null && !query.getStatusCodeList().isEmpty()) {
            slots = slotRepository.findByScheduleIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqualAndStatusCodeIn(
                query.getScheduleId(), query.getStartTime(), query.getEndTime(), query.getStatusCodeList());
        } else {
            slots = slotRepository.findByScheduleIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(
                query.getScheduleId(), query.getStartTime(), query.getEndTime());
        }

        List<SlotDetailModel> models = new ArrayList<>();
        for (Slot slot : slots) {
            models.add(this.toDetailModel(slot));
        }
        return models;
    }

    /**
     * 查询可预约时段。
     *
     * @param query 查询入参
     * @return 列表
     * @throws BusinessException 必填字段缺失
     */
    public List<BookableSlotModel> findBookableSlots(FindBookableSlotsQuery query) {
        if (query == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "query 不能为空");
        }
        if (query.getScheduleId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "scheduleId 不能为空");
        }
        if (query.getStartTime() == null || query.getEndTime() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "startTime/endTime 不能为空");
        }

        List<String> statusCandidates = query.getStatusCodeList();
        if (statusCandidates == null || statusCandidates.isEmpty()) {
            statusCandidates = List.of(SlotStatus.FREE.getCode(), SlotStatus.BUSY.getCode());
        }

        List<Slot> slots = slotRepository.findByScheduleIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqualAndStatusCodeIn(
            query.getScheduleId(), query.getStartTime(), query.getEndTime(), statusCandidates);

        List<BookableSlotModel> models = new ArrayList<>();
        for (Slot slot : slots) {
            // 录入错误态时段不向患者开放预约
            if (SlotStatus.ENTERED_IN_ERROR.getCode().equals(slot.getStatusCode())) {
                continue;
            }
            // 忙且不可约的时段跳过
            if (SlotStatus.BUSY_UNAVAILABLE.getCode().equals(slot.getStatusCode())) {
                continue;
            }

            Boolean isFull = false;
            if (slot.getCapacity() != null && slot.getBookedCount() != null) {
                if (slot.getBookedCount() >= slot.getCapacity()) {
                    isFull = true;
                }
            }

            BookableSlotModel model = new BookableSlotModel();
            model.setSlotId(slot.getId());
            model.setScheduleId(slot.getScheduleId());
            model.setStartTime(slot.getStartTime());
            model.setEndTime(slot.getEndTime());
            model.setIsFull(isFull);
            models.add(model);
        }
        return models;
    }

    /**
     * 按机构与日期分页查询时段。
     *
     * @param query 查询入参
     * @return 分页结果
     * @throws BusinessException 分页或主键参数非法
     */
    public Page<SlotDetailModel> pageSlotsByOrganizationAndDate(PageSlotsByOrganizationAndDateQuery query) {
        if (query == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "query 不能为空");
        }
        if (query.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        LocalDate date = query.getDate();
        if (date == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "date 不能为空");
        }
        Integer pageNo = query.getPageNo();
        Integer pageSize = query.getPageSize();
        if (pageNo == null || pageNo <= 0) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "pageNo 非法");
        }
        if (pageSize == null || pageSize <= 0) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "pageSize 非法");
        }

        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        Sort sort = Sort.by(Sort.Direction.ASC, FieldNameConstants.START_TIME);
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize, sort);
        Page<Slot> page = slotRepository.findByOrganizationIdAndStartTimeGreaterThanEqualAndStartTimeLessThan(
            query.getOrganizationId(), from, to, pageable);
        return page.map(this::toDetailModel);
    }

    /**
     * 聚合根到读侧明细模型的映射。
     *
     * @param slot 聚合根
     * @return 明细模型
     */
    private SlotDetailModel toDetailModel(Slot slot) {
        SlotDetailModel model = new SlotDetailModel();
        model.setId(slot.getId());
        model.setOrganizationId(slot.getOrganizationId());
        model.setScheduleId(slot.getScheduleId());
        model.setStartTime(slot.getStartTime());
        model.setEndTime(slot.getEndTime());
        model.setStatusCode(slot.getStatusCode());
        model.setCapacity(slot.getCapacity());
        model.setBookedCount(slot.getBookedCount());
        model.setOverbookedFlag(slot.getOverbookedFlag());
        model.setServiceCategoryJson(slot.getServiceCategoryJson());
        model.setServiceTypeJson(slot.getServiceTypeJson());
        model.setSpecialtyJson(slot.getSpecialtyJson());
        model.setAppointmentTypeJson(slot.getAppointmentTypeJson());
        model.setCommentText(slot.getCommentText());
        return model;
    }
}
