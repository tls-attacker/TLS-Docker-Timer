
package de.rub.nds.timingdockerevaluator.config;

import com.beust.jcommander.Parameter;
import de.rub.nds.timingdockerevaluator.util.DockerTargetManagement;

public class TimingDockerEvaluatorCommandConfig {



    @Parameter(names = "-i", description = "Number of measurements per step")
    private int measurementsPerStep = 500000;
    
    @Parameter(names = "-n", description = "Number of measurements overall")
    private int totalMeasurements = 500000;
    
    @Parameter(names = {"-threads", "-t"}, description = "Number of threads to use for evaluation (= docker containers to evaluate in parallel)")
    private int threads = 1;
    
    @Parameter(names = {"-library", "-l"}, description = "A specific library/libraries to filter for (use comma without blankspace for multiple)")
    private String specificLibrary = null;
    
    @Parameter(names = {"-version", "-v"}, description = "A specific version to filter for, requires a library (use comma without blankspace for multiple, toLowerCase() will be applied)")
    private String specificVersion = null;
    
    @Parameter(names = {"-baseVersion", "-b"}, description = "A base version to match using .contains() (use comma without blankspace for multiple)")
    private String baseVersion = null;
    
    @Parameter(names = "-ip", description = "Specific IP to connect to instead of managed docker container")
    private String specificIp = null;
    
    @Parameter(names = {"-port", "-p"}, description = "Specific port to connect to instead of managed docker container")
    private int specificPort = 4433;
    
    @Parameter(names = "-name", description = "Use a specific name for the output of a remote target")
    private String specificName = null;
    
    @Parameter(names = "-subtask", description = "Apply a specific subtask")
    private String specificSubtask = null;
    
    @Parameter(names = "-proxyControlPort", description = "Proxy control port")
    private int proxyControlPort = 4444;
    
    @Parameter(names = "-proxyDataPort", description = "Proxy data port")
    private int proxyDataPort = 5555;
    
    @Parameter(names = "-proxyIp", description = "Proxy IP")
    private String proxyIp = "127.0.0.1";
    
    @Parameter(names = "-proxy", description = "Use proxy (127.0.0.1) for measuring")
    private boolean useProxy = false;
    
    @Parameter(names = "-timeout", description = "Connection timeout to use for scanner and evaluation")
    private int timeout = 1000;
    
    @Parameter(names = "-host", description = "Use host network")
    private boolean useHostNetwork = false;
    
    @Parameter(names = {"--help", "-h"}, help = true)
    private boolean help = false;

    @Parameter(names = "-dry", description = "Collect images / targets but do not start evaluation process")
    private boolean dryRun = false;
    
    @Parameter(names = {"-additionalParameter","-a"}, description = "Additional parameter for the server inside the docker container")
    private String additionalParameter = null;
    
    @Parameter(names = {"-outputDirectory","-o"}, description = "Set the directory for the results (defaults to output-[date]-[time])")
    private String outputDirectory = null;
    
    @Parameter(names = {"-keepContainer"}, description = "Do not stop and remove the container")
    private boolean keepContainer = false;
    
    @Parameter(names = {"-noAutoFlags"}, description = "Do not set additional parameters for libraries where required")
    private boolean noAutoFlags = false;
    
    @Parameter(names = {"-printToConsole"}, description = "Print directly to console")
    private boolean printToConsole = false;
    
    @Parameter(names = {"-benchmark"}, description = "Print benchmark details")
    private boolean benchmark = false;
    
    @Parameter(names = {"-targetManagement"}, description = "How to handle server inside docker container")
    private DockerTargetManagement targetManagement = DockerTargetManagement.PORT_SWITCHING;
    
    @Parameter(names = {"-testVectors"}, description = "Test selected vectors step by step")
    private boolean onlyTestVectors = false;
    
    @Parameter(names = {"-cipher"}, description = "Specify cipher to use in tests")
    private String enforcedCipher = null;
    
    @Parameter(names = {"-neverStop"}, description = "Keep evaluating even for frequent connection failures")
    private boolean neverStop = false;
    
    @Parameter(names = {"-writeInEachStep"}, description = "Keep evaluating even for frequent connection failures")
    private boolean writeInEachStep = false;
    
    @Parameter(names = {"-echoTest"}, description = "(testing) use static traces to test with hard-coded echo server")
    private boolean echoTest = false;
    
    @Parameter(names = {"-restartQuickly"}, description = "Restart the docker container after few errors.")
    private boolean restartQuickly = false;
    
    
    public String getSpecificLibrary() {
        return specificLibrary;
    }

    public void setSpecificLibrary(String specificLibrary) {
        this.specificLibrary = specificLibrary;
    }

    public String getSpecificVersion() {
        return specificVersion;
    }

    public void setSpecificVersion(String specificVersion) {
        this.specificVersion = specificVersion;
    }

    public String getSpecificIp() {
        return specificIp;
    }

    public void setSpecificIp(String specificIp) {
        this.specificIp = specificIp;
    }

    public int getMeasurementsPerStep() {
        return measurementsPerStep;
    }

    public void setMeasurementsPerStep(int measurementsPerStep) {
        this.measurementsPerStep = measurementsPerStep;
    }

    public int getTotalMeasurements() {
        return totalMeasurements;
    }

    public void setTotalMeasurements(int totalMeasurements) {
        this.totalMeasurements = totalMeasurements;
    }

    public String getSpecificName() {
        return specificName;
    }

    public void setSpecificName(String specificName) {
        this.specificName = specificName;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public String getSpecificSubtask() {
        return specificSubtask;
    }

    public void setSpecificSubtask(String specificSubtask) {
        this.specificSubtask = specificSubtask;
    }

    public int getSpecificPort() {
        return specificPort;
    }

    public void setSpecificPort(int specificPort) {
        this.specificPort = specificPort;
    }
    
    public boolean isManagedTarget() {
        return getSpecificIp() == null;
    }

    public int getProxyControlPort() {
        return proxyControlPort;
    }

    public void setProxyControlPort(int proxyControlPort) {
        this.proxyControlPort = proxyControlPort;
    }

    public int getProxyDataPort() {
        return proxyDataPort;
    }

    public void setProxyDataPort(int proxyDataPort) {
        this.proxyDataPort = proxyDataPort;
    }

    public boolean isUseProxy() {
        return useProxy;
    }

    public void setUseProxy(boolean useProxy) {
        this.useProxy = useProxy;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public boolean isUseHostNetwork() {
        return useHostNetwork;
    }

    public void setUseHostNetwork(boolean useHostNetwork) {
        this.useHostNetwork = useHostNetwork;
    }

    public boolean isHelp() {
        return help;
    }

    public void setHelp(boolean help) {
        this.help = help;
    }

    public String getBaseVersion() {
        return baseVersion;
    }

    public void setBaseVersion(String baseVersion) {
        this.baseVersion = baseVersion;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public String getAdditionalParameter() {
        return additionalParameter;
    }

    public void setAdditionalParameter(String additionalParameter) {
        this.additionalParameter = additionalParameter;
    }

    public boolean isNoAutoFlags() {
        return noAutoFlags;
    }

    public void setNoAutoFlags(boolean noAutoFlags) {
        this.noAutoFlags = noAutoFlags;
    }

    public boolean isPrintToConsole() {
        return printToConsole;
    }

    public void setPrintToConsole(boolean printToConsole) {
        this.printToConsole = printToConsole;
    }

    public DockerTargetManagement getTargetManagement() {
        return targetManagement;
    }

    public void setTargetManagement(DockerTargetManagement targetManagement) {
        this.targetManagement = targetManagement;
    }
    
    public boolean additionalContainerActionsRequired() {
        return getTargetManagement() != DockerTargetManagement.KEEP_ALIVE;
    }

    public boolean isOnlyTestVectors() {
        return onlyTestVectors;
    }

    public void setOnlyTestVectors(boolean onlyTestVectors) {
        this.onlyTestVectors = onlyTestVectors;
    }

    public String getEnforcedCipher() {
        return enforcedCipher;
    }

    public void setEnforcedCipher(String enforcedCipher) {
        this.enforcedCipher = enforcedCipher;
    }

    public boolean isWriteInEachStep() {
        return writeInEachStep;
    }

    public void setWriteInEachStep(boolean writeInEachStep) {
        this.writeInEachStep = writeInEachStep;
    }

    public String getProxyIp() {
        return proxyIp;
    }

    public void setProxyIp(String proxyIp) {
        this.proxyIp = proxyIp;
    }

    public boolean isEchoTest() {
        return echoTest;
    }

    public void setEchoTest(boolean echoTest) {
        this.echoTest = echoTest;
    }
    
    public boolean isRestartQuickly() {
        return restartQuickly;
    }

    public void setRestartQuickly(boolean restartQuickly) {
        this.restartQuickly = restartQuickly;
    }

    public boolean isNeverStop() {
        return neverStop;
    }

    public void setNeverStop(boolean neverStop) {
        this.neverStop = neverStop;
    }

    public boolean isBenchmark() {
        return benchmark;
    }

    public void setBenchmark(boolean benchmark) {
        this.benchmark = benchmark;
    }

    public boolean isKeepContainer() {
        return keepContainer;
    }

    public void setKeepContainer(boolean keepContainer) {
        this.keepContainer = keepContainer;
    }
    
    public String getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }
}
