<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="CO-PO attainment" active="copo">
    <h1 class="h3 page-title mb-1">CO-PO attainment</h1>
    <p class="text-muted">Computed from real student results of the questions tagged with each Course Outcome. A student attains a CO when the CO score reaches its target; the CO level is 3 when at least 70% of students attained it, 2 for 60%, 1 for 50%, otherwise 0. A PO is the weighted average of the mapped CO levels.</p>
    <form class="row g-2 mb-3" method="get" action="<c:url value='/reports/outcomes'/>">
        <div class="col-md-4"><select class="form-select" name="subjectId" aria-label="Course" data-autosubmit><option value="">Choose a course...</option>
            <c:forEach items="${subjects}" var="s"><option value="${s.id}" ${s.id == subjectId ? 'selected' : ''}><c:out value="${s.name}"/></option></c:forEach></select></div>
        <c:if test="${not empty subjectId}">
            <div class="col-md-3"><select class="form-select" name="batchId" aria-label="Class"><option value="">All classes</option>
                <c:forEach items="${batches}" var="b"><option value="${b.id}" ${b.id == batchId ? 'selected' : ''}><c:out value="${b.name}"/></option></c:forEach></select></div>
            <div class="col-md-3"><select class="form-select" name="examId" aria-label="Exam"><option value="">All exams</option>
                <c:forEach items="${exams}" var="e"><option value="${e.id}" ${e.id == examId ? 'selected' : ''}><c:out value="${e.title}"/></option></c:forEach></select></div>
            <div class="col-auto"><button class="btn btn-primary" type="submit">Show</button></div>
        </c:if>
    </form>
    <c:if test="${not empty r}">
        <c:url var="qs" value=""><c:param name="subjectId" value="${subjectId}"/><c:if test="${not empty batchId}"><c:param name="batchId" value="${batchId}"/></c:if><c:if test="${not empty examId}"><c:param name="examId" value="${examId}"/></c:if></c:url>
        <div class="d-flex gap-2 mb-3">
            <a class="btn btn-outline-primary" href="<c:url value='/reports/outcomes/export.xlsx'/>${qs}">Download Excel</a>
            <a class="btn btn-outline-primary" href="<c:url value='/reports/outcomes/export.pdf'/>${qs}">Download PDF</a>
        </div>
        <div class="card mb-4"><div class="card-header">Course Outcome attainment</div><div class="table-responsive"><table class="table mb-0">
            <thead><tr><th>CO</th><th>Description</th><th>Target %</th><th>Students</th><th>Attained</th><th>Attained %</th><th>Average %</th><th>Level (0-3)</th></tr></thead><tbody>
            <c:forEach items="${r.cos}" var="c"><tr><td class="fw-semibold"><c:out value="${c.code}"/></td><td><c:out value="${c.description}"/></td><td>${c.target}</td><td>${c.students}</td><td>${c.attained}</td><td>${c.attainedPercent}</td><td>${c.averagePercent}</td>
                <td><span class="badge text-bg-${c.level >= 3 ? 'success' : (c.level == 2 ? 'primary' : (c.level == 1 ? 'warning' : 'secondary'))}">${c.level}</span></td></tr></c:forEach>
            <c:if test="${empty r.cos}"><tr><td colspan="8" class="text-center text-muted py-3">No Course Outcomes defined for this course. <a href="<c:url value='/teacher/outcomes?subjectId=${subjectId}'/>">Add them</a>.</td></tr></c:if>
            </tbody></table></div></div>
        <div class="card mb-4"><div class="card-header">Programme Outcome attainment (0 to 3)</div><div class="table-responsive"><table class="table mb-0">
            <thead><tr><th>PO</th><th>Description</th><th>Attainment</th><th>Computed from</th></tr></thead><tbody>
            <c:forEach items="${r.pos}" var="p"><tr><td class="fw-semibold"><c:out value="${p.code}"/></td><td><c:out value="${p.description}"/></td><td>${empty p.level ? '-' : p.level}</td><td class="small"><c:out value="${p.contributors}"/></td></tr></c:forEach>
            <c:if test="${empty r.pos}"><tr><td colspan="4" class="text-center text-muted py-3">No CO-PO mapping saved for this course.</td></tr></c:if>
            </tbody></table></div></div>
        <div class="card mb-4"><div class="card-header">Performance by Bloom's level</div><div class="table-responsive"><table class="table mb-0">
            <thead><tr><th>Bloom's level</th><th>Students</th><th>Average score %</th></tr></thead><tbody>
            <c:forEach items="${r.bloom}" var="b"><tr><td><c:out value="${b.level}"/></td><td>${b.students}</td><td>${b.averagePercent}</td></tr></c:forEach>
            <c:if test="${empty r.bloom}"><tr><td colspan="3" class="text-center text-muted py-3">No questions tagged with a Bloom's level have been answered yet.</td></tr></c:if>
            </tbody></table></div></div>
        <div class="card"><div class="card-header">Students (CO score %)</div><div class="table-responsive"><table class="table table-sm mb-0">
            <thead><tr><th>Student</th><c:forEach items="${r.coCodes}" var="code"><th><c:out value="${code}"/></th></c:forEach><th>Overall</th><th></th></tr></thead><tbody>
            <c:forEach items="${r.students}" var="s"><tr><td><c:out value="${s.name}"/> <span class="text-muted small"><c:out value="${s.username}"/></span></td>
                <c:forEach items="${r.coCodes}" var="code"><td>${empty s.coPercent[code] ? '-' : s.coPercent[code]}</td></c:forEach><td class="fw-semibold">${empty s.overall ? '-' : s.overall}</td>
                <td class="text-end"><a class="small" href="<c:url value='/reports/outcomes/student/${s.id}.pdf'><c:param name='subjectId' value='${subjectId}'/></c:url>">PDF</a></td></tr></c:forEach>
            <c:if test="${empty r.students}"><tr><td colspan="${r.coCodes.size() + 3}" class="text-center text-muted py-3">No submitted results with CO-tagged questions yet.</td></tr></c:if>
            </tbody></table></div></div>
    </c:if>
</t:layout>
