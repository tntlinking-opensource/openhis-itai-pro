/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.application;

import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.infrastructure.permission.RequirePermission;
import com.openhis.whale.job.application.dto.*;
import com.openhis.whale.job.application.support.CronExpressionSupport;
import com.openhis.whale.job.application.support.JobHandlerRegistry;
import com.openhis.whale.job.application.support.ScheduledJobQuartzBridge;
import com.openhis.whale.job.application.support.ScheduledJobQuartzKeys;
import com.openhis.whale.job.application.support.ScheduledJobTenantSupport;
import com.openhis.whale.job.application.support.ScheduledJobSnapshot;
import com.openhis.whale.job.domain.CronPresetType;
import com.openhis.whale.job.domain.ScheduledJobConcurrentPolicy;
import com.openhis.whale.job.domain.ScheduledJobMisfirePolicy;
import com.openhis.whale.job.domain.ScheduledJobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.SchedulerException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 定时任务管理应用服务。
 *
 * <p>任务元数据直接读写已有 {@code whale_qrtz_*} 表（经 Quartz Scheduler），提供运维可理解的 Cron 预设与校验。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledJobAppService {

    private final ScheduledJobQuartzBridge scheduledJobQuartzBridge;
    private final JobHandlerRegistry jobHandlerRegistry;
    private final ScheduledJobTenantSupport scheduledJobTenantSupport;

    /**
     * 分页查询定时任务列表。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @RequirePermission("Navigation.JobManagement")
    public PagedResult<ScheduledJobDto> getList(ScheduledJobQueryRequest request) {
        // Gate：分页参数由 BaseQueryRequest 归一（pageNo 对外 1-based，getPageNo() 返回 0-based 索引）
        int pageIndex = request.getPageNo();
        int pageSize = request.getPageSize();
        try {
            // Pre：从 Quartz 加载全部 Whale 托管任务
            List<ScheduledJobSnapshot> allJobs = scheduledJobQuartzBridge.listManagedJobs();
            List<ScheduledJobSnapshot> filtered = filterJobs(allJobs, request);
            int fromIndex = pageIndex * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, filtered.size());
            List<ScheduledJobDto> pageContent = new ArrayList<>();
            if (fromIndex < filtered.size()) {
                List<ScheduledJobSnapshot> pageSnapshots = filtered.subList(fromIndex, toIndex);
                java.util.Set<Long> tenantIds = new java.util.HashSet<>();
                for (ScheduledJobSnapshot snapshot : pageSnapshots) {
                    if (snapshot.getTenantId() != null) {
                        tenantIds.add(snapshot.getTenantId());
                    }
                }
                java.util.Map<Long, String> tenantNames = scheduledJobTenantSupport.resolveTenantNames(tenantIds);
                for (ScheduledJobSnapshot snapshot : pageSnapshots) {
                    pageContent.add(toDto(snapshot, tenantNames));
                }
            }
            // Asm：组装分页响应（pageNo 从 1 开始）
            return PagedResult.of(pageContent, filtered.size(), pageIndex + 1, pageSize);
        } catch (SchedulerException schedulerException) {
            throw new BusinessException("读取定时任务列表失败: " + schedulerException.getMessage());
        }
    }

    /**
     * 获取任务详情。
     *
     * @param request 任务键
     * @return 任务详情
     */
    @RequirePermission("Navigation.JobManagement")
    public ScheduledJobDto getByKey(ScheduledJobKeyRequest request) {
        // Gate：JobKey 必填，对应 whale_qrtz_job_details 主键
        assertJobKeyPresent(request.getJobName(), request.getJobGroup());
        try {
            // Pre：按 JobKey 从 Quartz 加载
            Optional<ScheduledJobSnapshot> snapshot = scheduledJobQuartzBridge.find(request.getJobName(), request.getJobGroup());
            if (snapshot.isEmpty()) {
                throw new BusinessException("定时任务不存在: " + request.getJobGroup() + "/" + request.getJobName());
            }
            // Asm：映射为运维 DTO
            Optional<ScheduledJobSnapshot> loaded = snapshot;
            java.util.Map<Long, String> tenantNames = scheduledJobTenantSupport.resolveTenantNames(
                    loaded.get().getTenantId() == null ? List.of() : List.of(loaded.get().getTenantId()));
            return toDto(loaded.get(), tenantNames);
        } catch (SchedulerException schedulerException) {
            throw new BusinessException("读取定时任务详情失败: " + schedulerException.getMessage());
        }
    }

    /**
     * 新建定时任务（默认暂停，避免误触发）。
     *
     * @param request 创建请求
     * @return 创建后的任务
     */
    @RequirePermission("Navigation.JobManagement")
    public ScheduledJobDto create(CreateScheduledJobRequest request) {
        // Gate：校验名称、处理器与 Cron
        validateCreateRequest(request);
        ScheduledJobSnapshot snapshot = buildSnapshotFromCreate(request);
        try {
            // Cmd：写入 whale_qrtz_*（经 Scheduler.scheduleJob）
            scheduledJobQuartzBridge.create(snapshot);
            // Asm：回读详情
            Optional<ScheduledJobSnapshot> created = scheduledJobQuartzBridge.find(snapshot.getJobName(), snapshot.getJobGroup());
            if (created.isEmpty()) {
                throw new BusinessException("任务创建后读取失败");
            }
            return toDto(created.get(), scheduledJobTenantSupport.resolveTenantNames(
                    created.get().getTenantId() == null ? List.of() : List.of(created.get().getTenantId())));
        } catch (IllegalStateException duplicateException) {
            String message = duplicateException.getMessage();
            if (message != null && message.contains("任务已存在")) {
                throw new BusinessException(message + "。请在列表中搜索该任务标识后编辑，勿重复新建。");
            }
            throw new BusinessException(message != null ? message : "创建定时任务失败");
        } catch (SchedulerException schedulerException) {
            throw new BusinessException("创建定时任务失败: " + schedulerException.getMessage());
        }
    }

    /**
     * 更新定时任务。
     *
     * @param request 更新请求
     * @return 更新后的任务
     */
    @RequirePermission("Navigation.JobManagement")
    public ScheduledJobDto update(UpdateScheduledJobRequest request) {
        // Gate：原 JobKey 与新 Cron/处理器须合法
        assertJobKeyPresent(request.getJobName(), request.getJobGroup());
        validateUpdateRequest(request);
        ScheduledJobSnapshot snapshot = buildSnapshotFromUpdate(request);
        try {
            // Cmd：更新 qrtz（必要时更换 JobKey）
            scheduledJobQuartzBridge.update(request.getJobName(), request.getJobGroup(), snapshot);
            Optional<ScheduledJobSnapshot> updated = scheduledJobQuartzBridge.find(snapshot.getJobName(), snapshot.getJobGroup());
            if (updated.isEmpty()) {
                throw new BusinessException("任务更新后读取失败");
            }
            return toDto(updated.get(), scheduledJobTenantSupport.resolveTenantNames(
                    updated.get().getTenantId() == null ? List.of() : List.of(updated.get().getTenantId())));
        } catch (SchedulerException schedulerException) {
            throw new BusinessException("更新定时任务失败: " + schedulerException.getMessage());
        }
    }

    /**
     * 删除定时任务。
     *
     * @param request 任务键
     */
    @RequirePermission("Navigation.JobManagement")
    public void delete(ScheduledJobKeyRequest request) {
        // Gate：JobKey 必填
        assertJobKeyPresent(request.getJobName(), request.getJobGroup());
        try {
            // Cmd：从 qrtz 删除 Job 与 Trigger
            scheduledJobQuartzBridge.delete(request.getJobName(), request.getJobGroup());
        } catch (SchedulerException schedulerException) {
            throw new BusinessException("删除定时任务失败: " + schedulerException.getMessage());
        }
    }

    /**
     * 修改运行状态（启用/暂停）。
     *
     * @param request 状态变更请求
     * @return 最新任务详情
     */
    @RequirePermission("Navigation.JobManagement")
    public ScheduledJobDto changeStatus(ChangeScheduledJobStatusRequest request) {
        // Gate：状态须为 NORMAL 或 PAUSE
        assertJobKeyPresent(request.getJobName(), request.getJobGroup());
        ScheduledJobStatus targetStatus = ScheduledJobStatus.fromCode(request.getStatus());
        if (targetStatus == null) {
            throw new ValidationException("无效的任务状态: " + request.getStatus());
        }
        try {
            // Cmd：调用 Quartz pause/resume，状态落在 whale_qrtz_triggers.trigger_state
            if (targetStatus == ScheduledJobStatus.PAUSE) {
                scheduledJobQuartzBridge.pause(request.getJobName(), request.getJobGroup());
            } else {
                scheduledJobQuartzBridge.resume(request.getJobName(), request.getJobGroup());
            }
            Optional<ScheduledJobSnapshot> snapshot = scheduledJobQuartzBridge.find(request.getJobName(), request.getJobGroup());
            if (snapshot.isEmpty()) {
                throw new BusinessException("定时任务不存在");
            }
            return toDto(snapshot.get(), scheduledJobTenantSupport.resolveTenantNames(
                    snapshot.get().getTenantId() == null ? List.of() : List.of(snapshot.get().getTenantId())));
        } catch (SchedulerException schedulerException) {
            throw new BusinessException("修改任务状态失败: " + schedulerException.getMessage());
        }
    }

    /**
     * 列出可绑定的租户选项（任务配置与日志筛选共用）。
     *
     * @return 租户下拉
     */
    @RequirePermission("Navigation.JobManagement")
    public List<ScheduledJobTenantOptionDto> listTenantOptions() {
        return scheduledJobTenantSupport.listActiveTenantOptions();
    }

    /**
     * 立即执行一次（不影响 Cron 计划）。
     *
     * @param request 任务键
     */
    @RequirePermission("Navigation.JobManagement")
    public void runNow(ScheduledJobKeyRequest request) {
        // Gate：JobKey 必填
        assertJobKeyPresent(request.getJobName(), request.getJobGroup());
        try {
            // Cmd：triggerJob 写入 whale_qrtz_fired_triggers（运行时）
            scheduledJobQuartzBridge.triggerNow(request.getJobName(), request.getJobGroup());
        } catch (SchedulerException schedulerException) {
            throw new BusinessException("立即执行任务失败: " + schedulerException.getMessage());
        }
    }

    /**
     * 校验 Cron 表达式并预览下次执行时间。
     *
     * @param request 校验请求
     * @return 校验结果
     */
    @RequirePermission("Navigation.JobManagement")
    public ValidateCronResponse validateCron(ValidateCronRequest request) {
        ValidateCronResponse response = new ValidateCronResponse();
        String cron = trimToNull(request.getCronExpression());
        if (cron == null) {
            response.setValid(false);
            response.setErrorMessage("Cron 表达式不能为空");
            return response;
        }
        // Pre：Quartz 校验合法性
        boolean valid = CronExpressionSupport.isValid(cron);
        response.setValid(valid);
        if (!valid) {
            response.setErrorMessage(CronExpressionSupport.getInvalidMessage(cron));
            return response;
        }
        response.setDescription(CronExpressionSupport.describe(cron));
        response.setNextFireTime(CronExpressionSupport.getNextExecution(cron));
        response.setNextFireTimes(CronExpressionSupport.getNextExecutions(cron, 5));
        return response;
    }

    /**
     * 通过可视化预设生成 Cron 表达式。
     *
     * @param request 预设参数
     * @return Cron 与描述
     */
    @RequirePermission("Navigation.JobManagement")
    public BuildCronResponse buildCron(BuildCronRequest request) {
        BuildCronResponse response = new BuildCronResponse();
        try {
            String cron = CronExpressionSupport.buildFromPreset(
                    request.getCronPresetType(),
                    request.getIntervalMinutes(),
                    request.getMinute(),
                    request.getHour(),
                    request.getDayOfWeek(),
                    request.getDayOfMonth(),
                    request.getCustomExpression());
            response.setCronExpression(cron);
            response.setDescription(CronExpressionSupport.describe(cron));
            return response;
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new ValidationException(illegalArgumentException.getMessage());
        }
    }

    /**
     * 列出可选 IJob 处理器（Spring Bean）。
     *
     * @return 处理器列表
     */
    @RequirePermission("Navigation.JobManagement")
    public List<JobHandlerOptionDto> listHandlers() {
        return jobHandlerRegistry.listHandlers();
    }

    /**
     * 获取页面下拉枚举（状态/策略/Cron 预设）。
     *
     * @return 选项集合
     */
    @RequirePermission("Navigation.JobManagement")
    public List<ScheduledJobOptionGroupDto> getOptions() {
        List<ScheduledJobOptionGroupDto> groups = new ArrayList<>();

        ScheduledJobOptionGroupDto statusGroup = new ScheduledJobOptionGroupDto();
        statusGroup.setGroupCode("status");
        statusGroup.setGroupLabel("运行状态");
        statusGroup.setOptions(toOptions(ScheduledJobStatus.values()));
        groups.add(statusGroup);

        ScheduledJobOptionGroupDto misfireGroup = new ScheduledJobOptionGroupDto();
        misfireGroup.setGroupCode("misfirePolicy");
        misfireGroup.setGroupLabel("错过触发策略");
        misfireGroup.setOptions(toMisfireOptions());
        groups.add(misfireGroup);

        ScheduledJobOptionGroupDto concurrentGroup = new ScheduledJobOptionGroupDto();
        concurrentGroup.setGroupCode("concurrentPolicy");
        concurrentGroup.setGroupLabel("并发策略");
        concurrentGroup.setOptions(toConcurrentOptions());
        groups.add(concurrentGroup);

        ScheduledJobOptionGroupDto presetGroup = new ScheduledJobOptionGroupDto();
        presetGroup.setGroupCode("cronPresetType");
        presetGroup.setGroupLabel("Cron 预设");
        presetGroup.setOptions(toPresetOptions());
        groups.add(presetGroup);

        return groups;
    }

    private List<ScheduledJobSnapshot> filterJobs(List<ScheduledJobSnapshot> source, ScheduledJobQueryRequest request) {
        List<ScheduledJobSnapshot> result = new ArrayList<>();
        String keyword = trimToNull(request.getKeyword());
        String jobGroup = trimToNull(request.getJobGroup());
        String status = trimToNull(request.getStatus());
        Long tenantId = request.getTenantId();
        for (ScheduledJobSnapshot snapshot : source) {
            // 按任务组精确过滤
            if (jobGroup != null && !jobGroup.equals(snapshot.getJobGroup())) {
                continue;
            }
            // 按租户过滤：指定 tenantId 时只保留绑定该租户的任务（tenantId 为空表示平台级）
            if (tenantId != null) {
                if (snapshot.getTenantId() == null || !tenantId.equals(snapshot.getTenantId())) {
                    continue;
                }
            }
            // 按运行状态过滤（对应 trigger_state）
            if (status != null && !status.equals(snapshot.getStatus())) {
                continue;
            }
            // 关键词匹配名称/显示名/Bean
            if (keyword != null) {
                boolean matched = containsIgnoreCase(snapshot.getJobName(), keyword)
                        || containsIgnoreCase(snapshot.getDisplayName(), keyword)
                        || containsIgnoreCase(snapshot.getJobBeanName(), keyword);
                if (!matched) {
                    continue;
                }
            }
            result.add(snapshot);
        }
        return result;
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        if (text == null) {
            return false;
        }
        return text.toLowerCase().contains(keyword.toLowerCase());
    }

    private void validateCreateRequest(CreateScheduledJobRequest request) {
        if (trimToNull(request.getJobName()) == null) {
            throw new ValidationException("任务名称不能为空");
        }
        if (trimToNull(request.getJobBeanName()) == null) {
            throw new ValidationException("任务处理器不能为空");
        }
        if (!jobHandlerRegistry.exists(request.getJobBeanName())) {
            throw new ValidationException("任务处理器不存在: " + request.getJobBeanName());
        }
        String cron = resolveCronExpression(request.getCronExpression(), request.getCronPresetType(),
                request.getIntervalMinutes(), request.getMinute(), request.getHour(),
                request.getDayOfWeek(), request.getDayOfMonth(), null);
        if (!CronExpressionSupport.isValid(cron)) {
            throw new ValidationException("Cron 表达式无效: " + CronExpressionSupport.getInvalidMessage(cron));
        }
    }

    private void validateUpdateRequest(UpdateScheduledJobRequest request) {
        if (trimToNull(request.getJobBeanName()) == null) {
            throw new ValidationException("任务处理器不能为空");
        }
        if (!jobHandlerRegistry.exists(request.getJobBeanName())) {
            throw new ValidationException("任务处理器不存在: " + request.getJobBeanName());
        }
        String cron = resolveCronExpression(request.getCronExpression(), request.getCronPresetType(),
                request.getIntervalMinutes(), request.getMinute(), request.getHour(),
                request.getDayOfWeek(), request.getDayOfMonth(), null);
        if (!CronExpressionSupport.isValid(cron)) {
            throw new ValidationException("Cron 表达式无效: " + CronExpressionSupport.getInvalidMessage(cron));
        }
    }

    private ScheduledJobSnapshot buildSnapshotFromCreate(CreateScheduledJobRequest request) {
        ScheduledJobSnapshot snapshot = new ScheduledJobSnapshot();
        snapshot.setJobName(request.getJobName().trim());
        String group = trimToNull(request.getJobGroup());
        if (group == null) {
            group = ScheduledJobQuartzKeys.DEFAULT_GROUP;
        }
        snapshot.setJobGroup(group);
        snapshot.setDisplayName(trimToNull(request.getDisplayName()));
        snapshot.setDescription(trimToNull(request.getDescription()));
        snapshot.setRemark(trimToNull(request.getRemark()));
        snapshot.setJobBeanName(request.getJobBeanName().trim());
        snapshot.setCronExpression(resolveCronExpression(request.getCronExpression(), request.getCronPresetType(),
                request.getIntervalMinutes(), request.getMinute(), request.getHour(),
                request.getDayOfWeek(), request.getDayOfMonth(), null));
        snapshot.setMisfirePolicy(defaultMisfirePolicy(request.getMisfirePolicy()));
        snapshot.setConcurrentPolicy(defaultConcurrentPolicy(request.getConcurrentPolicy()));
        snapshot.setTenantId(request.getTenantId());
        snapshot.setParameters(request.getParameters());
        String status = trimToNull(request.getStatus());
        if (status == null) {
            status = ScheduledJobStatus.PAUSE.getCode();
        }
        snapshot.setStatus(status);
        return snapshot;
    }

    private ScheduledJobSnapshot buildSnapshotFromUpdate(UpdateScheduledJobRequest request) {
        ScheduledJobSnapshot snapshot = new ScheduledJobSnapshot();
        String newName = trimToNull(request.getNewJobName());
        if (newName == null) {
            newName = request.getJobName();
        }
        snapshot.setJobName(newName.trim());
        String newGroup = trimToNull(request.getNewJobGroup());
        if (newGroup == null) {
            newGroup = request.getJobGroup();
        }
        snapshot.setJobGroup(newGroup.trim());
        snapshot.setDisplayName(trimToNull(request.getDisplayName()));
        snapshot.setDescription(trimToNull(request.getDescription()));
        snapshot.setRemark(trimToNull(request.getRemark()));
        snapshot.setJobBeanName(request.getJobBeanName().trim());
        snapshot.setCronExpression(resolveCronExpression(request.getCronExpression(), request.getCronPresetType(),
                request.getIntervalMinutes(), request.getMinute(), request.getHour(),
                request.getDayOfWeek(), request.getDayOfMonth(), null));
        snapshot.setMisfirePolicy(defaultMisfirePolicy(request.getMisfirePolicy()));
        snapshot.setConcurrentPolicy(defaultConcurrentPolicy(request.getConcurrentPolicy()));
        snapshot.setTenantId(request.getTenantId());
        snapshot.setParameters(request.getParameters());
        String status = trimToNull(request.getStatus());
        if (status == null) {
            status = ScheduledJobStatus.NORMAL.getCode();
        }
        snapshot.setStatus(status);
        return snapshot;
    }

    private String resolveCronExpression(String cronExpression, String presetType, Integer intervalMinutes,
                                         Integer minute, Integer hour, Integer dayOfWeek, Integer dayOfMonth,
                                         String customExpression) {
        if (trimToNull(cronExpression) != null) {
            return cronExpression.trim();
        }
        return CronExpressionSupport.buildFromPreset(presetType, intervalMinutes, minute, hour, dayOfWeek, dayOfMonth, customExpression);
    }

    private String defaultMisfirePolicy(String code) {
        ScheduledJobMisfirePolicy policy = ScheduledJobMisfirePolicy.fromCode(code);
        if (policy == null) {
            return ScheduledJobMisfirePolicy.DEFAULT.getCode();
        }
        return policy.getCode();
    }

    private String defaultConcurrentPolicy(String code) {
        ScheduledJobConcurrentPolicy policy = ScheduledJobConcurrentPolicy.fromCode(code);
        if (policy == null) {
            return ScheduledJobConcurrentPolicy.DISALLOW.getCode();
        }
        return policy.getCode();
    }

    private void assertJobKeyPresent(String jobName, String jobGroup) {
        if (trimToNull(jobName) == null) {
            throw new ValidationException("任务名称不能为空");
        }
        if (trimToNull(jobGroup) == null) {
            throw new ValidationException("任务分组不能为空");
        }
    }

    private ScheduledJobDto toDto(ScheduledJobSnapshot snapshot, java.util.Map<Long, String> tenantNames) {
        ScheduledJobDto dto = new ScheduledJobDto();
        dto.setJobName(snapshot.getJobName());
        dto.setJobGroup(snapshot.getJobGroup());
        dto.setDisplayName(snapshot.getDisplayName());
        dto.setDescription(snapshot.getDescription());
        dto.setRemark(snapshot.getRemark());
        dto.setJobBeanName(snapshot.getJobBeanName());
        dto.setCronExpression(snapshot.getCronExpression());
        dto.setCronDescription(CronExpressionSupport.describe(snapshot.getCronExpression()));
        dto.setTriggerType(snapshot.getTriggerType());
        dto.setStatus(snapshot.getStatus());
        ScheduledJobStatus status = ScheduledJobStatus.fromCode(snapshot.getStatus());
        if (status != null) {
            dto.setStatusLabel(status.getLabel());
        }
        dto.setMisfirePolicy(snapshot.getMisfirePolicy());
        ScheduledJobMisfirePolicy misfirePolicy = ScheduledJobMisfirePolicy.fromCode(snapshot.getMisfirePolicy());
        if (misfirePolicy != null) {
            dto.setMisfirePolicyLabel(misfirePolicy.getLabel());
        }
        dto.setConcurrentPolicy(snapshot.getConcurrentPolicy());
        ScheduledJobConcurrentPolicy concurrentPolicy = ScheduledJobConcurrentPolicy.fromCode(snapshot.getConcurrentPolicy());
        if (concurrentPolicy != null) {
            dto.setConcurrentPolicyLabel(concurrentPolicy.getLabel());
        }
        dto.setTenantId(snapshot.getTenantId());
        if (snapshot.getTenantId() != null && tenantNames != null) {
            dto.setTenantName(tenantNames.get(snapshot.getTenantId()));
        }
        dto.setParameters(snapshot.getParameters());
        dto.setNextFireTime(snapshot.getNextFireTime());
        dto.setPreviousFireTime(snapshot.getPreviousFireTime());
        return dto;
    }

    private List<ScheduledJobOptionDto> toOptions(ScheduledJobStatus[] statuses) {
        List<ScheduledJobOptionDto> options = new ArrayList<>();
        for (ScheduledJobStatus status : statuses) {
            ScheduledJobOptionDto option = new ScheduledJobOptionDto();
            option.setCode(status.getCode());
            option.setLabel(status.getLabel());
            options.add(option);
        }
        return options;
    }

    private List<ScheduledJobOptionDto> toMisfireOptions() {
        List<ScheduledJobOptionDto> options = new ArrayList<>();
        for (ScheduledJobMisfirePolicy policy : ScheduledJobMisfirePolicy.values()) {
            ScheduledJobOptionDto option = new ScheduledJobOptionDto();
            option.setCode(policy.getCode());
            option.setLabel(policy.getLabel());
            options.add(option);
        }
        return options;
    }

    private List<ScheduledJobOptionDto> toConcurrentOptions() {
        List<ScheduledJobOptionDto> options = new ArrayList<>();
        for (ScheduledJobConcurrentPolicy policy : ScheduledJobConcurrentPolicy.values()) {
            ScheduledJobOptionDto option = new ScheduledJobOptionDto();
            option.setCode(policy.getCode());
            option.setLabel(policy.getLabel());
            options.add(option);
        }
        return options;
    }

    private List<ScheduledJobOptionDto> toPresetOptions() {
        List<ScheduledJobOptionDto> options = new ArrayList<>();
        for (CronPresetType preset : CronPresetType.values()) {
            ScheduledJobOptionDto option = new ScheduledJobOptionDto();
            option.setCode(preset.getCode());
            option.setLabel(preset.getLabel());
            options.add(option);
        }
        return options;
    }
}
