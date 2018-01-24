# CIMS Sidecar

A zero-configuration service to assist with tablet synchronization on LANs. It
is especially useful when attempting to synchronize a large number of tablets
with a sizable remote database.

## Usage

In the future, this application may be deployable as a container. However, it is
extremely simple to build and run even without it.

### Building it

You need Java 8 and Apache Maven to build the project. After that, building the
project is as simple as:

```shell
# Build the the application jar
mvn package
```

Once this is done, you should find a built jar at 
`target/cims-sidecar-<version>.jar` under the project root. Now, you just need 
start it.

### Running it

To start a sidecar service on your local network, you just need to run the jar:

```shell
java -jar cims-sidecar-<version>.jar
```