/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.engine.depgraph;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opengamma.OpenGammaRuntimeException;
import com.opengamma.engine.ComputationTargetResolver;
import com.opengamma.engine.function.FunctionCompilationContext;
import com.opengamma.engine.function.resolver.CompiledFunctionResolver;
import com.opengamma.engine.marketdata.availability.MarketDataAvailabilityProvider;
import com.opengamma.engine.value.ValueRequirement;
import com.opengamma.engine.value.ValueSpecification;
import com.opengamma.util.ArgumentChecker;
import com.opengamma.util.Cancellable;

/**
 * Builds a dependency graph that describes how to calculate values that will satisfy a given
 * set of value requirements. Although a graph builder may itself use additional threads to
 * complete the graph it is only safe for a single calling thread to call any of the public
 * methods at any one time. If multiple threads are to attempt to add targets to the graph
 * concurrently, it is possible to synchronize on the builder instance.
 */
public final class DependencyGraphBuilder {

  private static final Logger s_loggerBuilder = LoggerFactory.getLogger(DependencyGraphBuilder.class);
  private static final Logger s_loggerResolver = LoggerFactory.getLogger(RequirementResolver.class);
  private static final Logger s_loggerContext = LoggerFactory.getLogger(GraphBuildingContext.class);

  private static final AtomicInteger s_nextObjectId = new AtomicInteger();
  private static final AtomicInteger s_nextDebugGraphId = new AtomicInteger();

  private static final boolean NO_BACKGROUND_THREADS = false; // DON'T CHECK IN WITH =true
  private static final int MAX_ADDITIONAL_THREADS = -1; // DON'T CHECK IN WITH !=-1
  private static final boolean DEBUG_DUMP_DEPENDENCY_GRAPH = true; // DON'T CHECK IN WITH =true
  private static final int MAX_CALLBACK_DEPTH = 16;

  private static int s_defaultMaxAdditionalThreads = NO_BACKGROUND_THREADS ? 0 : (MAX_ADDITIONAL_THREADS >= 0) ? MAX_ADDITIONAL_THREADS : Runtime.getRuntime().availableProcessors();

  public static int getDefaultMaxAdditionalThreads() {
    return s_defaultMaxAdditionalThreads;
  }

  private static final class RequirementResolver extends AggregateResolvedValueProducer {

    private final ResolveTask _parentTask;
    private final Set<ResolveTask> _tasks = new HashSet<ResolveTask>();

    public RequirementResolver(final ValueRequirement valueRequirement, final ResolveTask parentTask) {
      super(valueRequirement);
      s_loggerResolver.debug("Created requirement resolver {}/{}", valueRequirement, parentTask);
      _parentTask = parentTask;
    }

    protected void addTask(final GraphBuildingContext context, final ResolveTask task) {
      if (_tasks.add(task)) {
        addProducer(context, task);
      }
    }

    @Override
    protected boolean finished(final GraphBuildingContext context) {
      boolean addFallback = false;
      synchronized (this) {
        if (getPendingTasks() == 0) {
          addFallback = true;
          setPendingTasks(-1);
        }
      }
      if (addFallback) {
        final ResolveTask task = context.getOrCreateTaskResolving(getValueRequirement(), _parentTask);
        if (_tasks.add(task)) {
          s_loggerResolver.debug("Creating fallback task {}", task);
          task.addCallback(context, this);
          return false;
        } else {
          return super.finished(context);
        }
      } else {
        return super.finished(context);
      }
    }

    @Override
    public String toString() {
      return "Resolve" + getObjectId() + "[" + getValueRequirement() + ", " + _parentTask + "]";
    }

  }

  /**
   * Algorithm state. A context object is used by a single job thread. Objects referenced by the context may be shared with other
   * contexts however. The root context from which all per-thread contexts are cloned is not used by any builder thread. The
   * synchronization on the collation methods only is therefore sufficient.
   */
  public final class GraphBuildingContext {

    // This data is shared by all of the per-thread context builders

    private String _calculationConfigurationName;
    private MarketDataAvailabilityProvider _marketDataAvailabilityProvider;
    private ComputationTargetResolver _targetResolver;
    private CompiledFunctionResolver _functionResolver;
    private FunctionCompilationContext _compilationContext;
    // Note that the requirements and specifications maps could use "soft" or "weak" references; if data is missing then the work
    // will simply be repeated. This will not be time efficient, but could be useful in low memory situations or for big graphs as
    // the intermediate resolution fragments for securities that have already been processed and are disjoint from other parts of
    // the graph will not be needed again.
    private final ConcurrentMap<ValueRequirement, ConcurrentMap<ResolveTask, ResolveTask>> _requirements;
    private final ConcurrentMap<ValueSpecification, ConcurrentMap<ResolveTask, ResolvedValueProducer>> _specifications;

    // This data is per-thread

    private Map<ExceptionWrapper, ExceptionWrapper> _exceptions;
    private int _stackDepth;

    private GraphBuildingContext() {
      s_loggerContext.info("Created new context");
      _requirements = new ConcurrentHashMap<ValueRequirement, ConcurrentMap<ResolveTask, ResolveTask>>();
      _specifications = new ConcurrentHashMap<ValueSpecification, ConcurrentMap<ResolveTask, ResolvedValueProducer>>();
    }

    private GraphBuildingContext(final GraphBuildingContext copyFrom) {
      setCalculationConfigurationName(copyFrom.getCalculationConfigurationName());
      setMarketDataAvailabilityProvider(copyFrom.getMarketDataAvailabilityProvider());
      setTargetResolver(copyFrom.getTargetResolver());
      setFunctionResolver(copyFrom.getFunctionResolver());
      setCompilationContext(copyFrom.getCompilationContext());
      _requirements = copyFrom._requirements;
      _specifications = copyFrom._specifications;
    }

    // Configuration & resources

    public String getCalculationConfigurationName() {
      return _calculationConfigurationName;
    }

    private void setCalculationConfigurationName(final String calculationConfigurationName) {
      _calculationConfigurationName = calculationConfigurationName;
    }

    public MarketDataAvailabilityProvider getMarketDataAvailabilityProvider() {
      return _marketDataAvailabilityProvider;
    }

    private void setMarketDataAvailabilityProvider(final MarketDataAvailabilityProvider marketDataAvailabilityProvider) {
      _marketDataAvailabilityProvider = marketDataAvailabilityProvider;
    }

    public ComputationTargetResolver getTargetResolver() {
      return _targetResolver;
    }

    private void setTargetResolver(final ComputationTargetResolver targetResolver) {
      _targetResolver = targetResolver;
    }

    public CompiledFunctionResolver getFunctionResolver() {
      return _functionResolver;
    }

    private void setFunctionResolver(final CompiledFunctionResolver functionResolver) {
      _functionResolver = functionResolver;
    }

    public FunctionCompilationContext getCompilationContext() {
      return _compilationContext;
    }

    private void setCompilationContext(final FunctionCompilationContext compilationContext) {
      _compilationContext = compilationContext;
    }

    // Operations

    /**
     * Schedule the task for execution.
     * 
     * @param runnable task to execute, not {@code null}
     */
    public void run(final ResolveTask runnable) {
      s_loggerContext.debug("Running {}", runnable);
      addToRunQueue(runnable);
    }

    /**
     * Trigger an underlying pump operation. This may happen before returning or be deferred if the stack is past a
     * depth threshold.
     * 
     * @param pump underlying operation
     */
    public void pump(final ResolutionPump pump) {
      s_loggerContext.debug("Pumping {}", pump);
      if (++_stackDepth > MAX_CALLBACK_DEPTH) {
        addToRunQueue(new ResolutionPump.Pump(pump));
      } else {
        pump.pump(this);
      }
      _stackDepth--;
    }

    /**
     * Trigger a resolved callback.
     * 
     * @param callback callback object
     * @param valueRequirement requirement resolved
     * @param resolvedValue value resolved to
     * @param pump source of the next value
     */
    public void resolved(final ResolvedValueCallback callback, final ValueRequirement valueRequirement, final ResolvedValue resolvedValue, final ResolutionPump pump) {
      s_loggerContext.debug("Resolved {} to {}", valueRequirement, resolvedValue);
      _stackDepth++;
      // Note that NextFunctionStep does the finished transaction too early if we schedule resolved messages arbitrarily 
      callback.resolved(this, valueRequirement, resolvedValue, pump);
      _stackDepth--;
    }

    /**
     * Trigger a resolution failure.
     * 
     * @param callback callback object
     * @param valueRequirement requirement that failed to resolve or for which there are no further resolutions
     */
    public void failed(final ResolvedValueCallback callback, final ValueRequirement valueRequirement) {
      s_loggerContext.debug("Couldn't resolve {}", valueRequirement);
      _stackDepth++;
      // Note that NextFunctionStep does the finished transaction too early if we schedule resolved messages arbitrarily 
      callback.failed(this, valueRequirement);
      _stackDepth--;
    }

    /**
     * Stores an exception that should be reported to the user. Only store the first copy of an exception; after that increment
     * the count of times that it occurred.
     * 
     * @param t exception to store, not {@code null}
     */
    public void exception(final Throwable t) {
      s_loggerContext.debug("Caught exception", t);
      if (_exceptions == null) {
        _exceptions = new HashMap<ExceptionWrapper, ExceptionWrapper>();
      }
      ExceptionWrapper.createAndPut(t, _exceptions);
    }

    public ResolvedValueProducer resolveRequirement(final ValueRequirement requirement, final ResolveTask dependent) {
      s_loggerResolver.debug("Resolve requirement {}", requirement);
      if ((dependent != null) && dependent.hasParent(requirement)) {
        s_loggerResolver.debug("Can't introduce a ValueRequirement loop");
        return new ResolvedValueProducer() {

          @Override
          public Cancellable addCallback(final GraphBuildingContext context, final ResolvedValueCallback callback) {
            context.failed(callback, requirement);
            return null;
          }

          @Override
          public String toString() {
            return "ResolvedValueProducer[" + requirement + "]";
          }

        };
      }
      RequirementResolver resolver = null;
      for (ResolveTask task : getTasksResolving(requirement)) {
        if ((dependent != null) && dependent.hasParent(task)) {
          // Can't use this task; a loop would be introduced
          continue;
        }
        if (resolver == null) {
          resolver = new RequirementResolver(requirement, dependent);
        }
        resolver.addTask(this, task);
      }
      if (resolver != null) {
        resolver.start(this);
        return resolver;
      } else {
        s_loggerResolver.debug("Using direct resolution {}/{}", requirement, dependent);
        return getOrCreateTaskResolving(requirement, dependent);
      }
    }

    private ResolveTask getOrCreateTaskResolving(final ValueRequirement valueRequirement, final ResolveTask parentTask) {
      final ResolveTask newTask = new ResolveTask(valueRequirement, parentTask);
      ConcurrentMap<ResolveTask, ResolveTask> tasks = _requirements.get(valueRequirement);
      if (tasks == null) {
        tasks = new ConcurrentHashMap<ResolveTask, ResolveTask>();
        tasks.put(newTask, newTask);
        tasks = _requirements.putIfAbsent(valueRequirement, tasks);
        if (tasks == null) {
          addToRunQueue(newTask);
          return newTask;
        }
      }
      final ResolveTask existingTask = tasks.putIfAbsent(newTask, newTask);
      if (existingTask == null) {
        addToRunQueue(newTask);
        return newTask;
      } else {
        return existingTask;
      }
    }

    private Set<ResolveTask> getTasksResolving(final ValueRequirement valueRequirement) {
      final ConcurrentMap<ResolveTask, ResolveTask> tasks = _requirements.get(valueRequirement);
      if (tasks == null) {
        return Collections.emptySet();
      } else {
        return tasks.keySet();
      }
    }

    public Map<ResolveTask, ResolvedValueProducer> getTasksProducing(final ValueSpecification valueSpecification) {
      final Map<ResolveTask, ResolvedValueProducer> tasks = _specifications.get(valueSpecification);
      if (tasks == null) {
        return Collections.emptyMap();
      } else {
        return tasks;
      }
    }

    public ResolvedValueProducer declareTaskProducing(final ValueSpecification valueSpecification, final ResolveTask task, final ResolvedValueProducer producer) {
      ConcurrentMap<ResolveTask, ResolvedValueProducer> tasks = _specifications.get(valueSpecification);
      if (tasks == null) {
        tasks = new ConcurrentHashMap<ResolveTask, ResolvedValueProducer>();
        tasks.put(task, producer);
        tasks = _specifications.putIfAbsent(valueSpecification, tasks);
        if (tasks == null) {
          return producer;
        }
      }
      final ResolvedValueProducer existing = tasks.putIfAbsent(task, producer);
      if (existing == null) {
        return producer;
      } else {
        return existing;
      }
    }

    // Collation

    private synchronized void mergeThreadContext (final GraphBuildingContext context) {
      if (_exceptions == null) {
        _exceptions = new HashMap<ExceptionWrapper, ExceptionWrapper>();
      }
      if (context._exceptions != null) {
        for (ExceptionWrapper exception : context._exceptions.keySet()) {
          final ExceptionWrapper existing = _exceptions.get(exception);
          if (existing != null) {
            existing.incrementCount(exception.getCount());
          } else {
            _exceptions.put(exception, exception);
          }
        }
      }
    }

    private synchronized Map<Throwable, Integer> getExceptions() {
      if (_exceptions == null) {
        return Collections.emptyMap();
      }
      final Map<Throwable, Integer> result = new HashMap<Throwable, Integer>();
      for (ExceptionWrapper exception : _exceptions.keySet()) {
        result.put(exception.getException(), exception.getCount());
      }
      return result;
    }

  };

  private final int _objectId = s_nextObjectId.incrementAndGet();
  private final AtomicInteger _activeJobCount = new AtomicInteger();
  private final Set<Job> _activeJobs = new HashSet<Job>();
  private final Queue<ContextRunnable> _runQueue = new ConcurrentLinkedQueue<ContextRunnable>();
  private final Set<DependencyNode> _graphNodes = Collections.synchronizedSet(new HashSet<DependencyNode>());
  private final Map<ValueRequirement, ValueSpecification> _terminalOutputs = new ConcurrentHashMap<ValueRequirement, ValueSpecification>();
  private final GraphBuildingContext _context = new GraphBuildingContext();
  private final AtomicLong _completedSteps = new AtomicLong();
  private final AtomicLong _scheduledSteps = new AtomicLong();
  private final ResolvedValueCallback _getTerminalValuesCallback = new GetTerminalValuesCallback(_graphNodes, _terminalOutputs);
  private final Executor _executor;

  /**
   * Number of additional threads to launch while requirements are being added or the graph is being built.
   * The total number of threads used for graph construction may be up to this value or may be one higher
   * as a thread blocked on graph construction in the call to {@link #getDependencyGraph} will join in with
   * the remaining construction.
   */
  private volatile int _maxAdditionalThreads = getDefaultMaxAdditionalThreads();

  // TODO: we could have different run queues for the different states. When the PENDING one is considered, a bulk lookup operation can then be done

  // TODO: We should use an external execution framework rather than the one here; there are far better (and probably more accurate) implementations of
  // the algorithm in other projects I've worked on.

  public DependencyGraphBuilder() {
    this(DependencyGraphBuilderFactory.getDefaultExecutor());
  }

  protected DependencyGraphBuilder(final Executor executor) {
    _executor = executor;
  }

  protected GraphBuildingContext getContext() {
    return _context;
  }

  /**
   * @return the calculationConfigurationName
   */
  public String getCalculationConfigurationName() {
    return getContext().getCalculationConfigurationName();
  }

  /**
   * @param calculationConfigurationName the calculationConfigurationName to set
   */
  public void setCalculationConfigurationName(String calculationConfigurationName) {
    getContext().setCalculationConfigurationName(calculationConfigurationName);
  }

  /**
   * @return the market data availability provider
   */
  public MarketDataAvailabilityProvider getMarketDataAvailabilityProvider() {
    return getContext().getMarketDataAvailabilityProvider();
  }

  /**
   * @param marketDataAvailabilityProvider the market data availability provider to set
   */
  public void setMarketDataAvailabilityProvider(MarketDataAvailabilityProvider marketDataAvailabilityProvider) {
    getContext().setMarketDataAvailabilityProvider(marketDataAvailabilityProvider);
  }

  /**
   * @return the functionResolver
   */
  public CompiledFunctionResolver getFunctionResolver() {
    return getContext().getFunctionResolver();
  }

  /**
   * @param functionResolver the functionResolver to set
   */
  public void setFunctionResolver(CompiledFunctionResolver functionResolver) {
    getContext().setFunctionResolver(functionResolver);
  }

  /**
   * @return the targetResolver
   */
  public ComputationTargetResolver getTargetResolver() {
    return getContext().getTargetResolver();
  }

  /**
   * @param targetResolver the targetResolver to set
   */
  public void setTargetResolver(ComputationTargetResolver targetResolver) {
    getContext().setTargetResolver(targetResolver);
  }

  /**
   * @return the compilationContext
   */
  public FunctionCompilationContext getCompilationContext() {
    return getContext().getCompilationContext();
  }

  /**
   * @param compilationContext the compilationContext to set
   */
  public void setCompilationContext(FunctionCompilationContext compilationContext) {
    getContext().setCompilationContext(compilationContext);
  }

  public int getMaxAdditionalThreads() {
    return _maxAdditionalThreads;
  }

  /**
   * Sets the maximum number of background threads to use for graph building. Set to zero to disable
   * background building. When set to a non-zero amount, if there is additional pending work jobs may
   * be started.
   * 
   * @param maxAdditionalThreads maximum number of background threads to use
   */
  public void setMaxAdditionalThreads(final int maxAdditionalThreads) {
    ArgumentChecker.isTrue(maxAdditionalThreads >= 0, "maxAdditionalThreads");
    _maxAdditionalThreads = maxAdditionalThreads;
    startBackgroundBuild();
  }

  protected void checkInjectedInputs() {
    ArgumentChecker.notNullInjected(getMarketDataAvailabilityProvider(), "marketDataAvailabilityProvider");
    ArgumentChecker.notNullInjected(getFunctionResolver(), "functionResolver");
    ArgumentChecker.notNullInjected(getTargetResolver(), "targetResolver");
    ArgumentChecker.notNullInjected(getCalculationConfigurationName(), "calculationConfigurationName");
  }

  /**
   * Adds a target requirement to the graph. The requirement is queued and the call returns; construction
   * of the graph will happen on a background thread (if additional threads is non-zero), or when the
   * call to {@link #getDependencyGraph} is made. If it was not possible to satisfy the requirement that
   * must be checked after graph construction is complete.
   * 
   * @param requirement requirement to add, not {@code null}
   */
  public void addTarget(ValueRequirement requirement) {
    ArgumentChecker.notNull(requirement, "requirement");
    checkInjectedInputs();
    final ResolvedValueProducer resolvedValue = getContext().resolveRequirement(requirement, null);
    resolvedValue.addCallback(getContext(), _getTerminalValuesCallback);
    // If the run-queue was empty, we won't have started a thread, so double check 
    startBackgroundConstructionJob();
  }

  /**
   * Adds target requirements to the graph. The requirements are queued and the call returns; construction
   * of the graph will happen on a background thread (if additional threads is non-zero), or when the
   * call to {@link #getDependencyGraph} is made. If it was not possible to satisfy one or more requirements
   * that must be checked after graph construction is complete.
   * 
   * @param requirements requirements to add, not {@code null} and not containing {@code null}s.
   */
  public void addTarget(Set<ValueRequirement> requirements) {
    ArgumentChecker.noNulls(requirements, "requirements");
    checkInjectedInputs();
    for (ValueRequirement requirement : requirements) {
      final ResolvedValueProducer resolvedValue = getContext().resolveRequirement(requirement, null);
      resolvedValue.addCallback(getContext(), _getTerminalValuesCallback);
    }
    // If the run-queue was empty, we may not have started enough threads, so double check 
    startBackgroundConstructionJob();
  }

  /**
   * For compatibility with DependencyGraphBuilderFunctionalIntegrationTest in OG-Integration. When
   * branch is merged with the master, remove this.
   * 
   * @param requirement requirement to add, not {@code null}
   * @deprecated update OG-Integration and remove this when the branch is merged
   */
  @Deprecated
  protected void addTargetImpl(final ValueRequirement requirement) {
    final ResolvedValueProducer resolvedValue = getContext().resolveRequirement(requirement, null);
    resolvedValue.addCallback(getContext(), _getTerminalValuesCallback);
    startBackgroundConstructionJob();
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<RuntimeException> exception = new AtomicReference<RuntimeException>();
    resolvedValue.addCallback(getContext(), new ResolvedValueCallback() {

      @Override
      public void failed(final GraphBuildingContext context, final ValueRequirement value) {
        s_loggerBuilder.warn("Couldn't resolve {}", value);
        exception.set(new UnsatisfiableDependencyGraphException(value));
        latch.countDown();
      }

      @Override
      public void resolved(final GraphBuildingContext context, final ValueRequirement valueRequirement, final ResolvedValue resolvedValue, final ResolutionPump pump) {
        s_loggerBuilder.info("Resolved target {} to {}", valueRequirement, resolvedValue.getValueSpecification());
        exception.set(null);
        latch.countDown();
      }

      @Override
      public String toString() {
        return "AddTargetImpl[" + requirement + "]";
      }

    });
    try {
      boolean failed = true;
      for (int clock = 0; clock < 60; clock++) {
        if (latch.await(250, TimeUnit.MILLISECONDS)) {
          failed = false;
          break;
        }
        if (isGraphBuilt()) {
          if (!latch.await(0, TimeUnit.MILLISECONDS)) {
            s_loggerBuilder.warn("Graph construction stopped without failure or resolution of {}", requirement);
            throw new OpenGammaRuntimeException("Graph construction stopped without failure or resolution of " + requirement);
          }
          failed = false;
          break;
        }
      }
      if (failed) {
        s_loggerBuilder.warn("Timeout waiting for failure or resolution of {}", requirement);
        throw new OpenGammaRuntimeException("Timeout waiting for failure or resolution of " + requirement);
      }
    } catch (InterruptedException e) {
      throw new OpenGammaRuntimeException("Interrupted", e);
    }
    RuntimeException ex = exception.get();
    if (ex != null) {
      throw ex;
    }
  }

  protected void addToRunQueue(final ContextRunnable runnable) {
    s_loggerBuilder.debug("Queuing {}", runnable);
    final boolean dontSpawn = _runQueue.isEmpty();
    _runQueue.add(runnable);
    // Don't start construction jobs if the queue is empty or a sequential piece of work bounces between two threads (i.e. there
    // is already a background thread that is running the caller which can then execute the task it has just put into the run
    // queue). The moment the queue is non-empty, start a job if possible.
    if (!dontSpawn) {
      startBackgroundConstructionJob();
    }
    _scheduledSteps.incrementAndGet();
  }

  protected boolean startBackgroundConstructionJob() {
    int activeJobs = _activeJobCount.get();
    while (activeJobs < getMaxAdditionalThreads()) {
      if (_activeJobCount.compareAndSet(activeJobs, activeJobs + 1)) {
        synchronized (_activeJobs) {
          final Job job = createConstructionJob();
          _activeJobs.add(job);
          _executor.execute(job);
        }
        return true;
      }
      activeJobs = _activeJobCount.get();
    }
    return false;
  }

  /**
   * Job running thread.
   */
  protected final class Job implements Runnable, Cancellable {

    private volatile boolean _poison;

    private Job() {
    }

    @Override
    public void run() {
      s_loggerBuilder.info("Building job started for {}", DependencyGraphBuilder.this);
      boolean jobsLeftToRun;
      int completed = 0;
      do {
        // Create a new context for each logical block so that an exception from the build won't leave us with
        // an inconsistent context.
        final GraphBuildingContext context = new GraphBuildingContext(getContext());
        do {
          try {
            jobsLeftToRun = buildGraph(context);
            completed++;
          } catch (Throwable t) {
            s_loggerBuilder.warn("Graph builder exception", t);
            _context.exception(t);
            jobsLeftToRun = false;
          }
        } while (!_poison && jobsLeftToRun);
        s_loggerBuilder.debug("Merging thread context");
        getContext().mergeThreadContext(context);
        s_loggerBuilder.debug("Building job stopping");
        int activeJobs = _activeJobCount.decrementAndGet();
        // Watch for late arrivals in the run queue; they might have seen the old value
        // of activeJobs and not started anything.
        while (!_runQueue.isEmpty() && (activeJobs < getMaxAdditionalThreads()) && !_poison) {
          if (_activeJobCount.compareAndSet(activeJobs, activeJobs + 1)) {
            s_loggerBuilder.debug("Building job resuming");
            // Note the log messages may go from "resuming" to stopped if the poison arrives between
            // the check above and the check below. This might look odd, but what the hey - they're
            // only DEBUG level messages.
            jobsLeftToRun = true;
            break;
          }
          activeJobs = _activeJobCount.get();
        }
      } while (!_poison && jobsLeftToRun);
      synchronized (_activeJobs) {
        _activeJobs.remove(this);
      }
      s_loggerBuilder.info("{} building job stopped after {} operations", this, completed);
    }

    @Override
    public boolean cancel(final boolean mayInterrupt) {
      _poison = true;
      return true;
    }

  }

  protected Job createConstructionJob() {
    return new Job();
  }

  /**
   * Main process loop, takes a runnable task and executes it. If the graph has not been built when
   * getDependencyGraph is called, the calling thread will also join this. There are additional
   * threads that also run in a pool to complete the work of the graph building.
   * 
   * @param context the calling thread's building context
   * @return true if there is more work still to do, false if all the work is done
   */
  protected boolean buildGraph(final GraphBuildingContext context) {
    final ContextRunnable task = _runQueue.poll();
    if (task == null) {
      return false;
    }
    task.run(context);
    _completedSteps.incrementAndGet();
    return true;
  }

  /**
   * Tests if the graph has been built or if work is still required. Graphs are only built in the
   * background if additional threads is set to non-zero.
   * 
   * @return {@code true} if the graph has been built, {@code false} if it is outstanding.
   */
  public boolean isGraphBuilt() {
    synchronized (_activeJobs) {
      if (!_activeJobs.isEmpty()) {
        // One or more active jobs, so can't be built yet
        return false;
      }
    }
    // no active jobs, so built if there is nothing in the run queue
    return _runQueue.isEmpty();
  }

  /**
   * Returns the dependency graph if it has been completed by background threads. If the graph has
   * not been completed it will return {@code null}. If the number of additional threads is set to
   * zero then the graph will not be built until {@link #getDependencyGraph} is called.
   * 
   * @return the graph if built or {@code null} otherwise
   */
  public DependencyGraph pollDependencyGraph() {
    if (isGraphBuilt()) {
      return createDependencyGraph();
    }
    return null;
  }

  /**
   * Cancels any construction threads. If background threads had been started for graph construction, they
   * will be stopped and the construction abandoned. Note that this will also reset the number of
   * additional threads to zero to prevent further threads from being started by the existing ones before
   * they terminate. If a thread is already blocked in a call to {@link getDependencyGraph} it will receive
   * a {@link CancellationException} unless the graph construction completes before the cancellation is
   * noted by that or other background threads. The cancellation is temporary, the additional threads
   * can be reset afterwards for continued background building or a subsequent call to getDependencyGraph
   * can finish the work.
   */
  public void cancelActiveBuild() {
    setMaxAdditionalThreads(0);
    synchronized (_activeJobs) {
      for (Job job : _activeJobs) {
        job.cancel(true);
      }
      _activeJobs.clear();
    }
  }

  /**
   * If there are runnable tasks but not as many active jobs as the requested number then additional threads
   * will be started. This is called when the number of background threads is changed.
   */
  protected void startBackgroundBuild() {
    if (_runQueue.isEmpty()) {
      s_loggerBuilder.info("No pending runnable tasks for background building");
    } else {
      final Iterator<ContextRunnable> itr = _runQueue.iterator();
      while (itr.hasNext() && startBackgroundConstructionJob()) {
        itr.next();
      }
    }
  }

  /**
   * Estimate the completion of the build, from 0 (nothing completed) to 1 (all done). The completion is based on
   * the number of completed steps versus the currently known number of steps.
   * 
   * @return the completion estimate
   */
  public double estimateBuildFraction() {
    // Note that this will break for big jobs that are > 2^63 steps. Is this a limit that can be reasonably hit?
    // Loose synchronization okay; this is only a guesstimate
    final long completed = _completedSteps.get();
    final long scheduled = _scheduledSteps.get();
    if (scheduled <= 0) {
      return 100;
    }
    // TODO: need a better metric here; need to somehow predict/project the eventual number of "scheduled" steps
    s_loggerBuilder.info("Completed {} of {} scheduled steps", completed, scheduled);
    return (double) completed / (double) scheduled;
  }

  /**
   * Returns the constructed dependency graph able to compute as many of the requirements requested as
   * possible. If graph construction has not completed, will block the caller until it has and the
   * calling thread will be used for the remaining graph construction work (which will be the full
   * graph construction if additional threads is set to zero). For a non-blocking form see
   * {@link #pollDependencyGraph} or {@link #getDependencyGraph(boolean)}.
   * 
   * @return the graph, not {@code null}
   */
  public DependencyGraph getDependencyGraph() {
    return getDependencyGraph(true);
  }

  /**
   * Returns the constructed dependency graph able to compute as many of the requirements requested as
   * possible. If graph construction has not completed, the calling thread will participate in graph
   * construction (which will be the full graph construction if additional threads is set to zero). When
   * background threads are being used, the caller may optionally be blocked until all have completed.
   * For a completely non-blocking form see {@link #pollDependencyGraph}.
   * 
   * @param allowBackgroundContinuation block the caller until graph construction is complete. If set to
   *        {@code false} the function may return {@code null} if background threads are still completing
   *        but there was no work for the calling thread to do.
   * @return the graph if built or {@code null} if still being built in the background.
   */
  public DependencyGraph getDependencyGraph(final boolean allowBackgroundContinuation) {
    if (!isGraphBuilt()) {
      s_loggerBuilder.info("Building dependency graph");
      do {
        final Job job = createConstructionJob();
        synchronized (_activeJobs) {
          _activeJobs.add(job);
        }
        job.run();
        synchronized (_activeJobs) {
          if (_activeJobs.isEmpty()) {
            // We're done and there are no other jobs
            break;
          } else {
            // There are other jobs still running ...
            if (!_runQueue.isEmpty()) {
              // ... and stuff in the queue
              continue;
            }
          }
        }
        if (allowBackgroundContinuation) {
          // ... but nothing in the queue for us so take a nap
          s_loggerBuilder.info("Waiting for background threads");
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            throw new OpenGammaRuntimeException("Interrupted during graph building", e);
          }
        } else {
          return null;
        }
      } while (true);
      if (!isGraphBuilt()) {
        throw new CancellationException("Dependency graph building incomplete");
      }
    }
    return createDependencyGraph();
  }

  protected DependencyGraph createDependencyGraph() {
    final DependencyGraph graph = new DependencyGraph(getCalculationConfigurationName());
    s_loggerBuilder.debug("Converting internal representation to dependency graph");
    for (DependencyNode node : _graphNodes) {
      graph.addDependencyNode(node);
    }
    for (ValueSpecification valueSpecification : _terminalOutputs.values()) {
      graph.addTerminalOutputValue(valueSpecification);
    }
    //graph.dumpStructureASCII(System.out);
    if (DEBUG_DUMP_DEPENDENCY_GRAPH) {
      try {
        int graphFileId = s_nextDebugGraphId.getAndIncrement();
        final PrintStream ps = new PrintStream(new FileOutputStream("/tmp/dependencyGraph" + graphFileId + ".txt"));
        graph.dumpStructureASCII(ps);
        ps.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return graph;
  }

  /**
   * Returns a map of the originally requested value requirements to the value specifications that were put into the
   * graph as terminal outputs. Any unsatisfied requirements will be absent from the map.
   * 
   * @return the map of requirements to value specifications, not {@code null}
   */
  public Map<ValueRequirement, ValueSpecification> getValueRequirementMapping() {
    return new HashMap<ValueRequirement, ValueSpecification>(_terminalOutputs);
  }

  /**
   * Returns the set of exceptions that may have prevented graph construction.
   * 
   * @return the set of exceptions that were thrown by the building process, or {@code null} for none
   */
  public Map<Throwable, Integer> getExceptions() {
    return getContext().getExceptions();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "-" + _objectId;
  }

}
