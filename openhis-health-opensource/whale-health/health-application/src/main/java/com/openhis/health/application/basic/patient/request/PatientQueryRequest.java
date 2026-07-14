/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.patient.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 患者分页查询请求（患者管理菜单）
 *
 * <p>检索关键字见 {@link AbstractPageQuery} 的 {@code keyword}。</p>
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PatientQueryRequest extends AbstractHealthPageRequest {

}
