export const NAV_ITEMS = [
  { id: 'home', label: '首页' },
  { id: 'profile', label: '个人资料' },
  { id: 'family', label: '家庭成员' },
  { id: 'health-record', label: '记录指标' },
  { id: 'trends', label: '健康指标' },
  { id: 'records', label: '病历管理' },
  { id: 'consultation', label: 'AI 问诊' },
  { id: 'medications', label: '用药管理' },
  { id: 'settings', label: '设置' },
];

export const DEFAULT_AVATAR_URL = '/stitch/PRD-Based-UI-Prototypes/profile/assets/01.jpg';

export function getUserDisplayName(user, fallback = '用户') {
  return user?.name || user?.username || user?.account || user?.phone || fallback;
}

export const patient = {
  name: '李雪',
  displayName: '李明（自己）',
  age: '39 岁',
  gender: '男性',
  email: 'li.j@example.com',
  patientId: 'KF2-DKZWQ-BAI',
  avatar: DEFAULT_AVATAR_URL,
};

export const healthMetrics = [
  { key: 'ecg', label: '心率 (ECG)', value: '89', unit: 'BPM', hint: '正常范围', color: 'coral' },
  { key: 'pressure', label: '血压', value: '120', secondary: '/80', unit: 'mmHg', hint: '正常范围', color: 'blue' },
  { key: 'glucose', label: '血糖', value: '5.2', unit: 'mmol/L', hint: '上次为 5.07', color: 'green' },
];

export const trendMetrics = [
  { key: 'pressure', title: '收缩压与舒张压', value: '120/80', unit: 'mmHg', description: '过去 30 天趋势', color: 'coral' },
  { key: 'glucose', title: '平均血糖', value: '5.2', unit: 'mmol/L', description: '过去 30 天平均', color: 'blue' },
  { key: 'weight', title: '体重变化', value: '68.5', unit: 'kg', description: '较上月 -1.2 kg', color: 'green' },
];

export const medications = [
  { id: 'metformin', name: '盐酸二甲双胍片', detail: '500mg · 口服', time: '早餐后 08:00', status: '今日已服用', color: 'blue' },
  { id: 'amlodipine', name: '苯磺酸氨氯地平片', detail: '5mg · 口服', time: '晚餐后 18:00', status: '待服用', color: 'coral', warning: true },
  { id: 'vitamin', name: '阿托伐他汀钙片', detail: '20mg · 口服', time: '睡前 22:00', status: '今日已服用', color: 'green' },
];

export const medicalFiles = [
  { id: 'lab', name: 'Lab_Results_Oct2024.pdf', meta: '化验单 · 2024年10月 · 2.4 MB', type: 'PDF', tone: 'blue' },
  { id: 'checkup', name: 'XRay_Chest_PA.jpg', meta: '影像检查 · 2024年9月 · 1.8 MB', type: 'JPG', tone: 'blue' },
  { id: 'scan', name: 'Scanned_Notes_Unde...pdf', meta: 'OCR 处理 · 2024年9月 · 0.8 MB', type: 'PDF', tone: 'coral' },
  { id: 'ecg', name: 'ECG_Report_03.pdf', meta: '心电图 · 2024年8月 · 3.2 MB', type: 'PDF', tone: 'blue' },
];

export const chatSeed = [
  { id: 1, role: 'user', text: '医生，我最近买了 Apple Watch Series X 后开始担心心率和血压。与您的平均数据相比，我的心率有些偏高吗？' },
  { id: 2, role: 'assistant', text: '根据您的记录，心率平均值是正常的。但是，如果您感觉到头晕、胸闷或持续不适，建议及时就医。' },
];
