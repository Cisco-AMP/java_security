# Remote Code Execution for Java Developers

NOTE: This is an educational repository with demo code. This code is NOT meant for production or deploying.

Open `blog.md` for the writeup associated with this repo.

### Running the Demo

Running the server followed by running the client will result in the server being exploited.
Note the code here only uses Java 8, but this example runs just fine on Java 11.
Change the `COMMAND` string in `Client.java` to be the command you want the server to execute.
It is currently set to the location of the calculator binary on MacOS. 

Server:
```shell
cd $REPO_NAME/server
mvn clean compile package
java -jar ./target/server-0.0.1-SNAPSHOT.jar
```

Client:
```shell
cd $REPO_NAME/client
mvn clean compile package
java -jar ./target/client-0.0.1-SNAPSHOT.jar
```

Verify the server started calculator:
```shell
pstree -s "Calculator" | cat
```
