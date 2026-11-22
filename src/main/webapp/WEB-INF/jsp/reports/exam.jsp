<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<c:set var="e" value="${s.exam}"/>
<t:layout title="Report: ${e.title}" active="${currentUser.admin ? 'reports' : 'exams'}">
    <div class="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-3">
        <div><h1 class="h3 page-title mb-0"><c:out value="${e.title}"/></h1>
            <div class="text-muted"><c:out value="${e.subject.name}"/> &middot; total <c:out value="${fmt.num(s.totalMarks)}"/> marks &middot; pass at <c:out value="${fmt.num(e.passingMarks)}"/></div></div>
        <div class="d-flex flex-wrap gap-2 no-print">
            <c:if test="${e.resultPolicy == 'MANUAL' and e.status != 'DRAFT' and not currentUser.admin}"><form method="post" action="<c:url value='/teacher/exams/${e.id}/release'/>"><t:csrf/><input type="hidden" name="released" value="${!e.resultsReleased}"><input type="hidden" name="back" value="report">
                <button class="btn ${e.resultsReleased ? 'btn-success' : 'btn-warning'}" type="submit" data-confirm="${e.resultsReleased ? 'Hide the results from students again?' : 'Show results to all students of this exam now?'}">${e.resultsReleased ? 'Results released - click to hide' : 'Release results to students'}</button></form></c:if>
            <a class="btn btn-outline-primary" href="<c:url value='/reports/exams/${e.id}/export.xlsx'/>">Excel</a>
            <a class="btn btn-outline-primary" href="<c:url value='/reports/exams/${e.id}/export.pdf'/>">PDF</a>
            <a class="btn btn-primary" href="<c:url value='/reports/exams/${e.id}/marks-sheet.xlsx'/>" title="Every student's marks on every question, with CO / Bloom's level and CO attainment (NBA format)">Marks sheet (Excel)</a>
            <a class="btn btn-primary" href="<c:url value='/reports/exams/${e.id}/marks-sheet.pdf'/>">Marks sheet (PDF)</a>
            <button class="btn btn-outline-secondary" type="button" data-print>Print</button>
            <c:if test="${s.inProgress > 0}">
                <form method="post" action="<c:url value='/reports/exams/${e.id}/close-attempts'/>"><t:csrf/>
                    <button class="btn btn-outline-danger" type="submit" data-confirm="Submit and grade all ${s.inProgress} running attempt(s) now?">Close ${s.inProgress} running</button></form>
            </c:if>
        </div>
    </div>
    <div class="row g-3 mb-4">
        <div class="col-6 col-lg-2"><div class="card stat-card"><div class="card-body"><div class="stat-value">${s.attempted}<span class="fs-6 text-muted">/${s.assigned}</span></div><div class="stat-label">Students attempted</div></div></div></div>
        <div class="col-6 col-lg-2"><div class="card stat-card"><div class="card-body"><div class="stat-value"><c:out value="${fmt.pct(s.average)}"/></div><div class="stat-label">Average</div></div></div></div>
        <div class="col-6 col-lg-2"><div class="card stat-card"><div class="card-body"><div class="stat-value"><c:out value="${fmt.num(s.highest)}"/></div><div class="stat-label">Highest</div></div></div></div>
        <div class="col-6 col-lg-2"><div class="card stat-card"><div class="card-body"><div class="stat-value"><c:out value="${fmt.num(s.lowest)}"/></div><div class="stat-label">Lowest</div></div></div></div>
        <div class="col-6 col-lg-2"><div class="card stat-card"><div class="card-body"><div class="stat-value"><c:out value="${fmt.pct(s.passRate)}"/></div><div class="stat-label">Pass rate</div></div></div></div>
        <div class="col-6 col-lg-2"><div class="card stat-card"><div class="card-body"><div class="stat-value">${s.pendingManual}</div><div class="stat-label">Awaiting manual grading</div></div></div></div>
    </div>

    <div class="card mb-4"><div class="card-body">
        <h2 class="h6">Score distribution (best attempt per student)</h2>
        <c:set var="maxBucket" value="1"/>
        <c:forEach items="${s.buckets}" var="b"><c:if test="${b > maxBucket}"><c:set var="maxBucket" value="${b}"/></c:if></c:forEach>
        <div class="d-flex align-items-end gap-2" style="height:140px" role="img" aria-label="Score distribution histogram">
            <c:forEach items="${s.buckets}" var="b" varStatus="st">
                <div class="flex-fill text-center">
                    <div class="small text-muted">${b}</div>
                    <div class="bg-primary rounded-top" style="height:${b * 100 / maxBucket}px;min-height:2px"></div>
                    <div class="small text-muted">${st.index * 10}${st.last ? '-100' : '+'}</div>
                </div>
            </c:forEach>
        </div>
    </div></div>

    <div class="card mb-4"><div class="card-header bg-white fw-semibold">Student results</div><div class="table-responsive">
        <table class="table table-sm align-middle mb-0" id="results-table">
            <thead><tr><th>Student</th><th>Roll no</th><th>Attempt</th><th>Status</th><th>Submitted</th><th>Score</th><th>%</th><th>Result</th><th title="Tab / window switches">Focus</th><th></th></tr></thead>
            <tbody>
            <c:forEach items="${s.rows}" var="r">
                <tr><td><c:out value="${r.studentName}"/></td><td><c:out value="${r.rollNo}"/></td><td>#${r.attemptNo}</td>
                    <td class="small"><c:out value="${r.status.label}"/></td><td class="small"><c:out value="${fmt.dateTime(r.submittedAt)}"/></td>
                    <td><c:choose><c:when test="${r.graded}"><c:out value="${fmt.num(r.score)}"/> / <c:out value="${fmt.num(r.maxScore)}"/></c:when><c:otherwise>-</c:otherwise></c:choose></td>
                    <td><c:out value="${r.graded ? fmt.num(r.percentage) : '-'}"/></td>
                    <td><c:choose><c:when test="${not r.graded}">-</c:when>
                        <c:when test="${r.pendingManual > 0}"><span class="badge text-bg-warning">Pending</span></c:when>
                        <c:when test="${r.passed}"><span class="badge text-bg-success">Pass</span></c:when>
                        <c:otherwise><span class="badge text-bg-danger">Fail</span></c:otherwise></c:choose></td>
                    <td class="${r.focusLosses > 2 ? 'text-danger fw-semibold' : ''}">${r.focusLosses}</td>
                    <td class="text-end text-nowrap no-print">
                        <c:if test="${r.pendingManual > 0 and currentUser.teacher}"><a class="btn btn-sm btn-outline-warning" href="<c:url value='/teacher/grading/${r.attemptId}'/>">Grade</a></c:if>
                        <c:if test="${r.graded}"><a class="btn btn-sm btn-outline-secondary" href="<c:url value='/reports/attempts/${r.attemptId}/scorecard.pdf'/>">Scorecard</a></c:if>
                    </td></tr>
            </c:forEach>
            <c:if test="${empty s.rows}"><tr><td colspan="10" class="text-center text-muted py-4">No attempts yet.</td></tr></c:if>
            </tbody>
        </table>
    </div></div>

    <div class="card"><div class="card-header bg-white fw-semibold">Question analysis</div><div class="table-responsive">
        <table class="table table-sm align-middle mb-0">
            <thead><tr><th style="width:42%">Question</th><th>Type</th><th class="text-end">Attempts</th><th class="text-end">Answered</th><th class="text-end">Correct</th><th class="text-end">Avg marks</th><th>Difficulty</th><th class="no-print"></th></tr></thead>
            <tbody>
            <c:forEach items="${s.stats}" var="q">
                <tr><td><div class="text-truncate" style="max-width:420px"><c:out value="${q.text}"/></div></td>
                    <td class="small"><c:out value="${q.type.label}"/></td>
                    <td class="text-end">${q.attempts}</td><td class="text-end"><c:out value="${fmt.pct(q.percentAnswered())}"/></td>
                    <td class="text-end"><c:out value="${fmt.pct(q.percentCorrect())}"/></td>
                    <td class="text-end"><c:out value="${fmt.num(q.avgMarksRounded())}"/> / <c:out value="${fmt.num(q.maxMarks)}"/></td>
                    <td><span class="badge text-bg-${q.band() == 'Easy' ? 'success' : (q.band() == 'Hard' ? 'danger' : 'warning')}"><c:out value="${q.band()}"/></span></td>
                    <td class="text-end no-print"><c:if test="${currentUser.teacher}"><form method="post" action="<c:url value='/reports/exams/${e.id}/regrade/${q.sourceQuestionId}'/>"><t:csrf/>
                        <button class="btn btn-sm btn-outline-secondary" type="submit" data-confirm="Re-apply the current answer key from the question bank to all attempts of this exam and regrade them?">Regrade</button></form></c:if></td></tr>
            </c:forEach>
            <c:if test="${empty s.stats}"><tr><td colspan="8" class="text-center text-muted py-4">Statistics appear after attempts are graded.</td></tr></c:if>
            </tbody>
        </table>
    </div></div>
</t:layout>
