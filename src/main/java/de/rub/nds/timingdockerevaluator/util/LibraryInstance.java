package de.rub.nds.timingdockerevaluator.util;

import de.rub.nds.tls.subject.TlsImplementationType;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;


public class LibraryInstance {
    public static final int CSV_VERSION_PARTS = 6;
    
    private final TlsImplementationType implementationType;
    private final String version;

    public LibraryInstance(TlsImplementationType implementationType, String version) {
        this.implementationType = implementationType;
        this.version = version;
    }
    
    public static LibraryInstance fromDockerName(String dockerName) {
        TlsImplementationType library = getLibraryFromDockerName(dockerName);
        String version = getVersionFromDockerName(library, dockerName);
        return new LibraryInstance(library, version);
    }

    public TlsImplementationType getImplementationType() {
        return implementationType;
    }

    public String getVersion() {
        return version;
    }
    
    public String getDockerName() {
        return implementationType.name() + "-" + getVersion();
    }
    
    public List<String> getCsvPreparedDockerName() {
        List<String> output = new LinkedList<>();
        output.add(implementationType.name());
        
        String[] versionParts = version.split("\\.");
        boolean onlyNumeric = true;
        String letterSuffix = "";
        for(String part : versionParts) {
            for(char letter: part.toCharArray()) {
                if(!Character.isDigit(letter)) {
                    onlyNumeric = false;
                }
                if(onlyNumeric) {
                    output.add("'" + letter);
                } else {
                    letterSuffix = letterSuffix + letter;
                }
            }
        }
        if(!onlyNumeric) {
            output.add("'" + letterSuffix);
        }
        
        if(output.size() > CSV_VERSION_PARTS) {
            throw new RuntimeException("Too many CSV version parts: ");
        }
        while(output.size() < CSV_VERSION_PARTS) {
            output.add("-");
        }
        return output;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.implementationType);
        hash = 67 * hash + Objects.hashCode(this.version);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final LibraryInstance other = (LibraryInstance) obj;
        if (!Objects.equals(this.version, other.version)) {
            return false;
        }
        return this.implementationType == other.implementationType;
    }
    
    public static TlsImplementationType getLibraryFromDockerName(String suffix) {
        for(TlsImplementationType knownType : TlsImplementationType.values()) {
            if(suffix.startsWith(knownType.name())) {
                return knownType;
            }
        }
        return null;
    }
    
    public static String getVersionFromDockerName(TlsImplementationType library, String dockerName)  {
        return dockerName.replace(library.name()+ "-", "");
    }
    
    
}
