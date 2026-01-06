package works.weave.socks.cart.util;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class CartsCommonHelper {

    private CartsCommonHelper() {
        // utility class
    }

    public static boolean envTrue(String key) {
        String v = System.getenv(key);
        return v != null &&
                ("1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v));
    }

    public static String header(String name) {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null || attrs.getRequest() == null) {
                return null;
            }
            return attrs.getRequest().getHeader(name);
        } catch (Exception e) {
            return null;
        }
    }
}
