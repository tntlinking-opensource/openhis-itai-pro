/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.healthcheckup.service;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.HcDayPlan;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.HcDaySlot;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.HcDayType;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.HcSlotTemplate;
import com.zxplat.plugin.booking.domain.healthcheckup.repository.HcDayPlanRepository;
import com.zxplat.plugin.booking.domain.healthcheckup.repository.HcDaySlotRepository;
import com.zxplat.plugin.booking.domain.healthcheckup.repository.HcDayTypeRepository;
import com.zxplat.plugin.booking.domain.healthcheckup.repository.HcSlotTemplateRepository;
import com.zxplat.plugin.booking.domain.healthcheckup.support.HcSlotCalendarJsonSupport;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 体检号源日历写侧。
 *
 * <p>边界：模板保存、日计划维护、时段生成/复制、占释号。</p>
 */
@Service
@RequiredArgsConstructor
public class HealthCheckupSlotCalendarDomainService {

    private final HcSlotTemplateRepository slotTemplateRepository;
    private final HcDayPlanRepository dayPlanRepository;
    private final HcDayTypeRepository dayTypeRepository;
    private final HcDaySlotRepository daySlotRepository;

    /**
     * 保存或更新机构号源模板。
     *
     * @param entity 模板实体
     * @param operatorId 操作人
     * @return 落库后的模板
     */
    public HcSlotTemplate saveTemplate(HcSlotTemplate entity, Long operatorId) {
        if (entity.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        Optional<HcSlotTemplate> existing = slotTemplateRepository.findFirstByOrganizationIdAndIsDeletedFalse(entity.getOrganizationId());
        // 更新须落在已加载实体上，禁止给新对象 setId（Persistable.isNew 仍为 true 会走 persist）
        if (existing.isPresent()) {
            HcSlotTemplate current = existing.get();
            current.setSlotMinutes(entity.getSlotMinutes());
            current.setAmStart(entity.getAmStart());
            current.setAmEnd(entity.getAmEnd());
            current.setPmStart(entity.getPmStart());
            current.setPmEnd(entity.getPmEnd());
            current.setCapacityJson(entity.getCapacityJson());
            current.setRestWeekdays(entity.getRestWeekdays());
            current.setCampusCodesJson(entity.getCampusCodesJson());
            return this.saveAudited(current, operatorId, slotTemplateRepository::save);
        }
        return this.saveAudited(entity, operatorId, slotTemplateRepository::save);
    }

    /**
     * 创建或更新日计划。
     *
     * @param entity 日计划实体
     * @param operatorId 操作人
     * @return 落库后的日计划
     */
    public HcDayPlan upsertDayPlan(HcDayPlan entity, Long operatorId) {
        if (entity.getOrganizationId() == null || entity.getPlanDate() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "organizationId/planDate 不能为空");
        }
        Optional<HcDayPlan> existing = dayPlanRepository.findByOrganizationIdAndPlanDateAndIsDeletedFalse(
            entity.getOrganizationId(), entity.getPlanDate());
        if (entity.getRestFlag() == null) {
            entity.setRestFlag(Boolean.FALSE);
        }
        // 更新须落在已加载实体上，禁止给新对象 setId（Persistable.isNew 仍为 true 会走 persist）
        if (existing.isPresent()) {
            HcDayPlan current = existing.get();
            if (entity != current) {
                current.setRestFlag(entity.getRestFlag());
                current.setRemark(entity.getRemark());
            }
            return this.saveAudited(current, operatorId, dayPlanRepository::save);
        }
        return this.saveAudited(entity, operatorId, dayPlanRepository::save);
    }

    /**
     * 全量替换日开放合同类型。
     *
     * @param dayPlanId 日计划主键
     * @param contractTypeCodes 合同类型编码列表
     * @param operatorId 操作人
     */
    public void replaceDayTypes(Long dayPlanId, List<String> contractTypeCodes, Long operatorId) {
        if (dayPlanId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "dayPlanId 不能为空");
        }
        Set<String> wanted = new LinkedHashSet<>();
        if (contractTypeCodes != null) {
            for (String code : contractTypeCodes) {
                if (code == null || code.isBlank()) {
                    continue;
                }
                wanted.add(code.trim());
            }
        }
        // 含软删：复活已删行，避免 UNIQUE(day_plan_id, contract_type_code) 冲突
        List<HcDayType> all = dayTypeRepository.findAllByDayPlanId(dayPlanId);
        Set<String> kept = new LinkedHashSet<>();
        for (HcDayType row : all) {
            String code = row.getContractTypeCode();
            if (wanted.contains(code)) {
                if (Boolean.TRUE.equals(row.getIsDeleted())) {
                    if (!kept.contains(code)) {
                        this.reviveDayType(row, operatorId);
                        kept.add(code);
                    }
                } else {
                    kept.add(code);
                }
            } else if (!Boolean.TRUE.equals(row.getIsDeleted())) {
                row.setIsDeleted(Boolean.TRUE);
                row.setDeletedAt(LocalDateTime.now());
                row.setDeletedBy(operatorId);
                dayTypeRepository.save(row);
            }
        }
        for (String code : wanted) {
            if (kept.contains(code)) {
                continue;
            }
            HcDayType type = new HcDayType();
            type.setDayPlanId(dayPlanId);
            type.setContractTypeCode(code);
            this.saveAudited(type, operatorId, dayTypeRepository::save);
        }
    }

    /**
     * 新增日时段配额。
     *
     * @param entity 时段配额实体
     * @param operatorId 操作人
     * @return 落库后的时段配额
     */
    public HcDaySlot addDaySlot(HcDaySlot entity, Long operatorId) {
        this.validateDaySlotKey(entity);
        Optional<HcDaySlot> existing = this.findExistingPoolSlot(entity);
        if (existing.isPresent()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "同时段同类型同校区配额已存在");
        }
        if (entity.getCapacity() == null) {
            entity.setCapacity(0);
        }
        if (entity.getBookedCount() == null) {
            entity.setBookedCount(0);
        }
        if (entity.getOpenFlag() == null) {
            entity.setOpenFlag(Boolean.TRUE);
        }
        return this.saveAudited(entity, operatorId, daySlotRepository::save);
    }

    /**
     * 软删日时段配额（已约不可删）。
     *
     * @param daySlotId 时段配额主键
     * @param operatorId 操作人
     */
    public void deleteDaySlot(Long daySlotId, Long operatorId) {
        HcDaySlot slot = this.mustGetDaySlot(daySlotId);
        slot.assertDeletable();
        slot.setIsDeleted(Boolean.TRUE);
        slot.setDeletedAt(LocalDateTime.now());
        slot.setDeletedBy(operatorId);
        daySlotRepository.save(slot);
    }

    /**
     * 更新时段容量（新容量 ≥ 已约）。
     *
     * @param daySlotId 时段配额主键
     * @param newCapacity 新容量
     * @param operatorId 操作人
     * @return 更新后的时段配额
     */
    public HcDaySlot updateSlotCapacity(Long daySlotId, Integer newCapacity, Long operatorId) {
        HcDaySlot slot = this.mustGetDaySlot(daySlotId);
        slot.assertCapacityUpdatable(newCapacity);
        slot.setCapacity(newCapacity);
        return this.saveAudited(slot, operatorId, daySlotRepository::save);
    }

    /**
     * 设置时段开放/关闭。
     *
     * @param daySlotId 时段配额主键
     * @param openFlag 是否开放
     * @param operatorId 操作人
     * @return 更新后的时段配额
     */
    public HcDaySlot setSlotOpen(Long daySlotId, Boolean openFlag, Long operatorId) {
        HcDaySlot slot = this.mustGetDaySlot(daySlotId);
        slot.setOpenFlag(Boolean.TRUE.equals(openFlag));
        return this.saveAudited(slot, operatorId, daySlotRepository::save);
    }

    /**
     * 设置整日休息标记。
     *
     * @param organizationId 机构主键
     * @param planDate 计划日期
     * @param restFlag 是否休息
     * @param operatorId 操作人
     * @return 更新后的日计划
     */
    public HcDayPlan setDayRest(Long organizationId, LocalDate planDate, Boolean restFlag, Long operatorId) {
        HcDayPlan plan = dayPlanRepository.findByOrganizationIdAndPlanDateAndIsDeletedFalse(organizationId, planDate)
            .orElseGet(() -> {
                HcDayPlan created = new HcDayPlan();
                created.setOrganizationId(organizationId);
                created.setPlanDate(planDate);
                created.setRestFlag(Boolean.FALSE);
                return created;
            });
        plan.setRestFlag(Boolean.TRUE.equals(restFlag));
        return this.upsertDayPlan(plan, operatorId);
    }

    /**
     * 按模板生成未来 N 天日计划与时段配额。
     *
     * @param organizationId 机构主键
     * @param days 生成天数
     * @param fromDate 起始日期（为空则从明天起）
     * @param campusCodes 校区编码列表（校区模块启用时）
     * @param campusEnabled 校区模块是否启用
     * @param operatorId 操作人
     * @return 生成的日计划数量
     */
    public int generateFutureDays(
        Long organizationId,
        int days,
        LocalDate fromDate,
        List<String> campusCodes,
        boolean campusEnabled,
        Long operatorId) {
        if (organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        if (days <= 0) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "days 必须大于 0");
        }
        HcSlotTemplate template = slotTemplateRepository.findFirstByOrganizationIdAndIsDeletedFalse(organizationId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "请先保存号源模板"));
        if (template.getSlotMinutes() == null || template.getSlotMinutes() <= 0) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "模板时段间隔未配置，请先在模板设置中保存");
        }
        if (this.buildTimeWindows(template).isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "模板未配置上午/下午开放时段，请先完善模板设置");
        }
        LocalDate startDate = fromDate;
        if (startDate == null) {
            startDate = LocalDate.now().plusDays(1);
        }
        Map<String, Integer> capacityMap = HcSlotCalendarJsonSupport.parseCapacityMap(template.getCapacityJson());
        Set<Integer> restWeekdays = HcSlotCalendarJsonSupport.parseRestWeekdays(template.getRestWeekdays());
        List<String> effectiveCampusCodes = this.resolveCampusCodes(template, campusCodes, campusEnabled);
        int generated = 0;
        for (int i = 0; i < days; i++) {
            LocalDate planDate = startDate.plusDays(i);
            int isoWeekday = planDate.getDayOfWeek().getValue();
            // 模板休息日跳过生成
            if (restWeekdays.contains(isoWeekday)) {
                continue;
            }
            HcDayPlan plan = this.ensureDayPlan(organizationId, planDate, false, operatorId);
            this.ensureDayTypes(plan.getId(), capacityMap.keySet(), operatorId);
            this.generateSlotsForDay(plan, template, capacityMap, effectiveCampusCodes, campusEnabled, operatorId);
            generated++;
        }
        return generated;
    }

    /**
     * 复制源日计划到多个目标日期。
     *
     * @param organizationId 机构主键
     * @param sourceDate 源日期
     * @param targetDates 目标日期列表
     * @param operatorId 操作人
     */
    public void copyDayPlan(Long organizationId, LocalDate sourceDate, List<LocalDate> targetDates, Long operatorId) {
        if (organizationId == null || sourceDate == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "organizationId/sourceDate 不能为空");
        }
        if (targetDates == null || targetDates.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "targetDates 不能为空");
        }
        HcDayPlan sourcePlan = dayPlanRepository.findByOrganizationIdAndPlanDateAndIsDeletedFalse(organizationId, sourceDate)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "源日计划不存在"));
        List<HcDayType> sourceTypes = dayTypeRepository.findByDayPlanIdAndIsDeletedFalse(sourcePlan.getId());
        List<HcDaySlot> sourceSlots = daySlotRepository.findByDayPlanIdAndIsDeletedFalse(sourcePlan.getId());
        // 任一目标日存在已约则整次复制禁止
        for (LocalDate targetDate : targetDates) {
            this.assertTargetDateCopyable(organizationId, targetDate);
        }
        for (LocalDate targetDate : targetDates) {
            this.replaceTargetDayFromSource(organizationId, targetDate, sourcePlan, sourceTypes, sourceSlots, operatorId);
        }
    }

    /**
     * 按时间窗与间隔批量加档（已存在跳过；可多校区、各校区独立容量）。
     *
     * @param plan 日计划
     * @param windowStart 开放窗开始
     * @param windowEnd 开放窗结束
     * @param slotMinutes 时段间隔分钟
     * @param contractTypeCodes 合同类型编码
     * @param campusCodes 校区编码列表；空则按无校区（null）生成；列表元素可为 null
     * @param capacityByCampus 各校区容量；未配置的校区用 defaultCapacity
     * @param defaultCapacity 默认容量
     * @param openFlag 是否开放
     * @param operatorId 操作人
     * @return int[0]=新建数，int[1]=跳过数
     */
    public int[] batchAddDaySlots(
        HcDayPlan plan,
        LocalTime windowStart,
        LocalTime windowEnd,
        int slotMinutes,
        List<String> contractTypeCodes,
        List<String> campusCodes,
        Map<String, Integer> capacityByCampus,
        int defaultCapacity,
        boolean openFlag,
        Long operatorId) {
        if (plan == null || plan.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "日计划不能为空");
        }
        if (slotMinutes <= 0) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "时段间隔必须大于 0");
        }
        if (windowStart == null || windowEnd == null || !windowEnd.isAfter(windowStart)) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "开放窗结束时间必须大于开始时间");
        }
        Set<String> codes = new LinkedHashSet<>();
        if (contractTypeCodes != null) {
            for (String code : contractTypeCodes) {
                if (code == null || code.isBlank()) {
                    continue;
                }
                codes.add(code.trim());
            }
        }
        if (codes.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "请至少选择一种合同类型");
        }
        List<LocalTime[]> slices = this.splitWindow(windowStart, windowEnd, slotMinutes);
        if (slices.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "按当前间隔无法切出任何时段，请调整开放窗或间隔");
        }
        List<String> campuses = new ArrayList<>();
        if (campusCodes != null) {
            for (String campus : campusCodes) {
                if (campus == null || campus.isBlank()) {
                    campuses.add(null);
                } else {
                    campuses.add(campus.trim());
                }
            }
        }
        if (campuses.isEmpty()) {
            campuses.add(null);
        }
        this.ensureDayTypes(plan.getId(), codes, operatorId);
        int created = 0;
        int skipped = 0;
        for (String campusCode : campuses) {
            int capacity = this.resolveCampusCapacity(campusCode, capacityByCampus, defaultCapacity);
            for (LocalTime[] slice : slices) {
                for (String code : codes) {
                    boolean inserted = this.createPoolSlotIfAbsent(
                        plan, slice[0], slice[1], code, campusCode, capacity, openFlag, operatorId);
                    if (inserted) {
                        created++;
                    } else {
                        skipped++;
                    }
                }
            }
        }
        return new int[] {created, skipped};
    }

    private int resolveCampusCapacity(
        String campusCode, Map<String, Integer> capacityByCampus, int defaultCapacity) {
        if (campusCode != null && capacityByCampus != null && capacityByCampus.containsKey(campusCode)) {
            Integer configured = capacityByCampus.get(campusCode);
            if (configured != null) {
                return configured;
            }
        }
        return defaultCapacity;
    }

    /**
     * 占号：乐观锁递增 booked_count。
     *
     * @param daySlotId 时段配额主键
     * @param operatorId 操作人
     * @return 占号后的时段配额
     */
    public HcDaySlot occupySlot(Long daySlotId, Long operatorId) {
        HcDaySlot slot = this.mustGetDaySlot(daySlotId);
        slot.occupy();
        try {
            slot.setUpdatedAt(LocalDateTime.now());
            slot.setUpdatedBy(operatorId);
            return daySlotRepository.save(slot);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.SLOT_CONFLICT, "时段配额已被占用，请刷新后重试");
        }
    }

    /**
     * 释号：乐观锁递减 booked_count。
     *
     * @param daySlotId 时段配额主键
     * @param operatorId 操作人
     * @return 释号后的时段配额
     */
    public HcDaySlot releaseSlot(Long daySlotId, Long operatorId) {
        HcDaySlot slot = this.mustGetDaySlot(daySlotId);
        slot.release();
        try {
            slot.setUpdatedAt(LocalDateTime.now());
            slot.setUpdatedBy(operatorId);
            return daySlotRepository.save(slot);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.SLOT_CONFLICT, "时段配额已被修改，请刷新后重试");
        }
    }

    private HcDayPlan ensureDayPlan(Long organizationId, LocalDate planDate, boolean restFlag, Long operatorId) {
        Optional<HcDayPlan> existing = dayPlanRepository.findByOrganizationIdAndPlanDateAndIsDeletedFalse(organizationId, planDate);
        HcDayPlan plan;
        if (existing.isPresent()) {
            plan = existing.get();
            plan.setRestFlag(restFlag);
        } else {
            plan = new HcDayPlan();
            plan.setOrganizationId(organizationId);
            plan.setPlanDate(planDate);
            plan.setRestFlag(restFlag);
        }
        return this.upsertDayPlan(plan, operatorId);
    }

    private void ensureDayTypes(Long dayPlanId, Set<String> contractTypeCodes, Long operatorId) {
        List<HcDayType> all = dayTypeRepository.findAllByDayPlanId(dayPlanId);
        Set<String> activeCodes = new LinkedHashSet<>();
        Map<String, HcDayType> deletedByCode = new LinkedHashMap<>();
        for (HcDayType type : all) {
            String code = type.getContractTypeCode();
            if (Boolean.TRUE.equals(type.getIsDeleted())) {
                deletedByCode.putIfAbsent(code, type);
            } else {
                activeCodes.add(code);
            }
        }
        for (String code : contractTypeCodes) {
            if (activeCodes.contains(code)) {
                continue;
            }
            HcDayType deleted = deletedByCode.get(code);
            if (deleted != null) {
                this.reviveDayType(deleted, operatorId);
                continue;
            }
            HcDayType type = new HcDayType();
            type.setDayPlanId(dayPlanId);
            type.setContractTypeCode(code);
            this.saveAudited(type, operatorId, dayTypeRepository::save);
        }
    }

    private void reviveDayType(HcDayType row, Long operatorId) {
        row.setIsDeleted(Boolean.FALSE);
        row.setDeletedAt(null);
        row.setDeletedBy(null);
        row.setUpdatedAt(LocalDateTime.now());
        row.setUpdatedBy(operatorId);
        dayTypeRepository.save(row);
    }

    private void generateSlotsForDay(
        HcDayPlan plan,
        HcSlotTemplate template,
        Map<String, Integer> capacityMap,
        List<String> campusCodes,
        boolean campusEnabled,
        Long operatorId) {
        List<LocalTime[]> windows = this.buildTimeWindows(template);
        for (LocalTime[] window : windows) {
            List<LocalTime[]> slices = this.splitWindow(window[0], window[1], template.getSlotMinutes());
            for (LocalTime[] slice : slices) {
                for (String contractTypeCode : capacityMap.keySet()) {
                    int capacity = 0;
                    Integer configured = capacityMap.get(contractTypeCode);
                    if (configured != null) {
                        capacity = configured;
                    }
                    if (campusEnabled && !campusCodes.isEmpty()) {
                        for (String campusCode : campusCodes) {
                            this.ensurePoolSlot(plan, slice[0], slice[1], contractTypeCode, campusCode, capacity, operatorId);
                        }
                    } else {
                        this.ensurePoolSlot(plan, slice[0], slice[1], contractTypeCode, null, capacity, operatorId);
                    }
                }
            }
        }
    }

    private List<LocalTime[]> buildTimeWindows(HcSlotTemplate template) {
        List<LocalTime[]> windows = new ArrayList<>();
        if (template.getAmStart() != null && template.getAmEnd() != null) {
            windows.add(new LocalTime[] {template.getAmStart(), template.getAmEnd()});
        }
        if (template.getPmStart() != null && template.getPmEnd() != null) {
            windows.add(new LocalTime[] {template.getPmStart(), template.getPmEnd()});
        }
        return windows;
    }

    private List<LocalTime[]> splitWindow(LocalTime start, LocalTime end, int slotMinutes) {
        List<LocalTime[]> slices = new ArrayList<>();
        if (start == null || end == null || !end.isAfter(start)) {
            return slices;
        }
        LocalTime cursor = start;
        while (true) {
            LocalTime next = cursor.plusMinutes(slotMinutes);
            if (next.isAfter(end)) {
                break;
            }
            slices.add(new LocalTime[] {cursor, next});
            cursor = next;
        }
        return slices;
    }

    private void ensurePoolSlot(
        HcDayPlan plan,
        LocalTime startTime,
        LocalTime endTime,
        String contractTypeCode,
        String campusCode,
        int capacity,
        Long operatorId) {
        this.createPoolSlotIfAbsent(plan, startTime, endTime, contractTypeCode, campusCode, capacity, true, operatorId);
    }

    /**
     * 若不存在同池档位则新建；已存在返回 false。
     */
    private boolean createPoolSlotIfAbsent(
        HcDayPlan plan,
        LocalTime startTime,
        LocalTime endTime,
        String contractTypeCode,
        String campusCode,
        int capacity,
        boolean openFlag,
        Long operatorId) {
        Optional<HcDaySlot> existing;
        if (campusCode == null) {
            existing = daySlotRepository.findByOrganizationIdAndPlanDateAndStartTimeAndEndTimeAndContractTypeCodeAndCampusCodeIsNullAndIsDeletedFalse(
                plan.getOrganizationId(), plan.getPlanDate(), startTime, endTime, contractTypeCode);
        } else {
            existing = daySlotRepository.findByOrganizationIdAndPlanDateAndStartTimeAndEndTimeAndContractTypeCodeAndCampusCodeAndIsDeletedFalse(
                plan.getOrganizationId(), plan.getPlanDate(), startTime, endTime, contractTypeCode, campusCode);
        }
        if (existing.isPresent()) {
            return false;
        }
        HcDaySlot slot = new HcDaySlot();
        slot.setOrganizationId(plan.getOrganizationId());
        slot.setDayPlanId(plan.getId());
        slot.setPlanDate(plan.getPlanDate());
        slot.setStartTime(startTime);
        slot.setEndTime(endTime);
        slot.setContractTypeCode(contractTypeCode);
        slot.setCampusCode(campusCode);
        slot.setCapacity(capacity);
        slot.setBookedCount(0);
        slot.setOpenFlag(openFlag);
        this.saveAudited(slot, operatorId, daySlotRepository::save);
        return true;
    }

    private void assertTargetDateCopyable(Long organizationId, LocalDate targetDate) {
        List<HcDaySlot> slots = daySlotRepository.findByOrganizationIdAndPlanDateAndIsDeletedFalseOrderByStartTimeAsc(
            organizationId, targetDate);
        for (HcDaySlot slot : slots) {
            if (slot.getBookedCount() != null && slot.getBookedCount() > 0) {
                throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT,
                    "目标日期 " + targetDate + " 存在已约占用，整次复制已中止");
            }
        }
    }

    private void replaceTargetDayFromSource(
        Long organizationId,
        LocalDate targetDate,
        HcDayPlan sourcePlan,
        List<HcDayType> sourceTypes,
        List<HcDaySlot> sourceSlots,
        Long operatorId) {
        Optional<HcDayPlan> targetExisting = dayPlanRepository.findByOrganizationIdAndPlanDateAndIsDeletedFalse(
            organizationId, targetDate);
        if (targetExisting.isPresent()) {
            Long targetPlanId = targetExisting.get().getId();
            this.softDeleteDaySlotsByPlanId(targetPlanId, operatorId);
            this.softDeleteDayTypesByPlanId(targetPlanId, operatorId);
            targetExisting.get().setIsDeleted(Boolean.TRUE);
            targetExisting.get().setDeletedAt(LocalDateTime.now());
            targetExisting.get().setDeletedBy(operatorId);
            dayPlanRepository.save(targetExisting.get());
        }
        HcDayPlan targetPlan = new HcDayPlan();
        targetPlan.setOrganizationId(organizationId);
        targetPlan.setPlanDate(targetDate);
        targetPlan.setRestFlag(sourcePlan.getRestFlag());
        targetPlan.setRemark(sourcePlan.getRemark());
        targetPlan = this.upsertDayPlan(targetPlan, operatorId);
        List<String> typeCodes = new ArrayList<>();
        for (HcDayType sourceType : sourceTypes) {
            typeCodes.add(sourceType.getContractTypeCode());
        }
        this.replaceDayTypes(targetPlan.getId(), typeCodes, operatorId);
        for (HcDaySlot sourceSlot : sourceSlots) {
            HcDaySlot copied = new HcDaySlot();
            copied.setOrganizationId(organizationId);
            copied.setDayPlanId(targetPlan.getId());
            copied.setPlanDate(targetDate);
            copied.setStartTime(sourceSlot.getStartTime());
            copied.setEndTime(sourceSlot.getEndTime());
            copied.setContractTypeCode(sourceSlot.getContractTypeCode());
            copied.setCampusCode(sourceSlot.getCampusCode());
            copied.setCapacity(sourceSlot.getCapacity());
            copied.setBookedCount(0);
            copied.setOpenFlag(sourceSlot.getOpenFlag());
            this.saveAudited(copied, operatorId, daySlotRepository::save);
        }
    }

    private void softDeleteDaySlotsByPlanId(Long dayPlanId, Long operatorId) {
        List<HcDaySlot> slots = daySlotRepository.findByDayPlanIdAndIsDeletedFalse(dayPlanId);
        for (HcDaySlot slot : slots) {
            slot.setIsDeleted(Boolean.TRUE);
            slot.setDeletedAt(LocalDateTime.now());
            slot.setDeletedBy(operatorId);
        }
        if (!slots.isEmpty()) {
            daySlotRepository.saveAll(slots);
        }
    }

    private void softDeleteDayTypesByPlanId(Long dayPlanId, Long operatorId) {
        List<HcDayType> types = dayTypeRepository.findByDayPlanIdAndIsDeletedFalse(dayPlanId);
        for (HcDayType type : types) {
            type.setIsDeleted(Boolean.TRUE);
            type.setDeletedAt(LocalDateTime.now());
            type.setDeletedBy(operatorId);
        }
        if (!types.isEmpty()) {
            dayTypeRepository.saveAll(types);
        }
    }

    private List<String> resolveCampusCodes(HcSlotTemplate template, List<String> requestCampusCodes, boolean campusEnabled) {
        if (!campusEnabled) {
            return List.of();
        }
        if (requestCampusCodes != null && !requestCampusCodes.isEmpty()) {
            List<String> normalized = new ArrayList<>();
            for (String code : requestCampusCodes) {
                if (code == null || code.isBlank()) {
                    continue;
                }
                normalized.add(code.trim());
            }
            return normalized;
        }
        return HcSlotCalendarJsonSupport.parseCampusCodes(template.getCampusCodesJson());
    }

    private Optional<HcDaySlot> findExistingPoolSlot(HcDaySlot entity) {
        if (entity.getCampusCode() == null) {
            return daySlotRepository.findByOrganizationIdAndPlanDateAndStartTimeAndEndTimeAndContractTypeCodeAndCampusCodeIsNullAndIsDeletedFalse(
                entity.getOrganizationId(), entity.getPlanDate(), entity.getStartTime(), entity.getEndTime(),
                entity.getContractTypeCode());
        }
        return daySlotRepository.findByOrganizationIdAndPlanDateAndStartTimeAndEndTimeAndContractTypeCodeAndCampusCodeAndIsDeletedFalse(
            entity.getOrganizationId(), entity.getPlanDate(), entity.getStartTime(), entity.getEndTime(),
            entity.getContractTypeCode(), entity.getCampusCode());
    }

    private void validateDaySlotKey(HcDaySlot entity) {
        if (entity.getOrganizationId() == null || entity.getDayPlanId() == null || entity.getPlanDate() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "时段配额关键字段不能为空");
        }
        if (entity.getStartTime() == null || entity.getEndTime() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "startTime/endTime 不能为空");
        }
        if (!entity.getEndTime().isAfter(entity.getStartTime())) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "endTime 必须大于 startTime");
        }
        if (entity.getContractTypeCode() == null || entity.getContractTypeCode().isBlank()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "contractTypeCode 不能为空");
        }
    }

    private HcDaySlot mustGetDaySlot(Long daySlotId) {
        return daySlotRepository.findByIdAndIsDeletedFalse(daySlotId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "时段配额不存在"));
    }

    private <T extends com.zxplat.framework.core.domain.BaseAuditedEntity> T saveAudited(
        T entity, Long operatorId, java.util.function.Function<T, T> saver) {
        LocalDateTime now = LocalDateTime.now();
        if (entity.getId() == null) {
            entity.setCreatedAt(now);
            entity.setCreatedBy(operatorId);
            entity.setIsDeleted(Boolean.FALSE);
        }
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operatorId);
        return saver.apply(entity);
    }
}
