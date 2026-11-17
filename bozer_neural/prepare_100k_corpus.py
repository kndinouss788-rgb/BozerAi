"""Assemble the 100k BOZER dialogue corpus from OpenAssistant and UltraChat."""
import argparse, gzip, hashlib, json, re, unicodedata
from collections import Counter
from datetime import date
from pathlib import Path

OASST="OpenAssistant/oasst1"
OASST_REV="fdf72ae0827c1cda404aff25b6603abec9e3399b"
OASST_FILE="2023-04-12_oasst_ready.messages.jsonl.gz"
ULTRA="HuggingFaceH4/ultrachat_200k"
CATEGORIES=("dialogue_naturel","comprehension_demande","raisonnement_resolution",
"planification_missions","utilisation_outils_verification","securite_confidentialite_honnetete")
EMAIL=re.compile(r"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b",re.I)
PHONE=re.compile(r"(?<!\w)(?:\+\d{1,3}[\s.-]?)?(?:\d{3}[-.]\d{3}[-.]\d{4})(?!\w)")
MAX_CHARS=12000
SHARD_SIZE=10000

def norm(s):
    return " ".join(unicodedata.normalize("NFKC",s).casefold().split())

def valid(s, minimum=8):
    return isinstance(s,str) and minimum <= len(s.strip()) <= MAX_CHARS and not EMAIL.search(s) and not PHONE.search(s)

def category(user, answer):
    t=(user+"\n"+answer).casefold()
    rules=(
    ("securite_confidentialite_honnetete",("privacy","confidential","password","security","secure","safety","personal information","data protection","phishing","malware","sécurité","confidentialité","mot de passe","vie privée","données personnelles","honnête","risque","danger","permission","harmful")),
    ("utilisation_outils_verification",("python","javascript","typescript","programming","code","coding","terminal","shell","command line","api","database","debug","verify","browser","excel","sql","html","css","programme","programmation","commande","vérifier","fichier","logiciel","algorithme","spreadsheet")),
    ("planification_missions",("plan","schedule","organize","organise","roadmap","itinerary","step-by-step","timeline","project","task","mission","trip","planning","planifier","organiser","étapes","voyage","objectif","calendrier","routine","checklist")),
    ("raisonnement_resolution",("solve","reason","logic","calculate","equation","proof","compare","analyze","analyse","deduce","puzzle","math","résoudre","raisonnement","logique","calcul","équation","comparer","déduire","problème","therefore")),
    ("comprehension_demande",("explain","define","what is","why does","how does","describe","summarize","summary","translate","difference between","explique","définis","qu'est-ce","pourquoi","comment fonctionne","décris","résume","traduire","différence entre")))
    for name, words in rules:
        if any(re.search(r"(?<!\w)"+re.escape(w)+r"(?!\w)",t) for w in words): return name
    return "dialogue_naturel"

def label(labels,name):
    if isinstance(labels,dict):
        v=labels.get(name)
        if isinstance(v,dict): v=v.get("value")
        return float(v) if isinstance(v,(int,float)) else 0.0
    if isinstance(labels,list):
        for v in labels:
            if isinstance(v,dict) and v.get("name")==name and isinstance(v.get("value"),(int,float)):
                return float(v["value"])
    return 0.0

def allowed_oasst(row):
    if row.get("deleted") is True or row.get("review_result") is False or row.get("flagged") is True: return False
    labs=row.get("labels")
    if any(label(labs,k)>0 for k in ("spam","pii","not_appropriate","hate_speech","sexual_content")): return False
    scores=row.get("detoxify")
    if isinstance(scores,dict) and any(isinstance(scores.get(k),(int,float)) and scores[k]>=0.5 for k in
    ("toxicity","severe_toxicity","obscene","identity_attack","threat","sexual_explicit")): return False
    return valid(row.get("text"),12)

def collect_oasst(path,limit):
    rows=[]
    with gzip.open(path,"rt",encoding="utf-8") as f:
        for line in f:
            if line.strip(): rows.append(json.loads(line))
    by_id={r.get("message_id"):r for r in rows if r.get("message_id")}
    best={}
    for a in rows:
        if a.get("role")!="assistant" or not allowed_oasst(a): continue
        p=by_id.get(a.get("parent_id")); seen=set()
        while p and p.get("message_id") not in seen:
            seen.add(p.get("message_id"))
            if p.get("role")=="prompter": break
            p=by_id.get(p.get("parent_id"))
        if not p or p.get("role")!="prompter" or not allowed_oasst(p): continue
        pl,al=p.get("lang"),a.get("lang")
        if pl and al and pl!=al: continue
        u,ans=p["text"].strip(),a["text"].strip()
        if not valid(u) or not valid(ans,12): continue
        tree=a.get("message_tree_id") or p.get("message_tree_id") or a.get("parent_id") or a.get("message_id")
        if not tree: continue
        rec={"category":category(u,ans),"user":u,"assistant":ans,"language":al or pl or "und",
        "source":OASST,"source_license":"Apache-2.0","source_id":str(a.get("message_id")),
        "source_group_id":"oasst:"+str(tree),"review_status":"upstream_filtered_needs_sample_review"}
        score=label(a.get("labels"),"quality")+(0.05 if a.get("rank")==0 else 0)
        if str(tree) not in best or score>best[str(tree)][0]: best[str(tree)]=(score,rec)
    values=[v[1] for v in best.values()]
    values.sort(key=lambda x:hashlib.sha256(x["source_group_id"].encode()).hexdigest())
    return values[:limit]

def iter_ultra(limit,seen,revision):
    from datasets import load_dataset
    stream=load_dataset(ULTRA,split="train_sft",streaming=True,revision=revision)
    count=0
    for row in stream:
        pending=None; pairs=[]
        for m in row.get("messages") or []:
            if not isinstance(m,dict): continue
            role=str(m.get("role","")).casefold(); content=m.get("content")
            if not isinstance(content,str) or not content.strip(): continue
            if role in ("user","human","prompter"): pending=content.strip()
            elif role in ("assistant","gpt") and pending:
                pairs.append((pending,content.strip())); pending=None
        if not pairs: continue
        u,a=pairs[-1]
        if not valid(u) or not valid(a,12): continue
        key=norm(u)
        if key in seen: continue
        seen.add(key)
        sid=str(row.get("prompt_id") or hashlib.sha256(key.encode()).hexdigest())
        yield {"category":category(u,a),"user":u,"assistant":a,"language":"en",
        "source":ULTRA,"source_license":"MIT","source_id":sid,
        "source_group_id":"ultrachat:"+sid,"review_status":"upstream_filtered_needs_sample_review"}
        count+=1
        if count>=limit: return

def split_records(records,ntrain,nval,ntest):
    if len(records)!=ntrain+nval+ntest: raise ValueError("split sizes do not add to corpus size")
    ordered=sorted(records,key=lambda x:hashlib.sha256(x["source_group_id"].encode()).hexdigest())
    parts={"train":ordered[:ntrain],"validation":ordered[ntrain:ntrain+nval],"test":ordered[ntrain+nval:]}
    for name,part in parts.items():
        for missing in sorted(set(CATEGORIES)-{x["category"] for x in part}):
            donor=max((n for n in parts if n!=name),key=lambda n:sum(x["category"]==missing for x in parts[n]))
            dc=Counter(x["category"] for x in parts[donor])
            di=next((i for i,x in enumerate(parts[donor]) if x["category"]==missing and dc[missing]>1),None)
            rc=Counter(x["category"] for x in part)
            ri=next((i for i,x in enumerate(part) if rc[x["category"]]>1),None)
            if di is None or ri is None: raise ValueError("cannot balance category "+missing+" in "+name)
            part[ri],parts[donor][di]=parts[donor][di],part[ri]
    groups=[{x["source_group_id"] for x in parts[n]} for n in ("train","validation","test")]
    if groups[0]&groups[1] or groups[0]&groups[2] or groups[1]&groups[2]: raise ValueError("conversation group leaked between splits")
    for n,p in parts.items():
        if {x["category"] for x in p}!=set(CATEGORIES): raise ValueError("missing category in "+n)
    return parts

def write_shards(directory,records):
    directory=Path(directory); directory.mkdir(parents=True,exist_ok=True)
    for old in directory.glob("*.jsonl.gz"): old.unlink()
    for offset in range(0,len(records),SHARD_SIZE):
        path=directory/("part-%05d.jsonl.gz"%(offset//SHARD_SIZE))
        with path.open("wb") as raw:
            with gzip.GzipFile(filename="",mode="wb",fileobj=raw,compresslevel=9,mtime=0) as out:
                for item in records[offset:offset+SHARD_SIZE]:
                    out.write((json.dumps(item,ensure_ascii=False,separators=(",",":"))+"\n").encode("utf-8"))

def sha256(path):
    h=hashlib.sha256()
    with Path(path).open("rb") as f:
        for chunk in iter(lambda:f.read(1024*1024),b""): h.update(chunk)
    return h.hexdigest()

def build(output_dir,target=100000,max_oasst=10000):
    if target<100 or max_oasst<1: raise ValueError("invalid target or OASST limit")
    from huggingface_hub import HfApi,hf_hub_download
    out=Path(output_dir); out.mkdir(parents=True,exist_ok=True)
    raw=hf_hub_download(repo_id=OASST,repo_type="dataset",filename=OASST_FILE,revision=OASST_REV)
    oasst=collect_oasst(raw,min(max_oasst,target))
    seen={norm(x["user"]) for x in oasst}
    ultra_revision=HfApi().dataset_info(ULTRA).sha
    remaining=target-len(oasst)
    ultra=list(iter_ultra(remaining,seen,ultra_revision))
    all_records=oasst+ultra
    if len(all_records)!=target:
        raise RuntimeError("Wanted %s unique records, got %s (OASST=%s, UltraChat=%s)"%(target,len(all_records),len(oasst),len(ultra)))
    ntrain=int(target*.90); nval=int(target*.05); ntest=target-ntrain-nval
    parts=split_records(all_records,ntrain,nval,ntest)
    for name,items in parts.items(): write_shards(out/name,items)
    from .validate_corpus import validate_splits
    report=validate_splits(out/"train",out/"validation",out/"test")
    stats={}
    for name,items in parts.items():
        shards=[]
        for p in sorted((out/name).glob("*.jsonl.gz")):
            shards.append({"file":p.name,"bytes":p.stat().st_size,"sha256":sha256(p)})
        stats[name]={"examples":len(items),"categories":dict(Counter(x["category"] for x in items)),
        "languages":dict(Counter(x["language"] for x in items)),"sources":dict(Counter(x["source"] for x in items)),
        "shards":shards}
    manifest={"name":"BOZER NEURAL dialogue corpus 100k","version":"1.0.0","created":date.today().isoformat(),
    "total_examples":target,"split_sizes":{"train":ntrain,"validation":nval,"test":ntest},
    "sources":{OASST:{"revision":OASST_REV,"license":"Apache-2.0 (upstream dataset card)","url":"https://huggingface.co/datasets/OpenAssistant/oasst1"},
    ULTRA:{"revision":ultra_revision,"license":"MIT (upstream dataset card)","url":"https://huggingface.co/datasets/HuggingFaceH4/ultrachat_200k"}},
    "quality_controls":["Deleted, rejected, flagged, PII, spam, inappropriate and high-toxicity OASST rows excluded",
    "Empty, short and oversized texts excluded","Obvious email and phone patterns excluded",
    "Duplicate normalized prompts removed across sources","One example per OASST tree and one per UltraChat conversation",
    "Conversation groups kept within a single split","Train/validation/test prompt leakage checked"],
    "limitations":["Licenses are declarations in upstream dataset cards, not independent legal opinions on every underlying text.",
    "Examples have not all received independent human factual review.","This corpus does not itself train a model or create a checkpoint."],
    "validation":report,"splits":stats}
    (out/"manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    return manifest

def main():
    p=argparse.ArgumentParser(description="Prepare BOZER's 100k dialogue corpus")
    p.add_argument("--output-dir",default="data/bozer_neural_100k")
    p.add_argument("--target",type=int,default=100000)
    p.add_argument("--max-oasst",type=int,default=10000)
    a=p.parse_args(); result=build(a.output_dir,a.target,a.max_oasst)
    for name,s in result["splits"].items():
        print("%s: %s examples"%(name,s["examples"]))
        for shard in s["shards"]: print("  %s | %s bytes | sha256=%s"%(shard["file"],shard["bytes"],shard["sha256"]))
    print("TOTAL: %s examples"%result["total_examples"])

if __name__=="__main__": main()
