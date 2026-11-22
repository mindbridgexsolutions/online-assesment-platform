<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Categories" active="categories">
    <h1 class="h3 page-title mb-3">Question categories</h1>
    <div class="row g-4">
        <div class="col-lg-4">
            <div class="card"><div class="card-body">
                <h2 class="h6">Add a category</h2>
                <form method="post" action="<c:url value='/teacher/categories'/>">
                    <t:csrf/>
                    <div class="mb-2"><label class="form-label" for="subjectId">Subject</label>
                        <select class="form-select" id="subjectId" name="subjectId" required>
                            <c:forEach items="${subjects}" var="s"><option value="${s.id}"><c:out value="${s.name}"/></option></c:forEach>
                        </select></div>
                    <div class="mb-3"><label class="form-label" for="name">Name</label>
                        <input class="form-control" id="name" name="name" maxlength="120" required></div>
                    <button class="btn btn-primary" type="submit">Add</button>
                </form>
                <c:if test="${empty subjects}"><p class="text-muted small mt-3 mb-0">You are not assigned to any subject yet. Ask an administrator.</p></c:if>
            </div></div>
        </div>
        <div class="col-lg-8">
            <div class="card"><div class="table-responsive">
                <table class="table align-middle mb-0">
                    <thead><tr><th>Category</th><th>Subject</th><th class="text-end">Questions</th><th></th></tr></thead>
                    <tbody>
                    <c:forEach items="${categories}" var="c">
                        <tr>
                            <td>
                                <form method="post" action="<c:url value='/teacher/categories/${c.id}/rename'/>" class="d-flex gap-2">
                                    <t:csrf/>
                                    <input class="form-control form-control-sm" name="name" value="<c:out value='${c.name}'/>" maxlength="120" required>
                                    <button class="btn btn-sm btn-outline-secondary" type="submit">Rename</button>
                                </form>
                            </td>
                            <td><c:out value="${c.subjectName}"/></td>
                            <td class="text-end">${c.questionCount}</td>
                            <td class="text-end">
                                <form method="post" action="<c:url value='/teacher/categories/${c.id}/delete'/>">
                                    <t:csrf/>
                                    <button class="btn btn-sm btn-outline-danger" type="submit" data-confirm="Delete this category?">Delete</button>
                                </form>
                            </td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty categories}"><tr><td colspan="4" class="text-center text-muted py-4">No categories yet.</td></tr></c:if>
                    </tbody>
                </table>
            </div></div>
        </div>
    </div>
</t:layout>
