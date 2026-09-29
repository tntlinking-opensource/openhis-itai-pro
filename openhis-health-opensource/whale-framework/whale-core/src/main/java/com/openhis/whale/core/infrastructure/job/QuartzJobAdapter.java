/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quartz Job适配器
 *
 * <p>将Quartz的Job接口适配到IJob接口，支持Spring Bean注入，并在执行结束后通知 {@link IJobExecutionListener}。</p>
 */
@Slf4j
public class QuartzJobAdapter implements Job {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    /** 与 whale-job 模块 {@code ScheduledJobQuartzKeys#MANUAL_TRIGGER} 保持一致。 */
    private static final String MANUAL_TRIGGER_KEY = "whaleManualTrigger";

    /** 与 whale-job 模块 {@code ScheduledJobQuartzKeys#DISPLAY_NAME} 保持一致。 */
    private static final String DISPLAY_NAME_KEY = "displayName";

    /** 与 whale-job 模块 {@code ScheduledJobQuartzKeys#TENANT_ID} 保持一致。 */
    private static final String TENANT_ID_KEY = "tenantId";

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private AppServiceJobMethodRegistry appServiceJobMethodRegistry;

    @Autowired(required = false)
    private AppServiceJobMethodInvoker appServiceJobMethodInvoker;

    @Autowired(required = false)
    private List<IJobExecutionListener> jobExecutionListeners = new ArrayList<>();

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        long startedAtMillis = System.currentTimeMillis();
        LocalDateTime startedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(startedAtMillis), DEFAULT_ZONE);

        JobExecutionReport report = new JobExecutionReport();
        report.setQuartzJobName(context.getJobDetail().getKey().getName());
        report.setQuartzJobGroup(context.getJobDetail().getKey().getGroup());
        report.setStartedAt(startedAt);

        Map<String, Object> parameters = new HashMap<>();
        context.getMergedJobDataMap().forEach(parameters::put);
        report.setParameters(parameters);

        String jobBeanName = context.getMergedJobDataMap().getString("jobBeanName");
        report.setJobBeanName(jobBeanName);
        report.setDisplayName(context.getMergedJobDataMap().getString(DISPLAY_NAME_KEY));
        report.setTenantId(resolveTenantId(context.getMergedJobDataMap()));
        report.setExecutionType(resolveExecutionType(context.getMergedJobDataMap()));

        try {
            if (jobBeanName == null || jobBeanName.isEmpty()) {
                throw new JobExecutionException("Job bean name not found in JobDataMap");
            }

            JobContext jobContext = new JobContext();
            jobContext.setQuartzContext(context);
            jobContext.setJobName(context.getJobDetail().getKey().getName());
            jobContext.setJobGroup(context.getJobDetail().getKey().getGroup());
            jobContext.setParameters(parameters);
            if (report.getTenantId() != null) {
                jobContext.setTenantId(report.getTenantId());
            }

            log.info("Executing job: {} (group: {}) handler={}", jobContext.getJobName(), jobContext.getJobGroup(), jobBeanName);
            AppServiceJobMethodRegistry jobMethodRegistry = resolveAppServiceJobMethodRegistry();
            AppServiceJobMethodInvoker jobMethodInvoker = resolveAppServiceJobMethodInvoker();
            if (jobMethodRegistry != null
                    && jobMethodInvoker != null
                    && jobMethodRegistry.exists(jobBeanName)) {
                jobMethodInvoker.invoke(jobBeanName, jobContext);
            } else {
                ApplicationContext springContext = requireApplicationContext();
                IJob job = springContext.getBean(jobBeanName, IJob.class);
                job.execute(jobContext);
            }
            log.info("Job completed: {} (group: {})", jobContext.getJobName(), jobContext.getJobGroup());
            report.setSuccess(true);
        } catch (Exception e) {
            log.error("Error executing job: {}", context.getJobDetail().getKey(), e);
            report.setSuccess(false);
            report.setErrorMessage(resolveErrorMessage(e));
            notifyJobExecutionListeners(report, startedAtMillis);
            throw new JobExecutionException("Job execution failed", e);
        }

        notifyJobExecutionListeners(report, startedAtMillis);
    }

    /**
     * 通知所有已注册的任务执行监听器。
     */
    private void notifyJobExecutionListeners(JobExecutionReport report, long startedAtMillis) {
        report.setDurationMs(System.currentTimeMillis() - startedAtMillis);
        report.setFinishedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), DEFAULT_ZONE));
        if (jobExecutionListeners == null || jobExecutionListeners.isEmpty()) {
            return;
        }
        for (IJobExecutionListener listener : jobExecutionListeners) {
            try {
                listener.onJobFinished(report);
            } catch (Exception listenerError) {
                log.warn("Job execution listener failed: {}", listenerError.getMessage());
            }
        }
    }

    /**
     * 从异常链提取可读 message（反射包装常无自身 message，须下钻 cause）。
     *
     * @param error 任务执行异常
     * @return 非空错误文案；整链皆空时回落类名
     */
    private String resolveErrorMessage(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                return message;
            }
            Throwable cause = current.getCause();
            if (cause == null || cause == current) {
                break;
            }
            current = cause;
        }
        if (error == null) {
            return "unknown job error";
        }
        return error.getClass().getName();
    }

    private Long resolveTenantId(JobDataMap dataMap) {
        Object tenantId = dataMap.get(TENANT_ID_KEY);
        if (tenantId instanceof Long) {
            return (Long) tenantId;
        }
        if (tenantId instanceof Number) {
            return ((Number) tenantId).longValue();
        }
        if (tenantId instanceof String) {
            try {
                return Long.parseLong((String) tenantId);
            } catch (NumberFormatException e) {
                log.warn("Invalid tenantId format: {}", tenantId);
            }
        }
        return null;
    }

    private String resolveExecutionType(JobDataMap dataMap) {
        Object manual = dataMap.get(MANUAL_TRIGGER_KEY);
        if (manual instanceof Boolean && (Boolean) manual) {
            return "MANUAL";
        }
        if (manual instanceof String && Boolean.parseBoolean((String) manual)) {
            return "MANUAL";
        }
        return "SCHEDULED";
    }

    private ApplicationContext requireApplicationContext() {
        ApplicationContext context = resolveApplicationContext();
        if (context == null) {
            throw new IllegalStateException("ApplicationContext is not available in QuartzJobAdapter");
        }
        return context;
    }

    private ApplicationContext resolveApplicationContext() {
        if (applicationContext != null) {
            return applicationContext;
        }
        return null;
    }

    private AppServiceJobMethodRegistry resolveAppServiceJobMethodRegistry() {
        if (appServiceJobMethodRegistry != null) {
            return appServiceJobMethodRegistry;
        }
        ApplicationContext context = resolveApplicationContext();
        if (context == null) {
            return null;
        }
        try {
            return context.getBean(AppServiceJobMethodRegistry.class);
        } catch (NoSuchBeanDefinitionException ignored) {
            return null;
        }
    }

    private AppServiceJobMethodInvoker resolveAppServiceJobMethodInvoker() {
        if (appServiceJobMethodInvoker != null) {
            return appServiceJobMethodInvoker;
        }
        ApplicationContext context = resolveApplicationContext();
        if (context == null) {
            return null;
        }
        try {
            return context.getBean(AppServiceJobMethodInvoker.class);
        } catch (NoSuchBeanDefinitionException ignored) {
            return null;
        }
    }
}
