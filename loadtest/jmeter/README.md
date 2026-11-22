# JMeter load test - exam day

Simulates students: login -> dashboard -> open exam -> start -> player -> 4 autosaves -> submit -> results.

## Easiest way (defaults built in: 172.16.100.66:8080, 50 students, 30 s ramp-up)
- Windows : double-click `run_load_test.bat`  (set JMETER_HOME inside it if JMeter is not in C:\jmeter\apache-jmeter-5.6.3)
- Linux/Mac: `./run_load_test.sh <examId> [students] [rampupSeconds]`
It asks for the TEST exam id, asks you to type YES, runs without the JMeter window and writes
`results-<time>/report/index.html` (open in a browser). Nothing else to install except Java 17+ and JMeter 5.6.x.

## Before you run
- Test students `load1..loadN` (password Demo@12345) assigned to an OPEN, PUBLISHED test exam, not attempted yet
  (each student can attempt once -> copy the exam for every run). Exam must have no IP restriction.
- The plan answers question id 3 (single-choice). Change `"questionId":3` and `q3` in steps 06-08 to a real question of your test exam.
- App must run with COOKIE_SECURE=false on plain http. Run JMeter from a different computer on the same network.
- Never run during a real exam or against real students.
