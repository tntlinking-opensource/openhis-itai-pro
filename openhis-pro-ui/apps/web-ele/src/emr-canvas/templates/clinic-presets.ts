import { getInpatientWordPresets } from './inpatient-word-presets';
import { getOutpatientTemplatePresets } from './outpatient-record-preset';
import { getOpSurgeryPresets, type OpSurgeryPreset } from './op-surgery-presets';

export type { OpSurgeryPreset };

/** 医生站可套用的 Canvas 文书：门诊 + 手术套件 + 住院 Word 迁移 */
export function getClinicDocPresets(): OpSurgeryPreset[] {
  return [
    ...getOutpatientTemplatePresets(),
    ...getOpSurgeryPresets(),
    ...getInpatientWordPresets(),
  ];
}

export function getClinicDocPreset(code: string): OpSurgeryPreset | undefined {
  return getClinicDocPresets().find((item) => item.code === code);
}
