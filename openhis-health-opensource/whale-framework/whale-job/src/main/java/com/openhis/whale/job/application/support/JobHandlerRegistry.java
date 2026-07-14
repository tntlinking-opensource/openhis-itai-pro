/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.application.support;

import com.openhis.whale.core.infrastructure.job.AppServiceJobMethodDescriptor;
import com.openhis.whale.core.infrastructure.job.AppServiceJobMethodRegistry;
import com.openhis.whale.core.infrastructure.job.IJob;
import com.openhis.whale.job.application.dto.JobHandlerOptionDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 扫描 {@link IJob} Bean 与 {@link com.openhis.whale.core.infrastructure.job.SchedulableJob} 标记的 AppService 方法，
 * 供运维在页面上选择任务处理器。
 */
@Component
public class JobHandlerRegistry {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AppServiceJobMethodRegistry appServiceJobMethodRegistry;

    /**
     * 列出所有可用处理器（IJob Bean + AppService 方法，按 key 排序）。
     *
     * @return 处理器选项列表
     */
    public List<JobHandlerOptionDto> listHandlers() {
        List<JobHandlerOptionDto> options = new ArrayList<>();
        Map<String, IJob> beans = applicationContext.getBeansOfType(IJob.class);
        for (Map.Entry<String, IJob> entry : beans.entrySet()) {
            IJob job = entry.getValue();
            JobHandlerOptionDto option = new JobHandlerOptionDto();
            option.setBeanName(entry.getKey());
            option.setJobName(job.getJobName());
            option.setDescription(job.getJobDescription());
            options.add(option);
        }
        for (AppServiceJobMethodDescriptor descriptor : appServiceJobMethodRegistry.listHandlers()) {
            JobHandlerOptionDto option = new JobHandlerOptionDto();
            option.setBeanName(descriptor.getHandlerKey());
            option.setJobName(descriptor.getMethodName());
            option.setDescription(descriptor.getDescription());
            options.add(option);
        }
        options.sort(Comparator.comparing(JobHandlerOptionDto::getBeanName));
        return options;
    }

    /**
     * 校验处理器 key 是否存在（IJob Bean 或 AppService 方法路由）。
     *
     * @param beanName 处理器 key
     * @return 是否存在
     */
    public boolean exists(String beanName) {
        if (beanName == null || beanName.trim().isEmpty()) {
            return false;
        }
        String normalized = AppServiceJobMethodRegistry.normalizeHandlerKey(beanName);
        if (appServiceJobMethodRegistry.exists(normalized)) {
            return true;
        }
        return applicationContext.containsBean(normalized)
                && applicationContext.getBean(normalized) instanceof IJob;
    }
}
