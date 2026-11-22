<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Course outcomes" active="${currentUser.superAdmin ? 'cos' : 'outcomes'}">
    <h1 class="h3 page-title mb-1">Course Outcomes and CO-PO mapping</h1>
    <p class="text-muted">Define the Course Outcomes (CO) of a subject, map each CO to the Programme Outcomes (PO) set by the Super Administrator, then tag every question with a CO. Attainment reports use these tags.</p>
    <form class="row g-2 mb-3" method="get" action="<c:url value='/teacher/outcomes'/>">
        <div class="col-md-5"><select class="form-select" name="subjectId" data-autosubmit aria-label="Subject">
            <option value="">Choose a subject...</option>
            <c:forEach items="${subjects}" var="s"><option value="${s.id}" ${s.id == subjectId ? 'selected' : ''}><c:out value="${s.name}"/> (<c:out value="${s.code}"/>)</option></c:forEach></select></div>
        <div class="col-auto"><button class="btn btn-outline-primary" type="submit">Open</button></div>
    </form>
    <c:if test="${not empty subjectId}">
        <div class="card mb-4"><div class="card-header">Course Outcomes</div>
            <div class="table-responsive"><table class="table mb-0 align-middle">
                <thead><tr><th>CO</th><th>Description</th><th>Target %</th><th></th></tr></thead><tbody>
                <c:forEach items="${cos}" var="c"><tr><td class="fw-semibold"><c:out value="${c.code}"/></td><td><c:out value="${c.description}"/></td><td>${c.targetPercent}</td>
                    <td class="text-end"><form method="post" action="<c:url value='/teacher/outcomes/co/${c.id}/delete'/>"><t:csrf/><input type="hidden" name="subjectId" value="${subjectId}">
                        <button class="btn btn-sm btn-outline-danger" type="submit" data-confirm="Remove this Course Outcome?">Remove</button></form></td></tr></c:forEach>
                <c:if test="${empty cos}"><tr><td colspan="4" class="text-center text-muted py-3">No Course Outcomes yet.</td></tr></c:if>
                </tbody></table></div>
            <div class="card-footer"><form class="row g-2" method="post" action="<c:url value='/teacher/outcomes/co'/>"><t:csrf/><input type="hidden" name="subjectId" value="${subjectId}">
                <div class="col-md-2"><input class="form-control" name="code" placeholder="CO1" required maxlength="20"></div>
                <div class="col-md-6"><input class="form-control" name="description" placeholder="Describe the outcome" maxlength="500"></div>
                <div class="col-md-2"><input class="form-control" type="number" name="target" value="60" min="0" max="100" step="1" aria-label="Target percent"></div>
                <div class="col-md-2"><button class="btn btn-primary" type="submit">Add CO</button></div></form></div>
        </div>
        <div class="card"><div class="card-header">CO to PO mapping (0 = none, 1 = low, 2 = medium, 3 = high)</div>
            <c:choose><c:when test="${empty cos or empty pos}"><div class="card-body text-muted">Add Course Outcomes here, and ask the Super Administrator to define the Programme Outcomes, to map them.</div></c:when>
            <c:otherwise>
            <form method="post" action="<c:url value='/teacher/outcomes/map'/>"><t:csrf/><input type="hidden" name="subjectId" value="${subjectId}">
                <div class="table-responsive"><table class="table table-bordered mb-0 text-center align-middle">
                    <thead><tr><th></th><c:forEach items="${pos}" var="p"><th title="<c:out value='${p.description}'/>"><c:out value="${p.code}"/></th></c:forEach></tr></thead><tbody>
                    <c:forEach items="${cos}" var="c"><tr><th class="text-start"><c:out value="${c.code}"/></th>
                        <c:forEach items="${pos}" var="p"><c:set var="k" value="${c.id}:${p.id}"/>
                            <td><select class="form-select form-select-sm" name="m_${c.id}_${p.id}" aria-label="${c.code} to ${p.code}">
                                <c:forEach begin="0" end="3" var="lv"><option value="${lv}" ${map[k] == lv ? 'selected' : ''}>${lv}</option></c:forEach></select></td></c:forEach></tr></c:forEach>
                    </tbody></table></div>
                <div class="card-footer text-end"><button class="btn btn-primary" type="submit">Save mapping</button></div></form>
            </c:otherwise></c:choose>
        </div>
    </c:if>
</t:layout>
