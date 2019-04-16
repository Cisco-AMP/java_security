package com.cisco.amp.server;

import org.apache.commons.io.IOUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

@RestController
public class SubmissionController {


    @PostMapping("/submit")
    public String submit(HttpServletRequest requestEntity) throws IOException, ClassNotFoundException {

        //extract the bytes and deserialize them
        byte[] bytes = IOUtils.toByteArray(requestEntity.getInputStream());
        ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(bytes));
        Submission submission = (Submission) stream.readObject();

        return submission.toString();
    }

}
