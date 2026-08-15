/**
 * 病案菜单「门诊/住院病例查询」临床编辑门禁与病案管理员判定。
 *
 * <p>门诊：登录人 = 接诊医生（ADMITTER）且病例未归档 → 可写，否则只读。
 * 住院：登录人所属/所选科室 = 就诊入院科室 → 可写；其他科室只读；已「归档病例」只读。
 * 「归档病例 / 解档病例」仅 {@code ROLE_MRMS_ADMIN}。</p>
 */

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 内置病案管理员角色 code（与后端 WhalePlatformRoleCodeConstants 一致） */
export const ROLE_MRMS_ADMIN = 'ROLE_MRMS_ADMIN';

/** 门诊行上用于比对接诊医生与病例归档态的最小字段 */
export interface OutpatientClinicalEditGateRow {
  admitterPractitionerId?: string;
  /** 病例是否已归档（文书 LOCKED/ARCHIVED） */
  clinicalArchived?: boolean;
}

/** 住院行上用于比对入院科室与病例归档态的最小字段 */
export interface InpatientClinicalEditGateRow {
  /** 就诊入院科室主键 */
  organizationId?: string;
  /** 病例是否已归档（就诊下存在 LOCKED 文书） */
  clinicalArchived?: boolean;
}

/** 住院临床不可编辑时的原因（用于提示） */
export type InpatientClinicalEditDenialReason =
  | 'no_login_practitioner'
  | 'no_login_organization'
  | 'clinical_archived'
  | 'other_department';

/**
 * 比较两个业务主键（string 化后），防雪花精度问题。
 *
 * @param a 左侧 id
 * @param b 右侧 id
 * @return 是否同一主键
 */
function sameEntityId(a?: null | string, b?: null | string): boolean {
  const left = preserveApiIdForRequest(a);
  const right = preserveApiIdForRequest(b);
  if (!left || !right) {
    return false;
  }
  return left === right;
}

/**
 * 当前登录角色是否含病案管理员（可归档/解档病例）。
 *
 * @param userRoles 登录态角色 code 列表
 * @return true 表示可操作归档病例
 */
export function isMrmsAdminRole(userRoles: null | string[] | undefined): boolean {
  if (!userRoles?.length) {
    return false;
  }
  // 与后端 Gate 一致：忽略大小写比对 ROLE_MRMS_ADMIN
  for (const role of userRoles) {
    if (typeof role !== 'string') {
      continue;
    }
    if (role.trim().toUpperCase() === ROLE_MRMS_ADMIN) {
      return true;
    }
  }
  return false;
}

/**
 * 门诊病例：是否允许编辑（须为本人接诊且病例未归档）。
 *
 * @param loginPractitionerId 当前登录医务人员主键
 * @param row 列表行
 * @return true 可写；false 只读
 */
export function isOutpatientClinicalEditable(
  loginPractitionerId: null | string | undefined,
  row: OutpatientClinicalEditGateRow,
): boolean {
  // 病例归档后强制只读（病案管理员亦不可改文书，仅可解档）
  if (row.clinicalArchived) {
    return false;
  }
  return sameEntityId(loginPractitionerId, row.admitterPractitionerId);
}

/**
 * 住院病例不可编辑原因（可写时返回 null）。
 *
 * <p>同入院科室可写；仅「归档病例」额外锁文书。</p>
 *
 * @param loginPractitionerId 当前登录医务人员主键（须为大夫账号）
 * @param loginOrganizationId 登录所选/所属科室主键
 * @param row 列表行
 * @return 不可编辑原因；可写为 null
 */
export function resolveInpatientClinicalEditDenial(
  loginPractitionerId: null | string | undefined,
  loginOrganizationId: null | string | undefined,
  row: InpatientClinicalEditGateRow,
): InpatientClinicalEditDenialReason | null {
  // 无医务人员身份：非临床大夫账号
  if (!preserveApiIdForRequest(loginPractitionerId)) {
    return 'no_login_practitioner';
  }
  // 临床「归档病例」后文书 LOCKED，本科室也只读
  if (row.clinicalArchived) {
    return 'clinical_archived';
  }
  const loginOrg = preserveApiIdForRequest(loginOrganizationId);
  if (!loginOrg) {
    return 'no_login_organization';
  }
  // 行上缺科室主键时无法放行（避免误开写）
  const rowOrg = preserveApiIdForRequest(row.organizationId);
  if (!rowOrg || rowOrg !== loginOrg) {
    return 'other_department';
  }
  return null;
}

/**
 * 住院只读原因对应提示文案。
 *
 * @param reason 不可编辑原因
 * @return 用户可读提示
 */
export function inpatientClinicalEditDenialMessage(
  reason: InpatientClinicalEditDenialReason,
): string {
  if (reason === 'no_login_practitioner') {
    return '当前账号未关联医务人员，病历仅可查阅';
  }
  if (reason === 'no_login_organization') {
    return '未选择登录科室，病历仅可查阅';
  }
  if (reason === 'clinical_archived') {
    return '病例已归档，病历仅可查阅';
  }
  return '非本住院科室大夫，病历仅可查阅';
}
