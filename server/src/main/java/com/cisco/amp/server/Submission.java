package com.cisco.amp.server;

import java.io.Serializable;
import java.util.Collection;

public class Submission implements Serializable {

    private final Collection<String> values;

    public Submission(Collection<String> values) {
        this.values = values;
    }

    //Print out the collection - one entry on each line
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        //This is where the exploitation happens
        //Collection::iterator gets called for the for loop to iterate over the values
        //This is done without properly validating the user input first
        for (String entry : values) {
            sb.append(entry);
            sb.append("\n");
        }

        return sb.toString();
    }
}
