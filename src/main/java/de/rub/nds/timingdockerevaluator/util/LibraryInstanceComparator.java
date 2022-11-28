/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package de.rub.nds.timingdockerevaluator.util;

import java.util.Comparator;

/**
 *
 * @author marcel
 */
public class LibraryInstanceComparator implements Comparator<LibraryInstance> {

    @Override
    public int compare(LibraryInstance instance1, LibraryInstance instance2) {
        if (instance1.getImplementationType() != instance2.getImplementationType()) {
            return instance1.getImplementationType().name().compareTo(instance2.getImplementationType().name());
        }
        String[] instance1Parts = instance1.getVersion().split("\\.");
        String[] instance2Parts = instance2.getVersion().split("\\.");
        for (int i = 0; i < instance1Parts.length; i++) {
            if (instance2Parts.length <= i) {
                //inst2 has less parts -> shorter
                return 1;
            }
            try {
                int inst1Version = Integer.valueOf(instance1Parts[i]);
                int inst2Version = Integer.valueOf(instance2Parts[i]);
                if (inst1Version < inst2Version) {
                    return -1;
                } else if (inst1Version > inst2Version) {
                    return 1;
                }
            } catch (NumberFormatException numForm) {
                int inst1Prefix = getNumberPrefixOfString(instance1Parts[i]);
                int inst2Prefix = getNumberPrefixOfString(instance2Parts[i]);
                if(inst1Prefix < inst2Prefix || inst2Prefix == -1) {
                    return -1;
                } else if (inst1Prefix > inst2Prefix) {
                    return 1;
                }
                
                String string1Suffix = getStringSuffixOfString(instance1Parts[i]);
                String string2Suffix = getStringSuffixOfString(instance2Parts[i]);
                if(!string1Suffix.equals(string2Suffix)) {
                    return string1Suffix.compareTo(string2Suffix);
                }
            }
        }
        
        if(instance1Parts.length < instance2Parts.length) {
            // shorter -> version part we couldn't check yet
            return -1;
        }
        return 0;
    }
    
    private int getNumberPrefixOfString(String input) {
        String number = "";
        for(char letter: input.toCharArray()) {
            if(Character.isDigit(letter)) {
                number = number + letter;
            } else {
                break;
            }
        }
        if(number.length() > 0) {
            return Integer.valueOf(number);
        } else {
            return -1;
        }
    }
    
    private String getStringSuffixOfString(String input) {
        String suffix = "";
        boolean numeric = true;
        for(char letter: input.toCharArray()) {
            if(!Character.isDigit(letter)) {
                numeric = false;
            } 
            
            if(!numeric) {
                suffix = suffix + letter;
            }
        }
        return suffix;
    }

}
