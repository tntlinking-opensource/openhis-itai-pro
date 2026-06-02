import type { PractitionerWorkbenchDetail } from '#/api/basic/position';

import type { PractitionerSummary } from './types';

export function mapWorkbenchDetailToSummary(d: PractitionerWorkbenchDetail): PractitionerSummary {
  return {
    id: String(d.id ?? ''),
    name: d.name ?? '',
    busNo: d.busNo,
    genderEnum: d.genderEnum,
    phone: d.phone,
    orgId: d.orgId ? String(d.orgId) : undefined,
    currentOrgName: d.currentOrgName,
    statusEnum: (d.statusEnum as PractitionerSummary['statusEnum']) ?? 'ACTIVE',
    userName: d.userName,
    profileIncomplete: d.profileIncomplete,
  };
}
