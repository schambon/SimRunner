package org.schambon.loadsimrunner.runner;

public class WorkloadThread extends Thread {
    
    private String workloadName;
    private int threadNumber;


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
}
