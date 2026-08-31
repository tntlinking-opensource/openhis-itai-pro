import type { GroupRosterRowDto } from '#/api/peis/group-check';

export const PAY_STATUS = {
  UNPAID: 'UNPAID',
  ENROLLED: 'ENROLLED',
  PAID: 'PAID',
} as const;

export const CONTRACT_PAY_STATUS = {
  UNPAID: 'UNPAID',
  PAID: 'PAID',
} as const;

export function contractPayStatusLabel(code?: string) {
  if (code === CONTRACT_PAY_STATUS.PAID) return '已收';
  if (code === CONTRACT_PAY_STATUS.UNPAID) return '待收';
  return code || '待收';
}

export function contractPayStatusTagType(code?: string) {
  if (code === CONTRACT_PAY_STATUS.PAID) return 'success';
  return 'warning';
}

export const ARRIVAL_STATUS = {
  PENDING: 'PENDING',
  ARRIVED: 'ARRIVED',
} as const;

/** 名册人员类型：学生 / 教职工（同步 Campus）/ 企事业（仅 PEIS 名册）。 */
export type RosterMemberKind = 'STUDENT' | 'STAFF' | 'ENTERPRISE';

export const ROSTER_MEMBER_KIND_OPTIONS = [
  { value: 'STUDENT' as const, label: '学生' },
  { value: 'STAFF' as const, label: '教职工' },
  { value: 'ENTERPRISE' as const, label: '企事业' },
] as const;

export function inferRosterMemberKind(
  company?: { campusCollegeId?: string } | null,
): RosterMemberKind {
  return company?.campusCollegeId ? 'STUDENT' : 'ENTERPRISE';
}

export function rosterMemberNoLabel(kind: RosterMemberKind) {
  return kind === 'STUDENT' ? '学号' : '工号';
}

export function rosterOrgUnitLabel(kind: RosterMemberKind) {
  return kind === 'STUDENT' ? '班级' : '部门';
}

export function rosterMemberNo(row: GroupRosterRowDto | null | undefined) {
  if (!row) return '—';
  return row.studentNo || row.employeeNo || '—';
}

const GENDER_LABEL: Record<string, string> = {
  MALE: '男',
  FEMALE: '女',
  UNKNOWN: '未知',
};

export const GENDER_OPTIONS = [
  { value: 'MALE', label: '男' },
  { value: 'FEMALE', label: '女' },
  { value: 'UNKNOWN', label: '未知' },
] as const;

export function genderLabel(code?: string) {
  if (!code) return '—';
  return GENDER_LABEL[code] ?? code;
}

/** 名册建单态读侧（行 DTO 或登记台 desk 上下文）。 */
export type RosterEnrollmentLike = {
  payStatusCode?: string;
  enrolled?: boolean;
};

export function isRosterEnrolled(row: RosterEnrollmentLike | GroupRosterRowDto | null | undefined) {
  if (!row) return false;
  if (row.enrolled === true) return true;
  const code = row.payStatusCode;
  return code === PAY_STATUS.PAID || code === PAY_STATUS.ENROLLED;
}

export function enrollmentLabel(row: RosterEnrollmentLike | GroupRosterRowDto | null | undefined) {
  if (!row) return '—';
  if (isRosterEnrolled(row)) {
    if (row.payStatusCode === PAY_STATUS.PAID) return '已收费建单';
    if (row.payStatusCode === PAY_STATUS.ENROLLED) return '已建单（免收费）';
    return '已建单';
  }
  return '未建单';
}

export function enrollmentTagType(
  row: RosterEnrollmentLike | GroupRosterRowDto | null | undefined,
) {
  if (!row || !isRosterEnrolled(row)) return 'info';
  return row.payStatusCode === PAY_STATUS.PAID ? 'success' : 'primary';
}

export function arrivalLabel(code?: string) {
  if (code === ARRIVAL_STATUS.ARRIVED) return '已到检';
  if (code === ARRIVAL_STATUS.PENDING) return '待到检';
  return code || '—';
}

export function arrivalTagType(code?: string) {
  if (code === ARRIVAL_STATUS.ARRIVED) return 'success';
  if (code === ARRIVAL_STATUS.PENDING) return 'warning';
  return 'info';
}

export function packageDisplayName(item: {
  packageActivityId?: string | number;
  display?: string;
  code?: string;
}) {
  return item.display || item.code || '未命名套餐';
}

export function packageLabel(
  packageId: string | undefined,
  packages: Array<{ packageActivityId?: string | number; display?: string; code?: string }>,
) {
  if (!packageId) return '—';
  const hit = packages.find((item) => String(item.packageActivityId ?? '') === packageId);
  if (!hit) return '—';
  return packageDisplayName(hit);
}

export function packageLabels(
  packageIds: Array<string | number | undefined> | undefined,
  packages: Array<{ packageActivityId?: string | number; display?: string; code?: string }>,
) {
  if (!packageIds?.length) return '—';
  const labels = packageIds
    .map((id) => packageLabel(String(id ?? ''), packages))
    .filter((label) => label && label !== '—');
  return labels.length > 0 ? labels.join('、') : '—';
}

export function filterPackagesByIds(
  allPackages: Array<{ packageActivityId?: string | number; display?: string; code?: string }>,
  packageIds: Array<string | number | undefined> | undefined,
) {
  if (!packageIds?.length) return allPackages;
  const idSet = new Set(packageIds.map((id) => String(id ?? '')));
  return allPackages.filter((item) => idSet.has(String(item.packageActivityId ?? '')));
}
