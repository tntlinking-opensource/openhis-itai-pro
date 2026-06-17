export const REPORT_ELEMENT_DRAG_MIME = 'application/x-openhis-report-element';

export type ReportElementDragPayload =
  | { kind: 'palette'; code: string }
  | { kind: 'reorder'; index: number };

export function encodeReportElementDrag(payload: ReportElementDragPayload): string {
  return JSON.stringify(payload);
}

export function decodeReportElementDrag(raw: string): ReportElementDragPayload | null {
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as ReportElementDragPayload;
    if (parsed?.kind === 'palette' && typeof parsed.code === 'string') {
      return parsed;
    }
    if (parsed?.kind === 'reorder' && typeof parsed.index === 'number') {
      return parsed;
    }
  } catch {
    /* ignore malformed payload */
  }
  return null;
}

export function setReportElementDragData(
  event: DragEvent,
  payload: ReportElementDragPayload,
): void {
  const encoded = encodeReportElementDrag(payload);
  event.dataTransfer?.setData(REPORT_ELEMENT_DRAG_MIME, encoded);
  event.dataTransfer?.setData('text/plain', encoded);
  event.dataTransfer!.effectAllowed = payload.kind === 'reorder' ? 'move' : 'copy';
}
