/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application.dto;

/**
 * 平台用户关联的医务人员与行政机构主键（{@code adm_practitioner.id}、{@code adm_organization.id}）。
 */
public record PractitionerOrganizationIds(Long practitionerId, Long organizationId) {}
