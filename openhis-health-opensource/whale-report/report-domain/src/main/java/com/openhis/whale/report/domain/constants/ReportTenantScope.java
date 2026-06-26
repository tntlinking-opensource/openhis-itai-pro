package com.openhis.whale.report.domain.constants;

/**
 * sqlReport 域租户范围：平台 seed（tenantId=0）与院点覆盖。
 */
public final class ReportTenantScope {

    /** 出厂平台租户 id（Flyway 种子与 Loader 出厂定义）。 */
    public static final long PLATFORM_TENANT_ID = 0L;

    private ReportTenantScope() {
    }

    /**
     * 解析有效租户 id；未传时回退平台租户。
     *
     * @param tenantId 请求上下文租户，可为 null
     * @return 非 null 租户 id
     */
    public static long effectiveTenant(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return PLATFORM_TENANT_ID;
    }

    /**
     * 实体字段上的 tenantId 规范化；null 视为平台租户。
     *
     * @param tenantId 实体 tenantId
     * @return 非 null 租户 id
     */
    public static long entityTenant(Long tenantId) {
        return effectiveTenant(tenantId);
    }
}
