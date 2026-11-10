#!/usr/bin/env python3
"""Breeding-progression simulation: mirrors BreedingEngine (parent selection, drift), the blight roll and growth
chance, for a player who keeps the best two of each generation on one or more grafting frames.
Times assume the default randomTickSpeed (one random tick on a block every ~68 s).

Run: python3 tools/data/sim_breeding.py   (keep its formulas in step with BreedingEngine when tuning)
"""
import random, statistics as st, sys
MAX=10; RT_SEC=68.3
def expr(p): return max(p)
def drift(a, m):
    if random.random() >= 0.25 + 0.02*m: return a
    up = min(0.9, max(0.2, 0.75 - 0.05*(a-1) + 0.02*(m-1)))
    return max(1,min(MAX, a + (1 if random.random() < up else -1)))
def child(a,b):
    out={}
    for s in a:
        fa=drift(random.choice(a[s]), expr(a['vig'])); fb=drift(random.choice(b[s]), expr(b['vig']))
        out[s]=(max(fa,fb),min(fa,fb))
    return out
def score(g): return sum(expr(g[s]) for s in g)
def geom(p):
    n=1
    while random.random()>=p: n+=1
    return n
def grow_rt(g, stages=7):
    s=expr(g['str']); p=(1-(MAX-(MAX+s)//2)/MAX)*min(1,0.75+expr(g['gro'])*0.025)
    return sum(geom(p) for _ in range(stages))
def passes(g): return random.random() < min(1, 0.5 + 0.05*expr(g['vig']))
def cross_rt(a,b, spread=0.1):
    t=0
    while True:
        t+=1
        if random.random()<0.5: continue
        pa,pb=passes(a),passes(b)
        if pa and pb: return t,'cross'
        if (pa or pb) and random.random()<spread: return t,'clone'
def run(frames=1):
    base={'gain':(1,1),'gro':(1,1),'str':(1,1),'vig':(1,1)}
    A=dict(base); B=dict(base); t=grow_rt(A); milestones={}
    while not all(expr(A[s])==MAX for s in A):
        # several frames work in parallel: take the fastest of `frames` results each round
        results=[]
        for _ in range(frames):
            dt,k=cross_rt(A,B); c=dict(random.choice([A,B])) if k=='clone' else child(A,B)
            results.append((dt+grow_rt(c), c))
        dt=max(r[0] for r in results); t+=dt
        pool=sorted([A,B]+[r[1] for r in results], key=score, reverse=True); A,B=pool[0],pool[1]
        for lvl in (5,8,10):
            if lvl not in milestones and min(expr(A[s]) for s in A)>=lvl: milestones[lvl]=t
    return milestones
hrs=lambda rt: rt*RT_SEC/3600
random.seed(2)
for frames in (1,4):
    res=[run(frames) for _ in range(150)]
    print(f"{frames} frame(s): all stats 5: {hrs(st.median(r[5] for r in res)):.1f} h, all 8: {hrs(st.median(r[8] for r in res)):.1f} h, all 10: {hrs(st.median(r[10] for r in res)):.1f} h (p10 {hrs(sorted(r[10] for r in res)[15]):.1f}, p90 {hrs(sorted(r[10] for r in res)[135]):.1f})")
for v in (1,5,10):
    g={'gain':(1,1),'gro':(1,1),'str':(1,1),'vig':(v,v)}
    w=[]
    for _ in range(3000):
        t=0
        while True:
            dt,k=cross_rt(g,g); t+=dt
            if k=='cross': break
        w.append(t)
    print(f"vigour {v}: real cross every {hrs(st.mean(w))*60:.0f} min on one frame; a 25% mutation ~{hrs(st.mean(w))*60/0.25:.0f} min, 5% (diamond) ~{hrs(st.mean(w))/0.05:.1f} h")
