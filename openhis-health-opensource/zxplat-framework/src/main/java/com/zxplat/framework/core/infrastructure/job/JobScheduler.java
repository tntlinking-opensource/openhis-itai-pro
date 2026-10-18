/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 任务调度器
 *
 * <p>提供任务调度、暂停、恢复、删除等功能</p>
 *
 * @author OpenHIS Contributors
 */
@Slf4j
@Component
public class JobScheduler {

    @Autowired
    private Scheduler scheduler;

    /**
     * 调度一个简单任务（立即执行一次）
     *
     * @param jobBeanName 任务Bean名称（实现IJob接口的Spring Bean）
     * @param jobName 任务名称
     * @param jobGroup 任务组名
     * @param parameters 任务参数
     * @throws SchedulerException 调度异常
     */
    public void scheduleSimpleJob(String jobBeanName, String jobName, String jobGroup,
                                   Map<String, Object> parameters) throws SchedulerException {
        JobDetail jobDetail = createJobDetail(jobBeanName, jobName, jobGroup, parameters);
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(jobName + "_trigger", jobGroup)
                .startNow()
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
        log.info("Scheduled simple job: {} (group: {})", jobName, jobGroup);
    }

    /**
     * 调度一个Cron任务
     *
     * @param jobBeanName 任务Bean名称
     * @param jobName 任务名称
     * @param jobGroup 任务组名
     * @param cronExpression Cron表达式
     * @param parameters 任务参数
     * @throws SchedulerException 调度异常
     */
    public void scheduleCronJob(String jobBeanName, String jobName, String jobGroup,
                                String cronExpression, Map<String, Object> parameters) throws SchedulerException {
        JobDetail jobDetail = createJobDetail(jobBeanName, jobName, jobGroup, parameters);
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(jobName + "_trigger", jobGroup)
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
        log.info("Scheduled cron job: {} (group: {}, cron: {})", jobName, jobGroup, cronExpression);
    }

    /**
     * 调度一个固定间隔任务
     *
     * @param jobBeanName 任务Bean名称
     * @param jobName 任务名称
     * @param jobGroup 任务组名
     * @param intervalInSeconds 间隔时间（秒）
     * @param repeatCount 重复次数（-1表示无限重复）
     * @param parameters 任务参数
     * @throws SchedulerException 调度异常
     */
    public void scheduleIntervalJob(String jobBeanName, String jobName, String jobGroup,
                                    int intervalInSeconds, int repeatCount,
                                    Map<String, Object> parameters) throws SchedulerException {
        JobDetail jobDetail = createJobDetail(jobBeanName, jobName, jobGroup, parameters);

        SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInSeconds(intervalInSeconds);

        if (repeatCount >= 0) {
            scheduleBuilder.withRepeatCount(repeatCount);
        } else {
            scheduleBuilder.repeatForever();
        }

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(jobName + "_trigger", jobGroup)
                .withSchedule(scheduleBuilder)
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
        log.info("Scheduled interval job: {} (group: {}, interval: {}s, repeat: {})",
                jobName, jobGroup, intervalInSeconds, repeatCount);
    }

    /**
     * 暂停任务
     *
     * @param jobName 任务名称
     * @param jobGroup 任务组名
     * @throws SchedulerException 调度异常
     */
    public void pauseJob(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        scheduler.pauseJob(jobKey);
        log.info("Paused job: {} (group: {})", jobName, jobGroup);
    }

    /**
     * 恢复任务
     *
     * @param jobName 任务名称
     * @param jobGroup 任务组名
     * @throws SchedulerException 调度异常
     */
    public void resumeJob(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        scheduler.resumeJob(jobKey);
        log.info("Resumed job: {} (group: {})", jobName, jobGroup);
    }

    /**
     * 删除任务
     *
     * @param jobName 任务名称
     * @param jobGroup 任务组名
     * @throws SchedulerException 调度异常
     */
    public void deleteJob(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        scheduler.deleteJob(jobKey);
        log.info("Deleted job: {} (group: {})", jobName, jobGroup);
    }

    /**
     * 立即触发任务
     *
     * @param jobName 任务名称
     * @param jobGroup 任务组名
     * @throws SchedulerException 调度异常
     */
    public void triggerJob(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        scheduler.triggerJob(jobKey);
        log.info("Triggered job: {} (group: {})", jobName, jobGroup);
    }

    /**
     * 创建JobDetail
     */
    private JobDetail createJobDetail(String jobBeanName, String jobName, String jobGroup,
                                      Map<String, Object> parameters) {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("jobBeanName", jobBeanName);
        if (parameters != null) {
            jobDataMap.putAll(parameters);
        }

        return JobBuilder.newJob(QuartzJobAdapter.class)
                .withIdentity(jobName, jobGroup)
                .withDescription("ZXPlat Framework Job: " + jobBeanName)
                .usingJobData(jobDataMap)
                .storeDurably()
                .build();
    }
}
