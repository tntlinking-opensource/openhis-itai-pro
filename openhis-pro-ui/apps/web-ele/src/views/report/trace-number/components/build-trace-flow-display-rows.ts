/**
 * 将后端单端流水（OUT/IN 各一行）整理成「从 → 到」可读行。
 * 院内流转同单据号成对合并；进出院按单边补「院外/患者」。
 */
import type { TraceNumberRecordRowDto } from '#/api/report/trace-number-report';

/** 院内调拨成对类别。 */
const FLOW_CATEGORY_INTERNAL = 'INTERNAL_TRANSFER';

/** 进出院类别。 */
const FLOW_CATEGORY_HOSPITAL = 'HOSPITAL_IO';

/** 出库方向。 */
const FLOW_DIRECTION_OUT = 'OUT';

/** 入库方向。 */
const FLOW_DIRECTION_IN = 'IN';

/** 明细表展示行（对操作员可读）。 */
export interface TraceFlowDisplayRow {
  /** 行稳定 key。 */
  key: string;
  /** 业务时间。 */
  createdAt?: string;
  /** 从哪出。 */
  fromLocation: string;
  /** 到哪入。 */
  toLocation: string;
  /** 单据文案（类型 + 单号）。 */
  documentText: string;
  /** 类别文案。 */
  categoryText: string;
  /** 数量。 */
  quantity?: number | string;
  /** 单位文案。 */
  unitText: string;
  /** 患者（发药/退药才有）。 */
  patientName: string;
}

/** 空展示占位。 */
function dash(value?: string) {
  const text = String(value ?? '').trim();
  return text || '—';
}

/** 组装单据列：操作类型 + 单号。 */
function formatDocumentText(row: TraceNumberRecordRowDto) {
  const typeLabel = String(row.operationTypeLabel ?? '').trim();
  const busNo = String(row.busNo ?? '').trim();
  if (typeLabel && busNo) {
    return `${typeLabel} ${busNo}`;
  }
  return typeLabel || busNo || '—';
}

/** 组装单位列。 */
function formatUnitText(row: TraceNumberRecordRowDto) {
  const label = String(row.unitCodeLabel ?? '').trim();
  if (label) {
    return label;
  }
  return dash(row.unitCode);
}

/**
 * 类别可读文案：院内流转 / 进院 / 出院。
 * 优先 code，避免依赖「入库方向」这类绕口 label。
 */
function formatCategoryText(row: TraceNumberRecordRowDto) {
  const category = String(row.flowCategoryEnum ?? '').trim();
  if (category === FLOW_CATEGORY_INTERNAL) {
    return '院内流转';
  }
  if (category === FLOW_CATEGORY_HOSPITAL) {
    const direction = String(row.flowDirectionEnum ?? '').trim();
    if (direction === FLOW_DIRECTION_IN) {
      return '进院';
    }
    if (direction === FLOW_DIRECTION_OUT) {
      return '出院';
    }
    return '进出院';
  }
  return dash(row.flowCategoryLabel);
}

/** 是否院内流转。 */
function isInternalTransfer(row: TraceNumberRecordRowDto) {
  return String(row.flowCategoryEnum ?? '').trim() === FLOW_CATEGORY_INTERNAL;
}

/** 方向是否 OUT。 */
function isOut(row: TraceNumberRecordRowDto) {
  return String(row.flowDirectionEnum ?? '').trim() === FLOW_DIRECTION_OUT;
}

/** 方向是否 IN。 */
function isIn(row: TraceNumberRecordRowDto) {
  return String(row.flowDirectionEnum ?? '').trim() === FLOW_DIRECTION_IN;
}

/** 行去重 key。 */
function rowKey(row: TraceNumberRecordRowDto, index: number) {
  if (row.id != null && String(row.id).trim() !== '') {
    return String(row.id);
  }
  return `idx-${index}`;
}

/**
 * 单边进出院：进院「患者/院外→库位」，出院「库位→患者/院外」。
 */
function toHospitalIoDisplayRow(row: TraceNumberRecordRowDto, index: number): TraceFlowDisplayRow {
  const location = dash(row.locationName);
  const patient = String(row.patientName ?? '').trim();
  let fromLocation = '—';
  let toLocation = '—';
  // 进院：有患者（退药）从患者回库，否则从院外入库；出院：到患者或院外
  if (isIn(row)) {
    fromLocation = patient || '院外';
    toLocation = location;
  } else if (isOut(row)) {
    fromLocation = location;
    toLocation = patient || '院外';
  } else {
    fromLocation = location;
    toLocation = location;
  }
  return {
    key: `hospital-${rowKey(row, index)}`,
    createdAt: row.createdAt,
    fromLocation,
    toLocation,
    documentText: formatDocumentText(row),
    categoryText: formatCategoryText(row),
    quantity: row.quantity,
    unitText: formatUnitText(row),
    patientName: dash(row.patientName),
  };
}

/**
 * 院内流转成对：从 OUT 库位 → 到 IN 库位；单据取同单号两侧任一端。
 */
function toInternalPairDisplayRow(
  outRow: TraceNumberRecordRowDto,
  inRow: TraceNumberRecordRowDto,
  index: number,
): TraceFlowDisplayRow {
  const patient = String(outRow.patientName ?? inRow.patientName ?? '').trim();
  // 单位优先取出库侧，缺省再用入库侧
  let unitSource = outRow;
  if (!outRow.unitCode && !outRow.unitCodeLabel) {
    unitSource = inRow;
  }
  // 单据文案优先有单号的一侧
  let documentSource = outRow;
  if (!String(outRow.busNo ?? '').trim() && String(inRow.busNo ?? '').trim()) {
    documentSource = inRow;
  }
  return {
    key: `pair-${rowKey(outRow, index)}-${rowKey(inRow, index)}`,
    createdAt: outRow.createdAt || inRow.createdAt,
    fromLocation: dash(outRow.locationName),
    toLocation: dash(inRow.locationName),
    documentText: formatDocumentText(documentSource),
    categoryText: '院内流转',
    quantity: outRow.quantity ?? inRow.quantity,
    unitText: formatUnitText(unitSource),
    patientName: patient || '—',
  };
}

/** 不成对的院内流转单边（缺另一端时兜底）。 */
function toInternalSingleDisplayRow(
  row: TraceNumberRecordRowDto,
  index: number,
): TraceFlowDisplayRow {
  const location = dash(row.locationName);
  let fromLocation = '—';
  let toLocation = '—';
  if (isOut(row)) {
    fromLocation = location;
  } else if (isIn(row)) {
    toLocation = location;
  } else {
    fromLocation = location;
  }
  return {
    key: `internal-single-${rowKey(row, index)}`,
    createdAt: row.createdAt,
    fromLocation,
    toLocation,
    documentText: formatDocumentText(row),
    categoryText: '院内流转',
    quantity: row.quantity,
    unitText: formatUnitText(row),
    patientName: dash(row.patientName),
  };
}

/** 同单据号的院内流转 OUT/IN 槽位。 */
interface InternalPairSlot {
  out?: TraceNumberRecordRowDto;
  in?: TraceNumberRecordRowDto;
}

/**
 * 将原始流水转为展示行：同 busNo 的院内 OUT+IN 合并；其余按时间序保留。
 */
export function buildTraceFlowDisplayRows(
  records: TraceNumberRecordRowDto[],
): TraceFlowDisplayRow[] {
  const source = records ?? [];
  // 先按单据号收集院内流转 OUT/IN，便于成对合并
  const pairByBusNo = new Map<string, InternalPairSlot>();
  for (const row of source) {
    if (!row || !isInternalTransfer(row)) {
      continue;
    }
    const busNo = String(row.busNo ?? '').trim();
    if (!busNo) {
      continue;
    }
    let slot = pairByBusNo.get(busNo);
    if (!slot) {
      slot = {};
      pairByBusNo.set(busNo, slot);
    }
    if (isOut(row) && !slot.out) {
      slot.out = row;
    }
    if (isIn(row) && !slot.in) {
      slot.in = row;
    }
  }

  const consumed = new Set<string>();
  const displayRows: TraceFlowDisplayRow[] = [];

  // 按时间序输出；成对在首次碰到该单号任一侧时整行输出
  for (let i = 0; i < source.length; i += 1) {
    const row = source[i];
    if (!row) {
      continue;
    }
    const key = rowKey(row, i);
    if (consumed.has(key)) {
      continue;
    }

    if (isInternalTransfer(row)) {
      const busNo = String(row.busNo ?? '').trim();
      const slot = busNo ? pairByBusNo.get(busNo) : undefined;
      if (slot?.out && slot.in) {
        const outKey = rowKey(slot.out, i);
        const inKey = rowKey(slot.in, i);
        consumed.add(outKey);
        consumed.add(inKey);
        displayRows.push(toInternalPairDisplayRow(slot.out, slot.in, i));
        continue;
      }
      consumed.add(key);
      displayRows.push(toInternalSingleDisplayRow(row, i));
      continue;
    }

    consumed.add(key);
    displayRows.push(toHospitalIoDisplayRow(row, i));
  }

  return displayRows;
}
