<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Reports" active="reports">
    <h1 class="h3 page-title mb-3">Exam reports</h1>
    <div class="card"><div class="table-responsive"><table class="table table-hover align-middle mb-0">
        <thead><tr><th>Exam</th><th>Subject</th><th>Window</th><th>Status</th><th></th></tr></thead>
        <tbody>
        <c:forEach items="${page.content}" var="e">
            <tr><td><c:out value="${e.title}"/></td><td><c:out value="${e.subject.name}"/></td>
                <td class="small"><c:out value="${fmt.dateTime(e.startAt)}"/> &ndash; <c:out value="${fmt.dateTime(e.endAt)}"/></td>
                <td><span class="badge text-bg-${e.status == 'PUBLISHED' ? 'success' : (e.status == 'DRAFT' ? 'secondary' : 'dark')}"><c:out value="${e.status.label}"/></span></td>
                <td class="text-end"><a class="btn btn-sm btn-outline-primary" href="<c:url value='/reports/exams/${e.id}'/>">Open report</a></td></tr>
        </c:forEach>
        <c:if test="${empty page.content}"><tr><td colspan="5" class="text-center text-muted py-4">No exams.</td></tr></c:if>
        </tbody></table></div></div>
    <t:pager page="${page}" path="/reports"/>
</t:layout>
