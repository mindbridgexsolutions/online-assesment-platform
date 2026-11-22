<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Bulk upload students or teachers" active="users">
    <div class="row justify-content-center">
        <div class="col-lg-8 col-xl-6">
            <h1 class="h3 page-title mb-3">Bulk upload students or teachers</h1>
            <c:if test="${not empty error}"><div class="alert alert-danger"><c:out value="${error}"/></div></c:if>
            <div class="card mb-3"><div class="card-body p-4">
                <form method="post" enctype="multipart/form-data" action="<c:url value='/admin/users/import'/>">
                    <t:csrf/>
                    <div class="mb-3">
                        <label class="form-label" for="file">CSV, Excel (.xlsx) or Word (.docx) file</label>
                        <input class="form-control" type="file" id="file" name="file" accept=".csv,.xlsx,.docx" required>
                    </div>
                    <div class="mb-3">
                        <label class="form-label" for="role">Upload as</label>
                        <select class="form-select" id="role" name="role">
                            <option value="STUDENT">Students</option>
                            <option value="TEACHER">Teachers</option>
                        </select>
                        <div class="form-text">For teachers the roll_no and batch_code columns, and the batch below, are ignored.</div>
                    </div>
                    <div class="mb-3">
                        <label class="form-label" for="batchId">Add everyone to batch <span class="text-muted">(optional)</span></label>
                        <select class="form-select" id="batchId" name="batchId">
                            <option value="">- none (use batch_code column, if any) -</option>
                            <c:forEach items="${batches}" var="b"><option value="${b.id}"><c:out value="${b.name}"/> (<c:out value="${b.code}"/>)</option></c:forEach>
                        </select>
                    </div>
                    <button class="btn btn-primary" type="submit">Upload and create accounts</button>
                    <a class="btn btn-outline-secondary" href="<c:url value='/admin/users'/>">Cancel</a>
                </form>
            </div></div>
            <div class="card"><div class="card-body">
                <h2 class="h6">File format</h2>
                <p class="small text-muted mb-2">First row = column headers. Maximum <c:out value="${maxRows}"/> rows per file. Valid rows are created even if other rows are rejected; rejected rows are listed with their row number.</p>
                <ul class="small mb-2">
                    <li><code>username</code>, <code>full_name</code>, <code>email</code> - required</li>
                    <li><code>roll_no</code>, <code>phone</code> - optional</li>
                    <li><code>password</code> - optional; blank = a random password is generated</li>
                    <li><code>batch_code</code> - optional; adds the student to that batch</li>
                </ul>
                <a href="<c:url value='/admin/users/import/template'/>">Download student CSV template</a> &middot;
                <a href="<c:url value='/admin/users/import/template?role=TEACHER'/>">Download teacher CSV template</a>
            </div></div>
        </div>
    </div>
</t:layout>
