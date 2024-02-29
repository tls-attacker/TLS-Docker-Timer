package de.rub.nds.timingdockerevaluator.execution;

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
    private final Map<String, List<String>> unapplicableSubtasks = new HashMap<>();
    private final Map<String, List<String>> abortedSubtasks = new HashMap<>();

    private ExecutionWatcher() {
    }

    public static ExecutionWatcher getReference() {
        if (reference == null) {
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
        if (hadFailedImages()) {
            LOGGER.warn("Failed images:");
            LOGGER.warn("**************");
            LOGGER.warn("Failed Container: {}", failedContainer.stream().collect(Collectors.joining(",")));
            LOGGER.warn("Failed to Connect: {}", failedToConnect.stream().collect(Collectors.joining(",")));
            LOGGER.warn("Failed to Handshake: {}", failedToHandshake.stream().collect(Collectors.joining(",")));
            LOGGER.warn("Failed unexpected: {}", unexpectedFailure.stream().collect(Collectors.joining(",")));
            LOGGER.warn("Failed to find any applicable subtask: {}", failedToFindSubtask.stream().collect(Collectors.joining(",")));
        }
        if (!unapplicableSubtasks.isEmpty()) {
            LOGGER.warn("Failed Subtasks:");
            LOGGER.warn("**********************");
            for (String subtaskName : unapplicableSubtasks.keySet()) {
                LOGGER.warn("Failed subtask {}: {}", subtaskName, unapplicableSubtasks.get(subtaskName).stream().collect(Collectors.joining(",")));
            }
        }
        if (!abortedSubtasks.isEmpty()) {
            LOGGER.warn("Aborted Subtasks:");
            LOGGER.warn("**********************");
            for (String subtaskName : abortedSubtasks.keySet()) {
                LOGGER.warn("Aborted subtask {}: {}", subtaskName, abortedSubtasks.get(subtaskName).stream().collect(Collectors.joining(",")));
            }
        }
    }

    private boolean hadFailedImages() {
        return !failedContainer.isEmpty() || !failedToConnect.isEmpty() || !failedToHandshake.isEmpty() || !unexpectedFailure.isEmpty() || !failedToFindSubtask.isEmpty();
    }

    public synchronized void unapplicableSubtask(String subtaskName, String targetName) {
        if (!unapplicableSubtasks.containsKey(subtaskName)) {
            unapplicableSubtasks.put(subtaskName, new LinkedList<>());
        }
        unapplicableSubtasks.get(subtaskName).add(targetName);
    }

    public synchronized void abortedSubtask(String subtaskName, String targetName) {
        if (!abortedSubtasks.containsKey(subtaskName)) {
            abortedSubtasks.put(subtaskName, new LinkedList<>());
        }
        abortedSubtasks.get(subtaskName).add(targetName);
    }

    private void printProgress() {
        LOGGER.info("Completed {}/{} ({} failed - {} container related, {} failed to connect, {} handshake failed, {} no subtask applicable, {} unexpected)", finishedTasks, tasksOverall, failedTasks.size(), failedContainer.size(), failedToConnect.size(), failedToHandshake.size(), failedToFindSubtask.size(), unexpectedFailure.size());
    }
}
