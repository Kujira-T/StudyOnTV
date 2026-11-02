# 埋点（Google Analytics 4 + Microsoft Clarity）

两个 ID 只写在两个文件里，改完就生效：

- 电视端：`app/web/assets/js/analytics.js`
- 手机端：`admin/assets/analytics.js`

```js
var CONFIG = {
  ga4Id: 'G-XXXXXXXXXX',   // GA4 衡量 ID
  clarityId: 'abcdefghij', // Clarity 项目 ID
  enabled: true,
  debug: false             // true 时只在控制台打印，不上报
};
```

填的是占位符（含 `XXXX` / `abcdef`）时脚本不会加载第三方代码，本地开发不会有请求。

## 事件列表

| 事件 | 触发时机 | 参数 |
|---|---|---|
| `page_view` | 页面加载 | `page`、`title`、`ua_mobile` |
| `connect_attempt` | 家长页点「连接」 | `host` |
| `connect_success` | 连上电视 | `host` |
| `connect_fail` | 连接失败 | `reason` |
| `rule_save_success` | 保存规则成功 | 规则字段 |
| `rule_save_fail` | 保存失败 | — |
| `quiz_start` | 电视端开始一组题 | `level` |
| `quiz_pass` | 答对达标、解锁时长 | `correct`、`total`、`minutes` |
| `quiz_fail` | 答对不足 | `correct`、`total` |
| `lock_view` | 打开锁屏页 | `reason` |
| `study_click` | 孩子选「我去学习了」 | — |
| `question_add` | 手机上新增题目 | — |
| `question_import` | 导入题库 | `count` |
| `question_export` | 导出题库 | `count` |
| `question_delete` | 删除题目 | — |
| `js_error` | 前端报错 | `message`（截断 120 字） |

埋点只发匿名行为数据，**不发题干、不发孩子信息**。
