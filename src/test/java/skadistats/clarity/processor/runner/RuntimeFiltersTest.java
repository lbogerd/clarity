package skadistats.clarity.processor.runner;

import com.google.protobuf.GeneratedMessage;
import org.testng.annotations.Test;
import skadistats.clarity.ClarityExceptionHandler;
import skadistats.clarity.event.EventListener;
import skadistats.clarity.event.UsagePointMarker;
import skadistats.clarity.model.DTClass;
import skadistats.clarity.model.EngineType;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.model.engine.ContextData;
import skadistats.clarity.model.state.EntityState;
import skadistats.clarity.processor.entities.Entities;
import skadistats.clarity.processor.entities.OnEntityCreated;
import skadistats.clarity.processor.entities.OnEntityCreated_Event;
import skadistats.clarity.processor.entities.OnEntityPropertyChanged;
import skadistats.clarity.processor.reader.OnMessage;
import skadistats.clarity.processor.reader.OnMessage_Event;
import skadistats.clarity.wire.shared.demo.proto.Demo;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class RuntimeFiltersTest {

    private static final FieldPath FIELD_PATH = new FieldPath() { };

    @Test
    public void allIsTheDefaultCatchAllFilter() {
        var dtClass = new TestDTClass("CDOTA_Unit_Hero_Axe");
        var runner = new TestRunner(RunnerFilters.ALL);
        var catchAllCalls = new AtomicInteger();
        var listener = listener(MessageListeners.class, "catchAllMessage", OnMessage.class);
        listener.setListenerSam((OnMessage.Listener) message -> catchAllCalls.incrementAndGet());
        var event = new OnMessage_Event(runner, OnMessage.class, Set.of(listener));

        assertSame(runner.getContext().getFilters(), RunnerFilters.ALL);
        assertTrue(RunnerFilters.ALL.allowsMessage(Demo.CDemoStop.class));
        assertTrue(RunnerFilters.ALL.allowsEntity(dtClass));
        assertTrue(event.isListenedTo(Demo.CDemoStop.class));

        event.raise(Demo.CDemoStop.getDefaultInstance());

        assertEquals(catchAllCalls.get(), 1);
    }

    @Test
    public void rejectedMessageSkipsCatchAllButStillReachesTypedListener() {
        var filters = new RunnerFilters(
                messageClass -> messageClass != Demo.CDemoStop.class,
                dtClass -> true
        );
        var runner = new TestRunner(filters);
        var catchAllCalls = new AtomicInteger();
        var typedCalls = new AtomicInteger();
        var catchAll = listener(MessageListeners.class, "catchAllMessage", OnMessage.class);
        catchAll.setListenerSam((OnMessage.Listener) message -> catchAllCalls.incrementAndGet());
        var typed = listener(MessageListeners.class, "stopMessage", OnMessage.class);
        typed.setListenerSam((OnMessage.Listener) message -> typedCalls.incrementAndGet());
        var event = new OnMessage_Event(runner, OnMessage.class, Set.of(catchAll, typed));

        assertFalse(filters.allowsMessage(Demo.CDemoStop.class));
        assertTrue(event.isListenedTo(Demo.CDemoStop.class), "the typed listener remains interested");

        event.raise(Demo.CDemoStop.getDefaultInstance());

        assertEquals(catchAllCalls.get(), 0);
        assertEquals(typedCalls.get(), 1);
    }

    @Test
    public void rejectedEntitySkipsCatchAllButStillReachesExplicitClassPattern() {
        var rejectedClass = new TestDTClass("CDOTA_Unit_Hero_Axe");
        var runner = rejectingEntityRunner(rejectedClass);
        var catchAllCalls = new AtomicInteger();
        var explicitCalls = new AtomicInteger();
        var catchAll = listener(EntityListeners.class, "catchAllCreated", OnEntityCreated.class);
        catchAll.setListenerSam((OnEntityCreated.Listener) entity -> catchAllCalls.incrementAndGet());
        var explicit = listener(EntityListeners.class, "explicitCreated", OnEntityCreated.class);
        explicit.setListenerSam((OnEntityCreated.Listener) entity -> explicitCalls.incrementAndGet());

        var entities = new Entities();
        entities.initOnEntityCreated(runner.getContext(), catchAll);
        entities.initOnEntityCreated(runner.getContext(), explicit);
        var event = new OnEntityCreated_Event(runner, OnEntityCreated.class, Set.of(catchAll, explicit));

        event.raise(new Entity(0, 0, 0, rejectedClass));

        assertEquals(catchAllCalls.get(), 0);
        assertEquals(explicitCalls.get(), 1);
    }

    @Test
    public void explicitClassPatternTakesPriorityForPropertyChangedListener() {
        var rejectedClass = new TestDTClass("CDOTA_Unit_Hero_Axe");
        var allowCatchAll = new AtomicBoolean(false);
        var runner = new TestRunner(new RunnerFilters(
                messageClass -> true,
                dtClass -> allowCatchAll.get()
        ));
        var catchAllCalls = new AtomicInteger();
        var explicitCalls = new AtomicInteger();
        var catchAll = listener(EntityListeners.class, "catchAllPropertyChanged", OnEntityPropertyChanged.class);
        catchAll.setListenerSam((OnEntityPropertyChanged.Listener) (entity, fieldPath) -> catchAllCalls.incrementAndGet());
        var explicit = listener(EntityListeners.class, "explicitPropertyChanged", OnEntityPropertyChanged.class);
        explicit.setListenerSam((OnEntityPropertyChanged.Listener) (entity, fieldPath) -> explicitCalls.incrementAndGet());
        var event = new OnEntityPropertyChanged.Event(
                runner,
                OnEntityPropertyChanged.class,
                Set.of(catchAll, explicit)
        );

        event.raise(new Entity(0, 0, 0, rejectedClass), FIELD_PATH);

        assertEquals(catchAllCalls.get(), 0);
        assertEquals(explicitCalls.get(), 1);

        allowCatchAll.set(true);
        event.raise(new Entity(0, 0, 0, rejectedClass), FIELD_PATH);

        assertEquals(catchAllCalls.get(), 1, "the run-time predicate is evaluated for every dispatch");
        assertEquals(explicitCalls.get(), 2);
    }

    @Test
    public void constructorRejectsNullPredicates() {
        assertThrows(NullPointerException.class, () -> new RunnerFilters(null, dtClass -> true));
        assertThrows(NullPointerException.class, () -> new RunnerFilters(messageClass -> true, null));
    }

    private static TestRunner rejectingEntityRunner(DTClass rejectedClass) {
        return new TestRunner(new RunnerFilters(
                messageClass -> true,
                dtClass -> dtClass != rejectedClass
        ));
    }

    private static <A extends Annotation> EventListener<A> listener(
            Class<?> listenerClass,
            String methodName,
            Class<A> annotationClass
    ) {
        Method method = null;
        for (var candidate : listenerClass.getDeclaredMethods()) {
            if (candidate.getName().equals(methodName)) {
                method = candidate;
                break;
            }
        }
        if (method == null) {
            throw new AssertionError("missing listener method " + methodName);
        }
        var annotation = method.getAnnotation(annotationClass);
        var marker = annotationClass.getAnnotation(UsagePointMarker.class);
        return new EventListener<>(annotation, listenerClass, method, marker);
    }

    private static final class MessageListeners {
        @OnMessage
        private void catchAllMessage(GeneratedMessage message) {
        }

        @OnMessage(Demo.CDemoStop.class)
        private void stopMessage(Demo.CDemoStop message) {
        }
    }

    private static final class EntityListeners {
        @OnEntityCreated
        private void catchAllCreated(Entity entity) {
        }

        @OnEntityCreated(classPattern = "CDOTA_Unit_Hero_Axe")
        private void explicitCreated(Entity entity) {
        }

        @OnEntityPropertyChanged
        private void catchAllPropertyChanged(Entity entity, FieldPath fieldPath) {
        }

        @OnEntityPropertyChanged(classPattern = "CDOTA_Unit_Hero_Axe")
        private void explicitPropertyChanged(Entity entity, FieldPath fieldPath) {
        }
    }

    private static final class TestDTClass implements DTClass {
        private final String dtName;
        private int classId;

        private TestDTClass(String dtName) {
            this.dtName = dtName;
        }

        @Override
        public String getDtName() {
            return dtName;
        }

        @Override
        public int getClassId() {
            return classId;
        }

        @Override
        public void setClassId(int classId) {
            this.classId = classId;
        }

        @Override
        public EntityState getEmptyState() {
            return null;
        }

        @Override
        public String getNameForFieldPath(FieldPath fieldPath) {
            return "m_value";
        }

        @Override
        public FieldPath getFieldPathForName(String property) {
            return FIELD_PATH;
        }
    }

    private static final class TestRunner implements Runner {
        private final RunnerFilters filters;
        private final Context context;

        private TestRunner(RunnerFilters filters) {
            this.filters = filters;
            this.context = new Context(new ExecutionModel(this), new ContextData());
        }

        @Override
        public Context getContext() {
            return context;
        }

        @Override
        public int getTick() {
            return 0;
        }

        @Override
        public EngineType getEngineType() {
            return null;
        }

        @Override
        public ClarityExceptionHandler getExceptionHandler() {
            return (eventType, parameters, throwable) -> {
                throw new AssertionError("listener failed for " + eventType.getName(), throwable);
            };
        }

        @Override
        public RunnerFilters getFilters() {
            return filters;
        }
    }
}
