/** 5201 医疗类别（常用） */

export const YB_MED_TYPE_OPTIONS = [
  { label: '普通门诊', value: '11' },
  { label: '门诊挂号', value: '12' },
  { label: '急诊', value: '13' },
  { label: '门诊慢特病', value: '14' },
  { label: '普通住院', value: '21' },
  { label: '外伤住院', value: '22' },
  { label: '转外诊治住院', value: '23' },
] as const;

const MED_TYPE_LABEL = Object.fromEntries(
  YB_MED_TYPE_OPTIONS.map((item) => [item.value, item.label]),
);

/** 在院状态 */
const INHOSP_STAS_LABEL: Record<string, string> = {
  '0': '不在院',
  '1': '在院',
};

export function formatMedType(code?: string | null): string {
  if (!code) return '—';
  return MED_TYPE_LABEL[code] ?? code;
}

export function formatInhospStas(code?: string | null): string {
  if (!code) return '—';
  return INHOSP_STAS_LABEL[code] ?? code;
}

/** 从平台行读取 snake_case / camelCase 字段 */
export function rowField(row: Record<string, unknown>, ...keys: string[]): string {
  for (const key of keys) {
    const value = row[key];
    if (value != null && String(value).trim() !== '') {
      return String(value);
    }
  }
  return '';
}
