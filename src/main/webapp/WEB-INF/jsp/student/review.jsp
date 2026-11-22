<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Result" active="results">
    <div class="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-2">
        <div><h1 class="h3 page-title mb-0"><c:out value="${r.exam.title}"/></h1>
            <div class="text-muted small">Attempt #${r.attempt.attemptNo} &middot; submitted <c:out value="${fmt.dateTime(r.attempt.submittedAt)}"/>
                <c:if test="${not empty r.attempt.submitReason and r.attempt.submitReason != 'STUDENT'}"> &middot; <c:out value="${r.attempt.submitReason.label}"/></c:if></div></div>
        <div class="d-flex gap-2 no-print"><button class="btn btn-outline-secondary" type="button" data-print>Print</button>
            <a class="btn btn-outline-primary" href="<c:url value='/student/results'/>">All results</a></div>
    </div>
    <div class="card mb-4"><div class="card-body d-flex flex-wrap align-items-center gap-4">
        <div><div class="display-6 fw-bold"><c:out value="${fmt.num(r.grade.totalScore)}"/> <span class="fs-5 text-muted">/ <c:out value="${fmt.num(r.grade.maxScore)}"/></span></div>
            <div class="text-muted"><c:out value="${fmt.pct(r.grade.percentage)}"/></div></div>
        <div><c:choose>
            <c:when test="${r.grade.pendingManual > 0}"><span class="badge text-bg-warning fs-6">Partly graded &mdash; ${r.grade.pendingManual} answer(s) awaiting teacher</span></c:when>
            <c:when test="${r.grade.passed}"><span class="badge text-bg-success fs-6">Passed</span></c:when>
            <c:otherwise><span class="badge text-bg-danger fs-6">Not passed</span></c:otherwise></c:choose>
            <div class="small text-muted mt-1">Passing marks: <c:out value="${fmt.num(r.exam.passingMarks)}"/></div></div>
    </div></div>
    <div class="card mb-4"><div class="card-body">
        <h2 class="h6 text-uppercase text-muted mb-3">Summary</h2>
        <div class="row g-3 text-center">
            <div class="col-6 col-md-3"><div class="fs-3 fw-bold">${r.summary.total}</div><div class="text-muted small">Total questions</div></div>
            <div class="col-6 col-md-3"><div class="fs-3 fw-bold">${r.summary.attempted}</div><div class="text-muted small">Attempted</div></div>
            <div class="col-6 col-md-3"><div class="fs-3 fw-bold">${r.summary.notAttempted}</div><div class="text-muted small">Not attempted</div></div>
            <div class="col-6 col-md-3"><div class="fs-3 fw-bold"><c:out value="${fmt.num(r.grade.totalScore)}"/> / <c:out value="${fmt.num(r.grade.maxScore)}"/></div><div class="text-muted small">Marks obtained</div></div>
        </div>
        <hr>
        <div class="d-flex flex-wrap gap-4 small">
            <span><span class="badge text-bg-success">${r.summary.correct}</span> Correct</span>
            <span><span class="badge text-bg-info">${r.summary.partly}</span> Partly correct</span>
            <span><span class="badge text-bg-danger">${r.summary.incorrect}</span> Incorrect</span>
            <c:if test="${r.summary.awaiting > 0}"><span><span class="badge text-bg-warning">${r.summary.awaiting}</span> Awaiting grading</span></c:if>
        </div>
    </div></div>
</t:layout>
