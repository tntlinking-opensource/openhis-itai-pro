/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

import java.util.Set;

/**
 * 临床观察定义（DM-CLN-005）目录常量：分类码与发布态（与 {@code cln_observation_definition} 落库 code 一致）。
 */
public final class ObservationDefinitionConstants {

    private ObservationDefinitionConstants() {
    }

    /** 体检小项（结果录入单位）。 */
    public static final String CATEGORY_EXAM_ITEM = "exam_item";

    /** 生命体征类观察项。 */
    public static final String CATEGORY_VITALS = "vitals";

    /** 项目结论项（DES-04；落库 category_code 为 dept_conclusion）。 */
    public static final String CATEGORY_PROJECT_CONCLUSION = "dept_conclusion";

    /**
     * @deprecated 使用 {@link #CATEGORY_PROJECT_CONCLUSION}
     */
    @Deprecated
    public static final String CATEGORY_DEPT_CONCLUSION = CATEGORY_PROJECT_CONCLUSION;

    /** 其他观察分类。 */
    public static final String CATEGORY_OTHER = "other";

    /** 目录允许的 category_code 全集（BR-CLN-OD-03）。 */
    public static final Set<String> ALLOWED_CATEGORY_CODES = Set.of(
        CATEGORY_EXAM_ITEM,
        CATEGORY_VITALS,
        CATEGORY_PROJECT_CONCLUSION,
        CATEGORY_OTHER
    );

    /** 启用（与 Flyway 默认 status_enum 一致，小写）。 */
    public static final String STATUS_ACTIVE = "active";

    /** 停用。 */
    public static final String STATUS_INACTIVE = "inactive";

  /**
   * 判断观察定义是否为启用态（兼容 PEIS 小写 {@code active} 与 PublicationStatus {@code ACTIVE}）。
   *
   * @param statusEnum 落库 status_enum
   * @return 启用则 true
   */
  public static boolean isActiveStatus(String statusEnum) {
    if (statusEnum == null || statusEnum.trim().isEmpty()) {
      return false;
    }
    String normalized = statusEnum.trim();
    return STATUS_ACTIVE.equalsIgnoreCase(normalized) || "ACTIVE".equalsIgnoreCase(normalized);
  }
}
