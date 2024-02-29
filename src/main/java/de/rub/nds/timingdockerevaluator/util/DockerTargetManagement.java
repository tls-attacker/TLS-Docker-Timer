package de.rub.nds.timingdockerevaluator.util;

public enum DockerTargetManagement {
    KEEP_ALIVE("One server instance will be used for all measurements"),
    RESTART_SERVER("Will restart server inside docker after each handshake"),
    RESTART_CONTAINTER("Will restart docker container after each handshake"),
    PORT_SWITCHING("Will configure target to switch server port upon restart to avoid TCP wait state issues");
    
    private final String description;
    
    private DockerTargetManagement(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
