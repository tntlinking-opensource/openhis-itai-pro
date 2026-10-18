/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.config;

import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import com.zxplat.framework.core.infrastructure.event.DomainEventPublisher;
import com.zxplat.framework.core.infrastructure.multitenancy.HospitalSessionStore;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantContext;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantSessionStore;
import com.zxplat.framework.core.infrastructure.multitenancy.ThreadLocalTenantContext;
import com.zxplat.framework.core.infrastructure.security.CurrentUser;
import com.zxplat.framework.core.infrastructure.security.ICurrentUser;
import com.zxplat.framework.core.infrastructure.security.OrganizationUnitSessionStore;
import com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService;
import com.zxplat.framework.core.application.IPractitionerManagedScopeResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration
@EntityScan(basePackages = "com.zxplat.framework.core")
@EnableJpaRepositories(basePackages = "com.zxplat.framework.core.infrastructure.sequence")
public class ZxplatCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TenantContext.class)
    public TenantContext tenantContext() {
        return new ThreadLocalTenantContext();
    }

    @Bean
    @ConditionalOnMissingBean(BusinessSequenceService.class)
    public BusinessSequenceService businessSequenceService(JdbcTemplate jdbcTemplate) {
        return new BusinessSequenceService(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(ICurrentUser.class)
    public ICurrentUser currentUser(
            TenantContext tenantContext,
            @Lazy OrganizationUnitSessionStore organizationUnitSessionStore,
            @Lazy TenantSessionStore tenantSessionStore,
            @Lazy HospitalSessionStore hospitalSessionStore,
            ObjectProvider<IPractitionerManagedScopeResolver> practitionerManagedScopeResolver) {
        return new CurrentUser(
                tenantContext,
                organizationUnitSessionStore,
                tenantSessionStore,
                hospitalSessionStore,
                practitionerManagedScopeResolver.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(IDomainEventPublisher.class)
    public IDomainEventPublisher domainEventPublisher(
            ApplicationEventPublisher applicationEventPublisher,
            ICurrentUser currentUser) {
        return new DomainEventPublisher(applicationEventPublisher, currentUser);
    }
}
