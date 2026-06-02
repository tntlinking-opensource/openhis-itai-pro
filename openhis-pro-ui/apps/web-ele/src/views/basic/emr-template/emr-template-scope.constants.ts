/** 模板使用范围 */
export const EMR_TEMPLATE_SCOPE_LEVEL = {
  PERSONAL: 'PERSONAL',
  DEPARTMENT: 'DEPARTMENT',
  HOSPITAL: 'HOSPITAL',
} as const;

export type EmrTemplateScopeLevel =
  (typeof EMR_TEMPLATE_SCOPE_LEVEL)[keyof typeof EMR_TEMPLATE_SCOPE_LEVEL];

export const EMR_TEMPLATE_SCOPE_LEVEL_OPTIONS = [
  { label: '个人', value: EMR_TEMPLATE_SCOPE_LEVEL.PERSONAL },
  { label: '科室', value: EMR_TEMPLATE_SCOPE_LEVEL.DEPARTMENT },
  { label: '全院', value: EMR_TEMPLATE_SCOPE_LEVEL.HOSPITAL },
] as const;

/** 适用就诊类别（与后端 EncounterClass.code 一致） */
export const EMR_TEMPLATE_ENCOUNTER_CLASS = {
  AMB: 'AMB',
  IMP: 'IMP',
  EMER: 'EMER',
  OBSENC: 'OBSENC',
} as const;

export type EmrTemplateEncounterClassCode =
  (typeof EMR_TEMPLATE_ENCOUNTER_CLASS)[keyof typeof EMR_TEMPLATE_ENCOUNTER_CLASS];

export const EMR_TEMPLATE_ENCOUNTER_CLASS_OPTIONS = [
  { label: '门诊', value: EMR_TEMPLATE_ENCOUNTER_CLASS.AMB },
  { label: '住院', value: EMR_TEMPLATE_ENCOUNTER_CLASS.IMP },
  { label: '急诊', value: EMR_TEMPLATE_ENCOUNTER_CLASS.EMER },
  { label: '留观', value: EMR_TEMPLATE_ENCOUNTER_CLASS.OBSENC },
] as const;

/** 医生站场景 → listForStation */
export const EMR_TEMPLATE_STATION_KIND = {
  OUTPATIENT: 'OUTPATIENT',
  INPATIENT: 'INPATIENT',
} as const;

export type EmrTemplateStationKind =
  (typeof EMR_TEMPLATE_STATION_KIND)[keyof typeof EMR_TEMPLATE_STATION_KIND];

export function formatTemplateScopeTag(scopeLevel?: string): string {
  const opt = EMR_TEMPLATE_SCOPE_LEVEL_OPTIONS.find((o) => o.value === scopeLevel);
  return opt?.label ?? '';
}

export function formatEncounterClassLabel(code?: string): string {
  const opt = EMR_TEMPLATE_ENCOUNTER_CLASS_OPTIONS.find((o) => o.value === code);
  return opt?.label ?? code ?? '';
}
