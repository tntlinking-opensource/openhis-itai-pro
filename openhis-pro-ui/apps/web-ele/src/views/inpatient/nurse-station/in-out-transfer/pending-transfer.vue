<script setup lang="ts">
import { ref } from 'vue';

defineOptions({
  name: 'InpatientNurseStationPendingTransferModule',
});

type PendingTab = 'pending-exec' | 'refund' | 'takeback';

interface PatientHeaderInfo {
  age: number;
  allergy: string;
  attendingDoctor: string;
  balance: string;
  bedNo: string;
  diagnosis: string;
  gender: string;
  inAt: string;
  level: string;
  name: string;
  nurse: string;
  patientNo: string;
  prepay: string;
  remain: string;
  wardArea: string;
  wardRoom: string;
}

interface PendingOrderRow {
  executeSlots: string[];
  executeStartAt: string;
  id: string;
  orderContent: string;
  orderType: string;
  periodText: string;
  planDate: string;
  usageText: string;
}

const activeTab = ref<PendingTab>('pending-exec');

const patientHeader: PatientHeaderInfo = {
  age: 65,
  allergy: '植物花粉',
  attendingDoctor: '总鹏',
  balance: '81.86',
  bedNo: '04床',
  diagnosis: '癫痫',
  gender: '女',
  inAt: '2025年07月24日 10',
  level: '自理',
  name: '冯绍广',
  nurse: '李沪',
  patientNo: '20250000298',
  prepay: '0',
  remain: '-81.86',
  wardArea: '神经内一科病区',
  wardRoom: '神经内一科住院科室',
};

const orderRows = ref<PendingOrderRow[]>([
  {
    executeSlots: ['09:00'],
    executeStartAt: '07/24 10:36',
    id: 'r-1',
    orderContent:
      '注射用头孢地嗪(国惠)【0.5g/瓶】(住院西药房)\n静脉注射,2次/分,每日一次/约9点,9:00',
    orderType: '长期/西药',
    periodText: '0.5g(1瓶)',
    planDate: '07-26',
    usageText: '',
  },
  {
    executeSlots: ['14:00', '17:00'],
    executeStartAt: '07/24 10:37',
    id: 'r-2',
    orderContent:
      '氯化钠AA注射液【1粒：10包/大盒】(住院西药房)\n盐酸地芬尼多片【25mg*30片】(住院西药房)\n肌肉注射,间隔3小时(8,11,14,17)8:00-11:00-14:00-17:00',
    orderType: '长期/西药',
    periodText: '1粒(1包)\n25mg(1片)',
    planDate: '07-25',
    usageText: '',
  },
  {
    executeSlots: ['08:00'],
    executeStartAt: '07/24 10:35',
    id: 'r-3',
    orderContent: '维生素K1注射液(国基)【10mg*1ml*10支/盒】(住院西药房)\n滴患眼,适量,8:00',
    orderType: '长期/西药',
    periodText: '10mg(1支)',
    planDate: '07-26',
    usageText: '',
  },
]);

/** 供表格渲染多行医嘱内容。 */
function splitOrderLines(text: string) {
  return text.split('\n').filter((line) => !!line.trim());
}
</script>

<template>
  <div class="in-out-transfer-page">
    <section class="patient-header">
      <div class="patient-header__main">
        <div class="patient-header__avatar">
          <ElAvatar :size="42">患</ElAvatar>
        </div>
        <div class="patient-header__identity">
          <div class="patient-header__line-1">
            <span class="patient-header__bed">{{ patientHeader.bedNo }}</span>
            <span class="patient-header__name">{{ patientHeader.name }}</span>
            <ElTag size="small" effect="plain">{{ patientHeader.gender }}</ElTag>
            <ElTag size="small" effect="plain">{{ patientHeader.level }}</ElTag>
            <span class="patient-header__in-at">入科: {{ patientHeader.inAt }}</span>
          </div>
          <div class="patient-header__line-2">
            <span>病历号: {{ patientHeader.patientNo }}</span>
            <span>过敏史: {{ patientHeader.allergy }}</span>
            <span>{{ patientHeader.age }}cm/65kg</span>
            <span>术后天</span>
          </div>
        </div>
      </div>
      <div class="patient-header__extra">
        <span>住院科室: {{ patientHeader.wardRoom }}</span>
        <span>住院病区: {{ patientHeader.wardArea }}</span>
        <span>主治医生: {{ patientHeader.attendingDoctor }}</span>
        <span>责任护士: {{ patientHeader.nurse }}</span>
        <span>
          总额 <b>{{ patientHeader.balance }}</b>
        </span>
        <span>
          预交金 <b>{{ patientHeader.prepay }}</b>
        </span>
        <span>
          余额 <b>{{ patientHeader.remain }}</b>
        </span>
      </div>
    </section>

    <section class="page-toolbar">
      <ElSegmented
        v-model="activeTab"
        :options="[
          { label: '待处理执行单', value: 'pending-exec' },
          { label: '退费申请', value: 'refund' },
          { label: '待取药/待退药', value: 'takeback' },
        ]"
      />
      <ElButton type="success" size="small">转科</ElButton>
    </section>

    <section class="order-table-wrap">
      <ElTable :data="orderRows" border stripe class="pending-order-table" :show-header="false">
        <ElTableColumn type="selection" width="40" />
        <ElTableColumn prop="orderType" label="类型" min-width="110" />
        <ElTableColumn label="医嘱内容" min-width="420">
          <template #default="{ row }">
            <div class="order-content-cell">
              <p
                v-for="line in splitOrderLines(row.orderContent)"
                :key="`${row.id}-${line}`"
                class="order-content-line"
              >
                {{ line }}
              </p>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="periodText" label="开始/终止" min-width="120" />
        <ElTableColumn prop="executeStartAt" label="开始/终止" min-width="120" />
        <ElTableColumn label="预计执行" min-width="210">
          <template #default="{ row }">
            <div class="plan-time-cell">
              <div class="plan-time-cell__date">{{ row.planDate }}</div>
              <div class="plan-time-cell__slots">
                <span
                  v-for="slot in row.executeSlots"
                  :key="`${row.id}-${slot}`"
                  class="plan-time-chip"
                >
                  {{ slot }}
                </span>
              </div>
            </div>
          </template>
        </ElTableColumn>
      </ElTable>
    </section>
  </div>
</template>

<style scoped>
.in-out-transfer-page {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  background: #f2f6fa;
  padding: 8px;
  gap: 8px;
}

.patient-header {
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px 10px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.patient-header__main {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.patient-header__identity {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.patient-header__line-1,
.patient-header__line-2 {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.patient-header__line-2 {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.patient-header__bed {
  font-size: 18px;
  font-weight: 700;
}

.patient-header__name {
  font-size: 16px;
  font-weight: 600;
}

.patient-header__in-at {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.patient-header__extra {
  display: grid;
  grid-template-columns: repeat(4, minmax(90px, auto));
  align-content: center;
  gap: 4px 12px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.patient-header__extra b {
  color: #c9892c;
  font-weight: 700;
}

.page-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 6px 10px;
}

.order-table-wrap {
  flex: 1;
  min-height: 0;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 6px;
}

.pending-order-table {
  width: 100%;
  height: 100%;
}

.pending-order-table :deep(.el-table__row td.el-table__cell) {
  padding: 8px 0;
  vertical-align: top;
  font-size: 13px;
}

.order-content-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.order-content-line {
  margin: 0;
  line-height: 1.45;
}

.plan-time-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.plan-time-cell__date {
  font-weight: 500;
  color: var(--el-text-color-regular);
  min-width: 42px;
}

.plan-time-cell__slots {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.plan-time-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 44px;
  height: 24px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  color: #16a34a;
  font-size: 12px;
  background: #f8fffb;
}
</style>
