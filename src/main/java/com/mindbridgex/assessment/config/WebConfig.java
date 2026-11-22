package com.mindbridgex.assessment.config;

import com.mindbridgex.assessment.util.Flash;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new FlashInterceptor())
                .excludePathPatterns("/webjars/**", "/css/**", "/js/**", "/img/**", "/api/**");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/error/403").setViewName("error");
    }

    /** Moves a pending flash cookie into a request attribute (rendered by the layout) and expires the cookie. */
    static class FlashInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            Cookie[] cookies = request.getCookies();
            if (cookies == null) {
                return true;
            }
            for (Cookie c : cookies) {
                if (Flash.COOKIE.equals(c.getName())) {
                    Flash.Message m = Flash.decode(c.getValue());
                    if (m != null) {
                        request.setAttribute("flash", m);
                    }
                    Flash.clear(response);
                    break;
                }
            }
            return true;
        }
    }
}
