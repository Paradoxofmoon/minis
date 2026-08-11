# -*- coding: utf-8 -*-
"""FastAPI 搜索服务（云端部署，手机端访问入口）"""
import os, json
os.environ.setdefault("CHROMA_TELEMETRY_IMPL", "none")
from pathlib import Path
from fastapi import FastAPI, Query, Header, HTTPException
from fastapi.responses import HTMLResponse
import chromadb
from sentence_transformers import SentenceTransformer

# === 配置 ===
TOKEN = "afs-lit-2026"                                   # 简单鉴权（生产环境换成复杂 token）
CHROMA_PATH = Path(__file__).parent / "chroma_db"
COLLECTION_NAME = "literature"
MODEL_NAME = "BAAI/bge-small-zh-v1.5"

# === 初始化 ===
print(f"加载模型: {MODEL_NAME}...")
model = SentenceTransformer(MODEL_NAME)
print(f"加载向量库: {CHROMA_PATH}...")
client = chromadb.PersistentClient(path=str(CHROMA_PATH))
col = client.get_collection(COLLECTION_NAME)
print(f"就绪: {col.count()} 个 chunk")

def embed(text: str):
    return model.encode([text]).tolist()[0]

app = FastAPI(title="文献检索", docs_url=None, redoc_url=None)

@app.get("/search")
def search(
    q: str = Query(..., description="检索问题"),
    k: int = Query(5, ge=1, le=20),
    token: str = Query(default=""),
):
    # 鉴权
    if token != TOKEN:
        raise HTTPException(401, detail="Invalid token")
    # 查询
    r = col.query(
        query_embeddings=[embed(q)],
        n_results=k,
        include=["documents", "metadatas", "distances"],
    )
    # 整理返回
    results = []
    for i in range(len(r["ids"][0])):
        results.append({
            "rank": i + 1,
            "distance": round(r["distances"][0][i], 4),
            "document": r["documents"][0][i][:500] if r["documents"] else "",
            "metadata": r["metadatas"][0][i] if r["metadatas"] else {},
        })
    return {"query": q, "results": results}

@app.get("/", response_class=HTMLResponse)
def index():
    return HTMLResponse("""<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>文献检索</title>
<style>
body{font-family:sans-serif;max-width:700px;margin:20px auto;padding:10px}
input,button{padding:10px;font-size:16px}
input{width:70%} button{width:25%}
.result{margin:15px 0;padding:10px;background:#f5f5f5;border-radius:6px}
.dist{color:#888;font-size:12px}
</style></head><body>
<h2>无负极钠电池文献检索</h2>
<input id="q" placeholder="输入检索问题..." onkeydown="if(event.key==='Enter')search()">
<button onclick="search()">搜索</button>
<div id="results"></div>
<script>
async function search(){
  const q=document.getElementById('q').value;
  if(!q)return;
  document.getElementById('results').innerHTML='搜索中...';
  try{
    const r=await fetch('/search?q='+encodeURIComponent(q)+'&k=5&token=afs-lit-2026');
    const d=await r.json();
    let html='';
    d.results.forEach(r=>{
      html+=`<div class="result"><b>#${r.rank}</b> <span class="dist">(距离:${r.distance})</span><br>${r.document.substring(0,300)}</div>`;
    });
    document.getElementById('results').innerHTML=html||'无结果';
  }catch(e){document.getElementById('results').innerHTML='搜索失败: '+e.message}
}
</script></body></html>""")

@app.get("/health")
def health():
    return {"status": "ok", "chunks": col.count()}
