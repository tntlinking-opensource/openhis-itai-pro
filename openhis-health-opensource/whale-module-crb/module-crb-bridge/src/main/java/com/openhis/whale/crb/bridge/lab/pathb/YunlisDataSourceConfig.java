/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.lab.pathb;

import com.openhis.whale.crb.domain.config.CrbProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 外部 yunlis 只读 JDBC 数据源（与 Whale 主库隔离）。
 *
 * <p>仅当 {@code openhis.crb.lab.yunlis.enabled=true} 时装配。</p>
 */
@Configuration
@ConditionalOnProperty(name = "openhis.crb.lab.yunlis.enabled", havingValue = "true")
public class YunlisDataSourceConfig {

    /** Bean 名：yunlis DataSource。 */
    public static final String YUNLIS_DATA_SOURCE = "yunlisDataSource";

    /** Bean 名：yunlis JdbcTemplate。 */
    public static final String YUNLIS_JDBC_TEMPLATE = "yunlisJdbcTemplate";

    /**
     * 创建 yunlis Hikari 数据源（小池、只读消费）。
     *
     * @param crbProperties CRB 配置
     * @return DataSource
     */
    @Bean(name = YUNLIS_DATA_SOURCE)
    public DataSource yunlisDataSource(CrbProperties crbProperties) {
        CrbProperties.Yunlis yunlis = crbProperties.getLab().getYunlis();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(yunlis.getJdbcUrl());
        config.setUsername(yunlis.getUsername());
        config.setPassword(yunlis.getPassword());
        config.setPoolName("crb-yunlis");
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(0);
        // Outbox 需 UPDATE process_status，不可设全局 readOnly
        return new HikariDataSource(config);
    }

    /**
     * yunlis 专用 JdbcTemplate。
     *
     * @param yunlisDataSource yunlis 数据源
     * @return JdbcTemplate
     */
    @Bean(name = YUNLIS_JDBC_TEMPLATE)
    public JdbcTemplate yunlisJdbcTemplate(@Qualifier(YUNLIS_DATA_SOURCE) DataSource yunlisDataSource) {
        return new JdbcTemplate(yunlisDataSource);
    }
}
