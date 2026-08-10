# 无负极钠电池文献向量库 — 多端协同方案

> 桌面端入库 + GitHub 同步 + 云端搜索服务 + 手机浏览器查询

## 架构

```
桌面端(Windows)                      GitHub                     Ubuntu 云服务器
┌──────────────────┐   cdp 导出    ┌───────────┐  git pull    ┌──────────────────────┐
│ ChromaDB (本地)   │──git push──▶│ 快照文件   │─────▶       │ FastAPI 搜索服务     │
│ + 入库新论文      │             │  (~8MB)   │             │ + bge-small-zh-v1.5  │
│ + 本地检索        │             └───────────┘             │ + ChromaDB           │
└──────────────────┘                                        └──────────┬───────────┘
                                                                      │ REST API
                                                     手机浏览器 ──────┘ 输入问题 → 文献列表
```

## 组件说明

| 组件 | 技术 | 位置 |
|---|---|---|
| 向量库 | ChromaDB 1.5.x + bge-small-zh-v1.5 (512维) | 桌面本地 |
| 同步载体 | GitHub (minis 仓库, 私有) | 云端 |
| 导出格式 | cdp export 或 JSON 快照 (~8MB) | `exports/` 目录 |
| 搜索服务 | FastAPI + uvicorn | Ubuntu 云服务器 |
| 嵌入模型 | BAAI/bge-small-zh-v1.5 | 服务器本地加载 |
| 手机端 | 浏览器 HTML 页面 (零安装) | 服务器静态托管 |

## 桌面端操作

### 入库新论文
```bash
cd C:\Users\LX\Desktop\文献知识库
python _process_new.py                  # 提取文字 + VL图表
python literature_vector.py build       # 重建向量库
python output\manager.py migrate --force  # SQLite同步
```

### 导出快照并推送
```bash
cd C:\Users\LX\Desktop\文献向量库
python export_snapshot.py               # 导出为 exports/chroma_snapshot.json
git add exports/ 
git commit -m "update: 新论文入库"
git push origin main
```

## 云端服务器部署

### 环境安装
```bash
git clone https://github.com/Paradoxofmoon/minis.git
cd minis
pip install -r requirements.txt
```

### 重建向量库
```bash
python rebuild_from_snapshot.py       # 从快照 JSON 重建 ChromaDB
```

### 启动搜索服务
```bash
uvicorn server:app --host 0.0.0.0 --port 8000
```

## 手机端使用

浏览器访问 `http://你的服务器IP:8000` → 输入检索问题 → 返回相关文献及摘要。

---

## 同步流程

1. **桌面端**：入库新论文 → 运行 `export_snapshot.py` → `git push`
2. **GitHub**：托管快照文件（~8MB JSON，git 友好可 diff）
3. **云端**：cron 定时 `git pull` + `python rebuild_from_snapshot.py`
4. **手机端**：浏览器访问搜索页面，实时查询

## 注意事项

- 两端 ChromaDB 版本必须一致（`chromadb==1.5.9`）
- Embedding 模型必须相同（`BAAI/bge-small-zh-v1.5`）
- 导出时关闭所有 ChromaDB 写入进程
- 服务端必须配置鉴权（token 或 nginx 反代）
