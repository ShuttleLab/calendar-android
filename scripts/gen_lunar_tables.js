const fs = require('fs');
const SRC = '/Users/atlas/Data/shuttlelab/calendar-shuttle/node_modules/js-calendar-converter/src/constant';

const lunarSrc = fs.readFileSync(SRC + '/Lunar.js', 'utf8');
const lunarInfo = lunarSrc.match(/0x[0-9a-f]{5}/g);
const termSrc = fs.readFileSync(SRC + '/SolarTerm.js', 'utf8');
const sTermInfo = termSrc.match(/'[0-9a-f]{30}'/g).map(s => s.slice(1, -1));
const festSrc = fs.readFileSync(SRC + '/Festival.js', 'utf8');

function parseFest(block) {
  const out = [];
  const re = /'([\d-]+)':\s*\{\s*title:\s*'([^']+)'\s*\}/g;
  let m;
  while ((m = re.exec(block)) !== null) out.push([m[1], m[2]]);
  return out;
}
const solarBlock = festSrc.slice(festSrc.indexOf('const festival'), festSrc.indexOf('const lFestival'));
const lunarBlock = festSrc.slice(festSrc.indexOf('const lFestival'));
const festival = parseFest(solarBlock);
const lFestival = parseFest(lunarBlock);

console.error(`lunarInfo=${lunarInfo.length} sTermInfo=${sTermInfo.length} festival=${festival.length} lFestival=${lFestival.length}`);

function wrapHex(arr, perLine, indent) {
  const lines = [];
  for (let i = 0; i < arr.length; i += perLine) {
    lines.push(indent + arr.slice(i, i + perLine).join(', ') + ',');
  }
  return lines.join('\n');
}
function wrapStr(arr, perLine, indent) {
  const lines = [];
  for (let i = 0; i < arr.length; i += perLine) {
    lines.push(indent + arr.slice(i, i + perLine).map(s => `"${s}"`).join(', ') + ',');
  }
  return lines.join('\n');
}

const header = `package org.shuttlelab.calendar.data

/*
 * 农历数据表。**本文件由脚本从 js-calendar-converter 的 src/constant/ 逐字生成,不要手工编辑。**
 *
 * 数据源 = Web 端(calendar-shuttle)使用的同一个包 jjonline/calendar.js(js-calendar-converter
 * v0.0.7)。两端要给出**完全相同**的农历、节气与节日,唯一可靠的办法就是用同一份表加同一套算法;
 * 任何"自己实现一遍农历"的做法都会在某些年份与 Web 对不上,而那种差异只会在用户手里被发现。
 *
 * 手抄同样不可接受:lunarInfo 有 ${lunarInfo.length} 个十六进制字面量、sTermInfo 有 ${sTermInfo.length} 个 30 字符的十六进制串,
 * 抄错一位就是某一年的农历整体错一天,而且不会有任何报错。所以它们是生成的。
 *
 * 重新生成(需要 Web 项目在本地):
 *   node scripts/gen_lunar_tables.js > app/src/main/java/org/shuttlelab/calendar/data/LunarTables.kt
 *
 * EN: lunar data tables, GENERATED from js-calendar-converter's src/constant/ — do not hand-edit.
 * The source is the very same package the web app (calendar-shuttle) uses, jjonline/calendar.js.
 * Producing identical lunar dates, solar terms and festivals on both sides is only dependable
 * with the same tables and the same algorithm; any re-implementation drifts from the web in some
 * years, and that kind of difference is only ever discovered by a user. Transcribing by hand is
 * equally out: ${lunarInfo.length} hex literals plus ${sTermInfo.length} thirty-character hex strings, where a single
 * wrong digit shifts a whole year's lunar dates by one day and raises no error at all.
 *
 * Regenerate (needs the web project checked out locally):
 *   node scripts/gen_lunar_tables.js > app/src/main/java/org/shuttlelab/calendar/data/LunarTables.kt
 */
internal object LunarTables {

    /** 农历 1900-${1900 + lunarInfo.length - 1} 的闰月/大小月信息表(每年一个 hex 位掩码)。 */
    val lunarInfo = intArrayOf(
${wrapHex(lunarInfo, 10, '        ')}
    )

    /** 公历每月天数普通表(2 月由闰年规则单独算)。 */
    val solarMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    /** 天干。 */
    val gan = arrayOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")

    /** 地支。 */
    val zhi = arrayOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")

    /** 生肖(与地支同序)。 */
    val animals = arrayOf("鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪")

    /** 二十四节气名,从小寒(n=1)起。 */
    val solarTerm = arrayOf(
        "小寒", "大寒", "立春", "雨水", "惊蛰", "春分", "清明", "谷雨",
        "立夏", "小满", "芒种", "夏至", "小暑", "大暑", "立秋", "处暑",
        "白露", "秋分", "寒露", "霜降", "立冬", "小雪", "大雪", "冬至",
    )

    /** 数字 → 中文(星期与农历日尾数共用)。 */
    val nStr1 = arrayOf("日", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")

    /** 农历日的十位称呼。 */
    val nStr2 = arrayOf("初", "十", "廿", "卅")

    /** 农历月称呼(正月…腊月)。 */
    val nStr3 = arrayOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")

    /** 1900-${1900 + sTermInfo.length - 1} 各年二十四节气日期速查表(每年 30 字符,6 段 × 5 字符)。 */
    val sTermInfo = arrayOf(
${wrapStr(sTermInfo, 3, '        ')}
    )

    /** 公历节日,键为 "M-D"。 */
    val festival = mapOf(
${festival.map(([k, v]) => `        "${k}" to "${v}",`).join('\n')}
    )

    /** 农历节日,键为农历 "M-D"。 */
    val lunarFestival = mapOf(
${lFestival.map(([k, v]) => `        "${k}" to "${v}",`).join('\n')}
    )
}
`;
process.stdout.write(header);
