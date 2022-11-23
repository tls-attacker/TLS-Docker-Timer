package de.rub.nds.timingdockerevaluator.execution;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import de.rub.nds.timingdockerevaluator.config.TimingDockerEvaluatorCommandConfig;
import de.rub.nds.timingdockerevaluator.task.EvaluationTask;
import de.rub.nds.timingdockerevaluator.task.eval.RScriptManager;
import de.rub.nds.tls.subject.TlsImplementationType;
import de.rub.nds.tls.subject.constants.TlsImageLabels;
import de.rub.nds.tls.subject.docker.DockerClientManager;
import de.rub.nds.tls.subject.docker.DockerTlsManagerFactory;
import java.util.Arrays;
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
    
    private static final String PO_PATH= "PaddingOracle";
    private static final String BB_PATH = "Bleichenbacher";
    private static final String LUCKY13_PATH = "Lucky13";

    public static void main(String args[]) {
        evaluationConfig = new TimingDockerEvaluatorCommandConfig();
        JCommander commander = new JCommander(evaluationConfig);
        try {
            commander.parse(args);
            if (evaluationConfig.isHelp()) {
                commander.usage();
                return;
            }
            checkCommandCombinations();
        } catch (ParameterException ex) {
            LOGGER.error(ex);
            return;
        }
        
        if(evaluationConfig.isAnalyzeOnly()) {
            analyzeGivenResults();
        } else {
            measureTask(); 
        }
    }

    protected static void measureTask() {
        checkRStatus();
        logConfiguration();

        ExecutorService executor;
        if (evaluationConfig.isManagedTarget()) {
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
    
    protected static void analyzeGivenResults() {
        checkRStatus();
        RAnalyzer.analyzeGivenCSVs(evaluationConfig);
    }

    private static void logConfiguration() {
        LOGGER.info("Measurements per step set to {}", evaluationConfig.getMeasurementsPerStep());
        LOGGER.info("Maximum measurements per vector set to {}", evaluationConfig.getTotalMeasurements());
        LOGGER.info("Analyzing {} in parallel", evaluationConfig.getThreads());
        LOGGER.info("Ephemeral server instances set to: {}", evaluationConfig.isEphemeral());
        if(evaluationConfig.getSpecificSubtask() != null) {
            LOGGER.info("Limiting tests to subtask: {}", evaluationConfig.getSpecificSubtask());
        }
    }

    private static void checkCommandCombinations() {
        if (!evaluationConfig.isManagedTarget() && (evaluationConfig.getSpecificLibrary() != null || evaluationConfig.getSpecificVersion() != null)) {
            throw new ParameterException("Invalid combination of remote target and specific library/version. Filters can only be applied to managed targets!");
        } else if (evaluationConfig.getMeasurementsPerStep() > evaluationConfig.getTotalMeasurements()) {
            throw new ParameterException("Measurements per step exceed total number of measurements.");
        } else if (evaluationConfig.getBaseVersion() != null && evaluationConfig.getSpecificVersion() != null) {
            throw new ParameterException("Both specific and base version(s) specified.");
        }
        
        if(evaluationConfig.getAdditionalParameter() != null && evaluationConfig.isNoAutoFlags()) {
            LOGGER.warn("Will set additional parameters as well as automatically chosen flags. Set -noAutoFlags to avoid this.");
        }
    }

    private static ExecutorService getRemoteExecutor() {
        LOGGER.info("Evaluating remote target {}:{}", evaluationConfig.getSpecificIp(), evaluationConfig.getSpecificPort());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            if (!evaluationConfig.isDryRun()) {
                EvaluationTask task = new EvaluationTask(evaluationConfig);
                task.execute();
            }
        });
        return executor;
    }

    private static ThreadPoolExecutor prepareManagedExecutor() {
        preExecutionCleanup();
        collectTargets();
        LOGGER.info("Found {} applicable server images", images.size());
        LOGGER.info("Libraries: {}", images.stream().map(image -> image.getLabels().get(TlsImageLabels.IMPLEMENTATION.getLabelName())).distinct().collect(Collectors.joining(",")));
        LOGGER.info("Versions: {}", images.stream().map(image -> image.getLabels().get(TlsImageLabels.VERSION.getLabelName())).distinct().collect(Collectors.joining(",")));
        LOGGER.info("Will perform {} runs for each image", evaluationConfig.getRuns());
        ExecutionWatcher.getReference().setTasks(images.size() * evaluationConfig.getRuns());
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(evaluationConfig.getThreads());
        if (!evaluationConfig.isDryRun()) {
            for (Image image : images) {
                for(int i = 0; i < evaluationConfig.getRuns(); i++) {
                    final int currentValue = i;
                   executor.execute(() -> {
                    EvaluationTask task = new EvaluationTask(image, evaluationConfig, currentValue);
                    task.execute();
                }); 
                }
                
            }
        }
        return executor;
    }

    private static void checkRStatus() {
        if (evaluationConfig.isSkipR()) {
            LOGGER.info("R script is disabled. All vectors will be tested with maximum number of measurements.");
        } else if (!RScriptManager.rScriptGiven()) {
            LOGGER.error("Failed to find R script, must be provided in execution path");
            System.exit(0);
        }
    }

    private static void collectTargets() {
        List<Image> allAvailableImages = DockerTlsManagerFactory.getAllImages();
        images = allAvailableImages.parallelStream().filter(image -> imageSelection(image, null)).collect(Collectors.toList());
        images.addAll(allAvailableImages.stream().filter(image -> imageSelection(image, images)).collect(Collectors.toList()));
        for(int i = 1; i < evaluationConfig.getRuns(); i++) {
            
        }
    }

    private static boolean imageSelection(Image image, List<Image> presentImages) {
        TlsImplementationType implementation = TlsImplementationType.fromString(image.getLabels().get(TlsImageLabels.IMPLEMENTATION.getLabelName()));
        String version = image.getLabels().get(TlsImageLabels.VERSION.getLabelName());
        String role = image.getLabels().get(TlsImageLabels.CONNECTION_ROLE.getLabelName());
        // always prioritize local image with matching labels
        if (!matchesLocalOverNexusPriority(image, presentImages, implementation, version)) {
            return false;
        } else {
            return matchesImageFilters(implementation, version, role);
        }
    }

    private static boolean matchesImageFilters(TlsImplementationType implementation, String version, String role) {
        boolean matchesLibraryFilter = evaluationConfig.getSpecificLibrary() == null || Arrays.asList(evaluationConfig.getSpecificLibrary().split(",")).stream().anyMatch(implementation.name().toLowerCase()::equals);
        boolean matchesVersionFilter = true;
        if (evaluationConfig.getSpecificVersion() != null) {
            matchesVersionFilter = Arrays.asList(evaluationConfig.getSpecificVersion().split(",")).stream().anyMatch(version.toLowerCase()::equals);
        } else if (evaluationConfig.getBaseVersion() != null) {
            matchesVersionFilter = Arrays.asList(evaluationConfig.getBaseVersion().split(",")).stream().anyMatch(version.toLowerCase()::startsWith);
        }
        return role.equals("server") && matchesLibraryFilter && matchesVersionFilter;
    }

    private static boolean matchesLocalOverNexusPriority(Image image, List<Image> presentImages, TlsImplementationType implementation, String version) {
        boolean isFromNexus = Arrays.asList(image.getRepoTags()).stream().anyMatch(tag -> tag.contains("hydrogen.cloud.nds.rub.de"));
        if ((presentImages == null && isFromNexus) || (presentImages != null && !isFromNexus)) {
            //either add only from nexus or locally built
            return false;
        } else if (presentImages != null && isFromNexus) {
            //extend list, but avoid duplicates
            for (Image listedImage : presentImages) {
                TlsImplementationType listedImplementation = TlsImplementationType.fromString(listedImage.getLabels().get(TlsImageLabels.IMPLEMENTATION.getLabelName()));
                String listedVersion = listedImage.getLabels().get(TlsImageLabels.VERSION.getLabelName());
                if (listedImplementation == implementation && listedVersion.equals(version)) {
                    return false;
                }
            }
        }
        return true;
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
