# PRD Based UI Prototypes — Stitch 导出

导出日期：2026-07-21

| 页面 | Stitch Screen ID | 原始源码 | 本地图片版源码 | 参考图 | 已缓存页面图片 |
| --- | --- | --- | --- | --- | --- |
| 家庭成员 - 康伴智能医疗助手（中文版） | `e85bd841a69649b5aac3dde72509a3ad` | `family-members/index.html` | `family-members/index.local.html` | `family-members/reference.jpg` | 4 张 |
| 个人中心 - 康伴智能医疗助手（中文版） | `59b854a847684131acf9fbe0cfbba5c6` | `profile/index.html` | `profile/index.local.html` | `profile/reference.jpg` | 2 张 |
| 设置 - 康伴智能医疗助手（中文版） | `b3414355190d4777a6ea5285daa33103` | `settings/index.html` | `settings/index.local.html` | `settings/reference.jpg` | 1 张 |

说明：

- `index.html` 是 Stitch 原始 HTML 源码。
- `index.local.html` 将页面中 7 张 Google 托管的界面图片改为同目录 `assets/` 的本地文件。
- HTML 仍保留 Stitch 原始的 Tailwind CDN 和 Google Fonts 引用；若需要完全离线运行，需要再把这些第三方依赖打包到项目中。
