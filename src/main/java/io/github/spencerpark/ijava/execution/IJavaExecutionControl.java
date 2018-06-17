/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 Spencer Park
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.spencerpark.ijava.execution;

import jdk.jshell.EvalException;
import jdk.jshell.execution.DirectExecutionControl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * An ExecutionControl very similar to {@link jdk.jshell.execution.LocalExecutionControl} but which
 * also logs the actual result of an invocation before being serialized.
 */
public class IJavaExecutionControl extends DirectExecutionControl {
    /**
     * A special "class name" for a {@link jdk.jshell.spi.ExecutionControl.UserException} such that it may be
     * identified after serialization into an {@link jdk.jshell.EvalException} via {@link EvalException#getExceptionClassName()}.
     */
    public static final String EXECUTION_TIMEOUT_NAME = "Execution Timeout"; // Has spaces to not collide with a class name

    private static final Object NULL = new Object();

    private final ExecutorService executor;

    private final long timeoutTime;
    private final TimeUnit timeoutUnit;

    private volatile Future<Object> runningTask;

    private final Map<String, Object> results = new ConcurrentHashMap<>();

    public IJavaExecutionControl() {
        this(-1, TimeUnit.MILLISECONDS);
    }

    public IJavaExecutionControl(long timeoutTime, TimeUnit timeoutUnit) {
        this.timeoutTime = timeoutTime;
        this.timeoutUnit = timeoutTime > 0 ? Objects.requireNonNull(timeoutUnit) : TimeUnit.MILLISECONDS;
        this.executor = Executors.newSingleThreadExecutor((r) -> new Thread(r, "IJava-executor"));
    }

    public long getTimeoutDuration() {
        return timeoutTime;
    }

    public TimeUnit getTimeoutUnit() {
        return timeoutUnit;
    }

    public Object takeResult(String key) {
        Object result = this.results.remove(key);
        if (result == null)
            throw new IllegalStateException("No result with key: " + key);
        return result == NULL ? null : result;
    }

    private Object execute(Method doitMethod) throws TimeoutException, Exception {
        this.runningTask = this.executor.submit(() -> doitMethod.invoke(null));

        try {
            if (this.timeoutTime > 0)
                return this.runningTask.get(this.timeoutTime, this.timeoutUnit);
            return this.runningTask.get();
        } catch (CancellationException e) {
            // If canceled this means that stop() was invoked in which case the protocol is to
            // throw an ExecutionControl.StoppedException.
            throw new StoppedException();
        } catch (ExecutionException e) {
            // The execution threw an exception. The actual exception is the cause of the ExecutionException.
            Throwable cause = e.getCause();
            if (cause instanceof InvocationTargetException) {
                // Unbox further
                cause = cause.getCause();
            }
            if (cause == null)
                throw new UserException("null", "Unknown Invocation Exception", e.getStackTrace());
            else
                throw new UserException(String.valueOf(cause.getMessage()), String.valueOf(cause.getClass().getName()), cause.getStackTrace());
        } catch (TimeoutException e) {
            this.stop();
            throw new UserException(
                    String.format("Execution timed out after configured timeout of %d %s.", this.timeoutTime, this.timeoutUnit.toString().toLowerCase()),
                    EXECUTION_TIMEOUT_NAME,
                    new StackTraceElement[]{} // The trace is irrelevant because it is in the kernel space not the user space so leave it blank.
            );
        }
    }

    /**
     * This method was hijacked and actually only returns a key that can be
     * later retrieved via {@link #takeResult(String)}. This should be called
     * for every invocation as the objects are saved and not taking them will
     * leak the memory.
     * <p></p>
     * {@inheritDoc}
     *
     * @returns the key to use for {@link #takeResult(String) looking up the result}.
     */
    @Override
    protected String invoke(Method doitMethod) throws Exception {
        Object value = this.execute(doitMethod);
        String id = UUID.randomUUID().toString();
        this.results.put(id, value);
        return id;
    }

    @Override
    public void stop() throws EngineTerminationException, InternalException {
        if (this.runningTask != null)
            this.runningTask.cancel(true);
    }

    @Override
    public String toString() {
        return "IJavaExecutionControl{" +
                "timeoutTime=" + timeoutTime +
                ", timeoutUnit=" + timeoutUnit +
                '}';
    }
}