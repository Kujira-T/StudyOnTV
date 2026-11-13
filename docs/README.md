# docs/

README 里引用的图片资源。

| 文件 | 说明 |
|---|---|
| `how-it-works.png` | 「它是怎么工作的」流程图（README 中使用，2x 分辨率，已压到约 120KB） |
| `how-it-works.svg` | 上面这张图的矢量源文件，可直接改文案后重新导出 |

## 重新生成流程图

```bash
node scripts/gen-how-it-works.mjs
```

脚本纯 Node（无第三方依赖）：先按内容生成 SVG，再用本机 Chrome 无头模式截图为 2x PNG，最后用 Python + Pillow 做调色板压缩。
改文案只需要编辑脚本顶部的 `steps` 数组。

## 其它文档

- [安装指南](./install.md)
- [让 AI 加题（局域网接口）](./ai-api.md)
- [与 Home Assistant 联动](./home-assistant.md)
- [埋点说明](./analytics.md)
