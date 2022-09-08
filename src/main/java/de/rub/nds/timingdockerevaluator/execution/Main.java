package de.rub.nds.timingdockerevaluator.execution;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import de.rub.nds.timingdockerevaluator.config.TimingDockerEvaluatorCommandConfig;
import de.rub.nds.timingdockerevaluator.task.EvaluationTask;
import de.rub.nds.timingdockerevaluator.task.subtask.RScriptManager;
import de.rub.nds.tls.subject.TlsImplementationType;
import de.rub.nds.tls.subject.constants.TlsImageLabels;
import de.rub.nds.tls.subject.docker.DockerClientManager;
import de.rub.nds.tls.subject.docker.DockerTlsManagerFactory;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {

    private static List<Image> images;
    private static final Logger LOGGER = LogManager.getLogger();
    private static final DockerClient DOCKER = DockerClientManager.getDockerClient();
    private static TimingDockerEvaluatorCommandConfig evaluationConfig;

    public static void main(String args[]) {
        evaluationConfig = new TimingDockerEvaluatorCommandConfig();
        JCommander commander = new JCommander(evaluationConfig);
        try {
            commander.parse(args);
            if(evaluationConfig.isHelp()) {
                commander.usage();
                return;
            }
            checkCommandCombinations();
        } catch (ParameterException ex) {
            LOGGER.error(ex);
            return;
        }
        checkRStatus();
        LOGGER.info("Measurements per step set to {}", evaluationConfig.getMeasurementsPerStep());
        LOGGER.info("Maximum measurements per vector set to {}", evaluationConfig.getTotalMeasurements());
        LOGGER.info("Analyzing {} in parallel", evaluationConfig.getThreads());
        
        ExecutorService executor;
        if(evaluationConfig.isManagedTarget()) {
           executor = prepareManagedExecutor(); 
        } else {
            executor = getRemoteExecutor();
        }
        
        try {
            executor.shutdown();
            executor.awaitTermination(100, TimeUnit.DAYS);
            ExecutionWatcher.getReference().printSummary();
        } catch (InterruptedException ex) {
            LOGGER.error(ex);
        }
    }
    
    private static void checkCommandCombinations() {
        if(!evaluationConfig.isManagedTarget() && (evaluationConfig.getSpecificLibrary() != null || evaluationConfig.getSpecificVersion() != null)) {
            throw new ParameterException("Invalid combination of remote target and specific library/version. Filters can only be applied to managed targets!");
        } else if(evaluationConfig.getMeasurementsPerStep() > evaluationConfig.getTotalMeasurements()) {
            throw new ParameterException("Measurements per step exceed total number of measurements.");
        }
    }

    private static ExecutorService getRemoteExecutor() {
        LOGGER.info("Evaluating remote target {}:{}", evaluationConfig.getSpecificIp(), evaluationConfig.getSpecificPort());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            EvaluationTask task = new EvaluationTask(evaluationConfig);
            task.execute();
        });
        return executor;
    }

    private static ThreadPoolExecutor prepareManagedExecutor() {
        preExecutionCleanup();
        collectTargets();
        LOGGER.info("Found {} applicable server images", images.size());
        LOGGER.info("Starting docker evaluation");
        ExecutionWatcher.getReference().setTasks(images.size());
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(evaluationConfig.getThreads());
        for (Image image : images) {
            executor.execute(() -> {
                EvaluationTask task = new EvaluationTask(image, evaluationConfig);
                task.execute();
            });
        }
        return executor;
    }

    

    private static void checkRStatus() {
        if(evaluationConfig.isSkipR()) {
            LOGGER.info("R script is disabled. All vectors will be tested with maximum number of measurements.");
        } else if (!RScriptManager.rScriptGiven()) {
            LOGGER.error("Failed to find R script, must be provided in execution path");
            System.exit(0);
        }
    }

    private static void collectTargets() {
        images = DockerTlsManagerFactory.getAllImages().parallelStream().filter(image -> {
            TlsImplementationType implementation = TlsImplementationType.fromString(image.getLabels().get(TlsImageLabels.IMPLEMENTATION.getLabelName()));
            String version = image.getLabels().get(TlsImageLabels.VERSION.getLabelName());
            String role = image.getLabels().get(TlsImageLabels.CONNECTION_ROLE.getLabelName());
            boolean matchesLibraryFilter = evaluationConfig.getSpecificLibrary() == null || implementation.name().toLowerCase().equals(evaluationConfig.getSpecificLibrary().toLowerCase());
            boolean matchesSpecificVersion = evaluationConfig.getSpecificVersion() == null || version.toLowerCase().equals(evaluationConfig.getSpecificVersion().toLowerCase());

            return role.equals("server") && matchesLibraryFilter && matchesSpecificVersion;
        }).collect(Collectors.toList());
    }

    private static void preExecutionCleanup() {
        List<Container> containers = DOCKER.listContainersCmd().withShowAll(true).exec();
        List<Container> runningTimingEvalContainers = new LinkedList<>();
        containers.forEach(container -> {
            for (String name : container.getNames()) {
                if (name.contains(EvaluationTask.CONTAINER_NAME_PREFIX)) {
                    runningTimingEvalContainers.add(container);
                    break;
                }
            }
        });
        if (!runningTimingEvalContainers.isEmpty()) {
            LOGGER.warn("Found {} timing eval containers that are already running. Stop these containers to avoid name collisions y/N?", runningTimingEvalContainers.size());
            Scanner scanner = new Scanner(System.in);
            String read = scanner.next();
            if (read.equals("y")) {
                LOGGER.warn("Stopping containers");
                runningTimingEvalContainers.forEach(container -> DOCKER.removeContainerCmd(container.getId()).withForce(true).exec());
            } else {
                LOGGER.warn("Containers won't be stopped!");
            }
        }

    }
}
