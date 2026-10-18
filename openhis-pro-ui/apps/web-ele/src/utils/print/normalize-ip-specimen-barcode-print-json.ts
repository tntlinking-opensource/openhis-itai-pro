import { PRINT_JSON_REWRITE_ENABLED } from '#/utils/print/print-json-rewrite.config';

/**
 * 住院采血贴：打印前把条码加高加宽。仅国基改 JSON；红旗沿用库模板。
 */

interface PrintElementLike {
  options?: {
    field?: string;
    formatter?: string;
  };
}

/** 把采血贴 formatter 里的条码尺寸换成可扫的规格 */
function enlargeSpecimenBarcodeFormatter(formatter: string): string {
  return formatter
    .replaceAll('width:1.1,height:28,margin:0', 'width:2,height:64,margin:10')
    .replaceAll("setAttribute('height','10mm')", "setAttribute('height','16mm')")
    .replaceAll('height="10mm"', 'height="16mm"')
    .replaceAll('min-height:12mm', 'min-height:18mm')
    .replaceAll('height:26mm', 'height:28mm')
    .replaceAll(',h=28', ',h=64');
}

/**
 * 住院采血贴打印前放大条码（高 16mm、模块加粗、两侧留白）。
 * 纸张尺寸不动。
 */
export function normalizeIpSpecimenBarcodePrintJson(
  printJson: Record<string, unknown>,
): Record<string, unknown> {
  if (PRINT_JSON_REWRITE_ENABLED !== true) {
    return printJson;
  }
  const panels = printJson.panels;
  if (!Array.isArray(panels)) {
    return printJson;
  }
  for (const panel of panels) {
    if (!panel || typeof panel !== 'object') {
      continue;
    }
    const elements = (panel as { printElements?: unknown[] }).printElements;
    if (!Array.isArray(elements)) {
      continue;
    }
    for (const raw of elements) {
      if (!raw || typeof raw !== 'object') {
        continue;
      }
      const element = raw as PrintElementLike;
      const formatter = element.options?.formatter;
      if (!formatter || !formatter.includes('barcodeSvg')) {
        continue;
      }
      element.options!.formatter = enlargeSpecimenBarcodeFormatter(formatter);
    }
  }
  return printJson;
}
