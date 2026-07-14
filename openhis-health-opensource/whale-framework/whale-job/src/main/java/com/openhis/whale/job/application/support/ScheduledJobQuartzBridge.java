/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.application.support;

import com.openhis.whale.core.infrastructure.job.QuartzDisallowConcurrentJobAdapter;
import com.openhis.whale.core.infrastructure.job.QuartzJobAdapter;
import com.openhis.whale.job.application.dto.ScheduledJobParameterDto;
import com.openhis.whale.job.domain.ScheduledJobConcurrentPolicy;
import com.openhis.whale.job.domain.ScheduledJobMisfirePolicy;
import com.openhis.whale.job.domain.ScheduledJobStatus;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;
import static com.openhis.whale.job.application.support.ScheduledJobQuartzKeys.*;

/**
 * 通过 Quartz Scheduler 读写 {@code whale_qrtz_*} 表，不依赖额外业务表。
 */
@Slf4j
@Component
public class ScheduledJobQuartzBridge {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    /** 与 application.yml 中 org.quartz.scheduler.instanceName 一致。 */
    private static final String DEFAULT_SCHED_NAME = "WhaleScheduler";

    @Autowired
    private Scheduler scheduler;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    /**
     * 加载全部 Whale 托管的 Cron 任务（Job 类为 Whale 适配器且含 jobBeanName）。
     *
     * @return 任务快照列表
     */
    public List<ScheduledJobSnapshot> listManagedJobs() throws SchedulerException {
        List<ScheduledJobSnapshot> snapshots = new ArrayList<>();
        Set<JobKey> jobKeys = collectAllJobKeys();
        for (JobKey jobKey : jobKeys) {
            ScheduledJobSnapshot snapshot = loadSnapshot(jobKey).orElse(null);
            if (snapshot == null) {
                snapshot = loadOrphanSnapshot(jobKey).orElse(null);
            }
            if (snapshot == null) {
                snapshot = loadBareSnapshot(jobKey).orElse(null);
            }
            if (snapshot != null) {
                snapshots.add(snapshot);
            }
        }
        snapshots.sort(Comparator.comparing(ScheduledJobSnapshot::getJobGroup)
                .thenComparing(ScheduledJobSnapshot::getJobName));
        return snapshots;
    }

    /**
     * 按 JobKey 加载任务详情。
     *
     * @param jobName  任务名
     * @param jobGroup 任务组
     * @return 快照
     */
    public Optional<ScheduledJobSnapshot> find(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        Optional<ScheduledJobSnapshot> snapshot = loadSnapshot(jobKey);
        if (snapshot.isPresent()) {
            return snapshot;
        }
        return loadOrphanSnapshot(jobKey);
    }

    /**
     * 创建并注册 Cron 任务（写入 whale_qrtz_*）。
     *
     * @param snapshot 任务定义
     */
    public void create(ScheduledJobSnapshot snapshot) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(snapshot.getJobName(), snapshot.getJobGroup());
        if (scheduler.checkExists(jobKey)) {
            // 库中存在但列表不可见的「孤儿任务」（非 Whale 适配器 / 缺 jobBeanName）须先清理再创建
            if (find(snapshot.getJobName(), snapshot.getJobGroup()).isEmpty()) {
                scheduler.deleteJob(jobKey);
                log.warn("Removed orphan qrtz job before create: {}", jobKey);
            } else {
                throw new IllegalStateException("任务已存在: " + jobKey);
            }
        }
        scheduleCronJob(snapshot, jobKey);
        applyInitialStatus(snapshot, jobKey);
        log.info("Created scheduled job in qrtz: {} ({})", snapshot.getJobName(), snapshot.getJobGroup());
    }

    /**
     * 更新任务；若 jobName/jobGroup 变更则先删后建。
     *
     * @param oldJobName  原任务名
     * @param oldJobGroup 原任务组
     * @param snapshot    新定义
     */
    public void update(String oldJobName, String oldJobGroup, ScheduledJobSnapshot snapshot) throws SchedulerException {
        JobKey oldKey = JobKey.jobKey(oldJobName, oldJobGroup);
        if (!scheduler.checkExists(oldKey)) {
            throw new IllegalStateException("任务不存在: " + oldKey);
        }
        boolean keyChanged = !oldJobName.equals(snapshot.getJobName()) || !oldJobGroup.equals(snapshot.getJobGroup());
        if (keyChanged) {
            scheduler.deleteJob(oldKey);
            scheduleCronJob(snapshot, JobKey.jobKey(snapshot.getJobName(), snapshot.getJobGroup()));
        } else {
            scheduler.deleteJob(oldKey);
            scheduleCronJob(snapshot, oldKey);
        }
        applyInitialStatus(snapshot, JobKey.jobKey(snapshot.getJobName(), snapshot.getJobGroup()));
        log.info("Updated scheduled job in qrtz: {} ({})", snapshot.getJobName(), snapshot.getJobGroup());
    }

    /**
     * 从 Quartz 删除任务（同步删除 qrtz 行）。
     *
     * @param jobName  任务名
     * @param jobGroup 任务组
     */
    public void delete(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        if (!scheduler.checkExists(jobKey)) {
            return;
        }
        scheduler.deleteJob(jobKey);
        log.info("Deleted scheduled job from qrtz: {} ({})", jobName, jobGroup);
    }

    /**
     * 暂停任务。
     *
     * @param jobName  任务名
     * @param jobGroup 任务组
     */
    public void pause(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
        if (triggers == null || triggers.isEmpty()) {
            scheduler.pauseJob(jobKey);
            return;
        }
        // 按 Trigger 暂停，避免 pauseJob 在部分场景把 next_fire_time 置 0
        for (Trigger trigger : triggers) {
            scheduler.pauseTrigger(trigger.getKey());
        }
    }

    /**
     * 恢复任务。
     *
     * @param jobName  任务名
     * @param jobGroup 任务组
     */
    public void resume(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        scheduler.resumeJob(jobKey);
        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
        if (triggers != null) {
            for (Trigger trigger : triggers) {
                scheduler.resumeTrigger(trigger.getKey());
            }
        }
        // 创建后立即暂停的任务 next_fire_time 可能为 0 且 Trigger 仍为 PAUSED，须重建并再次 resume
        this.repairTriggerScheduleIfNeeded(jobKey);
        this.assertJobEnabledOrThrow(jobKey);
    }

    /**
     * 立即触发一次。
     *
     * @param jobName  任务名
     * @param jobGroup 任务组
     */
    public void triggerNow(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        if (!scheduler.checkExists(jobKey)) {
            throw new IllegalStateException("任务不存在: " + jobKey);
        }
        JobDataMap triggerData = new JobDataMap();
        triggerData.put(MANUAL_TRIGGER, Boolean.TRUE.toString());
        scheduler.triggerJob(jobKey, triggerData);
    }

    private void scheduleCronJob(ScheduledJobSnapshot snapshot, JobKey jobKey) throws SchedulerException {
        JobDetail jobDetail = buildJobDetail(snapshot, jobKey);
        CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(snapshot.getCronExpression());
        scheduleBuilder = CronExpressionSupport.applyMisfirePolicy(scheduleBuilder, snapshot.getMisfirePolicy());
        TriggerKey triggerKey = TriggerKey.triggerKey(snapshot.getJobName() + TRIGGER_SUFFIX, snapshot.getJobGroup());
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobDetail)
                .withSchedule(scheduleBuilder)
                .build();
        scheduler.scheduleJob(jobDetail, trigger);
    }

    private void applyInitialStatus(ScheduledJobSnapshot snapshot, JobKey jobKey) throws SchedulerException {
        if (!ScheduledJobStatus.PAUSE.getCode().equals(snapshot.getStatus())) {
            return;
        }
        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
        if (triggers == null || triggers.isEmpty()) {
            return;
        }
        for (Trigger trigger : triggers) {
            scheduler.pauseTrigger(trigger.getKey());
        }
    }

    private JobDetail buildJobDetail(ScheduledJobSnapshot snapshot, JobKey jobKey) {
        Class<? extends Job> jobClass = QuartzJobAdapter.class;
        if (ScheduledJobConcurrentPolicy.DISALLOW.getCode().equals(snapshot.getConcurrentPolicy())) {
            jobClass = QuartzDisallowConcurrentJobAdapter.class;
        }
        JobDataMap dataMap = new JobDataMap();
        dataMap.put(JOB_BEAN_NAME, snapshot.getJobBeanName());
        if (trimToNull(snapshot.getDisplayName()) != null) {
            dataMap.put(DISPLAY_NAME, snapshot.getDisplayName());
        }
        if (trimToNull(snapshot.getRemark()) != null) {
            dataMap.put(REMARK, snapshot.getRemark());
        }
        if (snapshot.getTenantId() != null) {
            dataMap.put(TENANT_ID, String.valueOf(snapshot.getTenantId()));
        }
        if (trimToNull(snapshot.getMisfirePolicy()) != null) {
            dataMap.put(MISFIRE_POLICY, snapshot.getMisfirePolicy());
        }
        if (trimToNull(snapshot.getConcurrentPolicy()) != null) {
            dataMap.put(CONCURRENT_POLICY, snapshot.getConcurrentPolicy());
        }
        if (snapshot.getParameters() != null) {
            for (ScheduledJobParameterDto parameter : snapshot.getParameters()) {
                if (parameter == null || trimToNull(parameter.getKey()) == null) {
                    continue;
                }
                if (ScheduledJobQuartzKeys.isReservedKey(parameter.getKey())) {
                    continue;
                }
                dataMap.put(parameter.getKey(), parameter.getValue());
            }
        }
        String description = trimToNull(snapshot.getDescription());
        if (description == null && trimToNull(snapshot.getDisplayName()) != null) {
            description = snapshot.getDisplayName();
        }
        return JobBuilder.newJob(jobClass)
                .withIdentity(jobKey)
                .withDescription(description)
                .usingJobData(dataMap)
                .storeDurably(false)
                .build();
    }

    private Optional<ScheduledJobSnapshot> loadOrphanSnapshot(JobKey jobKey) throws SchedulerException {
        if (!scheduler.checkExists(jobKey)) {
            return Optional.empty();
        }
        JobDetail jobDetail = scheduler.getJobDetail(jobKey);
        if (jobDetail == null) {
            return Optional.empty();
        }
        ScheduledJobSnapshot snapshot = new ScheduledJobSnapshot();
        snapshot.setJobName(jobKey.getName());
        snapshot.setJobGroup(jobKey.getGroup());
        snapshot.setDescription(jobDetail.getDescription());
        JobDataMap dataMap = jobDetail.getJobDataMap();
        String beanName = dataMap.getString(JOB_BEAN_NAME);
        if (trimToNull(beanName) == null) {
            beanName = "(未识别的任务类型)";
        }
        snapshot.setJobBeanName(beanName);
        snapshot.setDisplayName(dataMap.getString(DISPLAY_NAME));
        snapshot.setRemark(dataMap.getString(REMARK));
        Object tenantId = dataMap.get(TENANT_ID);
        if (tenantId instanceof Number) {
            snapshot.setTenantId(((Number) tenantId).longValue());
        } else if (tenantId instanceof String) {
            try {
                snapshot.setTenantId(Long.parseLong((String) tenantId));
            } catch (NumberFormatException ignored) {
                snapshot.setTenantId(null);
            }
        }
        snapshot.setConcurrentPolicy(resolveConcurrentPolicy(jobDetail));
        snapshot.setMisfirePolicy(ScheduledJobMisfirePolicy.DEFAULT.getCode());

        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
        if (triggers == null || triggers.isEmpty()) {
            snapshot.setStatus(ScheduledJobStatus.PAUSE.getCode());
            return Optional.of(snapshot);
        }
        Trigger trigger = triggers.get(0);
        Trigger.TriggerState triggerState = scheduler.getTriggerState(trigger.getKey());
        snapshot.setTriggerType(trigger.getClass().getSimpleName().replace("Trigger", "").toUpperCase());
        snapshot.setPreviousFireTime(toLocalDateTime(trigger.getPreviousFireTime()));
        snapshot.setStatus(resolveStatus(triggerState));
        if (trigger instanceof CronTrigger cronTrigger) {
            snapshot.setCronExpression(cronTrigger.getCronExpression());
        }
        snapshot.setNextFireTime(this.resolveNextFireTime(trigger, snapshot.getStatus(), snapshot.getCronExpression()));
        return Optional.of(snapshot);
    }

    /**
     * 合并 Scheduler 内存键与 whale_qrtz_job_details 表键，避免 Scheduler 未加载时列表漏项。
     */
    private Set<JobKey> collectAllJobKeys() throws SchedulerException {
        Set<JobKey> keys = new HashSet<>(scheduler.getJobKeys(GroupMatcher.anyJobGroup()));
        if (jdbcTemplate == null) {
            return keys;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT job_name, job_group FROM whale_qrtz_job_details WHERE sched_name = ?",
                    DEFAULT_SCHED_NAME);
            for (Map<String, Object> row : rows) {
                Object jobName = row.get("job_name");
                Object jobGroup = row.get("job_group");
                if (jobName != null && jobGroup != null) {
                    keys.add(JobKey.jobKey(String.valueOf(jobName), String.valueOf(jobGroup)));
                }
            }
        } catch (DataAccessException dataAccessException) {
            log.warn("读取 whale_qrtz_job_details 失败，仅使用 Scheduler 任务键: {}",
                    dataAccessException.getMessage());
        }
        return keys;
    }

    /** 无法解析 JobDetail 时仍返回最小快照，避免 qrtz 有记录但列表缺失。 */
    private Optional<ScheduledJobSnapshot> loadBareSnapshot(JobKey jobKey) throws SchedulerException {
        Optional<ScheduledJobSnapshot> fromDb = loadBareSnapshotFromDb(jobKey);
        if (fromDb.isPresent()) {
            return fromDb;
        }
        if (!scheduler.checkExists(jobKey)) {
            return Optional.empty();
        }
        ScheduledJobSnapshot snapshot = new ScheduledJobSnapshot();
        snapshot.setJobName(jobKey.getName());
        snapshot.setJobGroup(jobKey.getGroup());
        snapshot.setJobBeanName("(待修复)");
        snapshot.setStatus(ScheduledJobStatus.PAUSE.getCode());
        return Optional.of(snapshot);
    }

    /** 从 whale_qrtz_job_details 读取最小快照（Scheduler 未挂载时）。 */
    private Optional<ScheduledJobSnapshot> loadBareSnapshotFromDb(JobKey jobKey) {
        if (jdbcTemplate == null) {
            return Optional.empty();
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT description FROM whale_qrtz_job_details "
                            + "WHERE sched_name = ? AND job_name = ? AND job_group = ?",
                    DEFAULT_SCHED_NAME, jobKey.getName(), jobKey.getGroup());
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            ScheduledJobSnapshot snapshot = new ScheduledJobSnapshot();
            snapshot.setJobName(jobKey.getName());
            snapshot.setJobGroup(jobKey.getGroup());
            Object description = rows.get(0).get("description");
            if (description != null) {
                snapshot.setDescription(String.valueOf(description));
                snapshot.setDisplayName(String.valueOf(description));
            }
            snapshot.setJobBeanName("(待修复)");
            snapshot.setStatus(ScheduledJobStatus.PAUSE.getCode());
            return Optional.of(snapshot);
        } catch (DataAccessException dataAccessException) {
            log.warn("读取 qrtz 任务 {} 失败: {}", jobKey, dataAccessException.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ScheduledJobSnapshot> loadSnapshot(JobKey jobKey) throws SchedulerException {
        if (!scheduler.checkExists(jobKey)) {
            return Optional.empty();
        }
        JobDetail jobDetail = scheduler.getJobDetail(jobKey);
        if (!isWhaleManagedJob(jobDetail)) {
            return Optional.empty();
        }
        ScheduledJobSnapshot snapshot = new ScheduledJobSnapshot();
        snapshot.setJobName(jobKey.getName());
        snapshot.setJobGroup(jobKey.getGroup());
        snapshot.setDescription(jobDetail.getDescription());

        JobDataMap dataMap = jobDetail.getJobDataMap();
        snapshot.setJobBeanName(dataMap.getString(JOB_BEAN_NAME));
        snapshot.setDisplayName(dataMap.getString(DISPLAY_NAME));
        snapshot.setRemark(dataMap.getString(REMARK));
        Object tenantId = dataMap.get(TENANT_ID);
        if (tenantId instanceof Number) {
            snapshot.setTenantId(((Number) tenantId).longValue());
        } else if (tenantId instanceof String) {
            try {
                snapshot.setTenantId(Long.parseLong((String) tenantId));
            } catch (NumberFormatException ignored) {
                snapshot.setTenantId(null);
            }
        }
        String misfireFromMap = dataMap.getString(MISFIRE_POLICY);
        if (trimToNull(misfireFromMap) != null) {
            snapshot.setMisfirePolicy(misfireFromMap);
        }
        String concurrentFromMap = dataMap.getString(CONCURRENT_POLICY);
        if (trimToNull(concurrentFromMap) != null) {
            snapshot.setConcurrentPolicy(concurrentFromMap);
        } else {
            snapshot.setConcurrentPolicy(resolveConcurrentPolicy(jobDetail));
        }

        List<ScheduledJobParameterDto> parameters = new ArrayList<>();
        for (String key : dataMap.getKeys()) {
            if (ScheduledJobQuartzKeys.isReservedKey(key)) {
                continue;
            }
            ScheduledJobParameterDto parameter = new ScheduledJobParameterDto();
            parameter.setKey(key);
            Object value = dataMap.get(key);
            if (value != null) {
                parameter.setValue(String.valueOf(value));
            }
            parameters.add(parameter);
        }
        snapshot.setParameters(parameters);

        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
        if (triggers == null || triggers.isEmpty()) {
            snapshot.setStatus(ScheduledJobStatus.PAUSE.getCode());
            return Optional.of(snapshot);
        }
        Trigger trigger = triggers.get(0);
        TriggerKey triggerKey = trigger.getKey();
        Trigger.TriggerState triggerState = scheduler.getTriggerState(triggerKey);
        snapshot.setTriggerType(trigger.getClass().getSimpleName().replace("Trigger", "").toUpperCase());
        snapshot.setPreviousFireTime(toLocalDateTime(trigger.getPreviousFireTime()));
        snapshot.setStatus(resolveStatus(triggerState));
        if (trigger instanceof CronTrigger cronTrigger) {
            snapshot.setCronExpression(cronTrigger.getCronExpression());
            if (trimToNull(snapshot.getMisfirePolicy()) == null) {
                snapshot.setMisfirePolicy(ScheduledJobMisfirePolicy.DEFAULT.getCode());
            }
        }
        snapshot.setNextFireTime(this.resolveNextFireTime(trigger, snapshot.getStatus(), snapshot.getCronExpression()));
        return Optional.of(snapshot);
    }

    /**
     * 解析列表/详情展示的下次执行时间：暂停时不展示；运行中优先读 Trigger，无效时回退 Cron 推算。
     */
    private LocalDateTime resolveNextFireTime(Trigger trigger, String statusCode, String cronExpression) {
        if (ScheduledJobStatus.PAUSE.getCode().equals(statusCode)) {
            return null;
        }
        LocalDateTime fromTrigger = toLocalDateTime(trigger.getNextFireTime());
        if (isUsableNextFireTime(fromTrigger)) {
            return fromTrigger;
        }
        if (trimToNull(cronExpression) != null) {
            return CronExpressionSupport.getNextExecution(cronExpression);
        }
        return null;
    }

    /**
     * 启用后修复 Trigger：next_fire_time 无效或仍处于 PAUSED 时重建 Cron 并 resumeTrigger。
     */
    private void repairTriggerScheduleIfNeeded(JobKey jobKey) throws SchedulerException {
        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
        if (triggers == null || triggers.isEmpty()) {
            return;
        }
        JobDetail jobDetail = scheduler.getJobDetail(jobKey);
        String misfirePolicy = null;
        if (jobDetail != null) {
            misfirePolicy = jobDetail.getJobDataMap().getString(MISFIRE_POLICY);
        }
        for (Trigger trigger : triggers) {
            if (!(trigger instanceof CronTrigger cronTrigger)) {
                continue;
            }
            TriggerKey triggerKey = trigger.getKey();
            Trigger.TriggerState state = scheduler.getTriggerState(triggerKey);
            Date next = trigger.getNextFireTime();
            boolean stillPaused = state == Trigger.TriggerState.PAUSED || state == Trigger.TriggerState.NONE;
            boolean invalidNext = next == null || next.getTime() <= 0L;
            if (!stillPaused && !invalidNext) {
                continue;
            }
            CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(cronTrigger.getCronExpression());
            scheduleBuilder = CronExpressionSupport.applyMisfirePolicy(scheduleBuilder, misfirePolicy);
            Trigger rebuilt = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobKey)
                .withSchedule(scheduleBuilder)
                .build();
            scheduler.rescheduleJob(triggerKey, rebuilt);
            scheduler.resumeTrigger(triggerKey);
            log.info("Repaired trigger schedule after resume: {} ({})", triggerKey.getName(), triggerKey.getGroup());
        }
    }

    /**
     * 启用后校验 Trigger 已脱离 PAUSED，避免接口误报成功但列表仍显示已暂停。
     */
    private void assertJobEnabledOrThrow(JobKey jobKey) throws SchedulerException {
        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
        if (triggers == null || triggers.isEmpty()) {
            throw new SchedulerException("任务无 Trigger，无法启用: " + jobKey);
        }
        for (Trigger trigger : triggers) {
            Trigger.TriggerState state = scheduler.getTriggerState(trigger.getKey());
            if (state == Trigger.TriggerState.PAUSED || state == Trigger.TriggerState.NONE) {
                throw new SchedulerException("任务 Trigger 仍处于暂停状态: " + trigger.getKey());
            }
        }
    }

    private boolean isUsableNextFireTime(LocalDateTime next) {
        if (next == null) {
            return false;
        }
        return next.getYear() > 1971;
    }

    private boolean isWhaleManagedJob(JobDetail jobDetail) {
        String className = jobDetail.getJobClass().getName();
        if (!QuartzJobAdapter.class.getName().equals(className)
                && !QuartzDisallowConcurrentJobAdapter.class.getName().equals(className)) {
            return false;
        }
        String beanName = jobDetail.getJobDataMap().getString(JOB_BEAN_NAME);
        return trimToNull(beanName) != null;
    }

    private String resolveConcurrentPolicy(JobDetail jobDetail) {
        String className = jobDetail.getJobClass().getName();
        if (QuartzDisallowConcurrentJobAdapter.class.getName().equals(className)) {
            return ScheduledJobConcurrentPolicy.DISALLOW.getCode();
        }
        return ScheduledJobConcurrentPolicy.ALLOW.getCode();
    }

    private String resolveStatus(Trigger.TriggerState triggerState) {
        if (triggerState == Trigger.TriggerState.PAUSED) {
            return ScheduledJobStatus.PAUSE.getCode();
        }
        if (triggerState == Trigger.TriggerState.NONE) {
            return ScheduledJobStatus.PAUSE.getCode();
        }
        return ScheduledJobStatus.NORMAL.getCode();
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null || date.getTime() <= 0L) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), DEFAULT_ZONE);
    }
}
