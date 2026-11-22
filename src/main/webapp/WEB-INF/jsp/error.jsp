<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<c:set var="code" value="${not empty status ? status : requestScope['jakarta.servlet.error.status_code']}"/>
<c:set var="heading" value="${not empty title ? title : 'Error'}"/>
<c:choose>
    <c:when test="${code == 403}"><c:set var="msg" value="You are not allowed to access this page."/></c:when>
    <c:when test="${code == 404}"><c:set var="msg" value="The page you are looking for does not exist."/></c:when>
    <c:when test="${not empty message}"><c:set var="msg" value="${message}"/></c:when>
    <c:otherwise><c:set var="msg" value="Something went wrong. Please try again."/></c:otherwise>
</c:choose>
<t:layout title="${heading}">
    <div class="row justify-content-center">
        <div class="col-md-7 col-lg-5">
            <div class="card text-center">
                <div class="card-body p-5">
                    <div class="display-4 fw-bold text-primary mb-2"><c:out value="${empty code ? '!' : code}"/></div>
                    <h1 class="h4 mb-3"><c:out value="${heading}"/></h1>
                    <p class="text-muted"><c:out value="${msg}"/></p>
                    <a class="btn btn-primary" href="<c:url value='/'/>">Back to home</a>
                </div>
            </div>
        </div>
    </div>
</t:layout>
