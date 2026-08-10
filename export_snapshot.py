# -*- coding: utf-8 -*-
"""从桌面端 ChromaDB 导出快照为 JSON"""
import sys, json, os
os.environ.setdefault("CHROMA_TELEMETRY_IMPL", "none")
import chromadb
from pathlib import Path

CHROMA_PATH = r"C:\Users\LX\Desktop\文献知识库\output\chroma_db"
COLLECTION_NAME = "literature"
EXPORT_DIR = Path(__file__).parent / "exports"
EXPORT_DIR.mkdir(exist_ok=True)

client = chromadb.PersistentClient(path=CHROMA_PATH)
col = client.get_collection(COLLECTION_NAME)
data = col.get(include=["documents", "metadatas", "embeddings"])

# 把 numpy 数组转成 Python list
if "embeddings" in data and data["embeddings"] is not None:
    data["embeddings"] = [e.tolist() if hasattr(e, 'tolist') else e for e in data["embeddings"]]

output = EXPORT_DIR / "chroma_snapshot.json"
with open(output, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False)

count = len(data.get("ids", []))
size_kb = output.stat().st_size / 1024
print(f"OK: {count} chunks -> {output} ({size_kb:.0f} KB)")
