<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Sign in" bare="true">
    <div class="login-wrap">
        <div class="login-card px-3">
            <div class="text-center mb-4">
                <img src="<c:url value='/img/favicon.svg'/>" alt="" width="56" height="56" class="mb-2">
                <h1 class="h3 page-title mb-0">MindBridgeX Assess</h1>
                <div class="text-muted">Online assessment platform</div>
            </div>
            <div class="card">
                <div class="card-body p-4">
                    <c:if test="${not empty flash}">
                        <div class="alert alert-${flash.level == 'error' ? 'danger' : (flash.level == 'info' ? 'info' : 'success')} py-2"><c:out value="${flash.text}"/></div>
                    </c:if>
                    <c:if test="${not empty error}">
                        <div class="alert alert-danger py-2" role="alert"><c:out value="${error}"/></div>
                    </c:if>
                    <form method="post" action="<c:url value='/auth/login'/>" autocomplete="on">
                        <t:csrf/>
                        <input type="hidden" name="next" value="<c:out value='${next}'/>">
                        <div class="mb-3">
                            <label class="form-label" for="username">Username</label>
                            <input class="form-control form-control-lg" id="username" name="username" type="text"
                                   value="<c:out value='${username}'/>" required autofocus autocomplete="username"
                                   maxlength="60">
                        </div>
                        <div class="mb-4">
                            <label class="form-label" for="password">Password</label>
                            <input class="form-control form-control-lg" id="password" name="password" type="password"
                                   required autocomplete="current-password" maxlength="72">
                        </div>
                        <button class="btn btn-primary btn-lg w-100" type="submit">Sign in</button>
                    </form>
                </div>
            </div>
            <p class="text-center text-muted small mt-3"><a href="<c:url value='/forgot-password'/>">Forgot your password?</a></p>
        </div>
    </div>
</t:layout>
