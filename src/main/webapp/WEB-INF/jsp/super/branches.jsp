<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Branches" active="branches">
    <h1 class="h3 page-title mb-3">Engineering branches</h1>
    <div class="card mb-4"><div class="card-body">
        <form class="row g-2" method="post" action="<c:url value='/super/branches'/>">
            <t:csrf/>
            <div class="col-md-5"><input class="form-control" name="name" placeholder="Branch name, e.g. Computer Engineering" required maxlength="120"></div>
            <div class="col-md-3"><input class="form-control" name="code" placeholder="Code, e.g. COMP" required maxlength="40"></div>
            <div class="col-md-2"><button class="btn btn-primary" type="submit">Add branch</button></div>
        </form></div></div>
    <div class="card"><div class="table-responsive"><table class="table table-hover mb-0">
        <thead><tr><th>Branch</th><th>Subjects</th><th>Batches</th><th>Teachers</th><th>Students</th><th>Status</th><th class="text-end">Actions</th></tr></thead>
        <tbody>
        <c:forEach items="${stats}" var="b"><tr>
            <td class="fw-semibold"><c:out value="${b.name}"/> <code><c:out value="${b.code}"/></code></td>
            <td>${b.subjects}</td><td>${b.batches}</td><td>${b.teachers}</td><td>${b.students}</td>
            <td><span class="badge ${b.active ? 'text-bg-success' : 'text-bg-secondary'}">${b.active ? 'Active' : 'Inactive'}</span></td>
            <td class="text-end text-nowrap">
                <a class="btn btn-sm btn-outline-primary" href="<c:url value='/super/branches/${b.id}'/>">Assign subjects, batches, teachers</a>
                <form class="d-inline" method="post" action="<c:url value='/super/branches/${b.id}/active'/>"><t:csrf/><input type="hidden" name="active" value="${not b.active}">
                    <button class="btn btn-sm btn-outline-${b.active ? 'danger' : 'success'}" type="submit">${b.active ? 'Deactivate' : 'Activate'}</button></form>
            </td></tr></c:forEach>
        <c:if test="${empty stats}"><tr><td colspan="7" class="text-center text-muted py-4">No branches yet.</td></tr></c:if>
        </tbody></table></div></div>
</t:layout>
