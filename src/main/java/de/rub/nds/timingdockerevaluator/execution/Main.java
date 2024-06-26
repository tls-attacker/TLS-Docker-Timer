package de.rub.nds.timingdockerevaluator.execution;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import de.rub.nds.timingdockerevaluator.config.TimingDockerEvaluatorCommandConfig;
import de.rub.nds.timingdockerevaluator.task.EvaluationTask;
import de.rub.nds.timingdockerevaluator.util.TimingBenchmark;
import de.rub.nds.tls.subject.TlsImplementationType;
import de.rub.nds.tls.subject.constants.TlsImageLabels;
import de.rub.nds.tls.subject.docker.DockerClientManager;
import de.rub.nds.tls.subject.docker.DockerTlsManagerFactory;
import java.security.Security;
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
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class Main {

    private static List<Image> images;
    private static final Logger LOGGER = LogManager.getLogger();
    private static final DockerClient DOCKER = DockerClientManager.getDockerClient();
    private static TimingDockerEvaluatorCommandConfig evaluationConfig;

    public static void main(String args[]) {
        Security.addProvider(new BouncyCastleProvider());
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
        
        TimingBenchmark.setEvaluationConfig(evaluationConfig);
        measureTask(); 
    }

    protected static void measureTask() {
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

    private static void logConfiguration() {
        LOGGER.info("Measurements per step set to {}", evaluationConfig.getMeasurementsPerStep());
        LOGGER.info("Total measurements per vector set to {}", evaluationConfig.getTotalMeasurements());
        if(evaluationConfig.getThreads() > 1) {
            LOGGER.info("Analyzing {} in parallel", evaluationConfig.getThreads());
        }
        LOGGER.info("Target management set to: {} ({})", evaluationConfig.getTargetManagement(), evaluationConfig.getTargetManagement().getDescription());
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
        
        if(evaluationConfig.getMeasurementsPerStep() < evaluationConfig.getTotalMeasurements() && !evaluationConfig.isWriteInEachStep()) {
            LOGGER.warn("Configured to run in steps but reduced RAM mode (-writeInEachStep) is disabled.");
        }
        
        if(evaluationConfig.getAdditionalParameter() != null && evaluationConfig.isNoAutoFlags()) {
            LOGGER.warn("Will set additional parameters as well as automatically chosen flags for docker containers. Set -noAutoFlags to avoid this.");
        }
    }

    private static ExecutorService getRemoteExecutor() {
        LOGGER.info("Evaluating remote target {}:{}", evaluationConfig.getSpecificIp(), evaluationConfig.getSpecificPort());
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(evaluationConfig.getThreads());
        if (!evaluationConfig.isDryRun()) {
            ExecutionWatcher.getReference().setTasks(1);
            executor.execute(() -> {
                EvaluationTask task = new EvaluationTask(evaluationConfig);
                task.execute();
            });
         }
        return executor;
    }

    private static ThreadPoolExecutor prepareManagedExecutor() {
        preExecutionCleanup();
        collectTargets();
        LOGGER.info("Found {} applicable server images", images.size());
        LOGGER.info("Libraries: {}", images.stream().map(image -> image.getLabels().get(TlsImageLabels.IMPLEMENTATION.getLabelName())).distinct().collect(Collectors.joining(",")));
        LOGGER.info("Versions: {}", images.stream().map(image -> image.getLabels().get(TlsImageLabels.VERSION.getLabelName())).distinct().collect(Collectors.joining(",")));
        ExecutionWatcher.getReference().setTasks(images.size());
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(evaluationConfig.getThreads());
        if (!evaluationConfig.isDryRun()) {
            for (Image image : images) {
                executor.execute(() -> {
                    EvaluationTask task = new EvaluationTask(image, evaluationConfig);
                    task.execute();
                });              
            }
        }
        return executor;
    }

    private static void collectTargets() {
        List<Image> allAvailableImages = DockerTlsManagerFactory.getAllImages();
        images = allAvailableImages.parallelStream().filter(image -> imageSelection(image, null)).collect(Collectors.toList());
        images.addAll(allAvailableImages.stream().filter(image -> imageSelection(image, images)).collect(Collectors.toList()));
    }

    private static boolean imageSelection(Image image, List<Image> presentImages) {
        TlsImplementationType implementation = TlsImplementationType.fromString(image.getLabels().get(TlsImageLabels.IMPLEMENTATION.getLabelName()));
        String version = image.getLabels().get(TlsImageLabels.VERSION.getLabelName());
        String role = image.getLabels().get(TlsImageLabels.CONNECTION_ROLE.getLabelName());
        // always prioritize local image with matching labels
        if (!matchesLocalOverNexusPriority(image, presentImages, implementation, version)) {
            return false;
        } else {
            if(implementation == null || implementation.name() == null || version == null || role == null) {
                LOGGER.warn("TLS-Docker-Library found a docker image without proper tags ({})", image.getRepoTags());
                return false;
            } else {
                return matchesImageFilters(implementation, version, role);
            }
        }
    }

    private static boolean matchesImageFilters(TlsImplementationType implementation, String version, String role) {
        boolean matchesLibraryFilter = evaluationConfig.getSpecificLibrary() == null || Arrays.asList(evaluationConfig.getSpecificLibrary().split(",")).stream().anyMatch(implementation.name().toLowerCase()::equals);
        boolean matchesVersionFilter = true;
        if (evaluationConfig.getSpecificVersion() != null) {
            matchesVersionFilter = Arrays.asList(evaluationConfig.getSpecificVersion().toLowerCase().split(",")).stream().anyMatch(version.toLowerCase()::equals);
        } else if (evaluationConfig.getBaseVersion() != null) {
            matchesVersionFilter = Arrays.asList(evaluationConfig.getBaseVersion().toLowerCase().split(",")).stream().anyMatch(version.toLowerCase()::startsWith);
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
