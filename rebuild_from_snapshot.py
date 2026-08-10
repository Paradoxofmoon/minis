# -*- coding: utf-8 -*-
"""服务器端：从快照 JSON 重建 ChromaDB"""
import sys, json, os
from pathlib import Path
os.environ.setdefault("CHROMA_TELEMETRY_IMPL", "none")
import chromadb

SNAPSHOT_PATH = Path(__file__).parent / "exports" / "chroma_snapshot.json"
CHROMA_PATH = Path(__file__).parent / "chroma_db"
COLLECTION_NAME = "literature"

if not SNAPSHOT_PATH.exists():
    print(f"快照文件不存在: {SNAPSHOT_PATH}")
    sys.exit(1)

with open(SNAPSHOT_PATH, "r", encoding="utf-8") as f:
    data = json.load(f)

# 删除旧库重建
if CHROMA_PATH.exists():
    import shutil
    shutil.rmtree(CHROMA_PATH)

client = chromadb.PersistentClient(path=str(CHROMA_PATH))
col = client.create_collection(name=COLLECTION_NAME)

ids = data.get("ids", [])
documents = data.get("documents", [])
metadatas = data.get("metadatas", [])
embeddings = data.get("embeddings", [])

if ids:
    # 分批添加（避免单次太大）
    BATCH = 100
    for i in range(0, len(ids), BATCH):
        col.add(
            ids=ids[i:i+BATCH],
            documents=documents[i:i+BATCH] if documents else None,
            metadatas=metadatas[i:i+BATCH] if metadatas else None,
            embeddings=embeddings[i:i+BATCH] if embeddings else None,
        )
    print(f"重建完成: {len(ids)} 个 chunk → {CHROMA_PATH}")
else:
    print("快照为空，未重建")
