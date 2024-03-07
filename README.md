# TLS-Docker-Timer
The TLS-Docker-Timer project builds upon the TLS-Docker-Library and TLS-Attacker to collect timing measurements for Bleichenbacher, Vaudenay Padding Oracle, and Lucky13 attacks for various versions of open-source TLS libraries.

## Requirements
- Java 11
- Docker
- Maven
- Cloned and prepared TLS-Docker-Library repository (see project's README.md)
- At least one docker image of a TLS library

## Build
All dependencies of this repository should be available on Maven Central. Building the project thus only requires
```
mvn clean install
```
This will create an *apps/* folder in the repo directory containing the executable jar file.

## Collecting measurements
A typical command to start collecting measurements is:
```
java -jar TLS-Timing-Docker-Evaluator.jar -i 10000 -n 10000 -l openssl -v 1.1.1i
```
This command collects measurements for the OpenSSL version 1.1.1i docker image. More specifically:
- `-l` specifies the library (not case sensitive) based on the label assigned by the TLS-Docker-Library 
- `-v` specifies the versions to test (case sensitive) based on the label assigned by the TLS-Docker-Library. Multiple values can be separated by a comma.
- `-n` specifies the number of measurements to collect per attack vector
- `-i` specifies the number of measurements to collect before persisting a batch of measurements (set i < n for lower RAM footprint)

Additional CLI parameters:
- `-subtask` to specify the attack to collect measurements for (not case sensitive), current options are 'Bleichenbacher', 'PaddingOracle', and 'Lucky13'
- `-b` to specify a prefix of a version instead of a complete version (e.g `-b 1.1` would also cover 1.1.1i)
- `-name` to specify a name for the output directory (defaults to LIBRARY-VERSION)
- `-ip` and `-p` to specify an IP address and port to connect to for measurements instead of a locally managed docker instance 
- `-timeout` to set the connection timeout of TLS-Attacker when collecting measurements
- `-t` to specify how many targets should be measured in parallel (defaults to 1 - measuring in parallel may cause side effects that affect the accuracy of the obtained measurements)

To get a complete list of available CLI flags, use:
```
java -jar TLS-Timing-Docker-Evaluator.jar -h
```

## Execution Process
TLS-Docker-Timer will first collect a list of targets and start docker containers. Subsequently, each target will be analyzed for supported protocol features using TLS-Scanner. This allows us to identify which attacks can be tested and which cipher suite should be used. Currently, TLS-Docker-Timer always aims to select TLS_RSA_WITH_AES_128_CBC_SHA as it is supported in a wide range of library versions and can be used for all three attacks.
Once the supported features have been determined, applicable subtasks (attacks) will be selected before starting measurements of the attacks in the following order
- Bleichenbacher
- Padding Oracle
- Lucky13

The individual attacks have a varying number of attack vectors assigned. The tool will randomize the measurement order of the vectors of an attack to mitigate the impact of external side effects on the measurements. Once the specified number of measurements has been obtained, the tool writes result files which cover all possible pairs of vectors. Results are provided as csv files starting with the header line
`V1, V2` followed by the measurements of the two vectors. Note that the measurements are written separately, i.e all measurements of the first vector will be written as a block followed by the measurements of the second vector. The tool does not retain the specific order in which the measurements have been gathered.
