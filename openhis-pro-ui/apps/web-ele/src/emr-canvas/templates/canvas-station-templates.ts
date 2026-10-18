import type { TemplateDto } from '#/api/emr/template';
import { EMR_TEMPLATE_SCOPE_LEVEL } from '#/views/basic/emr-template/emr-template-scope.constants';

import { getClinicDocPresets } from './clinic-presets';
import type { OpSurgeryPreset } from './op-surgery-presets';

export const CANVAS_STATION_TEMPLATE_PREFIX = 'canvas:';

export function isCanvasStationTemplateId(id: string | undefined): boolean {
  return Boolean(id?.startsWith(CANVAS_STATION_TEMPLATE_PREFIX));
}

export function canvasPresetCodeFromTemplateId(id: string): string {
  return id.slice(CANVAS_STATION_TEMPLATE_PREFIX.length);
}

/** 侧栏「国基病历」下的本地调试模板，不入库（含住院 Word 迁移） */
export function getCanvasStationTemplateDtos(): TemplateDto[] {
  return getClinicDocPresets().map((item) => ({
    id: `${CANVAS_STATION_TEMPLATE_PREFIX}${item.code}`,
    name: item.name,
    code: item.code,
    documentTypeCode: item.typeCode === 'OUTPATIENT_RECORD' ? 'GUOJI_OUTPATIENT' : item.typeCode,
    documentTypeName: '国基病历',
    categoryCode: 'GUOJI',
    scopeLevel: EMR_TEMPLATE_SCOPE_LEVEL.HOSPITAL,
    encounterClassCode: item.code.startsWith('IP_WORD_') ? 'IMP' : 'AMB',
    isEnabled: true,
    enabled: true,
  }));
}

export function getCanvasStationPresetByTemplateId(
  templateId: string,
): OpSurgeryPreset | undefined {
  const code = canvasPresetCodeFromTemplateId(templateId);
  return getClinicDocPresets().find((item) => item.code === code);
}
