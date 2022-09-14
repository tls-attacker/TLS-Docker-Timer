package de.rub.nds.timingdockerevaluator.execution;

import de.rub.nds.timingdockerevaluator.task.subtask.EvaluationSubtaskReport;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ExecutionWatcher {
    private static ExecutionWatcher reference;
    private static final Logger LOGGER = LogManager.getLogger();
    
    private int tasksOverall;
    private int finishedTasks = 0;
    private final List<String> failedTasks = new LinkedList<>();
    private final List<String> unexpectedFailure = new LinkedList<>();
    private final List<String> failedContainer = new LinkedList<>();
    private final List<String> failedToHandshake = new LinkedList<>();
    private final List<String> failedToConnect = new LinkedList<>();
    private final List<String> failedToFindSubtask = new LinkedList<>();
    private final Map<String, List<String>> subtaskFindings = new HashMap<>();
    private final Map<String, List<String>> unapplicableSubtasks = new HashMap<>();
    private final Map<String, List<String>> abortedSubtasks = new HashMap<>();
    private ExecutionWatcher() { 
    }
    
    public static ExecutionWatcher getReference() {
        if(reference == null) {
            reference = new ExecutionWatcher();
        }
        return reference;
    }
    
    public synchronized void setTasks(int tasks) {
        tasksOverall = tasks;
        LOGGER.info("Tasks to execute: {}", tasksOverall);
    }
    
    public synchronized void failedTask(String targetName) {
        failedTasks.add(targetName);
    }
    
    public synchronized void failedUnexpected(String targetName) {
        unexpectedFailure.add(targetName);
        failedTask(targetName);
    }
    
    public synchronized void failedContainer(String targetName) {
        failedContainer.add(targetName);
        failedTask(targetName);
    }
    
    public synchronized void failedToHandshake(String targetName) {
        failedToHandshake.add(targetName);
        failedTask(targetName);
    }
    
    public synchronized void failedToConnect(String targetName) {
        failedToConnect.add(targetName);
        failedTask(targetName);
    }
    
    public synchronized void failedToFindSubtask(String targetName) {
        failedToFindSubtask.add(targetName);
        failedTask(targetName);
    }
    
    public synchronized void finishedTask() {
        finishedTasks++;
        printProgress();
    }
    
    public void printSummary() {
        LOGGER.info("Failed images:");
        LOGGER.info("**************");
        LOGGER.info("Failed Container: {}", failedContainer.stream().collect(Collectors.joining(",")));
        LOGGER.info("Failed to Connect: {}", failedToConnect.stream().collect(Collectors.joining(",")));
        LOGGER.info("Failed to Handshake: {}", failedToHandshake.stream().collect(Collectors.joining(",")));
        LOGGER.info("Failed unexpected: {}", unexpectedFailure.stream().collect(Collectors.joining(",")));
        LOGGER.info("Failed to find any applicable subtask: {}", failedToFindSubtask.stream().collect(Collectors.joining(",")));
        LOGGER.info("Failed Subtasks:");
        LOGGER.info("**********************");
        for(String subtaskName : unapplicableSubtasks.keySet()) {
            LOGGER.info("Failed subtask {}: {}", subtaskName, unapplicableSubtasks.get(subtaskName).stream().collect(Collectors.joining(",")));
        }
        LOGGER.info("Aborted Subtasks:");
        LOGGER.info("**********************");
        for(String subtaskName : abortedSubtasks.keySet()) {
            LOGGER.info("Aborted subtask {}: {}", subtaskName, abortedSubtasks.get(subtaskName).stream().collect(Collectors.joining(",")));
        }
        LOGGER.info("Findings:");
        LOGGER.info("*********");
        for(String subtaskName : subtaskFindings.keySet()) {
            LOGGER.info("Subtask {}: {}", subtaskName, subtaskFindings.get(subtaskName).stream().collect(Collectors.joining(",")));
        }
    }
    
    public synchronized void unapplicableSubtask(String subtaskName, String targetName) {
        if(!unapplicableSubtasks.containsKey(subtaskName)) {
            unapplicableSubtasks.put(subtaskName, new LinkedList<>());
        }
        unapplicableSubtasks.get(subtaskName).add(targetName);
    }
    
    
    
    public synchronized void finishedSubtask(EvaluationSubtaskReport subtaskReport) {
        if(!subtaskReport.getWithDifference().isEmpty()) {
            if(!subtaskFindings.containsKey(subtaskReport.getTaskName())) {
                subtaskFindings.put(subtaskReport.getTaskName(), new LinkedList<>());
            }
            subtaskFindings.get(subtaskReport.getTaskName()).add(subtaskReport.getTargetName() + subtaskReport.getWithDifference().stream().collect(Collectors.joining("++")));
        }
    }
    
    public synchronized void abortedSubtask(String subtaskName, String targetName) {
        if(!abortedSubtasks.containsKey(subtaskName)) {
            abortedSubtasks.put(subtaskName, new LinkedList<>());
        }
        abortedSubtasks.get(subtaskName).add(targetName);
    }
    
    private void printProgress() {
        LOGGER.info("Completed {}/{} ({} failed - {} container related, {} failed to connect, {} handshake failed, {} unexpected, {} no subtask applicable)", finishedTasks, tasksOverall, failedTasks.size(), failedContainer.size(), failedToConnect.size(), failedToHandshake.size(), unexpectedFailure.size(), failedToFindSubtask.size());
        if(!subtaskFindings.isEmpty()) {
            for(String key: subtaskFindings.keySet()) {
                LOGGER.info("Findings for {}: {}", key, subtaskFindings.get(key).size());
            }
        }
    }
}
