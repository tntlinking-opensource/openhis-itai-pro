<script setup lang="ts">
import type { DispensePrescriptionItemRow } from './types';

import { computed } from 'vue';

import TraceBatchScanDialog from '#/components/trace/trace-batch-scan-dialog.vue';
import type { TraceBatchScanLine } from '#/utils/trace';

import {
  parseDispenseLineQuantity,
  resolveDispenseTraceScanInventory,
} from '../services/resolve-dispense-trace-scan-inventory';

defineOptions({
  name: 'DispenseTraceScanDialog',
});

const props = withDefaults(
  defineProps<{
    lines: DispensePrescriptionItemRow[];
    patientName?: string;
    /** 各行已选发放库房×批号（与发药列表一致） */
    inventoryPickByLineId?: Record<string, string>;
    /** 汇总发药等场景：明细行无库房时的兜底发放库房 */
    defaultLocationId?: string;
  }>(),
  {
    patientName: '',
    inventoryPickByLineId: () => ({}),
    defaultLocationId: '',
  },
);

const visible = defineModel<boolean>({ default: false });
const traceByLineId = defineModel<Record<string, string>>('traceByLineId', { required: true });

/** 发药明细行映射为通用扫码行（库房/批号/品项用于库取与反查匹配） */
const scanLines = computed((): TraceBatchScanLine[] =>
  props.lines.map((row) => {
    const inventory = resolveDispenseTraceScanInventory(row, props.inventoryPickByLineId);
    return {
      id: row.id,
      itemId: row.medicationId,
      locationId: inventory.locationId || props.defaultLocationId || undefined,
      lotNumber: inventory.lotNumber,
      itemName: row.itemName,
      spec: row.spec,
      rxNo: row.rxNo,
      requiredTraceCount: parseDispenseLineQuantity(row),
      traceNo: row.traceCode === '—' ? '' : String(row.traceCode ?? '').trim(),
    };
  }),
);
</script>

<template>
  <TraceBatchScanDialog
    v-model="visible"
    v-model:trace-by-line-id="traceByLineId"
    :lines="scanLines"
    scene="dispense"
    :subtitle="props.patientName ? `患者：${props.patientName}` : ''"
    :default-location-id="props.defaultLocationId"
    show-rx-no-column
  />
</template>
