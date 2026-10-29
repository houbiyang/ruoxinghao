# 2026-07-30 正式实验对应源码快照

正式实验环境记录中的 `git_commit` 为 `5bb4e0439c155ab66de86e8b17a86747cb386b41`，该提交形成于 2026-07-27 15:35:44（北京时间）。本目录提供从该本地提交导出的[源码快照](experiment-20260727-5bb4e043-source.zip)和[逐文件清单](experiment-20260727-5bb4e043-manifest.csv)，供评审者获取实验所指的历史代码版本。原始提交的树对象为 `497428050ce0c7cfa2dfc2cf307cdd4b57aea557`。

快照包含后端、模拟器、前端的源码与测试、依赖锁文件、构建配置、Compose 配置、示例环境变量、启动脚本及当时的 README，共 382 个文件。导出时关闭 Git 的换行符转换；每个文件的内容都与原始提交的 Git blob SHA-1 一致。未将原提交中约 1.6 万个文件的整段历史公开：其余主要是 `node_modules`、Maven 缓存、IDE/运行状态和临时演示材料。快照不含实际 `.env`；示例文件中的密码和 API Key 均为同一占位值。

| 对象 | SHA-256 |
| --- | --- |
| `experiment-20260727-5bb4e043-source.zip` | `8FD5B14EF33367362DC679571B84874E17D716C0E22C17C8E594E14AACAC1140` |
| `experiment-20260727-5bb4e043-manifest.csv` | `F1B606776050643B97ECCC5094101EFC953711ACF639365DADFD57343F3BF3F8` |

在 Windows PowerShell 下载上述两个文件后，可核对下载值：

```powershell
Get-FileHash .\experiment-20260727-5bb4e043-source.zip -Algorithm SHA256
Get-FileHash .\experiment-20260727-5bb4e043-manifest.csv -Algorithm SHA256
Expand-Archive .\experiment-20260727-5bb4e043-source.zip .\experiment-source
```

逐文件清单列出快照内的相对路径、原始 Git blob SHA-1、SHA-256 和字节数。它可用于核对下载及解压后的文件；原始完整 Git 提交并未公开，因此不能在当前公开仓库直接 `git checkout 5bb4e043...`。这里提供的是该提交的**运行所需源码子集**，不是完整 Git 历史。

## 验证范围与使用边界

在独立解压目录中，使用 JDK 21 和 Maven 对快照运行测试：后端 147 项、模拟器 25 项通过；前端执行 `npm ci --ignore-scripts`、`npm run type-check` 与 `npm run build` 通过。原始 `maven-settings.xml` 将本地仓库路径写为开发机位置；在其他 Windows 机器上执行 Maven 时，使用命令行 `-Dmaven.repo.local` 指向自己的缓存，例如：

```powershell
Set-Location .\experiment-source\backend
mvn.cmd -gs ..\maven-settings.xml -s ..\maven-settings.xml "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" test
Set-Location ..\simulator
mvn.cmd -gs ..\maven-settings.xml -s ..\maven-settings.xml "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" test
```

前端可从快照根目录执行：

```powershell
Set-Location .\experiment-source\frontend
npm ci --ignore-scripts
npm run type-check
npm run build
```

这次验证证明历史源码快照可获取且可完成上述测试，**没有重新运行 75 轮正式实验**。2026-07-30 的数据目录记录 `reused_run_count: 75`、`new_run_count: 0`，表示汇总复用了当日已完成运行。快照与当前主分支也不是同一软件版本；当前主分支的运行结果不能直接替代历史实验数据。原始运行环境中的 PostgreSQL、Redis、EMQX、求解器原生库及随机种子仍需按[实验环境记录](../data/real-control-formal/experiment-environment.json)核对。历史 Compose 配置仅作为当时版本证据，不代表当前推荐的依赖版本或分发许可结论。
