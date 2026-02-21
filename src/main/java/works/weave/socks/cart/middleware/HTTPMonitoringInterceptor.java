package works.weave.socks.cart.middleware;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;

import io.prometheus.client.Histogram;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class HTTPMonitoringInterceptor implements HandlerInterceptor {
    static final Histogram requestLatency = Histogram.build()
            .name("http_request_duration_seconds")
            .help("Request duration in seconds.")
            .labelNames("service", "method", "path", "status_code")
            .register();

    private static final String startTimeKey = "startTime";

    @Value("${spring.application.name:carts}")
    private String serviceName;

    @Override
    public boolean preHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o)
            throws Exception {
        httpServletRequest.setAttribute(startTimeKey, System.nanoTime());
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o,
            ModelAndView modelAndView) throws Exception {
        Object startTimeAttr = httpServletRequest.getAttribute(startTimeKey);
        if (startTimeAttr == null) {
            return;
        }

        long start = (long) startTimeAttr;
        long elapsed = System.nanoTime() - start;
        double seconds = (double) elapsed / 1000000000.0;

        String matchedUrl = getMatchingURLPattern(httpServletRequest);
        if (matchedUrl != null && !matchedUrl.isEmpty()) {
            requestLatency.labels(
                    serviceName,
                    httpServletRequest.getMethod(),
                    matchedUrl,
                    Integer.toString(httpServletResponse.getStatus())).observe(seconds);
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
            Object o, Exception e) throws Exception {
    }

    /**
     * Get the matched URL pattern from the request attributes.
     * Spring Boot 3 uses PathPattern by default which stores the matched pattern in
     * request attributes.
     */
    private String getMatchingURLPattern(HttpServletRequest httpServletRequest) {
        // Skip error paths
        if ("/error".equals(httpServletRequest.getServletPath())) {
            return "";
        }

        // In Spring Boot 3, the best matching pattern is stored in request attributes
        Object pattern = httpServletRequest.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null) {
            return pattern.toString();
        }

        // Fallback to request URI if no pattern found
        String uri = httpServletRequest.getRequestURI();
        return uri != null ? uri : "";
    }
}
