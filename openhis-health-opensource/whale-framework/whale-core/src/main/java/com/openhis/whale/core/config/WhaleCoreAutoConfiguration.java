/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.config;

import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.event.DomainEventPublisher;
import com.openhis.whale.core.infrastructure.multitenancy.HospitalSessionStore;
import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import com.openhis.whale.core.infrastructure.multitenancy.TenantSessionStore;
import com.openhis.whale.core.infrastructure.multitenancy.ThreadLocalTenantContext;
import com.openhis.whale.core.infrastructure.security.CurrentUser;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import com.openhis.whale.core.infrastructure.security.OrganizationUnitSessionStore;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import com.openhis.whale.core.application.IPractitionerManagedScopeResolver;
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
@EntityScan(basePackages = "com.openhis.whale.core")
@EnableJpaRepositories(basePackages = "com.openhis.whale.core.infrastructure.sequence")
public class WhaleCoreAutoConfiguration {

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
