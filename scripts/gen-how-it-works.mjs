#!/usr/bin/env node
/**
 * 生成 README 中的「它是怎么工作的」示意图
 *   输出: docs/how-it-works.svg  +  docs/how-it-works.png
 *   用法: node scripts/gen-how-it-works.mjs
 *
 * 依赖: 无（纯 Node 生成 SVG）；PNG 由本机 Chrome 无头模式截图生成，找不到 Chrome 时自动跳过。
 */
import { writeFileSync, existsSync, mkdirSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve, dirname } from "node:path";
import { execFileSync } from "node:child_process";
import { pathToFileURL } from "node:url";

const W = 1600;
const H = 700;

/* ---------------------------------------------------------------- 内容定义 */
const steps = [
  {
    n: "1",
    color: "#FFC24A",
    color2: "#FF8A3D",
    title: "家长先定规则",
    body: ["在手机上选好题目范围、", "每日时长和可用时段"],
    icon: "phone",
  },
  {
    n: "2",
    color: "#FF9A2B",
    color2: "#FFC24A",
    title: "孩子完成学习任务",
    body: ["电视上用遥控器作答，", "题目按年龄段和科目匹配"],
    icon: "check",
  },
  {
    n: "3",
    color: "#FF6A1F",
    color2: "#FF9A4D",
    title: "解锁对应观看时长",
    body: ["完成任务后自动解锁，", "这次能看多久事先就清楚"],
    icon: "play",
  },
  {
    n: "4",
    color: "#E2481B",
    color2: "#FF7A3D",
    title: "时间到自动锁定",
    body: ["家长随时查看学习情况", "和真实的观看时长"],
    icon: "lock",
  },
];

/* ---------------------------------------------------------------- 图标绘制 */
function icon(name, cx, cy, color) {
  const s = `fill="none" stroke="${color}" stroke-width="4.5" stroke-linecap="round" stroke-linejoin="round"`;
  switch (name) {
    case "phone":
      // 手机 + 设置滑杆
      return `<g ${s} transform="translate(${cx - 26} ${cy})">
        <rect x="4" y="-30" width="44" height="60" rx="9"/>
        <line x1="14" y1="-14" x2="38" y2="-14"/>
        <circle cx="24" cy="-14" r="4.5" fill="${color}" stroke="none"/>
        <line x1="14" y1="2" x2="38" y2="2"/>
        <circle cx="32" cy="2" r="4.5" fill="${color}" stroke="none"/>
        <line x1="14" y1="18" x2="38" y2="18"/>
        <circle cx="20" cy="18" r="4.5" fill="${color}" stroke="none"/>
      </g>`;
    case "check":
      // 任务清单
      return `<g ${s} transform="translate(${cx - 26} ${cy})">
        <rect x="6" y="-30" width="40" height="60" rx="10"/>
        <polyline points="16,-12 21,-7 30,-18"/>
        <line x1="16" y1="6" x2="36" y2="6"/>
        <line x1="16" y1="20" x2="30" y2="20"/>
      </g>`;
    case "play":
      // 播放 + 解锁
      return `<g ${s} transform="translate(${cx - 26} ${cy})">
        <circle cx="26" cy="0" r="26"/>
        <path d="M21,-11 L37,0 L21,11 Z" fill="${color}" stroke="none"/>
      </g>`;
    case "lock":
      // 锁
      return `<g ${s} transform="translate(${cx - 26} ${cy})">
        <rect x="8" y="-4" width="36" height="32" rx="8"/>
        <path d="M16,-4 v-8 a10,10 0 0 1 20,0 v8"/>
        <circle cx="26" cy="12" r="3.4" fill="${color}" stroke="none"/>
      </g>`;
    default:
      return "";
  }
}

/* ---------------------------------------------------------------- 布局计算 */
const cardW = 310;
const cardH = 344;
const gap = 40;
const totalW = steps.length * cardW + (steps.length - 1) * gap;
const left = (W - totalW) / 2;
const cardY = 205;

const cards = steps.map((s, i) => {
  const x = left + i * (cardW + gap);
  const cx = x + cardW / 2;
  const iconCy = cardY + 126;

  const body = s.body
    .map(
      (line, li) =>
        `<text x="${cx}" y="${cardY + 262 + li * 30}" text-anchor="middle" font-size="19" fill="#9A9AA2" font-weight="400">${line}</text>`
    )
    .join("\n      ");

  return `  <g>
    <!-- 卡片 -->
    <rect x="${x}" y="${cardY}" width="${cardW}" height="${cardH}" rx="28"
          fill="#141416" stroke="rgba(255,255,255,.10)" stroke-width="1.5" filter="url(#cardShadow)"/>
    <!-- 顶部彩色条 -->
    <path d="M${x + 28},${cardY} H${x + cardW - 28} A28,28 0 0 1 ${x + cardW},${cardY + 28} V${cardY + 34} H${x} V${cardY + 28} A28,28 0 0 1 ${x + 28},${cardY} Z" fill="url(#bar${i})"/>

    <!-- 步骤序号 -->
    <circle cx="${x + 40}" cy="${cardY + 74}" r="21" fill="#1C1C21"/>
    <text x="${x + 40}" y="${cardY + 82}" text-anchor="middle" font-size="21" font-weight="700" fill="${s.color}">${s.n}</text>

    <!-- 图标 -->
    <circle cx="${cx}" cy="${iconCy}" r="56" fill="${s.color}" opacity=".16"/>
    <circle cx="${cx}" cy="${iconCy}" r="56" fill="none" stroke="${s.color}" stroke-opacity=".28" stroke-width="1.5"/>
    ${icon(s.icon, cx, iconCy, s.color)}

    <!-- 标题 -->
    <text x="${cx}" y="${cardY + 222}" text-anchor="middle" font-size="26" font-weight="700" fill="#F2F2F4">${s.title}</text>
    <!-- 说明 -->
    ${body}
  </g>`;
});

// 卡片之间的箭头
const arrows = steps
  .slice(0, -1)
  .map((_, i) => {
    const x1 = left + (i + 1) * (cardW + gap) - gap + 6;
    const x2 = x1 + gap - 12;
    const y = cardY + cardH / 2;
    return `  <g stroke="#B8C7E6" stroke-width="3" stroke-linecap="round" fill="none">
    <line x1="${x1}" y1="${y}" x2="${x2 - 8}" y2="${y}" stroke-dasharray="2 8"/>
    <polyline points="${x2 - 10},${y - 7} ${x2},${y} ${x2 - 10},${y + 7}" stroke-dasharray="none"/>
  </g>`;
  })
  .join("\n");

const loopFromX = left + 3 * (cardW + gap) + cardW / 2;
const loopToX = left + 1 * (cardW + gap) + cardW / 2;
const loopY = cardY + cardH + 64;

const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" role="img"
     aria-label="StudyOnTV 工作流程：家长在手机端设定题目范围与观看时长，孩子在电视上完成学习任务后解锁观看时长，时间用完自动锁定">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#0B0B0D"/>
      <stop offset="0.55" stop-color="#111013"/>
      <stop offset="1" stop-color="#141110"/>
    </linearGradient>
    <radialGradient id="glow1" cx="0.15" cy="0.1" r="0.6">
      <stop offset="0" stop-color="#FF8A3D" stop-opacity=".20"/>
      <stop offset="1" stop-color="#FF8A3D" stop-opacity="0"/>
    </radialGradient>
    <radialGradient id="glow2" cx="0.88" cy="0.95" r="0.6">
      <stop offset="0" stop-color="#FFB020" stop-opacity=".14"/>
      <stop offset="1" stop-color="#FFB020" stop-opacity="0"/>
    </radialGradient>
    ${steps
      .map(
        (s, i) => `<linearGradient id="bar${i}" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0" stop-color="${s.color}"/>
      <stop offset="1" stop-color="${s.color2}"/>
    </linearGradient>`
      )
      .join("\n    ")}
    <filter id="cardShadow" x="-20%" y="-20%" width="140%" height="150%">
      <feDropShadow dx="0" dy="10" stdDeviation="16" flood-color="#000000" flood-opacity="0.55"/>
    </filter>
    <pattern id="dots" width="26" height="26" patternUnits="userSpaceOnUse">
      <circle cx="1.6" cy="1.6" r="1.6" fill="#FFFFFF" fill-opacity=".16"/>
    </pattern>
    <marker id="loopArrow" viewBox="0 0 12 12" refX="9" refY="6" markerWidth="8" markerHeight="8" orient="auto-start-reverse">
      <path d="M1,1 L10,6 L1,11" fill="none" stroke="#6B6B74" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
    </marker>
  </defs>

  <!-- 背景 -->
  <rect width="${W}" height="${H}" fill="url(#bg)"/>
  <rect width="${W}" height="${H}" fill="url(#dots)" opacity=".55"/>
  <rect width="${W}" height="${H}" fill="url(#glow1)"/>
  <rect width="${W}" height="${H}" fill="url(#glow2)"/>

  <!-- 标题 -->
  <text x="${left}" y="104" font-size="38" font-weight="800" fill="#F2F2F4" letter-spacing="0.5">它是怎么工作的</text>
  <text x="${left}" y="146" font-size="20" fill="#9A9AA2">家长用手机定好规则，孩子在电视上完成任务，剩下的交给应用自动执行</text>

${cards.join("\n\n")}

${arrows}

  <!-- 回环箭头 -->
  <path d="M${loopFromX},${cardY + cardH} V${loopY} H${loopToX} V${cardY + cardH + 14}"
        fill="none" stroke="#5A5A62" stroke-width="2.4" stroke-dasharray="7 7" marker-end="url(#loopArrow)"/>
  <rect x="${(loopFromX + loopToX) / 2 - 190}" y="${loopY - 21}" width="380" height="42" rx="21" fill="#17171B" stroke="rgba(255,255,255,.12)"/>
  <text x="${(loopFromX + loopToX) / 2}" y="${loopY + 7}" text-anchor="middle" font-size="18" font-weight="600" fill="#D2D2D8">时间用完还想看？再完成一组学习任务</text>
</svg>
`;

/* ---------------------------------------------------------------- 输出文件 */
const root = resolve(dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1")), "..");
const docsDir = join(root, "docs");
if (!existsSync(docsDir)) mkdirSync(docsDir, { recursive: true });

const svgPath = join(docsDir, "how-it-works.svg");
writeFileSync(svgPath, svg, "utf8");
console.log("✓ " + svgPath);

/* PNG：用本机 Chrome 无头模式截图 */
const chromeCandidates = [
  process.env.CHROME_PATH,
  "C:/Program Files/Google/Chrome/Application/chrome.exe",
  "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
  "/usr/bin/google-chrome",
  "/usr/bin/chromium",
  "/usr/bin/chromium-browser",
].filter(Boolean);

const chrome = chromeCandidates.find((p) => existsSync(p));
if (!chrome) {
  console.log("… 未找到 Chrome，跳过 PNG 生成（SVG 已生成，README 可直接使用）");
  process.exit(0);
}

const htmlPath = join(tmpdir(), "sotv-how-it-works.html");
writeFileSync(
  htmlPath,
  `<!doctype html><meta charset="utf-8"><style>html,body{margin:0;padding:0;background:transparent}svg{display:block}</style>${svg}`,
  "utf8"
);

const pngPath = join(docsDir, "how-it-works.png");
execFileSync(
  chrome,
  [
    "--headless=new",
    "--disable-gpu",
    "--no-sandbox",
    "--hide-scrollbars",
    "--force-device-scale-factor=2",
    `--window-size=${W},${H}`,
    `--screenshot=${pngPath}`,
    pathToFileURL(htmlPath).href,
  ],
  { stdio: "ignore" }
);
rmSync(htmlPath, { force: true });

/* 可选：用 Python + Pillow 做调色板压缩（1MB+ → 约 120KB），不可用时保持原图 */
try {
  execFileSync(
    "python",
    [
      "-c",
      `from PIL import Image\nim=Image.open(r"${pngPath}").convert("RGB")\nim.quantize(colors=256,method=Image.Quantize.FASTOCTREE,dither=Image.Dither.FLOYDSTEINBERG).save(r"${pngPath}",optimize=True)`,
    ],
    { stdio: "ignore" }
  );
} catch {
  console.log("… 未找到 Python/Pillow，跳过 PNG 压缩");
}

console.log("✓ " + pngPath);
