import type { LayoutBox } from '#/types/report-layout';

export const SNAP_THRESHOLD_MM = 0.8;
export const GRID_SNAP_MM = 1;

export interface SnapGuideLine {
  orientation: 'h' | 'v';
  positionMm: number;
}

function round1(n: number): number {
  return Math.round(n * 10) / 10;
}

function collectAxisTargets(pageSize: number, others: LayoutBox[], axis: 'x' | 'y'): number[] {
  const targets = new Set<number>([0, pageSize / 2, pageSize]);
  for (const box of others) {
    const start = axis === 'x' ? (box.xMm ?? 0) : (box.yMm ?? 0);
    const size = axis === 'x' ? (box.widthMm ?? 0) : (box.heightMm ?? 0);
    targets.add(start);
    targets.add(start + size);
    targets.add(start + size / 2);
  }
  return [...targets];
}

/** 沿单轴对齐：左/右/中对齐到目标线 */
function snapScalar(
  pos: number,
  size: number,
  targets: number[],
  threshold: number,
): { value: number; guide: number | null } {
  let best = pos;
  let bestDist = threshold + 1;
  let guide: number | null = null;
  const right = pos + size;
  const center = pos + size / 2;

  for (const target of targets) {
    const candidates: Array<[number, number, number]> = [
      [target, Math.abs(pos - target), target],
      [target - size, Math.abs(right - target), target],
      [target - size / 2, Math.abs(center - target), target],
    ];
    for (const [candidate, dist, guidePos] of candidates) {
      if (dist <= threshold && dist < bestDist) {
        bestDist = dist;
        best = candidate;
        guide = guidePos;
      }
    }
  }

  return { value: best, guide };
}

function snapToGrid(value: number, grid: number): number {
  return Math.round(value / grid) * grid;
}

export function snapBoxMove(
  box: LayoutBox,
  pageW: number,
  pageH: number,
  otherBoxes: LayoutBox[],
  threshold = SNAP_THRESHOLD_MM,
): { box: LayoutBox; guides: SnapGuideLine[] } {
  const w = Math.max(1, box.widthMm ?? 1);
  const h = Math.max(1, box.heightMm ?? 1);
  let x = box.xMm ?? 0;
  let y = box.yMm ?? 0;
  const guides: SnapGuideLine[] = [];

  const sx = snapScalar(x, w, collectAxisTargets(pageW, otherBoxes, 'x'), threshold);
  const sy = snapScalar(y, h, collectAxisTargets(pageH, otherBoxes, 'y'), threshold);

  if (sx.guide != null) {
    x = sx.value;
    guides.push({ orientation: 'v', positionMm: sx.guide });
  } else {
    x = snapToGrid(x, GRID_SNAP_MM);
  }

  if (sy.guide != null) {
    y = sy.value;
    guides.push({ orientation: 'h', positionMm: sy.guide });
  } else {
    y = snapToGrid(y, GRID_SNAP_MM);
  }

  return {
    box: { ...box, xMm: round1(x), yMm: round1(y) },
    guides,
  };
}

export function snapBoxResize(
  box: LayoutBox,
  pageW: number,
  pageH: number,
  otherBoxes: LayoutBox[],
  threshold = SNAP_THRESHOLD_MM,
): { box: LayoutBox; guides: SnapGuideLine[] } {
  const x = box.xMm ?? 0;
  const y = box.yMm ?? 0;
  let w = Math.max(1, box.widthMm ?? 1);
  let h = Math.max(1, box.heightMm ?? 1);
  const guides: SnapGuideLine[] = [];

  const xTargets = collectAxisTargets(pageW, otherBoxes, 'x');
  const yTargets = collectAxisTargets(pageH, otherBoxes, 'y');

  const right = x + w;
  const bottom = y + h;

  let bestRightDist = threshold + 1;
  let bestRightGuide: number | null = null;
  for (const target of xTargets) {
    const dist = Math.abs(right - target);
    if (dist <= threshold && dist < bestRightDist) {
      bestRightDist = dist;
      w = Math.max(1, target - x);
      bestRightGuide = target;
    }
  }
  if (bestRightGuide != null) {
    guides.push({ orientation: 'v', positionMm: bestRightGuide });
  } else {
    w = snapToGrid(w, GRID_SNAP_MM);
  }

  let bestBottomDist = threshold + 1;
  let bestBottomGuide: number | null = null;
  for (const target of yTargets) {
    const dist = Math.abs(bottom - target);
    if (dist <= threshold && dist < bestBottomDist) {
      bestBottomDist = dist;
      h = Math.max(1, target - y);
      bestBottomGuide = target;
    }
  }
  if (bestBottomGuide != null) {
    guides.push({ orientation: 'h', positionMm: bestBottomGuide });
  } else {
    h = snapToGrid(h, GRID_SNAP_MM);
  }

  return {
    box: { ...box, widthMm: round1(w), heightMm: round1(h) },
    guides,
  };
}
