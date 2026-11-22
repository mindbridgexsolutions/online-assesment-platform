<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Batches" active="batches">
    <div class="d-flex justify-content-between align-items-center mb-3">
        <h1 class="h3 page-title mb-0">Batches / classes</h1>
        <a class="btn btn-primary" href="<c:url value='/admin/batches/new'/>">New batch</a>
    </div>
    <div class="card"><div class="table-responsive">
        <table class="table table-hover mb-0">
            <thead><tr><th>Name</th><th>Code</th><th>Students</th><th>Status</th><th class="text-end">Actions</th></tr></thead>
            <tbody>
            <c:forEach items="${batches}" var="b">
                <tr>
                    <td class="fw-semibold"><a href="<c:url value='/admin/batches/${b.id}'/>"><c:out value="${b.name}"/></a>
                        <div class="small text-muted"><c:out value="${b.description}"/></div></td>
                    <td><code><c:out value="${b.code}"/></code></td>
                    <td><c:out value="${b.students}"/></td>
                    <td><span class="badge ${b.active ? 'text-bg-success' : 'text-bg-secondary'}">${b.active ? 'Active' : 'Inactive'}</span></td>
                    <td class="text-end text-nowrap">
                        <a class="btn btn-sm btn-outline-primary" href="<c:url value='/admin/batches/${b.id}'/>">Students</a>
                        <a class="btn btn-sm btn-outline-secondary" href="<c:url value='/admin/batches/${b.id}/edit'/>">Edit</a>
                        <form class="d-inline" method="post" action="<c:url value='/admin/batches/${b.id}/toggle-active'/>">
                            <t:csrf/><input type="hidden" name="active" value="${not b.active}">
                            <button class="btn btn-sm btn-outline-${b.active ? 'danger' : 'success'}" type="submit">${b.active ? 'Deactivate' : 'Activate'}</button>
                        </form>
                    </td>
                </tr>
            </c:forEach>
            <c:if test="${empty batches}"><tr><td colspan="5" class="text-center text-muted py-4">No batches yet. Create the first one.</td></tr></c:if>
            </tbody>
        </table>
    </div></div>
</t:layout>
