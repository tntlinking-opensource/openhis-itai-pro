<script lang="ts" setup>
import type { MedicationCatalogInitResponse } from '#/api';

import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { medicationCatalogInitApi } from '#/api';

import MedicationCatalogEditDialog from '../medication-catalog-edit-dialog.vue';

defineOptions({
  name: 'MedicineCatalogDetail',
});

const route = useRoute();
const router = useRouter();

/** 书签/直达链接进入时拉选项；与列表写入 session 的兼容保留 */
const initOptions = ref<MedicationCatalogInitResponse>({} as MedicationCatalogInitResponse);
const dialogOpen = ref(true);
const initReady = ref(false);

const medicationDefId = computed(() => String(route.params.id || ''));

async function bootstrapInitOptions() {
  const raw = sessionStorage.getItem('medicationCatalogInitOptions');
  if (raw) {
    try {
      initOptions.value = JSON.parse(raw) as MedicationCatalogInitResponse;
    } catch {
      initOptions.value = {} as MedicationCatalogInitResponse;
    } finally {
      sessionStorage.removeItem('medicationCatalogInitOptions');
    }
  }
  if (!initOptions.value || Object.keys(initOptions.value).length === 0) {
    const res = await medicationCatalogInitApi();
    initOptions.value = res.data || ({} as MedicationCatalogInitResponse);
  }
  initReady.value = true;
}

onMounted(() => {
  void bootstrapInitOptions();
});

watch(dialogOpen, (open) => {
  if (!open) {
    router.replace('/basic/medicationcatalog');
  }
});

/** 从直达链接保存后回到列表，用 session 触发列表 onActivated 刷新 */
function handleRouteDialogSuccess() {
  sessionStorage.setItem('medicineCatalogRefresh', 'true');
}
</script>

<template>
  <MedicationCatalogEditDialog
    v-if="initReady"
    v-model="dialogOpen"
    :medication-def-id="medicationDefId"
    :init-options-source="initOptions"
    @success="handleRouteDialogSuccess"
  />
</template>
