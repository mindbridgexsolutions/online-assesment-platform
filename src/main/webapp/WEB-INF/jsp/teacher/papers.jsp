<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Question papers" active="papers">
    <h1 class="h3 page-title mb-1">Question papers</h1>
    <p class="text-muted">Each file you upload is kept here as one paper (a list of MCQs). Review and edit it, merge papers together, and only then merge it into the Question Bank. Nothing enters the Question Bank on its own.</p>

    <div class="card mb-4"><div class="card-body">
        <h2 class="h5">Upload a question paper</h2>
        <form method="post" enctype="multipart/form-data" class="row g-2 align-items-end" action="<c:url value='/teacher/papers/upload'><c:param name='_csrf' value='${_csrf.token}'/></c:url>">
            <div class="col-md-4"><label class="form-label small mb-1" for="subjectId">Subject</label>
                <select class="form-select" id="subjectId" name="subjectId" required>
                    <option value="">Choose subject</option>
                    <c:forEach items="${subjects}" var="sub"><option value="${sub.id}"><c:out value="${sub.name}"/> (<c:out value="${sub.code}"/>)</option></c:forEach>
                </select></div>
            <div class="col-md-5"><label class="form-label small mb-1" for="file">File (.docx, .xlsx or .csv)</label>
                <input class="form-control" id="file" type="file" name="file" accept=".docx,.xlsx,.csv" required></div>
            <div class="col-md-3"><button class="btn btn-primary w-100" type="submit">Upload paper</button></div>
        </form>
        <p class="small text-muted mt-2 mb-0">Questions are read as MCQ. Download a template:
            <a href="<c:url value='/teacher/exams/upload-template.docx'/>">Word paper template</a> &middot;
            <a href="<c:url value='/teacher/questions/import/template'/>">CSV template</a>.
            For other question types use <a href="<c:url value='/teacher/questions/import'/>">Import / export</a>.</p>
    </div></div>

    <form method="post" action="<c:url value='/teacher/papers/merge'/>"><t:csrf/>
        <div class="card"><div class="card-header bg-white d-flex flex-wrap gap-2 justify-content-between align-items-center">
            <span class="fw-semibold">Your papers</span>
            <span class="d-flex flex-wrap gap-2 align-items-center no-print">
                <input class="form-control form-control-sm w-auto" type="text" name="title" maxlength="200" placeholder="Name of merged paper (optional)">
                <button class="btn btn-sm btn-outline-primary" type="submit" title="Tick two or more draft papers of the same subject">Merge selected papers</button>
            </span>
        </div>
        <div class="table-responsive"><table class="table table-sm align-middle mb-0">
            <thead><tr><th style="width:36px"></th><th>Paper (file)</th><th>Subject</th><th class="text-end">Questions</th><th class="text-end">Valid</th><th class="text-end">Duplicates</th><th class="text-end">Failed</th><th>Status</th><th>Uploaded</th><th></th></tr></thead>
            <tbody>
            <c:forEach items="${papers}" var="p">
                <tr>
                    <td><c:if test="${p.status == 'PENDING'}"><input class="form-check-input" type="checkbox" name="ids" value="${p.id}" aria-label="Select ${p.fileName}"></c:if></td>
                    <td><a href="<c:url value='/teacher/uploads/${p.id}'/>"><c:out value="${p.fileName}"/></a>
                        <c:if test="${p.source == 'MERGE'}"><span class="badge text-bg-light border ms-1">merged paper</span></c:if>
                        <c:if test="${not empty p.examTitle}"><div class="small text-muted">Exam: <c:out value="${p.examTitle}"/></div></c:if></td>
                    <td class="small"><c:out value="${p.subjectName}"/></td>
                    <td class="text-end">${p.questions}</td><td class="text-end text-success">${p.valid}</td>
                    <td class="text-end text-warning">${p.duplicates}</td><td class="text-end text-danger">${p.failed}</td>
                    <td><span class="badge text-bg-${p.status == 'PENDING' ? 'warning' : 'secondary'}">${p.status == 'PENDING' ? 'Draft' : 'Merged into bank'}</span></td>
                    <td class="small"><c:out value="${fmt.dateTime(p.createdAt)}"/></td>
                    <td class="text-end text-nowrap"><a class="btn btn-sm btn-outline-primary" href="<c:url value='/teacher/uploads/${p.id}'/>">${p.status == 'PENDING' ? 'Review / edit' : 'View'}</a>
                        <c:if test="${p.status == 'PENDING'}"><a class="btn btn-sm btn-outline-secondary" href="<c:url value='/teacher/uploads/${p.id}/download.docx'/>">Word</a></c:if>
                        <c:if test="${p.valid > 0 or p.status == 'CONFIRMED'}"><a class="btn btn-sm btn-primary" href="<c:url value='/teacher/uploads/${p.id}/publish'/>">Publish</a></c:if></td>
                </tr>
            </c:forEach>
            <c:if test="${empty papers}"><tr><td colspan="10" class="text-center text-muted py-4">No papers yet. Upload a file above.</td></tr></c:if>
            </tbody>
        </table></div></div>
    </form>
</t:layout>
