# Distributed Cloud Storage
This project is for a course CSE811 in Ajou Univ. 
The application is basically a client-server model 
but it has different server nodes for better fault tolerance and to distribute client traffic. 
Each component is deployed in a docker container except the clients. 
The server container will be tied in a same network so that each node can communicate 
with each other.

The detail of implementation will not be explained since the code is pretty much self-explantory.

## Caution
* Master & Slaves should run first and they need a time to be tied with each other, 
which means that there must be a time to the slave to send a capacity to the master.

### Run server as container
```bash
$ ./server.sh
```

### Run Client as a container
```bash
$ ./client.sh
```

### Run Server in local
```bash
# Master
$ pwd
dapp_project/src
$ javac dapp/Master.java
$ java dapp.Master

# Slave
$ pwd
dapp_project/src
$ javac dapp/Slave.java
$ java dapp.Slave 7001
```

### Run Client in local
```bash
# Master
$ pwd
dapp_project/src
$ javac dapp/Client.java
$ java dapp.Client
```

### Commands in Client
```bash
# List files in the server
$ java dapp.Client list

# Download a file 
$ java dapp.Client download <file>

# Upload a file 
$ java dapp.Client upload <file>
```
