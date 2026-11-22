import csv,collections,sys
r=collections.defaultdict(list); bad=collections.Counter()
for x in csv.DictReader(open(sys.argv[1])):
    r[x['label']].append(int(x['elapsed']))
    if x['success']!='true': bad[(x['label'],x['responseCode'])]+=1
def p(v,q): v=sorted(v); return v[min(len(v)-1,int(len(v)*q))]
print('%-26s %5s %6s %6s %6s %6s  (ms)'%('request','n','avg','p95','p99','max'))
for k,v in sorted(r.items()): print('%-26s %5d %6d %6d %6d %6d'%(k,len(v),sum(v)/len(v),p(v,.95),p(v,.99),max(v)))
print('failures:',dict(bad) or 'none')
