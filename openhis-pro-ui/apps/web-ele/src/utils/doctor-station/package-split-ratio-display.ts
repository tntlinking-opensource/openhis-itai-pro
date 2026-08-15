/**
 * 医嘱规格与包装装量拼接：`5mg` + `1片x100` → `5mg1片x100`（落入【】内）。
 *
 * 后端 packageSplitRatioText 口径为装量后缀（如 1片x100），禁止再展示「1盒=24片」。
 */

/** 规格 + 装量后缀拼成临床规格段（无括号）。 */
export function mergePackageSplitIntoSpec(spec: string, ratioText?: null | string): string {
  const ratio = String(ratioText ?? '').trim();
  const base = String(spec ?? '').trim();
  if (!ratio) return base;
  if (!base) return ratio;
  return `${base}${ratio}`;
}

/**
 * 医嘱内容题名：名称【规格装量】，如「硝酸异山梨酯片【5mg1片x100】」。
 *
 * @param name 药品名
 * @param spec 规格（如 5mg）
 * @param ratioText 装量后缀（如 1片x100）
 */
export function buildMedicationContentTitleWithPackageSplit(
  name: string,
  spec: string,
  ratioText?: null | string,
): string {
  const n = String(name ?? '').trim();
  const mergedSpec = mergePackageSplitIntoSpec(spec, ratioText);
  if (n && mergedSpec) return `${n}【${mergedSpec}】`;
  return n || mergedSpec;
}
