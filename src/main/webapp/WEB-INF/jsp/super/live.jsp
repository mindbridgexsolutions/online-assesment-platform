<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Live correction" active="live">
    <h1 class="h3 page-title mb-1">Live exam correction</h1>
    <p class="text-muted">Pick an exam. You can fix a wrong question while students are still writing, without stopping the exam.</p>
    <div class="card"><div class="table-responsive"><table class="table table-hover mb-0">
        <thead><tr><th>Exam</th><th>Subject</th><th>Window</th><th>Status</th><th></th></tr></thead><tbody>
        <c:forEach items="${exams}" var="e"><tr>
            <td class="fw-semibold"><c:out value="${e.title}"/></td><td><c:out value="${e.subject.name}"/></td>
            <td class="small"><c:out value="${fmt.dateTime(e.startAt)}"/> to <c:out value="${fmt.dateTime(e.endAt)}"/></td>
            <td><c:out value="${e.status.label}"/></td>
            <td class="text-end"><a class="btn btn-sm btn-primary" href="<c:url value='/super/live/${e.id}'/>">Correct questions</a></td></tr></c:forEach>
        <c:if test="${empty exams}"><tr><td colspan="5" class="text-center text-muted py-4">No exams yet.</td></tr></c:if>
        </tbody></table></div></div>
</t:layout>
