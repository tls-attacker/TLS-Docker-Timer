package de.rub.nds.timingdockerevaluator.util;

import de.rub.nds.timingdockerevaluator.task.subtask.SubtaskNames;
import java.io.File;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class CsvFileGroup {
    private final LibraryInstance libraryInstance;
    
    private final List<File> bleichenbacherFiles = new LinkedList<>();
    private final List<File> paddingOracleFiles = new LinkedList<>();
    private final List<File> lucky13Files = new LinkedList<>();

    public CsvFileGroup(LibraryInstance libraryInstance) {
        this.libraryInstance = libraryInstance;
    }
    
    public void insertFile(File fileToAdd) {
        if(fileToAdd.getParentFile().getName().equals(SubtaskNames.BLEICHENBACHER.getCamelCaseName())) {
            getBleichenbacherFiles().add(fileToAdd);
        } else if(fileToAdd.getParentFile().getName().equals(SubtaskNames.PADDING_ORACLE.getCamelCaseName())) {
            getPaddingOracleFiles().add(fileToAdd);
        } else if(fileToAdd.getParentFile().getName().equals(SubtaskNames.LUCKY13.getCamelCaseName())) {
            getLucky13Files().add(fileToAdd);
        } else {
            throw new IllegalArgumentException("Failed to assign correct result type for folder " + fileToAdd.getParentFile().getName());
        }
    }

    public LibraryInstance getLibraryInstance() {
        return libraryInstance;
    }

    public List<File> getBleichenbacherFiles() {
        return bleichenbacherFiles;
    }

    public List<File> getPaddingOracleFiles() {
        return paddingOracleFiles;
    }

    public List<File> getLucky13Files() {
        return lucky13Files;
    }
    
    public Set<File> getAllUniqueFiles() {
        Set<File> allFiles = new HashSet<>();
        allFiles.addAll(bleichenbacherFiles);
        allFiles.addAll(paddingOracleFiles);
        allFiles.addAll(lucky13Files);
        return allFiles;
    }
}
