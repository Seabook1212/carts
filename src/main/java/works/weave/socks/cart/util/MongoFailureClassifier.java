package works.weave.socks.cart.util;

import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.MongoSocketOpenException;
import com.mongodb.MongoSocketReadException;
import com.mongodb.MongoSocketWriteException;
import com.mongodb.MongoTimeoutException;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Locale;

public final class MongoFailureClassifier {
    private MongoFailureClassifier() {
    }

    public static String classify(Throwable throwable) {
        Throwable root = rootCause(throwable);
        if (root == null) {
            return "db.operation_failed";
        }

        if (root instanceof MongoTimeoutException || root instanceof MongoExecutionTimeoutException) {
            return "db.timeout";
        }

        if (root instanceof MongoSocketOpenException
                || root instanceof MongoSocketReadException
                || root instanceof MongoSocketWriteException
                || root instanceof DataAccessResourceFailureException) {
            return "db.connection";
        }

        String message = root.getMessage();
        if (message != null) {
            String normalized = message.toLowerCase(Locale.ROOT);
            if (normalized.contains("timed out") || normalized.contains("timeout")) {
                return "db.timeout";
            }
            if (normalized.contains("wait queue")
                    || normalized.contains("connection pool")
                    || normalized.contains("checkout")) {
                return "db.pool_exhausted";
            }
        }

        return "db.operation_failed";
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
