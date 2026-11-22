package com.mindbridgex.assessment.config;

import jakarta.el.ELContext;
import jakarta.el.ELResolver;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.jsp.JspFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.beans.FeatureDescriptor;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tomcat 10.1 ships EL 5 which cannot read Java record components ({@code ${row.title}}). This resolver adds that so JSPs
 * can use records returned by services. It is registered when the first filter is initialised, i.e. after Jasper has
 * started and before the first page is compiled.
 */
@Configuration
public class RecordElResolver extends ELResolver {

    private static final Logger log = LoggerFactory.getLogger(RecordElResolver.class);
    private static final ConcurrentHashMap<String, Method> CACHE = new ConcurrentHashMap<>();

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        if (base == null || property == null || !base.getClass().isRecord()) {
            return null;
        }
        Method m = accessor(base.getClass(), property.toString());
        if (m == null) {
            return null;
        }
        try {
            Object v = m.invoke(base);
            context.setPropertyResolved(base, property);
            return v;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Method accessor(Class<?> type, String name) {
        String key = type.getName() + "#" + name;
        Method cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            // only record components - not arbitrary methods
            for (var rc : type.getRecordComponents()) {
                if (rc.getName().equals(name)) {
                    Method m = rc.getAccessor();
                    m.setAccessible(true);
                    CACHE.put(key, m);
                    return m;
                }
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return null;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        if (base != null && base.getClass().isRecord() && property != null && accessor(base.getClass(), property.toString()) != null) {
            context.setPropertyResolved(base, property);
        }
        return null;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        // read only
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        if (base != null && base.getClass().isRecord() && property != null && accessor(base.getClass(), property.toString()) != null) {
            context.setPropertyResolved(base, property);
            return true;
        }
        return false;
    }

    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
        return null;
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return null;
    }

    @Bean
    public FilterRegistrationBean<Filter> recordElResolverRegistrar() {
        Filter registrar = new Filter() {
            @Override
            public void init(FilterConfig filterConfig) {
                try {
                    JspFactory factory = JspFactory.getDefaultFactory();
                    if (factory != null) {
                        factory.getJspApplicationContext(filterConfig.getServletContext()).addELResolver(new RecordElResolver());
                        log.info("Record support registered for JSP expression language");
                    } else {
                        log.warn("JspFactory not available; record properties will not be readable in JSPs");
                    }
                } catch (RuntimeException e) {
                    log.warn("Could not register record EL resolver: {}", e.toString());
                }
            }

            @Override
            public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
                chain.doFilter(req, res);
            }
        };
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>(registrar);
        bean.setName("recordElResolverRegistrar");
        bean.setOrder(Integer.MIN_VALUE);
        bean.addUrlPatterns("/__el_registrar__");
        bean.setAsyncSupported(true);
        return bean;
    }
}
