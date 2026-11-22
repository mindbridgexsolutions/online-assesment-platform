<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Import / export questions" active="questions">
    <h1 class="h3 page-title mb-3">Import / export questions</h1>
    <c:if test="${not empty fileError}"><div class="alert alert-danger"><c:out value="${fileError}"/></div></c:if>
    <c:if test="${not empty result}">
        <div class="alert ${result.imported > 0 ? 'alert-success' : 'alert-warning'}">
            <strong>${result.imported}</strong> question(s) imported, <strong>${result.errors.size()}</strong> row(s) rejected.
        </div>
        <c:if test="${not empty result.errors}">
            <div class="card mb-3"><div class="table-responsive"><table class="table table-sm mb-0">
                <thead><tr><th style="width:90px">Row</th><th>Problem</th></tr></thead>
                <tbody><c:forEach items="${result.errors}" var="e"><tr><td>${e.row}</td><td><c:out value="${e.message}"/></td></tr></c:forEach></tbody>
            </table></div></div>
        </c:if>
    </c:if>
    <div class="row g-4">
        <div class="col-lg-6"><div class="card h-100"><div class="card-body">
            <h2 class="h5">Upload a question paper</h2>
            <p class="text-muted small">Upload an .xlsx, .csv or Word (.docx) file. It is kept as one named paper that you can review, edit (also in Word) and merge with other papers. <strong>Nothing goes into the Question Bank until you merge the paper.</strong> Questions are read as MCQ (max 300 rows).</p>
            <form method="post" enctype="multipart/form-data" action="<c:url value='/teacher/papers/upload'><c:param name='_csrf' value='${_csrf.token}'/></c:url>">
                <select class="form-select mb-2" name="subjectId" required aria-label="Subject">
                    <option value="">Choose subject of the paper</option>
                    <c:forEach items="${subjects}" var="sub"><option value="${sub.id}"><c:out value="${sub.name}"/> (<c:out value="${sub.code}"/>)</option></c:forEach>
                </select>
                <input class="form-control mb-3" type="file" name="file" accept=".xlsx,.csv,.docx" required>
                <button class="btn btn-primary" type="submit">Upload paper</button>
                <a class="btn btn-outline-secondary" href="<c:url value='/teacher/exams/upload-template.docx'/>">Word paper template</a>
                <a class="btn btn-outline-secondary" href="<c:url value='/teacher/questions/import/template'/>">CSV template</a>
                
            </form>
            <hr>
            <details>
                <summary class="small fw-semibold">Import directly (all question types, saves straight into the Question Bank)</summary>
                <p class="text-muted small mt-2">Use this only for types other than MCQ (true/false, fill in the blank, short answer, essay, matching, numeric) or when you really want to skip the paper step. Rows that fail validation are skipped and listed with the reason; valid rows are saved at once (max 1000 rows).</p>
                <form method="post" enctype="multipart/form-data" action="<c:url value='/teacher/questions/import'><c:param name='_csrf' value='${_csrf.token}'/></c:url>">
                    <input class="form-control mb-2" type="file" name="file" accept=".xlsx,.csv,.docx" required>
                    <button class="btn btn-outline-danger btn-sm" type="submit" data-confirm="This saves the questions straight into the Question Bank without a review. Continue?">Import directly</button>
                    <a class="btn btn-outline-secondary btn-sm" href="<c:url value='/teacher/questions/import/template.docx'/>">Word template (all types)</a>
                </form>
                <p class="small text-muted mt-2 mb-1"><strong>Columns:</strong> subject_code, category, type, difficulty, marks, question, options, correct, tolerance, case_sensitive, partial, explanation, tags</p>
                <ul class="small text-muted mb-0">
                    <li>Separate options with <code>|</code>. For matching use <code>left=right|left=right</code>.</li>
                    <li><code>correct</code>: option numbers (<code>2</code> or <code>1,3</code>), <code>TRUE</code>/<code>FALSE</code>, or the number for numeric questions.</li>
                    <li>Types: MCQ_SINGLE, MCQ_MULTI, TRUE_FALSE, FILL_BLANK, SHORT_ANSWER, ESSAY, MATCHING, NUMERIC.</li>
                </ul>
            </details>
        </div></div></div>
        <div class="col-lg-6"><div class="card h-100"><div class="card-body">
            <h2 class="h5">Export</h2>
            <p class="text-muted small">Download your question bank as an Excel file. The export can be edited and imported again.</p>
            <form method="get" action="<c:url value='/teacher/questions/export'/>" class="d-flex gap-2">
                <select class="form-select" name="subjectId"><option value="">All my subjects</option>
                    <c:forEach items="${subjects}" var="s"><option value="${s.id}"><c:out value="${s.name}"/></option></c:forEach></select>
                <button class="btn btn-outline-primary" type="submit">Export</button>
            </form>
        </div></div></div>
    </div>
</t:layout>
