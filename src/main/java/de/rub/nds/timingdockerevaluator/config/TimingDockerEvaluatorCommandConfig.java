
package de.rub.nds.timingdockerevaluator.config;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

public class TimingDockerEvaluatorCommandConfig {

    public boolean isKeepContainer() {
        return keepContainer;
    }

    public void setKeepContainer(boolean keepContainer) {
        this.keepContainer = keepContainer;
    }
   
    @Parameter(names = "-i", description = "Number of measurements per step")
    private int measurementsPerStep = 10000;
    
    @Parameter(names = "-n", description = "Number of measurements overall")
    private int totalMeasurements = 100000;
    
    @Parameter(names = {"-threads", "-t"}, description = "Number of threads to use for evaluation (= docker containers to evaluate in parallel)")
    private int threads = 1;
    
    @Parameter(names = {"-library", "-l"}, description = "A specific library/libraries to filter for (use comma without blankspace for multiple)")
    private String specificLibrary = null;
    
    @Parameter(names = {"-version", "-v"}, description = "A specific version to filter for, requires a library (use comma without blankspace for multiple)")
    private String specificVersion = null;
    
    @Parameter(names = {"-baseVersion", "-b"}, description = "A base version to match using .contains() (use comma without blankspace for multiple)")
    private String baseVersion = null;
    
    @Parameter(names = "-ip", description = "Specific IP to connect to instead of managed docker container")
    private String specificIp = null;
    
    @Parameter(names = {"-port", "-p"}, description = "Specific port to connect to instead of managed docker container")
    private int specificPort = 1337;
    
    @Parameter(names = "-skipr", description = "Skip execution of the R script and only measure")
    private boolean skipR = false;
    
    @Parameter(names = "-name", description = "Use a specific name for the output of a remote target")
    private String specificName = null;
    
    @Parameter(names = "-subtask", description = "Apply a specific subtask")
    private String specificSubtask = null;
    
    @Parameter(names = "-proxyControlPort", description = "Proxy control port")
    private int proxyControlPort = 4444;
    
    @Parameter(names = "-proxyDataPort", description = "Proxy data port")
    private int proxyDataPort = 5555;
    
    @Parameter(names = "-proxy", description = "Use proxy (127.0.0.1) for measuring")
    private boolean useProxy = false;
    
    @Parameter(names = "-timeout", description = "Connection timeout to use for scanner and evaluation")
    private int timeout = 1000;
    
    @Parameter(names = "-host", description = "Use host network")
    private boolean useHostNetwork = false;
    
    @Parameter(names = {"--help", "-h"}, help = true)
    private boolean help = false;
    
    @Parameter(names = "-manageOnly", description = "Only manage docker containers")
    private boolean manageOnly = false;
    
    @Parameter(names = "-measureOnly", description = "Only measure using given ip and port")
    private boolean measureOnly = false;
    
    @Parameter(names = "-dry", description = "Collect images / targets but do not start evaluation process")
    private boolean dryRun = false;
    
    @Parameter(names = {"-ephemeralContainers","-e"}, description = "Server containers are restarted before each new handshake")
    private boolean ephemeral = false;
    
    @Parameter(names = {"-killProcess","-k"}, description = "Kill and restart the server process inside the docker container for each handshake")
    private boolean killProcess = false;
    
    @Parameter(names = {"-additionalParameter","-a"}, description = "Additional parameter for the server inside the docker container")
    private String additionalParameter = null;
    
    @Parameter(names = {"-keepContainer"}, description = "Do not stop and remove the container")
    private boolean keepContainer = false;
    
    @Parameter(names = {"-csvInput"}, description = "Look for result csv's in the specified directory and provide R output")
    private String csvInput = null;
    
    @Parameter(names = {"-rAnalyzedInput"}, description = "Look for R results in the specified directory and print the results")
    private String rAnalyzedInput = null;
    
    @Parameter(names = {"-runs"}, description = "Test determined targets multiple times (possibly in parallel)")
    private int runs = 1;
    
    @Parameter(names = {"-noAutoFlags"}, description = "Do not set additional parameters for libraries where required")
    private boolean noAutoFlags = false;
    
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

    public boolean isSkipR() {
        return skipR;
    }

    public void setSkipR(boolean skipR) {
        this.skipR = skipR;
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
    
    public boolean isManageOnly() {
        return manageOnly;
    }

    public void setManageOnly(boolean manageOnly) {
        this.manageOnly = manageOnly;
    }

    public boolean isMeasureOnly() {
        return measureOnly;
    }

    public void setMeasureOnly(boolean measureOnly) {
        this.measureOnly = measureOnly;
    }

    public boolean isEphemeral() {
        return ephemeral;
    }

    public void setEphemeral(boolean ephemeral) {
        this.ephemeral = ephemeral;
    }

    public boolean isKillProcess() {
        return killProcess;
    }

    public void setKillProcess(boolean killProcess) {
        this.killProcess = killProcess;
    }

    public String getAdditionalParameter() {
        return additionalParameter;
    }

    public void setAdditionalParameter(String additionalParameter) {
        this.additionalParameter = additionalParameter;
    }

    public String getCsvInput() {
        return csvInput;
    }

    public void setCsvInput(String csvInput) {
        this.csvInput = csvInput;
    }
    
    public boolean isAnalyzeOnly() {
        return csvInput != null || rAnalyzedInput != null;
    }

    public int getRuns() {
        return runs;
    }

    public void setRuns(int runs) {
        this.runs = runs;
    }

    public boolean isNoAutoFlags() {
        return noAutoFlags;
    }

    public void setNoAutoFlags(boolean noAutoFlags) {
        this.noAutoFlags = noAutoFlags;
    }

    public String getrAnalyzedInput() {
        return rAnalyzedInput;
    }

    public void setrAnalyzedInput(String rAnalyzedInput) {
        this.rAnalyzedInput = rAnalyzedInput;
    }
}
