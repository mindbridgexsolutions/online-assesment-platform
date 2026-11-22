<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Result release" active="results">
    <h1 class="h3 page-title mb-1">Result release control</h1>
    <p class="text-muted">Results stay hidden from students until the release date set by the teacher. Release early or move the date here. Every change needs a reason and is logged.</p>
    <div class="card"><div class="table-responsive"><table class="table mb-0 align-middle">
        <thead><tr><th>Exam</th><th>Policy</th><th>Release</th><th style="width:42%">Change</th></tr></thead><tbody>
        <c:forEach items="${exams}" var="e"><tr>
            <td><c:out value="${e.title}"/><div class="small text-muted"><c:out value="${e.subject.name}"/></div></td>
            <td class="small"><c:out value="${e.resultPolicy.label}"/></td>
            <td class="small"><c:choose><c:when test="${e.resultPolicy == 'SCHEDULED' and not empty e.resultsPublishAt}"><c:out value="${fmt.dateTime(e.resultsPublishAt)}"/></c:when><c:otherwise>-</c:otherwise></c:choose></td>
            <td><form class="d-flex flex-wrap gap-2" method="post" action="<c:url value='/super/results/${e.id}'/>"><t:csrf/>
                <input class="form-control form-control-sm" type="datetime-local" name="at" aria-label="New release date and time" style="max-width:210px">
                <input class="form-control form-control-sm" name="reason" placeholder="Reason" required minlength="5" style="max-width:180px">
                <button class="btn btn-sm btn-outline-primary" name="now" value="false">Set date</button>
                <button class="btn btn-sm btn-success" name="now" value="true">Release now</button></form></td></tr></c:forEach>
        </tbody></table></div></div>
</t:layout>
