/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.runtime.insight;

import com.zxplat.framework.core.application.util.JsonUtils;
import com.zxplat.framework.core.domain.event.DomainEvent;
import com.zxplat.framework.core.infrastructure.runtime.IRuntimeInsightStore;
import com.zxplat.framework.core.infrastructure.runtime.RuntimeInsightInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理主数据（adm_organization / adm_practitioner）增删改运行时洞察。
 * 通过反射绑定 Health 领域事件，无需 health-application 参与。
 */
@Component
public class HealthAdministrationRuntimeInsightListener {

    private static final Logger log = LoggerFactory.getLogger(HealthAdministrationRuntimeInsightListener.class);
    private static final String RUNTIME_EXECUTOR = "frameworkRuntimeExecutor";
    private static final String CATEGORY_BUSINESS = "business";

    private static final Map<String, String> INSIGHT_CODE_BY_EVENT = Map.of(
            "OrganizationCreatedEvent", "adm_organization.create",
            "OrganizationEditedEvent", "adm_organization.update",
            "OrganizationAbolishedEvent", "adm_organization.delete",
            "PractitionerCreatedEvent", "adm_practitioner.create",
            "PractitionerEditedEvent", "adm_practitioner.update",
            "PractitionerRetiredEvent", "adm_practitioner.delete"
    );

    private final IRuntimeInsightStore runtimeInsightStore;
    private final FrameworkRuntimeContextResolver runtimeContextResolver;
    private final ApplicationContext applicationContext;

    public HealthAdministrationRuntimeInsightListener(
            IRuntimeInsightStore runtimeInsightStore,
            FrameworkRuntimeContextResolver runtimeContextResolver,
            ApplicationContext applicationContext) {
        this.runtimeInsightStore = runtimeInsightStore;
        this.runtimeContextResolver = runtimeContextResolver;
        this.applicationContext = applicationContext;
    }

    @Async(RUNTIME_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDomainEvent(DomainEvent event) {
        if (event == null) {
            return;
        }
        try {
            String insightCode = INSIGHT_CODE_BY_EVENT.get(event.getClass().getSimpleName());
            if (insightCode == null) {
                return;
            }
            Map<String, Object> payload = buildPayload(event);
            runtimeInsightStore.record(RuntimeInsightInfo.builder()
                    .insightCode(insightCode)
                    .category(CATEGORY_BUSINESS)
                    .operatorUserId(runtimeContextResolver.resolveLoginUserId())
                    .payloadJson(JsonUtils.toJson(payload))
                    .observedAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.trace("Framework runtime insight skipped: {}", e.getMessage());
        }
    }

    private Map<String, Object> buildPayload(DomainEvent event) {
        String eventName = event.getClass().getSimpleName();
        if (eventName.startsWith("Organization")) {
            return buildOrganizationPayload(event);
        }
        if (eventName.startsWith("Practitioner")) {
            return buildPractitionerPayload(event);
        }
        return runtimeContextResolver.buildCommonContext();
    }

    private Map<String, Object> buildOrganizationPayload(DomainEvent event) {
        String name = readStringProperty(event, "name");
        String typeEnumCode = readStringProperty(event, "typeEnum");
        if (name != null && typeEnumCode != null) {
            return runtimeContextResolver.buildOrganizationPayload(name, resolveOrganizationTypeDisplay(typeEnumCode));
        }
        Map<String, Object> loaded = loadOrganizationFields(event.getAggregateId());
        if (name == null) {
            name = asString(loaded.get("organizationName"));
        }
        String organizationType = typeEnumCode != null
                ? resolveOrganizationTypeDisplay(typeEnumCode)
                : asString(loaded.get("organizationType"));
        return runtimeContextResolver.buildOrganizationPayload(name, organizationType);
    }

    private Map<String, Object> buildPractitionerPayload(DomainEvent event) {
        Map<String, Object> orgFields = loadOrganizationFieldsViaPractitioner(event.getAggregateId());
        return runtimeContextResolver.buildOrganizationPayload(
                asString(orgFields.get("organizationName")),
                asString(orgFields.get("organizationType")));
    }

    private Map<String, Object> loadOrganizationFields(Long organizationId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (organizationId == null) {
            return result;
        }
        try {
            Object queryService = applicationContext.getBean(
                    Class.forName("com.zxplat.domain.administration.organization.service.OrganizationQueryService"));
            Method method = queryService.getClass().getMethod("loadOrganizationByIdForDisplay", Long.class);
            Object organization = method.invoke(queryService, organizationId);
            if (organization != null) {
                result.put("organizationName", invokeGetter(organization, "getName"));
                result.put("organizationType", resolveOrganizationTypeDisplay(readStringProperty(organization, "typeEnum")));
            }
        } catch (Exception ignored) {
            // Health 模块未就绪时跳过
        }
        return result;
    }

    private Map<String, Object> loadOrganizationFieldsViaPractitioner(Long practitionerId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (practitionerId == null) {
            return result;
        }
        try {
            Object queryService = applicationContext.getBean(
                    Class.forName("com.zxplat.domain.administration.practitioner.service.PractitionerQueryService"));
            Method method = queryService.getClass().getMethod("findById", Long.class);
            Object practitioner = method.invoke(queryService, practitionerId);
            if (practitioner == null) {
                return result;
            }
            Object orgId = invokeGetter(practitioner, "getOrgId");
            if (orgId instanceof Long organizationId) {
                return loadOrganizationFields(organizationId);
            }
        } catch (Exception ignored) {
            // Health 模块未就绪时跳过
        }
        return result;
    }

    private String resolveOrganizationTypeDisplay(String typeEnumCode) {
        if (typeEnumCode == null || typeEnumCode.isBlank()) {
            return null;
        }
        try {
            Object queryService = applicationContext.getBean(
                    Class.forName("com.zxplat.domain.terminology.service.CodeSystemQueryService"));
            Class<?> enumClass = Class.forName("com.zxplat.domain.share.enums.OrganizationType");
            Method method = queryService.getClass().getMethod("getDisplayByCode", Class.class, String.class);
            Object display = method.invoke(queryService, enumClass, typeEnumCode);
            return display != null ? String.valueOf(display) : typeEnumCode;
        } catch (Exception ignored) {
            return typeEnumCode;
        }
    }

    private static String readStringProperty(Object target, String propertyName) {
        if (target == null) {
            return null;
        }
        try {
            String getter = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
            Object value = target.getClass().getMethod(getter).invoke(target);
            return value != null ? String.valueOf(value) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Object invokeGetter(Object target, String getterName) {
        try {
            return target.getClass().getMethod(getterName).invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
