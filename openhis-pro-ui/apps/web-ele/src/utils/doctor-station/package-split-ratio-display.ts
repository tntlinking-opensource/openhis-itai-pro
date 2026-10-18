/**
 * 医嘱规格与包装装量拼接：`5mg` + `1片x100` → `5mg1片x100`（落入【】内）。
 *
 * 后端 packageSplitRatioText 口径为装量后缀（如 1片x100），禁止再展示「1盒=24片」。
 * 输液/注射（injectFlag）不拼装量后缀，仅保留规格。
 */

/** 规格 + 装量后缀拼成临床规格段（无括号）；输液不拼装量。 */
export function mergePackageSplitIntoSpec(
  spec: string,
  ratioText?: null | string,
  injectFlag?: boolean | null,
): string {
  const base = String(spec ?? '').trim();
  // 输液/注射：只保留规格，不拼「1瓶x40」一类装量
  if (injectFlag === true) {
    return base;
  }
  const ratio = String(ratioText ?? '').trim();
  if (!ratio) return base;
  if (!base) return ratio;
  return `${base}${ratio}`;
}

/**
 * 医嘱内容题名：名称【规格装量】，如「硝酸异山梨酯片【5mg1片x100】」。
 * 输液示例：「甘露醇注射液【250ml: 50g】」（无瓶x40）。
 *
 * @param name 药品名
 * @param spec 规格（如 5mg）
 * @param ratioText 装量后缀（如 1片x100）
 * @param injectFlag 输液/注射目录标记；为 true 时不拼装量
 */
export function buildMedicationContentTitleWithPackageSplit(
  name: string,
  spec: string,
  ratioText?: null | string,
  injectFlag?: boolean | null,
): string {
  const n = String(name ?? '').trim();
  const mergedSpec = mergePackageSplitIntoSpec(spec, ratioText, injectFlag);
  if (n && mergedSpec) return `${n}【${mergedSpec}】`;
  return n || mergedSpec;
}
