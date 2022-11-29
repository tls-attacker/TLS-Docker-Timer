package de.rub.nds.timingdockerevaluator.util;

public enum DockerTargetManagement {
    KEEP_ALIVE, // one server instance for all measurements
    RESTART_SERVER, // restart server inside docker after each handshake
    RESTART_CONTAINTER, // restart docker container after each handshake
    PORT_SWITCHING // configure target to switch ports upon restart to avoid TCP wait state issues
}
