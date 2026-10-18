import type { Command } from '@hufe921/canvas-editor';

import { headerHasHospitalTitleSlot, replaceHeaderHospitalTitle } from '#/emr-canvas/templates/canvas-kit';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';

/** 院名：hospitalName / orgName 互填，空则用登录选院 */
export function withHospitalMetadata(values: Record<string, string>): Record<string, string> {
  const named = String(values.hospitalName ?? '').trim() || String(values.orgName ?? '').trim();
  const hospital = named || resolvePrintHospitalName().trim();
  if (!hospital) return { ...values };
  return {
    ...values,
    hospitalName: hospital,
    orgName: String(values.orgName ?? '').trim() || hospital,
  };
}

/** 仅当当前纸面来自带院名槽的模板时，把院名写入页眉标题行 */
function applyHospitalTitle(command: Command, hospital: string) {
  const text = hospital.trim();
  if (!text) return false;
  const data = command.getValue()?.data;
  const header = data?.header ?? [];
  if (!data || !headerHasHospitalTitleSlot(header)) return false;
  command.executeSetValue(
    {
      header: replaceHeaderHospitalTitle(header, text),
      main: data.main,
      footer: data.footer ?? [],
    },
    { isSetCursor: false },
  );
  return true;
}

/**
 * 按 conceptId 把元数据写入正文控件，并把院名写入页眉标题。
 */
export function applyMetadataValues(
  command: Command | null | undefined,
  values: Record<string, string>,
  options?: { overwrite?: boolean },
) {
  if (!command) return 0;
  const overwrite = options?.overwrite !== false;
  const enriched = withHospitalMetadata(values);
  const payload = Object.entries(enriched)
    .filter(([key, value]) => {
      if (key === 'hospitalName' || key === 'orgName') return false;
      return value != null && String(value).trim() !== '';
    })
    .map(([conceptId, value]) => ({
      conceptId,
      value: String(value),
      isOverwrite: overwrite,
    }));
  if (payload.length) {
    command.executeSetControlValueList(payload);
  }
  const titled = applyHospitalTitle(command, enriched.hospitalName ?? '');
  return payload.length + (titled ? 1 : 0);
}
