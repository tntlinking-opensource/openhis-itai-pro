package com.openhis.whale.report.domain.constants;

/**
 * 报表域租户范围：平台 seed（tenantId=0）与院点覆盖。
 */
public final class ReportTenantScope {

    /** 出厂平台租户 id */
    public static final long PLATFORM_TENANT_ID = 0L;

    private ReportTenantScope() {
    }

    /** 未传租户时回退平台租户。 */
    public static long effectiveTenant(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return PLATFORM_TENANT_ID;
    }

    /** 实体上的 tenantId，null 视为平台。 */
    public static long entityTenant(Long tenantId) {
        return effectiveTenant(tenantId);
    }
}
