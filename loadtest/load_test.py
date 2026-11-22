#!/usr/bin/env python3
"""Load test: N students log in, start the exam at the same moment, autosave while answering, then submit.
Usage: python3 load_test.py [students=1000] [login_concurrency=100] [autosaves_per_student=6] [base_url]
Students must be named load1..loadN with password Demo@12345 and be assigned to an open exam."""
import re, sys, json, time, random, threading, http.cookiejar, urllib.request, urllib.parse, urllib.error
from concurrent.futures import ThreadPoolExecutor

N = int(sys.argv[1]) if len(sys.argv) > 1 else 1000
LC = int(sys.argv[2]) if len(sys.argv) > 2 else 100
SAVES = int(sys.argv[3]) if len(sys.argv) > 3 else 6
B = sys.argv[4] if len(sys.argv) > 4 else 'http://localhost:8080'
PW = 'Demo@12345'
lat = {}; errs = {}; lock = threading.Lock()

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k): return None

class C:
    def __init__(s):
        s.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), NoRedirect())
    def req(s, tag, method, path, data=None, h=None):
        h = dict(h or {}); body = None
        if isinstance(data, dict) and h.get('Content-Type') == 'application/json': body = json.dumps(data).encode()
        elif isinstance(data, dict): body = urllib.parse.urlencode(data).encode(); h['Content-Type'] = 'application/x-www-form-urlencoded'
        t = time.time()
        try:
            try: r = s.op.open(urllib.request.Request(B + path, data=body, method=method, headers=h), timeout=60)
            except urllib.error.HTTPError as e: r = e
            b = r.read().decode('utf8', 'replace'); st = r.status; loc = r.headers.get('Location')
        except Exception as e:
            st, b, loc = 0, str(e), None
        d = time.time() - t
        with lock:
            lat.setdefault(tag, []).append(d)
            if st >= 400 or st == 0: errs.setdefault((tag, st), []).append(b[:120])
        return st, b, loc

def csrf(b): return re.search(r'name="_csrf" (?:value|content)="([^"]+)"', b).group(1)

def login(i):
    c = C(); st, b, _ = c.req('GET /login', 'GET', '/login')
    st, b, loc = c.req('POST login', 'POST', '/auth/login', {'username': 'load%d' % i, 'password': PW, '_csrf': csrf(b)})
    return c, (st == 302)

def start(c):
    st, b, _ = c.req('GET dashboard', 'GET', '/student')
    m = re.search(r'/student/exams/(\d+)', b); eid = m.group(1)
    st, b, _ = c.req('GET instructions', 'GET', '/student/exams/' + eid)
    st, b, loc = c.req('POST start', 'POST', '/student/exams/%s/start' % eid, {'_csrf': csrf(b)})
    aid = re.search(r'/student/attempts/(\d+)', loc).group(1)
    st, b, _ = c.req('GET player', 'GET', '/student/attempts/' + aid)
    sess = re.search(r'data-session="([^"]+)"', b).group(1)
    qs = re.findall(r'data-qid="(\d+)" data-type="(\w+)"', b)
    ans = []
    for qid, typ in qs:
        sec = b[b.index('data-qid="%s"' % qid):]; sec = sec[:sec.index('</section>')]
        a = ''
        if typ in ('MCQ_SINGLE', 'TRUE_FALSE'):
            opts = re.findall(r'type="radio" name="q%s" value="([^"]+)"' % qid, sec); a = random.choice(opts) if opts else ''
        elif typ == 'MCQ_MULTI':
            opts = re.findall(r'type="checkbox" name="q%s" value="([^"]+)"' % qid, sec); a = json.dumps(random.sample(opts, min(2, len(opts))))
        elif typ == 'NUMERIC': a = '3.14'
        elif typ == 'MATCHING': a = json.dumps({l: l for l in re.findall(r'data-left="([^"]+)"', sec)})
        elif typ == 'ESSAY': a = 'Load test essay answer.'
        else: a = 'Paris'
        ans.append({'questionId': int(qid), 'answer': a, 'flagged': False})
    return aid, {'Content-Type': 'application/json', 'X-Player-Session': sess, 'X-XSRF-TOKEN': csrf(b)}, ans

def student(i, barrier, ready):
    c, ok = login(i)
    ready.append(ok); barrier.wait()          # everybody starts the exam in the same instant
    if not ok: return False
    aid, H, ans = start(c)
    for _ in range(SAVES):
        time.sleep(random.uniform(1, 4))
        k = random.randint(1, 3)
        c.req('autosave', 'POST', '/api/attempts/%s/answers' % aid, {'answers': random.sample(ans, min(k, len(ans)))}, H)
    st, b, _ = c.req('submit', 'POST', '/api/attempts/%s/submit' % aid, {'answers': ans}, H)
    c.req('GET results', 'GET', '/student/results')
    return st == 200

def pct(v, p): v = sorted(v); return v[min(len(v) - 1, int(len(v) * p))]

if __name__ == '__main__':
    barrier = threading.Barrier(N); ready = []
    t0 = time.time(); print('Starting %d students (login concurrency %d)...' % (N, LC))
    # logins are throttled by LC through a semaphore; the barrier then releases the start burst
    sem = threading.Semaphore(LC)
    def run(i):
        with sem: c, ok = login(i)
        ready.append(ok); barrier.wait()
        if not ok: return False
        aid, H, ans = start(c)
        for _ in range(SAVES):
            time.sleep(random.uniform(1, 4))
            c.req('autosave', 'POST', '/api/attempts/%s/answers' % aid, {'answers': random.sample(ans, min(random.randint(1, 3), len(ans)))}, H)
        st, b, _ = c.req('submit', 'POST', '/api/attempts/%s/submit' % aid, {'answers': ans}, H)
        c.req('GET results', 'GET', '/student/results')
        return st == 200
    def safe(i):
        try: return run(i)
        except threading.BrokenBarrierError: return False
        except Exception as e:
            with lock: errs.setdefault(('client', 0), []).append(repr(e)[:120])
            return False
    with ThreadPoolExecutor(N) as ex: res = list(ex.map(safe, range(1, N + 1)))
    print('\nFinished in %.0fs: %d/%d students completed OK\n' % (time.time() - t0, sum(res), N))
    print('%-18s %6s %8s %8s %8s %8s' % ('request', 'count', 'p50', 'p95', 'p99', 'max'))
    for k, v in sorted(lat.items()): print('%-18s %6d %7.2fs %7.2fs %7.2fs %7.2fs' % (k, len(v), pct(v, .5), pct(v, .95), pct(v, .99), max(v)))
    print('\nErrors:'); [print('  ', k, len(v), v[0]) for k, v in errs.items()] if errs else print('   none')
