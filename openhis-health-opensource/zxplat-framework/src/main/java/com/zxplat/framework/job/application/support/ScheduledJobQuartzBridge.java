/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.job.application.support;

import com.zxplat.framework.core.infrastructure.job.QuartzDisallowConcurrentJobAdapter;
import com.zxplat.framework.core.infrastructure.job.QuartzJobAdapter;
import com.zxplat.framework.job.application.dto.ScheduledJobParameterDto;
import com.zxplat.framework.job.domain.ScheduledJobConcurrentPolicy;
import com.zxplat.framework.job.domain.ScheduledJobMisfirePolicy;
import com.zxplat.framework.job.domain.ScheduledJobStatus;
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

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;
import static com.zxplat.framework.job.application.support.ScheduledJobQuartzKeys.*;

/**
 * 通过 Quartz Scheduler 读写 {@code zxplat_qrtz_*} 表，不依赖额外业务表。
 */
@Slf4j
@Component
public class ScheduledJobQuartzBridge {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    /** 与 application.yml 中 org.quartz.scheduler.instanceName 一致。 */
    private static final String DEFAULT_SCHED_NAME = "ZxplatScheduler";

    @Autowired
    private Scheduler scheduler;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    /**
     * 加载全部 ZXPlat 托管的 Cron 任务（Job 类为 ZXPlat 适配器且含 jobBeanName）。
     *
     * <p>列表路径只读：禁止在此改写 {@code job_class_name} 或重建 Trigger。
     * {@code getList} 经 {@code ReadOnlyTransactionAspect} 走只读事务，写操作会触发
     * PostgreSQL「当前事务被终止」，后续 {@code checkExists} 全部失败。</p>
     *
     * @return 任务快照列表
     */
    public List<ScheduledJobSnapshot> listManagedJobs() throws SchedulerException {
        List<ScheduledJobSnapshot> snapshots = new ArrayList<>();
        Set<JobKey> jobKeys = collectAllJobKeys();
        for (JobKey jobKey : jobKeys) {
            // 单任务失败不拖垮整页；坏类名/坏 Trigger 降级为库表最小快照
            try {
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
            } catch (SchedulerException | RuntimeException loadError) {
                log.warn("加载任务 {} 失败，降级库表白名单: {}", jobKey, loadError.getMessage());
                loadBareSnapshotFromDb(jobKey).ifPresent(snapshots::add);
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
     * 创建并注册 Cron 任务（写入 zxplat_qrtz_*）。
     *
     * @param snapshot 任务定义
     */
    public void create(ScheduledJobSnapshot snapshot) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(snapshot.getJobName(), snapshot.getJobGroup());
        if (scheduler.checkExists(jobKey)) {
            // 库中存在但列表不可见的「孤儿任务」（非 ZXPlat 适配器 / 缺 jobBeanName）须先清理再创建
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
        if (!scheduler.checkExists(jobKey)) {
            throw new SchedulerException("任务不存在: " + jobKey);
        }
        // Job + Trigger 双暂停，并回写 trigger_state，避免接口成功但列表仍显示运行中
        scheduler.pauseJob(jobKey);
        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
        if (triggers != null) {
            for (Trigger trigger : triggers) {
                scheduler.pauseTrigger(trigger.getKey());
            }
        }
        persistTriggerState(jobKey, "PAUSED");
        assertJobPausedOrThrow(jobKey);
    }

    /**
     * 恢复任务。
     *
     * @param jobName  任务名
     * @param jobGroup 任务组
     */
    public void resume(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        ensureSchedulerRunning();
        ensureJobClassCurrent(jobKey);
        scheduler.resumeJob(jobKey);
        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
        if (triggers != null) {
            for (Trigger trigger : triggers) {
                scheduler.resumeTrigger(trigger.getKey());
            }
        }
        // 创建后立即暂停的任务 next_fire_time 可能为 0/过期 且 Trigger 仍为 PAUSED，须重建并强制 resume
        this.repairTriggerScheduleIfNeeded(jobKey, false);
        // 与 pause() 对称写回，避免列表仍以库中 PAUSED 为准显示「已暂停」
        persistTriggerState(jobKey, "WAITING");
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
        ensureSchedulerRunning();
        ensureJobClassCurrent(jobKey);
        // 立即执行不改变启停态：原先 PAUSED 的修复后仍暂停
        repairTriggerScheduleIfNeeded(jobKey, true);
        JobDataMap triggerData = new JobDataMap();
        triggerData.put(MANUAL_TRIGGER, Boolean.TRUE.toString());
        scheduler.triggerJob(jobKey, triggerData);
    }

    /**
     * 确保调度器已启动；standby/未启动时手动执行与 Cron 都不会真正跑。
     */
    private void ensureSchedulerRunning() throws SchedulerException {
        if (scheduler.isShutdown()) {
            throw new SchedulerException("调度器已关闭，无法执行任务");
        }
        if (!scheduler.isStarted() || scheduler.isInStandbyMode()) {
            scheduler.start();
            log.warn("Scheduler 未启动或处于 standby，已自动 start()");
        }
    }

    /**
     * 安全读取 JobDetail：类名不可加载时返回 null，不抛到列表层。
     *
     * @param jobKey 任务键
     * @return JobDetail；不存在或类加载失败时 null
     */
    private JobDetail getJobDetailOrNull(JobKey jobKey) {
        try {
            return scheduler.getJobDetail(jobKey);
        } catch (SchedulerException loadError) {
            log.warn("读取 JobDetail 失败 {}: {}", jobKey, loadError.getMessage());
            return null;
        }
    }

    /**
     * JobDetail 类名不是当前适配器时替换，否则触发时会 ClassNotFound。
     * <p>仅用于 resume / triggerNow 等写路径；列表只读路径不得调用。</p>
     */
    private void ensureJobClassCurrent(JobKey jobKey) throws SchedulerException {
        JobDetail jobDetail;
        try {
            jobDetail = scheduler.getJobDetail(jobKey);
        } catch (SchedulerException loadError) {
            if (!rewriteJobClassNameInDb(jobKey)) {
                throw new SchedulerException("无法加载任务定义（请检查 job_class_name）: " + jobKey, loadError);
            }
            jobDetail = scheduler.getJobDetail(jobKey);
        }
        if (jobDetail == null) {
            if (rewriteJobClassNameInDb(jobKey)) {
                jobDetail = scheduler.getJobDetail(jobKey);
            }
        }
        if (jobDetail == null) {
            throw new SchedulerException("任务定义不存在: " + jobKey);
        }
        Class<? extends Job> targetClass = resolveTargetJobClass(jobDetail);
        if (targetClass.getName().equals(jobDetail.getJobClass().getName())) {
            return;
        }
        JobDetail rebuilt = JobBuilder.newJob(targetClass)
                .withIdentity(jobKey)
                .withDescription(jobDetail.getDescription())
                .usingJobData(jobDetail.getJobDataMap())
                .storeDurably(jobDetail.isDurable())
                .requestRecovery(jobDetail.requestsRecovery())
                .build();
        scheduler.addJob(rebuilt, true, true);
        rewriteJobClassNameInDb(jobKey, targetClass.getName());
        log.info("Replaced job class for {}: {} -> {}",
                jobKey, jobDetail.getJobClass().getName(), targetClass.getName());
    }

    /**
     * 按 is_nonconcurrent / 旧类名把 job_class_name 写成当前适配器 FQCN。
     *
     * @return 是否发生了改写
     */
    private boolean rewriteJobClassNameInDb(JobKey jobKey) {
        if (jdbcTemplate == null) {
            return false;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT job_class_name, is_nonconcurrent FROM zxplat_qrtz_job_details "
                            + "WHERE sched_name = ? AND job_name = ? AND job_group = ?",
                    DEFAULT_SCHED_NAME,
                    jobKey.getName(),
                    jobKey.getGroup());
            if (rows.isEmpty()) {
                return false;
            }
            Map<String, Object> row = rows.get(0);
            String current = row.get("job_class_name") == null ? null : String.valueOf(row.get("job_class_name"));
            Object nonConcurrent = row.get("is_nonconcurrent");
            boolean disallow = Boolean.TRUE.equals(nonConcurrent)
                    || "t".equalsIgnoreCase(String.valueOf(nonConcurrent))
                    || "true".equalsIgnoreCase(String.valueOf(nonConcurrent))
                    || (current != null && current.contains("DisallowConcurrent"));
            String target = disallow
                    ? QuartzDisallowConcurrentJobAdapter.class.getName()
                    : QuartzJobAdapter.class.getName();
            if (target.equals(current)) {
                return false;
            }
            rewriteJobClassNameInDb(jobKey, target);
            return true;
        } catch (DataAccessException dataAccessException) {
            log.warn("读取/改写 job_class_name 失败: {}", dataAccessException.getMessage());
            return false;
        }
    }

    private void rewriteJobClassNameInDb(JobKey jobKey, String targetClassName) {
        if (jdbcTemplate == null || targetClassName == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "UPDATE zxplat_qrtz_job_details SET job_class_name = ? "
                            + "WHERE sched_name = ? AND job_name = ? AND job_group = ?",
                    targetClassName,
                    DEFAULT_SCHED_NAME,
                    jobKey.getName(),
                    jobKey.getGroup());
        } catch (DataAccessException dataAccessException) {
            log.warn("回写 job_class_name 失败: {}", dataAccessException.getMessage());
        }
    }

    private Class<? extends Job> resolveTargetJobClass(JobDetail jobDetail) {
        String concurrent = jobDetail.getJobDataMap().getString(CONCURRENT_POLICY);
        if (ScheduledJobConcurrentPolicy.DISALLOW.getCode().equals(concurrent)) {
            return QuartzDisallowConcurrentJobAdapter.class;
        }
        Class<? extends Job> jobClass = jobDetail.getJobClass();
        if (QuartzDisallowConcurrentJobAdapter.class.isAssignableFrom(jobClass)
                || jobClass.getSimpleName().contains("DisallowConcurrent")) {
            return QuartzDisallowConcurrentJobAdapter.class;
        }
        return QuartzJobAdapter.class;
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
        JobDetail jobDetail = getJobDetailOrNull(jobKey);
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
        snapshot.setStatus(resolveStatus(triggerState, jobKey));
        if (trigger instanceof CronTrigger cronTrigger) {
            snapshot.setCronExpression(cronTrigger.getCronExpression());
        }
        snapshot.setNextFireTime(this.resolveNextFireTime(trigger, snapshot.getStatus(), snapshot.getCronExpression()));
        return Optional.of(snapshot);
    }

    /**
     * 合并 Scheduler 内存键与 zxplat_qrtz_job_details 表键，避免 Scheduler 未加载时列表漏项。
     */
    private Set<JobKey> collectAllJobKeys() throws SchedulerException {
        Set<JobKey> keys = new HashSet<>(scheduler.getJobKeys(GroupMatcher.anyJobGroup()));
        if (jdbcTemplate == null) {
            return keys;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT job_name, job_group FROM zxplat_qrtz_job_details WHERE sched_name = ?",
                    DEFAULT_SCHED_NAME);
            for (Map<String, Object> row : rows) {
                Object jobName = row.get("job_name");
                Object jobGroup = row.get("job_group");
                if (jobName != null && jobGroup != null) {
                    keys.add(JobKey.jobKey(String.valueOf(jobName), String.valueOf(jobGroup)));
                }
            }
        } catch (DataAccessException dataAccessException) {
            log.warn("读取 zxplat_qrtz_job_details 失败，仅使用 Scheduler 任务键: {}",
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

    /** 从 zxplat_qrtz_job_details 读取最小快照（Scheduler 未挂载时）。 */
    private Optional<ScheduledJobSnapshot> loadBareSnapshotFromDb(JobKey jobKey) {
        if (jdbcTemplate == null) {
            return Optional.empty();
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT description FROM zxplat_qrtz_job_details "
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
        JobDetail jobDetail = getJobDetailOrNull(jobKey);
        if (!isZxplatManagedJob(jobDetail)) {
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
        snapshot.setStatus(resolveStatus(triggerState, jobKey));
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
     * 修复 Trigger：next_fire_time 无效/已过期或仍处于 PAUSED 时重建 Cron。
     *
     * @param jobKey        任务键
     * @param restorePaused true=修复前若为暂停则修复后仍暂停（立即执行）；
     *                      false=修复后强制 resume（启用路径，避免「重建后又 pause」导致启用失败）
     */
    private void repairTriggerScheduleIfNeeded(JobKey jobKey, boolean restorePaused) throws SchedulerException {
        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
        if (triggers == null || triggers.isEmpty()) {
            return;
        }
        JobDetail jobDetail = scheduler.getJobDetail(jobKey);
        String misfirePolicy = null;
        if (jobDetail != null) {
            misfirePolicy = jobDetail.getJobDataMap().getString(MISFIRE_POLICY);
        }
        Date now = new Date();
        for (Trigger trigger : triggers) {
            if (!(trigger instanceof CronTrigger cronTrigger)) {
                continue;
            }
            TriggerKey triggerKey = trigger.getKey();
            Trigger.TriggerState state = scheduler.getTriggerState(triggerKey);
            Date next = trigger.getNextFireTime();
            boolean wasPaused = state == Trigger.TriggerState.PAUSED || state == Trigger.TriggerState.NONE;
            boolean invalidNext = next == null || next.getTime() <= 0L || next.before(now);
            if (!wasPaused && !invalidNext) {
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
            // 启用路径必须 resume；立即执行路径才保留原先的暂停态
            if (restorePaused && wasPaused) {
                scheduler.pauseTrigger(triggerKey);
            } else {
                scheduler.resumeTrigger(triggerKey);
            }
            log.info("Repaired trigger schedule: {} ({}) wasPaused={} restorePaused={}",
                    triggerKey.getName(), triggerKey.getGroup(), wasPaused, restorePaused);
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

    /**
     * 暂停后校验 Trigger 均为 PAUSED，避免接口误报成功但列表仍显示运行中。
     */
    private void assertJobPausedOrThrow(JobKey jobKey) throws SchedulerException {
        String dbState = readTriggerStateFromDb(jobKey);
        if ("PAUSED".equals(dbState)) {
            return;
        }
        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
        if (triggers == null || triggers.isEmpty()) {
            return;
        }
        for (Trigger trigger : triggers) {
            Trigger.TriggerState state = scheduler.getTriggerState(trigger.getKey());
            if (state != Trigger.TriggerState.PAUSED) {
                throw new SchedulerException(
                        "任务 Trigger 未能暂停，当前状态=" + state + ": " + trigger.getKey());
            }
        }
    }

    /**
     * 将 Trigger 状态直接写回 zxplat_qrtz_triggers（与 Scheduler API 对齐）。
     */
    private void persistTriggerState(JobKey jobKey, String triggerState) {
        if (jdbcTemplate == null || triggerState == null || triggerState.isBlank()) {
            return;
        }
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE zxplat_qrtz_triggers SET trigger_state = ? "
                            + "WHERE sched_name = ? AND job_name = ? AND job_group = ?",
                    triggerState,
                    DEFAULT_SCHED_NAME,
                    jobKey.getName(),
                    jobKey.getGroup());
            if (updated == 0) {
                log.warn("未更新到 Trigger 行: job={}/{} state={}",
                        jobKey.getName(), jobKey.getGroup(), triggerState);
            }
        } catch (DataAccessException dataAccessException) {
            log.warn("写回 trigger_state 失败: job={}/{} cause={}",
                    jobKey.getName(), jobKey.getGroup(), dataAccessException.getMessage());
        }
    }

    /**
     * 读库中 trigger_state；多 Trigger 时任一非 PAUSED 则返回该值，全部 PAUSED 返回 PAUSED。
     */
    private String readTriggerStateFromDb(JobKey jobKey) {
        if (jdbcTemplate == null) {
            return null;
        }
        try {
            List<String> states = jdbcTemplate.query(
                    "SELECT trigger_state FROM zxplat_qrtz_triggers "
                            + "WHERE sched_name = ? AND job_name = ? AND job_group = ?",
                    (rs, rowNum) -> rs.getString(1),
                    DEFAULT_SCHED_NAME,
                    jobKey.getName(),
                    jobKey.getGroup());
            if (states.isEmpty()) {
                return null;
            }
            for (String state : states) {
                if (state != null && !"PAUSED".equals(state)) {
                    return state;
                }
            }
            return "PAUSED";
        } catch (DataAccessException dataAccessException) {
            log.warn("读取 trigger_state 失败: job={}/{} cause={}",
                    jobKey.getName(), jobKey.getGroup(), dataAccessException.getMessage());
            return null;
        }
    }

    private boolean isUsableNextFireTime(LocalDateTime next) {
        if (next == null) {
            return false;
        }
        if (next.getYear() <= 1971) {
            return false;
        }
        // 已过期的 next_fire_time 不算有效，回退用 Cron 推算
        return next.isAfter(LocalDateTime.now(DEFAULT_ZONE).minusMinutes(1));
    }

    private boolean isZxplatManagedJob(JobDetail jobDetail) {
        if (jobDetail == null) {
            return false;
        }
        Class<? extends Job> jobClass = jobDetail.getJobClass();
        if (jobClass == null) {
            return false;
        }
        // 以类型/简单类名识别适配器，避免库中旧 FQCN 导致列表当孤儿、状态/执行异常
        if (!QuartzJobAdapter.class.isAssignableFrom(jobClass)
                && !jobClass.getSimpleName().equals("QuartzJobAdapter")
                && !jobClass.getSimpleName().equals("QuartzDisallowConcurrentJobAdapter")) {
            return false;
        }
        String beanName = jobDetail.getJobDataMap().getString(JOB_BEAN_NAME);
        return trimToNull(beanName) != null;
    }

    private String resolveConcurrentPolicy(JobDetail jobDetail) {
        Class<? extends Job> jobClass = jobDetail.getJobClass();
        if (QuartzDisallowConcurrentJobAdapter.class.isAssignableFrom(jobClass)
                || jobClass.getSimpleName().contains("DisallowConcurrent")) {
            return ScheduledJobConcurrentPolicy.DISALLOW.getCode();
        }
        return ScheduledJobConcurrentPolicy.ALLOW.getCode();
    }

    private String resolveStatus(Trigger.TriggerState triggerState, JobKey jobKey) {
        // 以库中 trigger_state 为准，避免 Scheduler 内存与 JDBC 行不一致时列表误显示运行中
        String dbState = readTriggerStateFromDb(jobKey);
        if ("PAUSED".equals(dbState)) {
            return ScheduledJobStatus.PAUSE.getCode();
        }
        if (dbState != null && !dbState.isBlank()) {
            if ("COMPLETE".equals(dbState) || "NONE".equals(dbState)) {
                return ScheduledJobStatus.PAUSE.getCode();
            }
            return ScheduledJobStatus.NORMAL.getCode();
        }
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
