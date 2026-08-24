package skadistats.clarity.processor.runner;

import com.google.protobuf.GeneratedMessage;
import skadistats.clarity.model.DTClass;

import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * Run-time filters for catch-all message and entity listeners.
 *
 * <p>Explicit message types and entity class patterns declared in listener
 * annotations take priority over these filters.
 */
public final class RunnerFilters {

    public static final RunnerFilters ALL = new RunnerFilters(messageClass -> true, dtClass -> true);

    private final Predicate<Class<? extends GeneratedMessage>> messageFilter;
    private final Predicate<DTClass> entityFilter;

    public RunnerFilters(
            Predicate<Class<? extends GeneratedMessage>> messageFilter,
            Predicate<DTClass> entityFilter
    ) {
        this.messageFilter = requireNonNull(messageFilter, "messageFilter");
        this.entityFilter = requireNonNull(entityFilter, "entityFilter");
    }

    public boolean allowsMessage(Class<? extends GeneratedMessage> messageClass) {
        return messageFilter.test(messageClass);
    }

    public boolean allowsEntity(DTClass dtClass) {
        return entityFilter.test(dtClass);
    }

}
