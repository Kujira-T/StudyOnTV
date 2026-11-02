import {readFileSync,writeFileSync,existsSync,mkdirSync,mkdtempSync,rmSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {tmpdir} from 'node:os';
import {resolve,join,basename} from 'node:path';
import {pathToFileURL} from 'node:url';

const input=resolve(process.argv[2]||'shots');
const output=resolve('docs/screenshots');
mkdirSync(output,{recursive:true});
const chrome=[process.env.CHROME_PATH,'C:/Program Files/Google/Chrome/Application/chrome.exe','/Applications/Google Chrome.app/Contents/MacOS/Google Chrome','/usr/bin/google-chrome'].filter(Boolean).find(existsSync);
if(!chrome)throw new Error('Set CHROME_PATH to a Chrome executable');
const jobs=[
 ['phone','setup-mobile.png','setup-mobile.png'],
 ['tv','setup-tv.png','setup-tv.png'],
 ['tv','tv-quiz.png','tv-quiz.png'],
 ['tv','tv-pair.png','tv-pair.png'],
 ['tv','tv-lock-schedule.png','tv-lock-schedule.png'],
 ['desktop','parent-desktop.png','parent-desktop.png'],
 ['tablet','tablet-quiz.png','tablet-quiz.png'],
 ['tablet','tablet-handwriting.png','tablet-handwriting.png'],
 ['phone','phone-quiz.png','phone-quiz.png'],
 ['phone','phone-rules.png','parent-rules.png'],
 ['phone','phone-bank.png','parent-bank.png'],
 ['phone','phone-log.png','parent-log.png'],
 ['phone','phone-review.png','parent-review.png']
];
for(const [type,source,name] of jobs){
 const file=join(input,source);if(!existsSync(file))continue;
 const png=readFileSync(file);
 const sourceWidth=png.readUInt32BE(16),sourceHeight=png.readUInt32BE(20);
 const expectedRatio=type==='tv'?16/9:type==='tablet'?16/10:null;
 if(expectedRatio&&Math.abs(sourceWidth/sourceHeight-expectedRatio)>.002)throw new Error(`${source}: capture the full ${type} screen at ${type==='tv'?'16:9':'16:10'}; received ${sourceWidth}x${sourceHeight}`);
 const browser=type==='desktop';
 const contentWidth=type==='phone'?390:1280;
 const bezel=browser?1:type==='phone'?12:16;
 const width=contentWidth+bezel*2+104;
 const contentHeight=Math.ceil(contentWidth*sourceHeight/sourceWidth);
 const toolbarHeight=browser?38:0;
 const height=40+bezel*2+contentHeight+toolbarHeight+(type==='tv'?54:0)+72;
 const radius=browser?12:type==='phone'?44:28;
 const src='data:image/png;base64,'+png.toString('base64');
 const html=`<!doctype html><meta charset="utf-8"><style>
 *{box-sizing:border-box}html,body{margin:0;background:#f1f3ee;width:${width}px;height:${height}px;overflow:hidden}
 .stage{padding:40px 52px 72px;position:relative}
 .device{position:relative;padding:${bezel}px;background:${browser?'#cdd5c8':'linear-gradient(140deg,#464b45,#202420)'};border-radius:${radius}px;box-shadow:0 18px 32px #31402724,0 0 0 1px #64715b;}
 .screen{border-radius:${browser?'0 0 11px 11px':`${radius-bezel}px`};overflow:hidden;background:white;line-height:0}
 img{display:block;width:${contentWidth}px;height:auto}
 .window-bar{display:flex;align-items:center;gap:6px;height:38px;padding:0 14px;background:#e9ede4;border-radius:11px 11px 0 0;font:12px Arial,"Microsoft YaHei",sans-serif;color:#68755f}
 .window-dot{width:7px;height:7px;background:#a3ad9b;border-radius:50%}.window-title{flex:1;text-align:center;padding-right:39px}
 .stand{height:42px;width:100px;background:#262c25;margin:auto}
 .foot{height:12px;border-radius:8px;background:#343b31;width:320px;margin:auto}
 </style><div class="stage"><div class="device">${browser?'<div class="window-bar"><span class="window-dot"></span><span class="window-dot"></span><span class="window-dot"></span><span class="window-title">题了个题 · 家长管理</span></div>':''}<div class="screen"><img src="${src}"></div></div>${type==='tv'?'<div class="stand"></div><div class="foot"></div>':''}</div>`;
 const temp=join(tmpdir(),'study-frame-'+basename(name)+'.html');writeFileSync(temp,html);
 const profile=mkdtempSync(join(tmpdir(),'study-shots-chrome-'));
 try {
  execFileSync(chrome,['--headless=new',`--user-data-dir=${profile}`,'--disable-gpu','--hide-scrollbars','--force-device-scale-factor=1',`--window-size=${width},${height}`,`--screenshot=${join(output,name)}`,pathToFileURL(temp).href],{stdio:'ignore'});
 } finally { rmSync(profile,{recursive:true,force:true,maxRetries:3,retryDelay:100}); }
 console.log(name);
}
