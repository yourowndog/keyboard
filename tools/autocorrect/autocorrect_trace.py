#!/usr/bin/env python3
"""Frozen offline replay used for the 2026-07-12 autocorrect forensics.

WARNING: This is not the production autocorrect implementation and it is not an
instrumented Android/IME trace. It reimplements selected Kotlin behavior and
freezes policy/shortcut tables so two repository worktrees can be compared.

Assumptions:
* the primary NgramSuggestionEngine loaded successfully;
* no previous lexical word is supplied;
* the neural gate supplies no verdict;
* fallback is not entered;
* packaged assets represent the active dictionary and bigram state.

Invocation:
    python3 tools/autocorrect/autocorrect_trace.py WORKTREE baseline diagnosis
    python3 tools/autocorrect/autocorrect_trace.py WORKTREE head diagnosis

Modes are diagnosis, compact, or omitted for full JSON. The revision label
baseline selects the pre-cleanup policy-cull reconstruction; every other label
selects the cleanup-HEAD reconstruction. See the canonical forensic record in
docs/autocorrect/autocorrect-regression-forensic-record.md before interpreting
output.
"""

import json
import math
import re
import sys
from pathlib import Path

if len(sys.argv) < 3:
    raise SystemExit(
        "usage: autocorrect_trace.py WORKTREE {baseline|head} "
        "[diagnosis|compact]"
    )

ROOT = Path(sys.argv[1])
REV = sys.argv[2]
TOKENS = ["las", "sugg3stions", "correc5ed", "5his", "thatbsuggestiins", "inthe", "teh", "742", "PS2", "v2", "3.14", "zsh"]
POS = {c:(float(i),0.0) for i,c in enumerate("qwertyuiop")}
POS.update({c:(i+0.5,1.0) for i,c in enumerate("asdfghjkl")})
POS.update({c:(i+1.5,2.0) for i,c in enumerate("zxcvbnm")})
BUILTIN = {"bc","rn","tf","lmk","ppl","msg","thx","sry","btw","imo","idk","omg","wtf","smh","ngl","tbh","fr","wdym","bday","pls","llms","cs","gen","config","ai","ya","def","tho","nah","ugh","oof","bruh","cuz","g","i"}
ANTI = {"sams":{"samson","samoa"},"min":{"mine","mini","mind"},"hurray":{"hurrah"},"uh":{"uhuru","u"},"id":{"i'd"},"s":{"so","see"},"hows":{"how"},"minecraft":{"mineshaft"},"hesd":{"he'd"},"d":{"don't"},"snd":{"and"},"t":{"to"},"gor":{"gore"},"snf":{"sncf"},"ir":{"iron"},"congrats":{"contrast"},"domething":{"something"},"kaylyn":{"kayla"},"ton":{"top","to"},"quora":{"quota"},"un":{"under"},"facetime":{"peacetime"},"constipated":{"constituted"},"gramps":{"tramps"},"peeps":{"peep"},"caps":{"capsule"},"co":{"court","could"},"7o":{"to"},"duh":{"oh","dug"},"dopesick":{"homesick"},"nugs":{"bugs"},"tomcat":{"tosca"},"deadhead":{"deadbeat"},"junkie":{"junk"}}
SHORTCUTS = {"im":"I'm","i'm":"I'm","ive":"I've","id":"I'd","ill":"I'll","dont":"don't","cant":"can't","wont":"won't","wint":"won't","didnt":"didn't","doesnt":"doesn't","isnt":"isn't","arent":"aren't","wasnt":"wasn't","werent":"weren't","hasnt":"hasn't","havent":"haven't","hadnt":"hadn't","couldnt":"couldn't","wouldnt":"wouldn't","shouldnt":"shouldn't","youre":"you're","theyre":"they're","hes":"he's","shes":"she's","thats":"that's","whats":"what's","whos":"who's","lets":"let's","ac":"AC","itd":"it'd","hows":"how's","km":"I'm","moms":"Mom's"}
OVERRIDES = {w.lower():10_000_000 for w in ["kiry","congrats","Claira","Christmas","min","Mom","Aorus","GPU","Hurray","Sam","Sam's","uh","oof","bc","pls","idk","wtf"]}

def load_dict():
    raw, display = {}, {}
    for line in (ROOT/"app/src/main/assets/ime/dict/unified_dictionary.tsv").read_text().splitlines():
        p=line.split("\t")
        if len(p)<2: continue
        try: f=int(float(p[1]))
        except: continue
        lo=p[0].lower()
        if f >= raw.get(lo,-1): raw[lo]=f; display[lo]=p[0]
    for w,f in OVERRIDES.items():
        raw[w]=f; display.setdefault(w,w)
    return raw,display

def load_bigrams():
    out={}
    for line in (ROOT/"app/src/main/assets/ime/dict/final_mobile_bigrams.tsv").read_text().splitlines():
        p=line.split("\t")
        if len(p)<2 or " " not in p[0]: continue
        a,b=p[0].split(" ",1)
        try: f=int(p[1])
        except: continue
        out[(a.lower(),b.lower())]=f
    return out

def osa(a,b):
    n,m=len(a),len(b); d=[[0]*(m+1) for _ in range(n+1)]
    for i in range(n+1): d[i][0]=i
    for j in range(m+1): d[0][j]=j
    for i in range(1,n+1):
        for j in range(1,m+1):
            d[i][j]=min(d[i-1][j]+1,d[i][j-1]+1,d[i-1][j-1]+(a[i-1]!=b[j-1]))
            if i>1 and j>1 and a[i-1]==b[j-2] and a[i-2]==b[j-1]: d[i][j]=min(d[i][j],d[i-2][j-2]+1)
    return d[n][m]

def keydist(a,b):
    a,b=a.lower(),b.lower()
    if a==b:return 0.0
    if a not in POS or b not in POS:return 2.0
    x,y=POS[a]; u,v=POS[b]
    return min(2.0,math.hypot(x-u,y-v))

def spatial(a,b):
    cost=0.0;i=0;n=min(len(a),len(b))
    while i<n:
        if a[i].lower()==b[i].lower():i+=1;continue
        if i+1<n and a[i].lower()==b[i+1].lower() and a[i+1].lower()==b[i].lower():cost+=.3;i+=2;continue
        cost+=keydist(a[i],b[i]);i+=1
    return cost+abs(len(a)-len(b))*.5

RAW,DISPLAY=load_dict(); BIGRAM=load_bigrams()
ASSET={x.strip().lower() for x in (ROOT/"app/src/main/assets/ime/dict/protected_forms.txt").read_text().splitlines() if x.strip()}
PROTECTED=BUILTIN|ASSET

def retrieve(t):
    q=t.lower(); edits=[]
    for w,f in RAW.items():
        if abs(len(w)-len(q))>2:continue
        d=osa(q,w)
        if d<=2:edits.append((w,d,f))
    edits.sort(key=lambda x:(x[1],-x[2]));edits=edits[:50]
    prefixes=sorted(((w,f) for w,f in RAW.items() if w.startswith(q)),key=lambda x:-x[1])[:10]
    merged=[];seen=set(); em={w for w,_,_ in edits}
    for w,d,f in edits+[(w,0,f) for w,f in prefixes]:
        if w in seen:continue
        seen.add(w);merged.append((w,d,f,"edit" if w in em else "prefix"))
    return edits,prefixes,merged

def score(t,w,d,f,baseline):
    lo=t.lower(); c=w.lower(); components={"edit":float(d),"spatial":spatial(lo,c),"contextual":0.0,"bigram":0.0,"missing_bigram":0.0,"apostrophe":0.0,"exact":0.0,"user_boost":0.0,"frequency":-math.log(f+1.0)*.1}
    if baseline and (lo in PROTECTED or c in ANTI.get(lo,set())): return float("inf"),{"policy_cull":"protected" if lo in PROTECTED else "anti-correction"}
    na,nb=lo.replace("'",""),c.replace("'","")
    if "'" in c:
        if na==nb:components["apostrophe"]=-20.0
        elif spatial(na,nb)<2.0 and math.log(f+1.0)>=8.5:components["apostrophe"]=-10.0
    if d==0 and spatial(lo,c)==0:components["exact"]=-100.0
    return sum(components.values()),components

def blockers(t,c,valid,is_edit,anti,protected):
    out=[]; change=c!=t; casing=c.lower()==t.lower()
    if not change:out.append("NO_CHANGE")
    if valid and not casing:out.append("VALID_WORD_IMMUNITY")
    if anti:out.append("ANTI_CORRECTION")
    if protected:out.append("PROTECTED_VOCAB")
    if change and any(ch.isdigit() for ch in t):out.append("NUMERIC_TOKEN")
    if len(t)<2 and not casing:out.append("TOO_SHORT")
    if not is_edit and not casing:out.append("NOT_A_CORRECTION")
    return out

def classification(t):
    lo=t.lower(); digits=any(c.isdigit() for c in t); letters=any(c.isalpha() for c in t)
    if lo in PROTECTED:return "protected vocabulary"
    if digits and not letters:return "numeric"
    if digits and letters:return "mixed alphanumeric (runtime collapses to NUMERIC_TOKEN)"
    if re.search(r"[^A-Za-z']",t):return "identifier/code-like"
    return "ordinary word"

def trace(t,baseline):
    q=t.lower(); edits,prefixes,merged=retrieve(t)
    filtered=[x for x in merged if baseline or x[0] not in ANTI.get(q,set())]
    ranked=[]
    for w,d,f,src in filtered:
        s,parts=score(t,w,d,f,baseline)
        ranked.append({"candidate":DISPLAY.get(w,w),"normalized":w,"source":src,"edit_distance":d,"keyboard_distance":spatial(q,w),"raw_frequency":f,"ln_frequency":math.log(f+1.0),"contextual_evidence":parts.get("contextual",0.0),"contraction_rules":"none (dictionary apostrophe candidate)" if "'" in w else "none","score_components":parts,"final_score":s})
    ranked.sort(key=lambda x:x["final_score"])
    valid=any(x["normalized"]==q for x in ranked)
    if not any(x["normalized"]==q for x in ranked):
        ranked.insert(0,{"candidate":t,"normalized":q,"source":"typed-token UI insertion","edit_distance":None,"keyboard_distance":0.0,"raw_frequency":RAW.get(q,0),"ln_frequency":math.log(RAW.get(q,0)+1.0),"contextual_evidence":0.0,"contraction_rules":"none","score_components":{},"final_score":None})
    for x in ranked:
        anti=x["normalized"] in ANTI.get(q,set()); is_edit=x["source"]=="edit"
        x["commit_policy"]={"allowed":not blockers(t,x["candidate"],valid,is_edit,anti,q in PROTECTED),"reasons":blockers(t,x["candidate"],valid,is_edit,anti,q in PROTECTED)}
    seg=[]
    if q.isalpha() and len(q)>=4 and q not in RAW:
        for i in range(2,len(q)-1):
            a,b=q[:i],q[i:];freq=BIGRAM.get((a,b),0)
            seg.append({"split":a+" "+b,"left_is_word":a in RAW,"right_is_word":b in RAW,"bigram_frequency":freq,"survives":a in RAW and b in RAW and freq>0})
    survivors=[x for x in seg if x["survives"]]
    return {"revision":REV,"raw":t,"normalized":q,"classification":classification(t),"runtime_digit_guard":any(c.isdigit() for c in t),"protected_membership":{"member":q in PROTECTED,"source":"built-in" if q in BUILTIN else ("protected_forms.txt" if q in ASSET else None)},"anti_correction_pairs":sorted(ANTI.get(q,set())),"contraction_fast_path":SHORTCUTS.get(q),"fallback_entered":False,"fallback_basis":"assumption only: this offline replay cannot determine live engine mode","retrieved_edit":[{"candidate":DISPLAY.get(w,w),"distance":d,"frequency":f} for w,d,f in edits],"retrieved_prefix":[{"candidate":DISPLAY.get(w,w),"distance":0,"frequency":f} for w,f in prefixes],"displayed_order":ranked,"segmentation":{"proposals":seg,"accepted":survivors[0]["split"] if len(survivors)==1 else None,"survivor_count":len(survivors)}}

traces={t:trace(t,REV=="baseline") for t in TOKENS}
if len(sys.argv)>3 and sys.argv[3]=="diagnosis":
    targets={"las":"last","sugg3stions":"suggestions","correc5ed":"corrected","5his":"this","thatbsuggestiins":"that suggestions","inthe":"in the","teh":"the","742":"742","PS2":"PS2","v2":"v2","3.14":"3.14","zsh":"zsh"}
    out={}
    for token,x in traces.items():
        ranked=x["displayed_order"]
        target=targets[token].lower()
        hit=next((c for c in ranked if c["normalized"]==target),None)
        out[token]={"class":x["classification"],"protected":x["protected_membership"],"anti":x["anti_correction_pairs"],"contraction":x["contraction_fast_path"],"fallback":x["fallback_entered"],"edit_count":len(x["retrieved_edit"]),"prefix_count":len(x["retrieved_prefix"]),"edit_candidates":[f"{c['candidate']}:{c['distance']}:{c['frequency']}" for c in x["retrieved_edit"]],"top5":[f"{c['candidate']}:{c['final_score']}:{','.join(c['commit_policy']['reasons']) or 'ALLOW'}" for c in ranked[:5]],"target":None if hit is None else {"rank":ranked.index(hit)+1,"score":hit["final_score"],"edit":hit["edit_distance"],"kbd":hit["keyboard_distance"],"lnfreq":hit["ln_frequency"],"ctx":hit["contextual_evidence"],"apostrophe":hit["score_components"].get("apostrophe",0.0),"gate":hit["commit_policy"]},"segmentation":x["segmentation"]}
    print(json.dumps(out,indent=2,allow_nan=True))
elif len(sys.argv)>3 and sys.argv[3]=="compact":
    compact={}
    for token,x in traces.items():
        compact[token]={
            "meta":{k:x[k] for k in ["raw","normalized","classification","runtime_digit_guard","protected_membership","anti_correction_pairs","contraction_fast_path","fallback_entered"]},
            "edit":[f"{c['candidate']}|d={c['distance']}|f={c['frequency']}" for c in x["retrieved_edit"]],
            "prefix":[f"{c['candidate']}|d=0|f={c['frequency']}" for c in x["retrieved_prefix"]],
            "ranked":[f"{c['candidate']}|{c['source']}|d={c['edit_distance']}|kbd={c['keyboard_distance']:.3f}|lnf={c['ln_frequency']:.3f}|ctx={c['contextual_evidence']:.1f}|apos={c['score_components'].get('apostrophe',0.0)}|score={c['final_score']}|gate={','.join(c['commit_policy']['reasons']) or 'ALLOW'}" for c in x["displayed_order"]],
            "segmentation":x["segmentation"],
        }
    print(json.dumps(compact,indent=2,allow_nan=True))
else:
    print(json.dumps(traces,indent=2,allow_nan=True))
