# 定制覆盖层

本目录用于上游同步后强制恢复本 fork 的改动。

## 内容
- `overlay/`：完整覆盖文件
- `manifest.json`：覆盖清单与构建变体
- `removed-paths.txt`：已删除路径；若上游变更仅命中这些路径则跳过构建
- `guarded-paths.txt`：敏感路径记录

## 当前定制
1. 弹幕匹配增强（剧名清洗/评分/多源）
2. 内置点播+直播：`http://114.55.251.49:5244/d/tvbox/vod.json`，设置页不明文显示
3. 软件内更新源改为 `wulalala66/webhtv`
4. 仅构建：`leanback + armeabi-v7a`、`mobile + arm64-v8a`
