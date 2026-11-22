<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Subjects" active="subjects">
    <div class="d-flex justify-content-between align-items-center mb-3">
        <h1 class="h3 page-title mb-0">Subjects</h1>
        <a class="btn btn-primary" href="<c:url value='/admin/subjects/new'/>">New subject</a>
    </div>
    <div class="card"><div class="table-responsive">
        <table class="table table-hover mb-0">
            <thead><tr><th>Subject</th><th>Code</th><th>Assigned teachers</th><th>Status</th><th class="text-end">Actions</th></tr></thead>
            <tbody>
            <c:forEach items="${subjects}" var="s">
                <tr>
                    <td class="fw-semibold"><c:out value="${s.name}"/></td>
                    <td><code><c:out value="${s.code}"/></code></td>
                    <td>
                        <c:forEach items="${s.teachers}" var="t"><span class="badge text-bg-light border me-1"><c:out value="${t}"/></span></c:forEach>
                        <c:if test="${empty s.teachers}"><span class="text-muted small">none - assign a teacher</span></c:if>
                    </td>
                    <td><span class="badge ${s.active ? 'text-bg-success' : 'text-bg-secondary'}">${s.active ? 'Active' : 'Inactive'}</span></td>
                    <td class="text-end text-nowrap">
                        <a class="btn btn-sm btn-outline-primary" href="<c:url value='/admin/subjects/${s.id}/edit'/>">Edit / assign teachers</a>
                        <form class="d-inline" method="post" action="<c:url value='/admin/subjects/${s.id}/toggle-active'/>">
                            <t:csrf/><input type="hidden" name="active" value="${not s.active}">
                            <button class="btn btn-sm btn-outline-${s.active ? 'danger' : 'success'}" type="submit">${s.active ? 'Deactivate' : 'Activate'}</button>
                        </form>
                    </td>
                </tr>
            </c:forEach>
            <c:if test="${empty subjects}"><tr><td colspan="5" class="text-center text-muted py-4">No subjects yet.</td></tr></c:if>
            </tbody>
        </table>
    </div></div>
</t:layout>
