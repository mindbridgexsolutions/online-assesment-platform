<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Audit log" active="audit">
    <h1 class="h3 page-title mb-3">Audit log</h1>
    <form class="card card-body mb-3" method="get" action="<c:url value='/admin/audit'/>">
        <div class="row g-2 align-items-end">
            <div class="col-md-3"><label class="form-label small mb-1" for="action">Action</label>
                <select class="form-select" id="action" name="action">
                    <option value="">All actions</option>
                    <c:forEach items="${actions}" var="a"><option value="<c:out value='${a}'/>" ${a == action ? 'selected' : ''}><c:out value="${a}"/></option></c:forEach>
                </select></div>
            <div class="col-md-3"><label class="form-label small mb-1" for="actor">User</label>
                <input class="form-control" id="actor" name="actor" value="<c:out value='${actor}'/>" placeholder="username"></div>
            <div class="col-md-2"><label class="form-label small mb-1" for="from">From</label>
                <input class="form-control" type="date" id="from" name="from" value="${from}"></div>
            <div class="col-md-2"><label class="form-label small mb-1" for="to">To</label>
                <input class="form-control" type="date" id="to" name="to" value="${to}"></div>
            <div class="col-md-2 d-grid"><button class="btn btn-outline-primary" type="submit">Filter</button></div>
        </div>
    </form>
    <div class="card"><div class="table-responsive">
        <table class="table table-sm table-hover mb-0">
            <thead><tr><th>When</th><th>User</th><th>Action</th><th>Entity</th><th>Details</th><th>IP</th></tr></thead>
            <tbody>
            <c:forEach items="${page.content}" var="l">
                <tr>
                    <td class="text-nowrap small"><c:out value="${fmt.dateTime(l.createdAt)}"/></td>
                    <td><c:out value="${l.actorName}"/></td>
                    <td><span class="badge text-bg-light border"><c:out value="${l.action}"/></span></td>
                    <td class="small"><c:out value="${l.entityType}"/> <c:out value="${l.entityId}"/></td>
                    <td class="small text-muted"><c:out value="${l.details}"/></td>
                    <td class="small text-muted"><c:out value="${l.ipAddress}"/></td>
                </tr>
            </c:forEach>
            <c:if test="${empty page.content}"><tr><td colspan="6" class="text-center text-muted py-4">No entries.</td></tr></c:if>
            </tbody>
        </table>
    </div></div>
    <t:pager page="${page}" path="/admin/audit"/>
</t:layout>
