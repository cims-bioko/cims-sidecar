# CIMS Sidecar

A zero-configuration service to assist with tablet synchronization on LANs. It
is especially useful when attempting to synchronize a large number of tablets
with a sizable remote database.


## Building

You need Java 8+ and Apache Maven to build the project. After that, building the
project is as simple as:

```shell
# Build the the application jar
mvn package
```

## Running

To start a sidecar service on your local network, you just need to run the jar:

```shell
# Run the service (zeroconf disabled)
java -jar cims-sidecar-<version>.jar
```

### Enabling the embedded zeroconf service

By default, mobile devices will not be able to find the service over zeroconf/mdns. The reason is that the 
embedded zeroconf service is disabled due to its instability in production. In production, the service is deployed using
the avahi zeroconf service. To start the service with the embedded zeroconf service enabled, run the following:

```shell
# Run the service (zeroconf enabled)
java -Dspring.profiles.active=zeroconf -jar cims-sidecar-<version>.jar
```

## Running in production (like Ubuntu, Raspbian, or Armbian)

While running the service manually is helpful during development and testing, the service is really meant to run 
reliably in real production environments. If the power goes out, you will want to be sure that the service comes back 
online automatically. To facilitate this, the service provides a debian linux package for easy setup in production 
environments. To build and install it, you can do the following:

```shell
# Build the project
mvn clean package

# Install the package
dpgk -i target/cims-<version>_all.deb
```

This will install the package on the same machine you built it on. The package is portable, so you can transfer the .deb
file to another machine and install with dpkg as well. Once you install the package, the service will start 
automatically. However, it likely will need to be configured before it will function properly.

### Configuring the service

The default sidecar configuration needs to be updated before it will function in your environment. The steps are:

 * Configure server URL (optional)
 * Configure username and password
 * Configure avahi/zeroconf (optional)
 
 #### Configuring the server URL
 
 By default, sidecar is configured to contact https://cims-bioko.org. If you want to have sidecar interact with another
 CIMS server, you simply need to set the URL in /etc/cims-sidecar/cims-sidecar.properties:
 
```properties
##
# The URL for the remote CIMS server to fetch data from
# It *must* end with a '/'
##
cims.server.url=https://another-server-url/
```

#### Configuring the username and password

The default username and password used when contacting the server are not meant to be usable. You need to configure a 
new user on your CIMS server and assign it a role that contains the MOBILE_SYNC permission. Then, assign that user to 
campaigns you wish to sync to the sidecar. Finally, configure sidecar to use that user account in 
/etc/cims-sidecar/cims-sidecar.properties:

```properties
##
# The CIMS server username to use while fetching data
##
app.download.username=your-username

##
# The CIMS server password
##
app.download.password=your-password
``` 

#### Configure avahi/zeroconf

By default, the debian package installs an avahi service definition that advertises the cims-sidecar service on port
8080, which matches the configuration in /etc/cims-sidecar/cims-sidecar.properties by default. If you change the server
port to something other than 8080 you will need to update this configuration to match. To do that, edit 
/etc/avahi/services/cims-sidecar.service:

```xml
<service-group>
...
  <service>
    <type>_cimssc._tcp</type>
    <port>your-custom-port-number</port>
  </service>
...
</service-group>
```

### Starting and stopping the service

The debian package installs sidecar as a service as a System V init script. This allows the system (and you) to run the
sidecar service like any built-in service on linux. For example, the following commands show how to stop, start, and 
restart the service:

```shell
# Stop the sidecar service
sudo service cims-sidecar stop

# Start the sidecar service
sudo service cims-sidecar start

# Restart the sidecar service
sudo service cims-sidecar restart
```