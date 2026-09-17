import calendar from '/Users/atlas/Data/shuttlelab/calendar-shuttle/node_modules/js-calendar-converter/dist/js-calendar-converter.mjs';

const out = [];
function add(y, m, d) {
  const r = calendar.solar2lunar(y, m, d);
  if (r === -1 || !r) return;
  out.push([
    `${y}-${String(m).padStart(2,'0')}-${String(d).padStart(2,'0')}`,
    r.lYear, r.lMonth, r.lDay, r.isLeap ? 1 : 0,
    r.IMonthCn, r.IDayCn, r.Term ?? '', r.festival ?? '', r.lunarFestival ?? '',
    r.Animal, r.nWeek,
  ].join('|'));
}
// 每 23 天取一个样本,覆盖 1900-02-01 ~ 2100-12-31:既能撞上闰月边界与节气跨日,
// 又不至于让夹具大到没人看。
let dt = Date.UTC(1900, 1, 1);
const end = Date.UTC(2100, 11, 31);
while (dt <= end) {
  const d = new Date(dt);
  add(d.getUTCFullYear(), d.getUTCMonth() + 1, d.getUTCDate());
  dt += 23 * 86400000;
}
// 额外钉死一批"必须对"的日子:边界、春节/除夕、闰月、节气。
const extra = [
  [1900,1,31],[1900,2,1],[1987,11,1],[1987,6,1],[2000,1,1],[2004,1,22],
  [2020,5,23],[2020,4,23],[2023,1,21],[2023,1,22],[2024,2,9],[2024,2,10],
  [2025,1,28],[2025,1,29],[2026,2,16],[2026,2,17],[2033,12,22],[2033,1,31],
  [2100,1,1],[2099,12,31],[2026,9,17],[2026,10,1],[2026,12,22],[2026,6,21],
];
for (const [y,m,d] of extra) add(y,m,d);
console.log(out.join('\n'));
