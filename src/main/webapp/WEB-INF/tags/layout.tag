<%@ tag language="java" pageEncoding="UTF-8" body-content="scriptless" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ attribute name="title" required="true" %>
<%@ attribute name="active" required="false" %>
<%@ attribute name="js" required="false" description="Page specific script under /js, e.g. player.js" %>
<%@ attribute name="bare" required="false" description="true = no navigation bar (exam player, login)" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="_csrf" content="<c:out value='${_csrf.token}'/>">
    <meta name="_csrf_header" content="<c:out value='${_csrf.headerName}'/>">
    <title><c:out value="${title}"/> | MindBridgeX Assess</title>
    <link rel="icon" href="<c:url value='/img/favicon.svg'/>" type="image/svg+xml">
    <link rel="stylesheet" href="<c:url value='/webjars/bootstrap/5.3.3/css/bootstrap.min.css'/>">
    <link rel="stylesheet" href="<c:url value='/css/app.css'/>">
</head>
<body class="${bare == 'true' ? 'bare' : ''}">
<c:if test="${bare != 'true'}">
    <nav class="navbar navbar-expand-lg navbar-dark app-nav">
        <div class="container-xxl">
            <a class="navbar-brand" href="<c:url value='/'/>">MindBridgeX <span>Assess</span></a>
            <c:if test="${not empty currentUser}">
                <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#mainNav"
                        aria-controls="mainNav" aria-expanded="false" aria-label="Toggle navigation">
                    <span class="navbar-toggler-icon"></span>
                </button>
                <div class="collapse navbar-collapse" id="mainNav">
                    <ul class="navbar-nav me-auto">
                        <c:if test="${currentUser.superAdmin}">
                            <li class="nav-item"><a class="nav-link ${active == 'sdash' ? 'active' : ''}" href="<c:url value='/super'/>">Super Admin</a></li>
                            <li class="nav-item dropdown"><a class="nav-link dropdown-toggle" href="#" role="button" data-bs-toggle="dropdown" aria-expanded="false">People &amp; setup</a>
                                <ul class="dropdown-menu">
                                    <li><a class="dropdown-item" href="<c:url value='/admin/users'/>">Users</a></li>
                                    <li><a class="dropdown-item" href="<c:url value='/admin/batches'/>">Batches</a></li>
                                    <li><a class="dropdown-item" href="<c:url value='/admin/subjects'/>">Subjects</a></li>
                                    <li><a class="dropdown-item" href="<c:url value='/super/branches'/>">Branches</a></li>
                                    <li><a class="dropdown-item" href="<c:url value='/admin/audit'/>">Audit log</a></li>
                                </ul></li>
                            <li class="nav-item dropdown"><a class="nav-link dropdown-toggle" href="#" role="button" data-bs-toggle="dropdown" aria-expanded="false">Academics</a>
                                <ul class="dropdown-menu">
                                    <li><a class="dropdown-item" href="<c:url value='/super/outcomes'/>">Programme outcomes (PO)</a></li>
                                    <li><a class="dropdown-item" href="<c:url value='/teacher/outcomes'/>">Course outcomes (CO) and mapping</a></li>
                                    <li><a class="dropdown-item" href="<c:url value='/reports/outcomes'/>">CO-PO attainment reports</a></li>
                                    <li><a class="dropdown-item" href="<c:url value='/admin/duplicates'/>">Duplicate questions</a></li>
                                    <li><a class="dropdown-item" href="<c:url value='/super/results'/>">Result release control</a></li>
                                    <li><a class="dropdown-item" href="<c:url value='/hod'/>">Exam approvals</a></li>
                                </ul></li>
                            <li class="nav-item"><a class="nav-link ${active == 'overrides' ? 'active' : ''}" href="<c:url value='/super/overrides'/>">Approvals<c:if test="${pendingApprovals > 0}"> <span class="badge rounded-pill text-bg-warning" title="Waiting for approval">${pendingApprovals}</span></c:if></a></li>
                            <li class="nav-item"><a class="nav-link ${active == 'live' ? 'active' : ''}" href="<c:url value='/super/live'/>">Live correction</a></li>
                            <li class="nav-item"><a class="nav-link ${active == 'exams' ? 'active' : ''}" href="<c:url value='/teacher/exams'/>">Exams</a></li>
                            <li class="nav-item"><a class="nav-link ${active == 'questions' ? 'active' : ''}" href="<c:url value='/teacher/questions'/>">Question bank</a></li>
                        </c:if>
                        <c:if test="${currentUser.admin and not currentUser.superAdmin}">
                            <li class="nav-item"><a class="nav-link ${active == 'dashboard' ? 'active' : ''}" href="<c:url value='/admin'/>">Dashboard</a></li>
                            <li class="nav-item"><a class="nav-link ${active == 'users' ? 'active' : ''}" href="<c:url value='/admin/users'/>">Users</a></li>
                            <li class="nav-item"><a class="nav-link ${active == 'batches' ? 'active' : ''}" href="<c:url value='/admin/batches'/>">Batches</a></li>
                            <li class="nav-item"><a class="nav-link ${active == 'subjects' ? 'active' : ''}" href="<c:url value='/admin/subjects'/>">Subjects</a></li>
                            <li class="nav-item"><a class="nav-link ${active == 'reports' ? 'active' : ''}" href="<c:url value='/reports'/>">Reports</a></li>
                            <li class="nav-item"><a class="nav-link ${active == 'audit' ? 'active' : ''}" href="<c:url value='/admin/audit'/>">Audit log</a></li>
                        </c:if>
                        <c:if test="${currentUser.iqac and not currentUser.teacher and not currentUser.admin}">
                            <li class="nav-item"><a class="nav-link active" href="<c:url value='/reports/outcomes'/>">CO-PO reports</a></li>
                        </c:if>
                        <c:if test="${currentUser.teacher and not currentUser.admin}">
                            <li class="nav-item"><a class="nav-link ${active == 'dashboard' ? 'active' : ''}" href="<c:url value='/teacher'/>">Dashboard</a></li>
                            <li class="nav-item"><a class="nav-link ${active == 'questions' ? 'active' : ''}" href="<c:url value='/teacher/questions'/>">Question bank</a></li>
                            <li class="nav-item"><a class="nav-link ${active == 'categories' ? 'active' : ''}" href="<c:url value='/teacher/categories'/>">Categories</a></li>
                            <li class="nav-item"><a class="nav-link ${active == 'exams' ? 'active' : ''}" href="<c:url value='/teacher/exams'/>">Exams</a></li>
                            <li class="nav-item"><a class="nav-link ${active == 'grading' ? 'active' : ''}" href="<c:url value='/teacher/grading'/>">Grading</a></li>
                            <li class="nav-item"><a class="nav-link ${active == 'outcomes' ? 'active' : ''}" href="<c:url value='/teacher/outcomes'/>">Outcomes (CO-PO)</a></li>
                            <c:if test="${currentUser.hod}"><li class="nav-item"><a class="nav-link ${active == 'hod' ? 'active' : ''}" href="<c:url value='/hod'/>">HoD approvals</a></li></c:if>
                            <li class="nav-item"><a class="nav-link ${active == 'copo' ? 'active' : ''}" href="<c:url value='/reports/outcomes'/>">CO-PO reports</a></li>
                        </c:if>
                        <c:if test="${currentUser.student and not currentUser.admin and not currentUser.teacher}">
                            <li class="nav-item"><a class="nav-link ${active == 'dashboard' ? 'active' : ''}" href="<c:url value='/student'/>">My exams</a></li>
                            <li class="nav-item"><a class="nav-link ${active == 'results' ? 'active' : ''}" href="<c:url value='/student/results'/>">My results</a></li>
                        </c:if>
                    </ul>
                    <div class="dropdown">
                        <a class="nav-link dropdown-toggle text-white" href="#" role="button" data-bs-toggle="dropdown" aria-expanded="false">
                            <c:out value="${currentUser.fullName}"/>
                        </a>
                        <ul class="dropdown-menu dropdown-menu-end">
                            <li><a class="dropdown-item" href="<c:url value='/auth/change-password'/>">Change password</a></li>
                            <li><hr class="dropdown-divider"></li>
                            <li>
                                <form method="post" action="<c:url value='/auth/logout'/>">
                                    <input type="hidden" name="${_csrf.parameterName}" value="<c:out value='${_csrf.token}'/>">
                                    <button type="submit" class="dropdown-item">Sign out</button>
                                </form>
                            </li>
                        </ul>
                    </div>
                </div>
            </c:if>
        </div>
    </nav>
</c:if>

<main class="${bare == 'true' ? '' : 'container-xxl py-4'}">
    <c:if test="${not empty flash}">
        <c:set var="flashClass" value="${flash.level == 'error' ? 'danger' : (flash.level == 'info' ? 'info' : 'success')}"/>
        <div class="alert alert-${flashClass} alert-dismissible fade show" role="alert" data-autodismiss="6000">
            <c:out value="${flash.text}"/>
            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
        </div>
    </c:if>
    <jsp:doBody/>
</main>

<c:if test="${bare != 'true'}">
    <footer class="app-footer text-center text-muted small py-3">
        &copy; MindBridgeX Solutions Pvt. Ltd. &middot; All times shown in <c:out value="${fmt.zoneName()}"/>
    </footer>
</c:if>

<script src="<c:url value='/webjars/bootstrap/5.3.3/js/bootstrap.bundle.min.js'/>"></script>
<script src="<c:url value='/js/app.js'/>"></script>
<c:if test="${not empty js}"><script src="<c:url value='/js/${js}'/>"></script></c:if>
</body>
</html>
