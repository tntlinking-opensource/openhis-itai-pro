package com.openhis.whale.report.application.config;

import com.openhis.whale.core.application.util.FileUploadUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * 暴露套打定位纸上传资源：{@code /uploads/report-guides/**} → {@code {user.dir}/uploads/report-guides/}。
 */
@Configuration
public class ReportUploadResourceConfiguration implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String base = FileUploadUtils.getDefaultBaseDir();
        String guideDir = base + File.separator + "report-guides" + File.separator;
        registry.addResourceHandler("/uploads/report-guides/**")
                .addResourceLocations("file:" + guideDir);
    }
}
