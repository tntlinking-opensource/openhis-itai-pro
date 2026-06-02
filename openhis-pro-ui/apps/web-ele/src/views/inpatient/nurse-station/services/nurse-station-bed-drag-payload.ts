/** 护士站床位拖放 dataTransfer 类型 */
export const NURSE_STATION_BED_DRAG_MIME = 'application/json';

export interface NurseStationBedDragPayload {
  patientId: string;
  fromBedNo: string;
}

/** 写入拖拽载荷 */
export function setNurseStationBedDragPayload(
  dataTransfer: DataTransfer,
  payload: NurseStationBedDragPayload,
) {
  dataTransfer.setData(NURSE_STATION_BED_DRAG_MIME, JSON.stringify(payload));
}

/** 解析拖拽中的患者 id */
export function readNurseStationBedDragPatientId(e: DragEvent): string | null {
  try {
    const raw = e.dataTransfer?.getData(NURSE_STATION_BED_DRAG_MIME);
    if (!raw) {
      return null;
    }
    const o = JSON.parse(raw) as { patientId?: string };
    if (!o.patientId || typeof o.patientId !== 'string') {
      return null;
    }
    return o.patientId;
  } catch {
    return null;
  }
}

/** 通知床位图展示悬停文案 */
export function dispatchNurseStationBedDragStart(detail: {
  fromBedLabel: string;
  hasBed: boolean;
  patientName: string;
}) {
  window.dispatchEvent(new CustomEvent('nurse-station-bed-drag-start', { detail }));
}
