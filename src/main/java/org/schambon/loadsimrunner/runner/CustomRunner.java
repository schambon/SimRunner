package org.schambon.loadsimrunner.runner;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.schambon.loadsimrunner.WorkloadManager;
import org.schambon.loadsimrunner.report.Reporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomRunner extends AbstractRunner {

    private static Logger LOGGER = LoggerFactory.getLogger(CustomRunner.class);

    private AbstractRunner delegate;

    public CustomRunner(WorkloadManager workloadConfiguration, Reporter reporter) {
        super(workloadConfiguration, reporter);

        try {
            Class<? extends AbstractRunner> clazz = (Class<? extends AbstractRunner>) Class.forName(params.getString("class"));
            Constructor<? extends AbstractRunner> ctor = clazz.getDeclaredConstructor(WorkloadManager.class, Reporter.class);
            delegate = ctor.newInstance(workloadConfiguration, reporter);
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException |
                 InstantiationException | IllegalAccessException | InvocationTargetException e) {
            LOGGER.error("Cannot instantiate custom runner", e);
            delegate = new AbstractRunner(workloadConfiguration, reporter) {

                @Override
                protected long doRun() {
                    LOGGER.info("no-op task (unreachable custom runner)");
                    return 0;
                }
                
            };
        }
    }

    @Override
    protected long doRun() {
        return delegate.doRun();
    }
    
}
