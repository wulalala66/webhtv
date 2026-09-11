# 定制覆盖层

本目录用于上游同步后强制恢复本 fork 的改动。

## 内容
- `overlay/`：完整覆盖文件
- `manifest.json`：覆盖清单与构建变体
- `removed-paths.txt`：已删除路径（当前无）
- `guarded-paths.txt`：敏感路径记录
- `upstream-base.sha`：上次同步的上游 main commit
- `upstream-release.tag`：上次跟进的上游稳定 release tag

## 当前定制
1. 内置点播+直播：`http://114.55.251.49:5244/d/tvbox/vod.json`，设置页不明文显示网址；自定义后可正常显示自定义名称
2. 仅构建：`leanback + armeabi-v7a`、`mobile + arm64-v8a`

其余代码（含弹幕）保持与上游官方一致。

## 自动同步 / 构建策略
- 每 6 小时检测一次上游 main 是否有更新
- 上游 main 有更新时同步代码并重新应用本覆盖层
- **仅当上游 main 出现新的稳定 release tag 时**才触发本 fork 的 release 构建（不是每次同步都构建）
- 可手动触发 `sync-upstream.yml` 并勾选 `force_build` 强制构建
