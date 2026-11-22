<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Programme outcomes" active="outcomes">
    <h1 class="h3 page-title mb-1">Programme Outcomes (PO) and PSOs</h1>
    <p class="text-muted">Teachers define Course Outcomes (CO) for their subjects and map each CO to these POs. A PO with no branch applies to every branch.</p>
    <div class="card mb-4"><div class="card-body"><form class="row g-2" method="post" action="<c:url value='/super/outcomes'/>"><t:csrf/>
        <div class="col-md-2"><input class="form-control" name="code" placeholder="PO1 or PSO1" required maxlength="20"></div>
        <div class="col-md-5"><input class="form-control" name="description" placeholder="Engineering knowledge" maxlength="500"></div>
        <div class="col-md-3"><select class="form-select" name="branchId"><option value="">All branches</option>
            <c:forEach items="${branches}" var="b"><option value="${b.id}"><c:out value="${b.name}"/></option></c:forEach></select></div>
        <div class="col-md-2"><button class="btn btn-primary" type="submit">Add PO</button></div></form></div></div>
    <div class="card"><div class="table-responsive"><table class="table mb-0">
        <thead><tr><th>PO</th><th>Description</th><th>Branch</th><th></th></tr></thead><tbody>
        <c:forEach items="${pos}" var="p"><tr><td class="fw-semibold"><c:out value="${p.code}"/></td><td><c:out value="${p.description}"/></td>
            <td><c:out value="${empty p.branchName ? 'All branches' : p.branchName}"/></td>
            <td class="text-end"><form method="post" action="<c:url value='/super/outcomes/${p.id}/delete'/>"><t:csrf/><button class="btn btn-sm btn-outline-danger">Remove</button></form></td></tr></c:forEach>
        <c:if test="${empty pos}"><tr><td colspan="4" class="text-center text-muted py-3">No Programme Outcomes yet.</td></tr></c:if>
        </tbody></table></div></div>
</t:layout>
