<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Upload result" active="users">
    <h1 class="h3 page-title mb-3">Upload result</h1>
    <div class="row g-3 mb-3">
        <div class="col-md-4"><div class="card stat-card"><div class="card-body"><div class="text-muted small">Rows read</div><div class="stat-value"><c:out value="${result.totalRows}"/></div></div></div></div>
        <div class="col-md-4"><div class="card stat-card"><div class="card-body"><div class="text-muted small">Accounts created</div><div class="stat-value text-success"><c:out value="${result.created.size()}"/></div></div></div></div>
        <div class="col-md-4"><div class="card stat-card"><div class="card-body"><div class="text-muted small">Rows rejected</div><div class="stat-value ${result.errors.size() > 0 ? 'text-danger' : ''}"><c:out value="${result.errors.size()}"/></div></div></div></div>
    </div>

    <c:if test="${not empty result.errors}">
        <div class="card mb-3">
            <div class="card-header text-danger">Rejected rows (fix these in your file and upload them again)</div>
            <div class="table-responsive"><table class="table table-sm mb-0">
                <thead><tr><th style="width:6rem">Row</th><th>Problem</th></tr></thead>
                <tbody>
                <c:forEach items="${result.errors}" var="e"><tr><td><c:out value="${e.row}"/></td><td><c:out value="${e.message}"/></td></tr></c:forEach>
                </tbody>
            </table></div>
        </div>
    </c:if>

    <c:if test="${not empty result.created}">
        <div class="card">
            <div class="card-header d-flex justify-content-between align-items-center">
                <span>Sign-in details of the new accounts</span>
                <span class="d-flex gap-2 no-print">
                    <button type="button" class="btn btn-sm btn-outline-primary" data-csv-from="#credTable" data-csv-name="credentials.csv">Download CSV</button>
                    <button type="button" class="btn btn-sm btn-outline-secondary" data-print>Print</button>
                </span>
            </div>
            <div class="alert alert-warning mx-3 mt-3 mb-0 py-2 small">Passwords are shown <strong>only on this page</strong> and are not stored in readable form. Download or print them now. Students must change the password at first sign-in.</div>
            <div class="table-responsive"><table class="table table-sm mb-0" id="credTable">
                <thead><tr><th>Username</th><th>Name</th><th>Password</th></tr></thead>
                <tbody>
                <c:forEach items="${result.created}" var="c">
                    <tr><td><c:out value="${c.username}"/></td><td><c:out value="${c.fullName}"/></td><td><code><c:out value="${c.password}"/></code></td></tr>
                </c:forEach>
                </tbody>
            </table></div>
        </div>
    </c:if>
    <div class="mt-3"><a class="btn btn-primary" href="<c:url value='/admin/users'/>">Back to users</a>
        <a class="btn btn-outline-secondary" href="<c:url value='/admin/users/import'/>">Upload another file</a></div>
</t:layout>
