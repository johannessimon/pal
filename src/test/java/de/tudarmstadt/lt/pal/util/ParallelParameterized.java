package de.tudarmstadt.lt.pal.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.runners.Parameterized;
import org.junit.runners.model.RunnerScheduler;

/**
 * Parallel JUnit test case runner,
 * taken from http://sqa.stackexchange.com/questions/6982/junit-parallelcomputer-runs-all-test-classes-as-one
 */
public class ParallelParameterized extends Parameterized {

    public ParallelParameterized(Class<?> arg0) throws Throwable {

        super(arg0);

        setScheduler(new RunnerScheduler() {

            private final ExecutorService service = Executors.newFixedThreadPool(4);

            public void schedule(Runnable childStatement) {
                service.submit(childStatement);
            }

            public void finished() {
                try {
                    service.shutdown();
                    service.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                }
            }
        });
    }
}