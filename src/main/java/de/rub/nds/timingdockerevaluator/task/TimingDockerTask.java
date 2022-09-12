
package de.rub.nds.timingdockerevaluator.task;

import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotModifiedException;
import de.rub.nds.timingdockerevaluator.config.TimingDockerEvaluatorCommandConfig;
import static de.rub.nds.timingdockerevaluator.task.EvaluationTask.CONTAINER_NAME_PREFIX;
import de.rub.nds.timingdockerevaluator.task.exception.InstanceCreationFailedException;
import de.rub.nds.tls.subject.TlsImplementationType;
import de.rub.nds.tls.subject.docker.DockerTlsManagerFactory;
import de.rub.nds.tls.subject.docker.DockerTlsServerInstance;
import java.util.LinkedList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class TimingDockerTask {
    private static final Logger LOGGER = LogManager.getLogger();
    protected final TimingDockerEvaluatorCommandConfig evaluationConfig;

    public TimingDockerTask(TimingDockerEvaluatorCommandConfig evaluationConfig) {
        this.evaluationConfig = evaluationConfig;
    }
    
    public void stopContainter(DockerTlsServerInstance dockerInstance) {
        try {
            dockerInstance.stop();
        } catch (NotModifiedException exception) {
            LOGGER.warn("Failed to stop container for {} - was already stopped or never started!", dockerInstance.getImage().getId());
        }
    }

    public void waitForContainer() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ex) {
        }
    }
    
    protected DockerTlsServerInstance createDockerInstance(TlsImplementationType implementation, String version, boolean onHostNetwork) {
        try {
            String containerName = CONTAINER_NAME_PREFIX + (implementation + version).replace(":", "-");
            DockerTlsManagerFactory.TlsServerInstanceBuilder targetInstanceBuilder = DockerTlsManagerFactory.getTlsServerBuilder(implementation, version);
            targetInstanceBuilder = targetInstanceBuilder.containerName(containerName).port(4433).hostname("0.0.0.0").ip("0.0.0.0").parallelize(true);
            if(onHostNetwork) {
                targetInstanceBuilder = targetInstanceBuilder.hostConfigHook(hostConfig -> {
                        hostConfig.withPortBindings(new LinkedList<>()).withNetworkMode("host");
                        return hostConfig;});
            }     
            DockerTlsServerInstance targetInstance = targetInstanceBuilder.build();
            return targetInstance;
        } catch (DockerException | InterruptedException ex) {
            LOGGER.error("Failed to create instance ", ex);
            throw new InstanceCreationFailedException();
        }
    }
}
