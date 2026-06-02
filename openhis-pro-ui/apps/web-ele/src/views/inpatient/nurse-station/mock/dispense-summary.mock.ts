/** 汇总领药：患者领药明细 mock。 */
export const nurseStationDispenseOrdersByPatientIdMock = {
  'p-001': [
    {
      id: 'dispense-p001-1',
      orderType: 'long',
      drugCategory: 'western',
      orderClass: '长期',
      orderContent: '碳辛注射液 [12ml:0.3g]（静脉滴注 12ml TID）',
      pickupAmount: '1支',
      pickupPharmacy: '内二区药房',
      schedule: [
        {
          date: '03-14',
          slots: [
            { checked: true, time: '08:00' },
            { checked: true, time: '13:00' },
          ],
        },
        {
          date: '03-15',
          slots: [
            { checked: true, time: '08:00' },
            { checked: true, time: '13:00' },
          ],
        },
        {
          date: '03-16',
          slots: [
            { checked: true, time: '08:00' },
            { checked: true, time: '13:00' },
          ],
        },
      ],
    },
    {
      id: 'dispense-p001-2',
      orderType: 'temp',
      drugCategory: 'chinese',
      orderClass: '临时',
      orderContent: '中药配方颗粒（清热化痰方）',
      pickupAmount: '2剂',
      pickupPharmacy: '住院中药房',
      schedule: [{ date: '03-14', slots: [{ checked: true, time: '18:00' }] }],
    },
  ],
  'p-002': [
    {
      id: 'dispense-p002-1',
      orderType: 'long',
      drugCategory: 'western',
      orderClass: '长期',
      orderContent: '0.9%氯化钠注射液 [500ml]（静脉滴注 BID）',
      pickupAmount: '2袋',
      pickupPharmacy: '住院西药房',
      schedule: [
        {
          date: '03-14',
          slots: [
            { checked: true, time: '09:00' },
            { checked: true, time: '20:00' },
          ],
        },
      ],
    },
  ],
};

/** 汇总领药：兜底明细 mock。 */
export const nurseStationDispenseFallbackOrdersMock = [
  {
    id: 'dispense-fallback-1',
    orderType: 'temp',
    drugCategory: 'western',
    orderClass: '临时',
    orderContent: '维生素C注射液 [2g]（静推）',
    pickupAmount: '1支',
    pickupPharmacy: '住院西药房',
    schedule: [{ date: '03-14', slots: [{ checked: true, time: '10:00' }] }],
  },
];

/** 汇总领药：汇总单据 mock。 */
export const nurseStationDispenseSummarySheetsMock = [
  {
    id: 'summary-001',
    sheetNo: 'MSY202604230002',
    applyTime: '2026-04-23 15:15:02',
    dispenseTime: '',
    pharmacy: '疗区药房',
    applicant: '住院护士',
    pickupUser: '',
    dispenseStatus: '待配药',
  },
  {
    id: 'summary-002',
    sheetNo: 'MSY202604230001',
    applyTime: '2026-04-23 15:04:14',
    dispenseTime: '',
    pharmacy: '疗区药房',
    applicant: '住院护士',
    pickupUser: '',
    dispenseStatus: '待配药',
  },
  {
    id: 'summary-003',
    sheetNo: 'MSY202603060002',
    applyTime: '2026-03-06 17:03:16',
    dispenseTime: '2026-03-06 17:23:40',
    pharmacy: '疗区药房',
    applicant: '王海明',
    pickupUser: '王海明',
    dispenseStatus: '已发放',
  },
];
