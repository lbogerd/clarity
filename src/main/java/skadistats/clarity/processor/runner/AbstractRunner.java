package skadistats.clarity.processor.runner;

import org.slf4j.Logger;
import skadistats.clarity.ClarityExceptionHandler;
import skadistats.clarity.LogChannel;
import skadistats.clarity.event.InsertEvent;
import skadistats.clarity.event.Provides;
import skadistats.clarity.io.Util;
import skadistats.clarity.logger.PrintfLoggerFactory;
import skadistats.clarity.model.EngineType;

import static java.util.Objects.requireNonNull;

@Provides({OnInit.class})
public abstract class AbstractRunner implements Runner {

    protected static final Logger log = PrintfLoggerFactory.getLogger(LogChannel.runner);

    @InsertEvent
    private OnInit.Event evInitRun;

    protected final EngineType engineType;
    protected final RunnerFilters filters;
    protected Context context;
    protected ClarityExceptionHandler exceptionHandler = (eventType, parameters, throwable) -> Util.uncheckedThrow(throwable);

    public AbstractRunner(EngineType engineType) {
        this(engineType, RunnerFilters.ALL);
    }

    public AbstractRunner(EngineType engineType, RunnerFilters filters) {
        this.engineType = engineType;
        this.filters = requireNonNull(filters, "filters");
    }

    private ExecutionModel createExecutionModel(Object... processors) {
        var executionModel = new ExecutionModel(this);
        addProcessorsToModel(executionModel, processors);
        return executionModel;
    }

    private void addProcessorsToModel(ExecutionModel executionModel, Object[] processors) {
        for (var p : processors) {
            if (p instanceof Object[]) {
                addProcessorsToModel(executionModel, (Object[]) p);
            } else {
                executionModel.addProcessor(p);
            }
        }
    }

    protected void initWithProcessors(Object... processors) {
        var em = createExecutionModel(processors);
        context = new Context(em, engineType.getContextData());
        em.initialize(context);
        if (evInitRun != null) {
            evInitRun.raise();
        }
    }

    @Override
    public EngineType getEngineType() {
        return engineType;
    }

    @Override
    public Context getContext() {
        return context;
    }

    @Override
    public ClarityExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    @Override
    public RunnerFilters getFilters() {
        return filters;
    }

    public void setExceptionHandler(ClarityExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

}
