<script setup lang="ts">
import type { GfChargeMode } from '#/api/campus/types';

import { onMounted, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import {
  getMasterSwitchApi,
  listFreePercentsApi,
  saveFreePercentsApi,
  saveMasterSwitchApi,
} from '#/api/campus/campus-gf-config';
import { GF_CLASS_OPTIONS, gfClassLabel, unwrapRecords } from '#/api/campus/types';
import { useRequest } from '#/hooks/use-request';
import ChargeModeSwitch from '#/views/campus/components/ChargeModeSwitch.vue';
import {
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElMessage,
  ElSwitch,
  ElTable,
  ElTableColumn,
} from 'element-plus';

defineOptions({ name: 'CampusGfConfig' });

const chargeMode = ref<GfChargeMode>('SELF_PAY_GF');
const masterEnabled = ref(false);
const rows = ref<Array<{ classEnum: string; selfPayRatio: number; id?: string }>>([]);

async function loadMaster() {
  const res = await getMasterSwitchApi();
  masterEnabled.value = Boolean(res.enabled);
}

async function loadPercents() {
  const res = await listFreePercentsApi({ chargeModeEnum: chargeMode.value });
  const records = unwrapRecords(res);
  if (records.length === 0) {
    rows.value = GF_CLASS_OPTIONS.map((o) => ({
      classEnum: o.value,
      selfPayRatio: 0,
    }));
  } else {
    rows.value = GF_CLASS_OPTIONS.map((o) => {
      const hit = records.find((r) => r.classEnum === o.value);
      return {
        id: hit?.id != null ? String(hit.id) : undefined,
        classEnum: o.value,
        selfPayRatio: Number(hit?.selfPayRatio ?? 0),
      };
    });
  }
}

const { run: runLoad, loading } = useRequest(
  async () => {
    await Promise.all([loadMaster(), loadPercents()]);
  },
  { manual: true },
);

async function saveMaster() {
  await saveMasterSwitchApi({ enabled: masterEnabled.value });
  ElMessage.success('总开关已保存');
}

async function savePercents() {
  await saveFreePercentsApi({
    chargeModeEnum: chargeMode.value,
    items: rows.value.map((r) => ({
      id: r.id,
      classEnum: r.classEnum,
      selfPayRatio: r.selfPayRatio,
    })),
  });
  ElMessage.success('甲乙丙比例已保存');
  await loadPercents();
}

function onModeChange() {
  void runLoad();
}

watch(chargeMode, () => {
  void loadPercents();
});

onMounted(() => {
  void runLoad();
});
</script>

<template>
  <Page title="公费配置" description="公费总开关与甲乙丙自付比例；按收费模式分别维护。">
    <ElCard shadow="never" class="mb-4">
      <ElForm inline>
        <ElFormItem label="公费总开关">
          <ElSwitch v-model="masterEnabled" @change="saveMaster" />
        </ElFormItem>
      </ElForm>
    </ElCard>

    <div class="mb-3">
      <ChargeModeSwitch v-model="chargeMode" @change="onModeChange" />
    </div>

    <ElCard v-loading="loading" shadow="never">
      <template #header>
        <span>甲乙丙自付比例（0～1）</span>
      </template>
      <ElTable :data="rows" border size="small">
        <ElTableColumn label="类别" width="120">
          <template #default="{ row }">{{ gfClassLabel(row.classEnum) }}</template>
        </ElTableColumn>
        <ElTableColumn label="自付比例" min-width="160">
          <template #default="{ row }">
            <ElInputNumber
              v-model="row.selfPayRatio"
              :min="0"
              :max="1"
              :step="0.01"
              :precision="4"
              class="w-full"
            />
          </template>
        </ElTableColumn>
      </ElTable>
      <div class="mt-3">
        <ElButton type="primary" @click="savePercents">保存当前模式比例</ElButton>
      </div>
    </ElCard>
  </Page>
</template>
