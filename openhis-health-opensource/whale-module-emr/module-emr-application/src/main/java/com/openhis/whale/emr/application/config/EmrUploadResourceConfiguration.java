/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.config;

import com.openhis.whale.core.application.util.FileUploadUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * 暴露 EMR 编辑器上传图片：{@code /uploads/emr/**} → {@code {user.dir}/uploads/emr/}。
 */
@Configuration
public class EmrUploadResourceConfiguration implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String base = FileUploadUtils.getDefaultBaseDir();
        String emrDir = base + File.separator + "emr" + File.separator;
        registry.addResourceHandler("/uploads/emr/**")
                .addResourceLocations("file:" + emrDir);
    }
}
