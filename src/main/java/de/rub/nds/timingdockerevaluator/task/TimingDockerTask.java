package de.rub.nds.timingdockerevaluator.task;

import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotModifiedException;
import de.rub.nds.timingdockerevaluator.config.TimingDockerEvaluatorCommandConfig;
import static de.rub.nds.timingdockerevaluator.task.EvaluationTask.CONTAINER_NAME_PREFIX;
import de.rub.nds.timingdockerevaluator.task.exception.InstanceCreationFailedException;
import de.rub.nds.timingdockerevaluator.util.TimingBenchmark;
import de.rub.nds.tls.subject.TlsImplementationType;
import de.rub.nds.tls.subject.docker.DockerTlsManagerFactory;
import de.rub.nds.tls.subject.docker.DockerTlsServerInstance;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class TimingDockerTask {

    public TimingDockerEvaluatorCommandConfig getEvaluationConfig() {
        return evaluationConfig;
    }

    private static final Logger LOGGER = LogManager.getLogger();
    private final TimingDockerEvaluatorCommandConfig evaluationConfig;

    public TimingDockerTask(TimingDockerEvaluatorCommandConfig evaluationConfig) {
        this.evaluationConfig = evaluationConfig;
    }

    public void stopContainter(DockerTlsServerInstance dockerInstance) {
        try {
            TimingBenchmark.print("Stopping container");
            dockerInstance.kill();
            dockerInstance.remove();
            TimingBenchmark.print("Stopped container");
        } catch (NotModifiedException exception) {
            LOGGER.warn("Failed to stop container for {} - was already stopped or never started!", dockerInstance.getImage().getId());
        }
    }

    public void waitForContainer() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException ex) {
        }
    }

    protected DockerTlsServerInstance createDockerInstance(TlsImplementationType implementation, String version, boolean onHostNetwork) {
        try {
            String containerName = CONTAINER_NAME_PREFIX + (implementation + version).replace(":", "-") + "-" + RandomStringUtils.randomAlphanumeric(6);
            DockerTlsManagerFactory.TlsServerInstanceBuilder targetInstanceBuilder = DockerTlsManagerFactory.getTlsServerBuilder(implementation, version);
            targetInstanceBuilder = targetInstanceBuilder.containerName(containerName).port(4433).hostname("0.0.0.0").ip("0.0.0.0").parallelize(true);
            if (onHostNetwork) {
                targetInstanceBuilder = targetInstanceBuilder.hostConfigHook(hostConfig -> {
                    hostConfig.withPortBindings(new LinkedList<>()).withNetworkMode("host");
                    return hostConfig;
                });
            }
            addAdditionalParameters(implementation, version, targetInstanceBuilder);
            DockerTlsServerInstance targetInstance = targetInstanceBuilder.build();
            return targetInstance;
        } catch (DockerException | InterruptedException ex) {
            LOGGER.error("Failed to create instance ", ex);
            throw new InstanceCreationFailedException();
        }
    }

    private void addAdditionalParameters(TlsImplementationType implementation, String version, DockerTlsManagerFactory.TlsServerInstanceBuilder builder) {
        List<String> additionalParameters = new LinkedList<>();
        if (getEvaluationConfig().getAdditionalParameter() != null) {
            additionalParameters.add(getEvaluationConfig().getAdditionalParameter());
        }
        if(!getEvaluationConfig().isNoAutoFlags()) {
            
            switch(implementation) {
                case WOLFSSL:
                    // loop server (TODO: check if known in all versions)
                    //additionalParameters.add("-i");
                    break;
                case BOTAN:
                    // allow RSA KEX
                    if(version.startsWith("2.") || version.equals("1.11.31") || version.equals("1.11.32") || version.equals("1.11.33") || version.equals("1.11.34")) {
                       additionalParameters.add("--policy=/cert/policies/botan-policy.txt"); 
                    }
                    break;
                case MBEDTLS:
                    // do not enforce client auth
                    // (ignored or known in all versions)
                    additionalParameters.add("auth_mode=none");
                    break;
            }
        }
        
        if(!additionalParameters.isEmpty()) {
            builder.additionalParameters(additionalParameters.stream().collect(Collectors.joining(" ")));
        }
    }
}
