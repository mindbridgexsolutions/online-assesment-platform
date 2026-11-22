<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Teacher dashboard" active="dashboard">
    <h1 class="h3 page-title mb-4">Welcome, <c:out value="${currentUser.fullName}"/></h1>
    <div class="row g-3 mb-4">
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body"><div class="stat-value">${questionCount}</div><div class="stat-label">Questions in bank</div></div></div></div>
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body"><div class="stat-value">${publishedExams}</div><div class="stat-label">Published exams</div></div></div></div>
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body"><div class="stat-value">${draftExams}</div><div class="stat-label">Draft exams</div></div></div></div>
        <div class="col-6 col-lg-3"><a class="text-decoration-none" href="<c:url value='/teacher/grading'/>"><div class="card stat-card"><div class="card-body"><div class="stat-value">${pendingGrading}</div><div class="stat-label">Waiting for manual grading</div></div></div></a></div>
    </div>
    <div class="row g-3">
        <div class="col-lg-6"><div class="card h-100"><div class="card-body">
            <h2 class="h6">Your subjects</h2>
            <c:forEach items="${subjects}" var="s"><span class="badge text-bg-light border me-1 mb-1"><c:out value="${s.name}"/></span></c:forEach>
            <c:if test="${empty subjects}"><p class="text-muted mb-0">No subjects assigned yet. Ask an administrator to assign you to a subject.</p></c:if>
        </div></div></div>
        <div class="col-lg-6"><div class="card h-100"><div class="card-body">
            <h2 class="h6">Quick actions</h2>
            <div class="d-flex flex-wrap gap-2">
                <a class="btn btn-primary" href="<c:url value='/teacher/questions/new'/>">Add question</a>
                <a class="btn btn-outline-primary" href="<c:url value='/teacher/exams/new'/>">Create exam</a>
                <a class="btn btn-outline-secondary" href="<c:url value='/teacher/questions/import'/>">Import questions</a>
            </div>
        </div></div></div>
    </div>
</t:layout>
