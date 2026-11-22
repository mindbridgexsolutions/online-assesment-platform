<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Upload questions" active="exams">
    <h1 class="h3 page-title mb-1">Upload Questions (.docx)</h1>
    <p class="text-muted">Exam: <strong><c:out value="${exam.title}"/></strong> (<c:out value="${exam.subject.name}"/>). Nothing is saved until you review and confirm.</p>
    <c:if test="${not empty fileError}"><div class="alert alert-danger"><c:out value="${fileError}"/></div></c:if>
    <div class="row g-4">
        <div class="col-lg-6"><div class="card h-100"><div class="card-body">
            <h2 class="h5">1. Upload your file</h2>
            <form method="post" enctype="multipart/form-data" action="<c:url value='/teacher/exams/${exam.id}/upload'><c:param name='_csrf' value='${_csrf.token}'/></c:url>">
                <input class="form-control mb-3" type="file" name="file" accept=".docx" required>
                <button class="btn btn-primary" type="submit">Upload and check</button>
                <a class="btn btn-outline-secondary" href="<c:url value='/teacher/exams/upload-template.docx'/>">Download Template</a>
                <a class="btn btn-link" href="<c:url value='/teacher/exams/${exam.id}'/>">Cancel</a>
            </form>
        </div></div></div>
        <div class="col-lg-6"><div class="card h-100"><div class="card-body">
            <h2 class="h5">Template rules</h2>
            <ul class="small mb-0">
                <li>One table, one row per question: <code>Q No | Question | Option A | Option B | Option C | Option D | Correct | Marks | CO | Bloom's</code>.</li>
                <li>The question and every option can be text, a picture, or both (just use Insert &gt; Picture in Word).</li>
                <li><strong>Correct</strong> is a letter, e.g. <code>B</code>; several letters (<code>A,C</code>) make a multiple-answer question.</li>
                <li><strong>CO</strong> is a number from 1 to 10 (1 means CO1) and must be a Course Outcome defined for the subject. <strong>Bloom's</strong> is a number from 1 to 6 (1 Remember, 2 Understand, 3 Apply, 4 Analyze, 5 Evaluate, 6 Create). Marks are optional.</li>
                <li>Rows with a problem are listed with the row number and reason; the good rows still go through. Duplicates are flagged and you choose to skip or keep them.</li>
            </ul>
        </div></div></div>
    </div>
</t:layout>
