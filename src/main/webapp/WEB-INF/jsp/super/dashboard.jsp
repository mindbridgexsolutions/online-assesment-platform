<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Super Admin" active="sdash">
    <h1 class="h3 page-title mb-1">Super Administrator</h1>
    <p class="text-muted">Overall coordination across all engineering branches.</p>
    <div class="row g-3 mb-4">
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body"><div class="text-muted small">Active users</div><div class="stat-value"><c:out value="${totals.users}"/></div></div></div></div>
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body"><div class="text-muted small">Teachers / students</div><div class="stat-value"><c:out value="${totals.teachers}"/> / <c:out value="${totals.students}"/></div></div></div></div>
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body"><div class="text-muted small">Branches</div><div class="stat-value"><c:out value="${totals.branches}"/></div></div></div></div>
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body"><div class="text-muted small">Published exams</div><div class="stat-value"><c:out value="${totals.exams}"/></div></div></div></div>
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body"><div class="text-muted small">Students in an exam now</div><div class="stat-value"><c:out value="${totals.liveAttempts}"/></div></div></div></div>
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body"><div class="text-muted small">Administrators</div><div class="stat-value"><c:out value="${totals.admins}"/></div></div></div></div>
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body"><div class="text-muted small">Rejected items</div><div class="stat-value"><c:out value="${totals.rejected}"/></div></div></div></div>
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body"><div class="text-muted small">Duplicate groups</div><div class="stat-value"><c:out value="${totals.duplicateGroups}"/></div></div></div></div>
    </div>
    <div class="card mb-4">
        <div class="card-header d-flex justify-content-between"><span>Branches</span><a href="<c:url value='/super/branches'/>">Manage</a></div>
        <div class="table-responsive"><table class="table mb-0">
            <thead><tr><th>Branch</th><th>Subjects</th><th>Batches</th><th>Teachers</th><th>Students</th><th>Exams</th><th>Submissions</th></tr></thead>
            <tbody>
            <c:forEach items="${stats}" var="b"><tr>
                <td><a href="<c:url value='/super/branches/${b.id}'/>"><c:out value="${b.name}"/></a> <code><c:out value="${b.code}"/></code></td>
                <td>${b.subjects}</td><td>${b.batches}</td><td>${b.teachers}</td><td>${b.students}</td><td>${b.exams}</td><td>${b.attempts}</td></tr></c:forEach>
            <c:if test="${empty stats}"><tr><td colspan="7" class="text-center text-muted py-3">No branches yet. Add your first branch.</td></tr></c:if>
            </tbody></table></div>
    </div>
    <div class="card"><div class="card-header">Quick actions</div><div class="card-body d-flex flex-wrap gap-2">
        <a class="btn btn-primary" href="<c:url value='/admin/users/new'/>">Add user or administrator</a>
        <a class="btn btn-outline-primary" href="<c:url value='/super/live'/>">Correct a live exam</a>
        <a class="btn btn-outline-primary" href="<c:url value='/super/overrides'/>">Approve / reject</a>
        <a class="btn btn-outline-primary" href="<c:url value='/admin/duplicates'/>">Clean duplicates</a>
        <a class="btn btn-outline-primary" href="<c:url value='/reports/outcomes'/>">CO-PO attainment</a>
        <a class="btn btn-outline-primary" href="<c:url value='/admin/audit'/>">Audit log</a>
    </div></div>
</t:layout>
