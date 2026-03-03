package works.weave.socks.cart.util;

import brave.Span;

public final class TracingExceptionTagger {
    private TracingExceptionTagger() {
    }

    public static void tagException(Span span, Throwable throwable) {
        if (span == null || throwable == null) {
            return;
        }

        String type = throwable.getClass().getName();
        String message = throwable.getMessage();

        span.tag("error.type", type);
        span.tag("exception.type", type);
        if (message != null && !message.isBlank()) {
            span.tag("error.message", message);
            span.tag("exception.message", message);
        }
    }
}
