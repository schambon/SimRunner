package org.schambon.loadsimrunner.runner;

import java.util.HashMap;
import java.util.Map;

public class WorkloadThread extends Thread {
    
    private String workloadName;
    private int threadNumber;
    private Map<String, Object> context = new HashMap<>();


    public WorkloadThread(String workloadName, int threadNumber, Runnable executor) {
        super(executor);

        this.threadNumber = threadNumber;
        this.workloadName = workloadName;
    }

    public String getWorkloadName() {
        return workloadName;
    }

    public int getThreadNumber() {
        return threadNumber;
    }

    public void setContextValue(String name, Object value) {
        this.context.put(name, value);
    }

    public Object getContextValue(String name) {
        return this.context.get(name);
    }
}
