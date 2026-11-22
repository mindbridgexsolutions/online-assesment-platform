<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Admin dashboard" active="dashboard">
    <h1 class="h3 page-title mb-4">Administration</h1>
    <div class="row g-3 mb-4">
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body">
            <div class="text-muted small">Active students</div><div class="stat-value"><c:out value="${students}"/></div></div></div></div>
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body">
            <div class="text-muted small">Active teachers</div><div class="stat-value"><c:out value="${teachers}"/></div></div></div></div>
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body">
            <div class="text-muted small">Batches / subjects</div><div class="stat-value"><c:out value="${batchCount}"/> / <c:out value="${subjectCount}"/></div></div></div></div>
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body">
            <div class="text-muted small">Questions in bank</div><div class="stat-value"><c:out value="${questionCount}"/></div></div></div></div>
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body">
            <div class="text-muted small">Published exams</div><div class="stat-value"><c:out value="${publishedExams}"/></div></div></div></div>
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body">
            <div class="text-muted small">Draft exams</div><div class="stat-value"><c:out value="${draftExams}"/></div></div></div></div>
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body">
            <div class="text-muted small">Attempts in progress now</div><div class="stat-value"><c:out value="${liveAttempts}"/></div></div></div></div>
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body">
            <div class="text-muted small">Awaiting manual grading</div><div class="stat-value"><c:out value="${pendingGrading}"/></div></div></div></div>
    </div>
    <div class="card">
        <div class="card-header">Quick actions</div>
        <div class="card-body d-flex flex-wrap gap-2">
            <a class="btn btn-primary" href="<c:url value='/admin/users/new'/>">Add user</a>
            <a class="btn btn-outline-primary" href="<c:url value='/admin/users/import'/>">Bulk upload students</a>
            <a class="btn btn-outline-primary" href="<c:url value='/admin/batches'/>">Manage batches</a>
            <a class="btn btn-outline-primary" href="<c:url value='/admin/subjects'/>">Manage subjects</a>
            <a class="btn btn-outline-primary" href="<c:url value='/admin/audit'/>">View audit log</a>
        </div>
    </div>
</t:layout>
