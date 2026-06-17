export interface MmRulerTick {
  mm: number;
  length: number;
  major: boolean;
}

export interface BuildMmRulerTicksOptions {
  lengthMm: number;
  majorStepMm?: number;
  mediumStepMm?: number;
}

/** 生成 0..lengthMm 的刻度（含终点）。 */
export function buildMmRulerTicks(options: BuildMmRulerTicksOptions): MmRulerTick[] {
  const { lengthMm, majorStepMm = 10, mediumStepMm = 5 } = options;
  const max = Math.max(0, Math.ceil(lengthMm));
  const ticks: MmRulerTick[] = [];
  for (let mm = 0; mm <= max; mm += 1) {
    const major = mm % majorStepMm === 0;
    const medium = !major && mm % mediumStepMm === 0;
    ticks.push({
      mm,
      major,
      length: major ? 6 : medium ? 4 : 2.2,
    });
  }
  return ticks;
}

export function buildMmRulerLabels(lengthMm: number, labelStepMm = 10): number[] {
  const labels: number[] = [];
  for (let mm = labelStepMm; mm < lengthMm; mm += labelStepMm) {
    labels.push(mm);
  }
  return labels;
}
