<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<c:set var="isEdit" value="${not empty form.id}"/>
<t:layout title="${isEdit ? 'Edit question' : 'New question'}" active="questions" js="question-form.js">
    <h1 class="h3 page-title mb-3"><c:out value="${isEdit ? 'Edit question' : 'New question'}"/></h1>
    <c:if test="${not empty errors}"><div class="alert alert-danger"><ul class="mb-0"><c:forEach items="${errors}" var="e"><li><c:out value="${e}"/></li></c:forEach></ul></div></c:if>
    <c:choose>
        <c:when test="${isEdit}"><c:url var="action" value="/teacher/questions/${form.id}"><c:param name="_csrf" value="${_csrf.token}"/></c:url></c:when>
        <c:otherwise><c:url var="action" value="/teacher/questions"><c:param name="_csrf" value="${_csrf.token}"/></c:url></c:otherwise>
    </c:choose>
    <c:url var="catUrl" value="/teacher/questions/categories"/>
    <form method="post" action="${action}" enctype="multipart/form-data" id="qform" data-categories-url="${catUrl}"
          data-selected-category="${form.categoryId}" data-files-url="<c:url value='/files/q/'/>">
        <div class="row g-4">
            <div class="col-lg-8">
                <div class="card mb-3"><div class="card-body">
                    <div class="mb-3"><label class="form-label" for="text">Question</label>
                        <textarea class="form-control" id="text" name="text" rows="4" maxlength="5000" aria-describedby="textHelp"><c:out value="${form.text}"/></textarea></div>
                    <div class="mb-3">
                        <label class="form-label" for="image">Attachment (optional: image up to 2 MB, or a PDF / Word .docx file up to 10 MB)</label>
                        <input class="form-control" type="file" id="image" name="image" accept="image/png,image/jpeg,image/gif,image/webp,.pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document">
                        <c:if test="${not empty form.existingImage}">
                            <div class="mt-2 d-flex align-items-center gap-3">
                                <t:attachment name="${form.existingImage}" thumb="true"/>
                                <div class="form-check"><input class="form-check-input" type="checkbox" id="removeImage" name="removeImage" value="true">
                                    <label class="form-check-label" for="removeImage">Remove attachment</label></div>
                            </div>
                        </c:if>
                    </div>

                    <div id="sec-choice" data-types="MCQ_SINGLE,MCQ_MULTI" class="d-none">
                        <label class="form-label">Options <span class="text-muted small">(tick the correct answer<span data-multi-hint class="d-none">s</span>)</span></label>
                        <div data-rows="choice"></div>
                        <button type="button" class="btn btn-sm btn-outline-secondary mt-2" data-add-row="choice">+ Add option</button>
                        <div class="form-check form-switch mt-3 d-none" data-types-only="MCQ_MULTI">
                            <input class="form-check-input" type="checkbox" role="switch" id="partialMarking" name="partialMarking" value="true" ${form.partialMarking ? 'checked' : ''}>
                            <label class="form-check-label" for="partialMarking">Partial marking (marks per correct choice; wrong choices cancel out)</label>
                        </div>
                    </div>

                    <div id="sec-tf" data-types="TRUE_FALSE" class="d-none">
                        <label class="form-label d-block">Correct answer</label>
                        <div class="form-check form-check-inline"><input class="form-check-input" type="radio" name="tfAnswer" id="tfT" value="true" ${form.tfAnswer == true ? 'checked' : ''}><label class="form-check-label" for="tfT">True</label></div>
                        <div class="form-check form-check-inline"><input class="form-check-input" type="radio" name="tfAnswer" id="tfF" value="false" ${form.tfAnswer == false ? 'checked' : ''}><label class="form-check-label" for="tfF">False</label></div>
                    </div>

                    <div id="sec-text" data-types="FILL_BLANK,SHORT_ANSWER" class="d-none">
                        <label class="form-label"><span data-label-for="FILL_BLANK">Accepted answers (any one is correct)</span><span data-label-for="SHORT_ANSWER" class="d-none">Expected keywords (marks are proportional to keywords found)</span></label>
                        <div data-rows="text"></div>
                        <button type="button" class="btn btn-sm btn-outline-secondary mt-2" data-add-row="text">+ Add</button>
                        <div class="form-check form-switch mt-3">
                            <input class="form-check-input" type="checkbox" role="switch" id="caseSensitive" name="caseSensitive" value="true" ${form.caseSensitive ? 'checked' : ''}>
                            <label class="form-check-label" for="caseSensitive">Case sensitive</label>
                        </div>
                    </div>

                    <div id="sec-match" data-types="MATCHING" class="d-none">
                        <label class="form-label">Pairs (left item &rarr; right item). Students see the right side shuffled.</label>
                        <div data-rows="match"></div>
                        <button type="button" class="btn btn-sm btn-outline-secondary mt-2" data-add-row="match">+ Add pair</button>
                    </div>

                    <div id="sec-num" data-types="NUMERIC" class="d-none">
                        <div class="row g-3">
                            <div class="col-sm-6"><label class="form-label" for="numericAnswer">Correct value</label>
                                <input class="form-control" id="numericAnswer" name="numericAnswer" type="number" step="any" value="<c:out value='${form.numericAnswer}'/>"></div>
                            <div class="col-sm-6"><label class="form-label" for="numericTolerance">Tolerance (&plusmn;)</label>
                                <input class="form-control" id="numericTolerance" name="numericTolerance" type="number" step="any" min="0" value="<c:out value='${form.numericTolerance}'/>"></div>
                        </div>
                    </div>

                    <div id="sec-essay" data-types="ESSAY" class="d-none">
                        <div class="alert alert-info mb-0">Essay answers are graded manually by the teacher after submission.</div>
                    </div>
                </div></div>
                <div class="card"><div class="card-body">
                    <label class="form-label" for="explanation">Explanation shown in result review (optional)</label>
                    <textarea class="form-control" id="explanation" name="explanation" rows="3" maxlength="5000"><c:out value="${form.explanation}"/></textarea>
                </div></div>
            </div>
            <div class="col-lg-4">
                <div class="card mb-3"><div class="card-body">
                    <div class="mb-3"><label class="form-label" for="type">Type</label>
                        <select class="form-select" id="type" name="type">
                            <c:forEach items="${types}" var="ty"><option value="${ty}" ${ty == form.type ? 'selected' : ''}><c:out value="${ty.label}"/></option></c:forEach>
                        </select></div>
                    <div class="mb-3"><label class="form-label" for="subjectId">Subject</label>
                        <select class="form-select" id="subjectId" name="subjectId" required>
                            <option value="">Select...</option>
                            <c:forEach items="${subjects}" var="s"><option value="${s.id}" ${s.id == form.subjectId ? 'selected' : ''}><c:out value="${s.name}"/></option></c:forEach>
                        </select></div>
                    <div class="mb-3"><label class="form-label" for="categoryId">Category</label>
                        <select class="form-select" id="categoryId" name="categoryId">
                            <option value="">None</option>
                            <c:forEach items="${categories}" var="cat"><option value="${cat.id}" ${cat.id == form.categoryId ? 'selected' : ''}><c:out value="${cat.name}"/></option></c:forEach>
                        </select></div>
                    <div class="mb-3"><label class="form-label" for="bloom">Bloom's level</label>
                        <select class="form-select" id="bloom" name="bloom"><option value="">Not set</option>
                            <c:forEach items="${blooms}" var="b"><option value="${b}" ${b == form.bloom ? 'selected' : ''}><c:out value="${b.label}"/></option></c:forEach></select></div>
                    <div class="mb-3"><label class="form-label" for="unit">Unit / module</label>
                        <input class="form-control" id="unit" name="unit" maxlength="80" placeholder="e.g. Unit 2" value="<c:out value='${form.unit}'/>"></div>
                    <div class="mb-3"><label class="form-label" for="difficulty">Difficulty</label>
                        <select class="form-select" id="difficulty" name="difficulty">
                            <c:forEach items="${difficulties}" var="d"><option value="${d}" ${d == form.difficulty ? 'selected' : ''}><c:out value="${d.label}"/></option></c:forEach>
                        </select></div>
                    <div class="mb-3"><label class="form-label" for="defaultMarks">Default marks</label>
                        <input class="form-control" id="defaultMarks" name="defaultMarks" type="number" step="0.01" min="0.01" max="1000" required value="<c:out value='${form.defaultMarks}'/>"></div>
                    <div class="mb-0"><label class="form-label" for="tags">Tags (comma separated)</label>
                        <input class="form-control" id="tags" name="tags" maxlength="400" value="<c:out value='${form.tags}'/>"></div>
                </div></div>
                <div class="d-grid gap-2">
                    <div class="mb-3"><label class="form-label" for="coId">Course Outcome (CO) this question measures</label>
                        <select class="form-select" id="coId" name="coId">
                            <option value="">None</option>
                            <c:forEach items="${cos}" var="co"><option value="${co.id}" data-subject="${co.subjectId}" ${co.id == form.coId ? 'selected' : ''}><c:out value="${co.code}"/> - <c:out value="${co.subjectName}"/></option></c:forEach>
                        </select>
                        <div class="form-text">Used for CO/PO attainment reports. Define COs under Outcomes.</div></div>
                    <div class="form-check mb-3"><input class="form-check-input" type="checkbox" id="allowDuplicate" name="allowDuplicate" value="true" ${form.allowDuplicate ? 'checked' : ''}>
                        <input type="hidden" name="_allowDuplicate" value="on"><label class="form-check-label" for="allowDuplicate">Save even if an identical question already exists</label></div>
                    <button class="btn btn-primary" type="submit">Save question</button>
                    <a class="btn btn-outline-secondary" href="<c:url value='/teacher/questions'/>">Cancel</a>
                </div>
            </div>
        </div>
        <%-- existing option rows, rendered as data for the script to build the editor --%>
        <div id="existing-options" class="d-none">
            <c:forEach items="${form.options}" var="o">
                <span data-id="${o.id}" data-text="<c:out value='${o.text}'/>" data-match="<c:out value='${o.matchText}'/>" data-correct="${o.correct}" data-image="<c:out value='${o.imagePath}'/>"></span>
            </c:forEach>
        </div>
    </form>
</t:layout>
