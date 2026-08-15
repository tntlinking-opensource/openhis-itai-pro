/**
 * 接口请求体 ID 规范化：禁止将雪花 Long 等标识用 Number() 再提交（会丢精度）。
 * 与 `.cursor/skills/frontend-codegen/SKILL.md`「请求体中的 ID」一节一致。
 */

/** 写入 POST/PUT 请求体的 id：string 原样保留；禁止以 JSON number 类型提交业务 Long。 */
export function preserveApiIdForRequest(raw: unknown): string | undefined {
  if (raw === undefined || raw === null) return undefined;
  if (typeof raw === 'string') {
    const s = raw.trim();
    return s === '' ? undefined : s;
  }
  if (typeof raw === 'bigint') return String(raw);
  if (typeof raw === 'number') {
    if (!Number.isFinite(raw)) return undefined;
    return String(raw);
  }
  const s = String(raw).trim();
  return s === '' ? undefined : s;
}

/** 当前登录用户医务人员 id（审核 approverId 等），禁止 Number(practitionerId) 再传参。 */
export function resolveLoginPractitionerIdForRequest(
  userInfo: null | Record<string, unknown> | undefined,
): string | undefined {
  if (!userInfo) return undefined;
  const ext = (userInfo.extraProperties ?? {}) as Record<string, unknown>;
  const raw = userInfo.practitionerId ?? ext.PractitionerId ?? ext.practitionerId;
  return preserveApiIdForRequest(raw);
}

/**
 * 当前登录科室 id：优先登录所选科室，其次医务人员所属科室。
 * 禁止 Number(organizationId) 再传参。
 *
 * @param userInfo 登录用户信息
 * @param selectedOrganizationId 登录页所选科室（可空）
 */
export function resolveLoginOrganizationIdForRequest(
  userInfo: null | Record<string, unknown> | undefined,
  selectedOrganizationId?: null | string,
): string | undefined {
  const selected = preserveApiIdForRequest(selectedOrganizationId);
  if (selected) {
    return selected;
  }
  if (!userInfo) return undefined;
  const ext = (userInfo.extraProperties ?? {}) as Record<string, unknown>;
  const raw = userInfo.organizationId ?? ext.OrganizationId ?? ext.organizationId;
  return preserveApiIdForRequest(raw);
}
