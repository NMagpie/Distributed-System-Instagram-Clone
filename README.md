 # PAD Lab 1: Web Proxy

## Made by: Sorochin Nichita, FAF-191

### Navigation

- [Description](#description)
- [Main Components](#main-components)
    - [Message Broker](#message-broker)
    - [Consumer](#consumer)
    - [Producer](#producer)
    - [RTP-Server](#rtp-server)
- [User Technologies](#used-technologies)

# Description

Microservice system designed as Instagram Clone. The primary interaction with client
happens through the Gateway API, which has several REST API endpoints.

- `WIP` In future the number of microservices will be customizable.

- `WIP` For all the Apps, you can find configuration in `/src/resources/application.conf`.
There you can configure network addresses for Apps (like `hostname` and address of database servers.
For every value default is `localhost`).

All the diagrams and documents are located in folder `docs`.

# Main Components

- ## Gateway API `WIP` (`Scala + Akka`)

The server, which is a bridge in users and microservices interaction. Forwards all
the requests it has received.

Located in the folder `Gateway`.

- ## Discovery Service `WIP` (`Scala + Akka`)

The service with static port, which every other service and gateway are trying
to connect to. Contains all the data about state of services and gateways, and if other side
is not connected to the system, it is considered turned off.

Located in the folder `Discovery Service`

- ## Other stuff coming soon...

# Used Technologies

- ### Scala Language - 2.13.8

- ### Akka Library - 2.6.19

- ### Alpakka Http - 10.2.10

- ### Akka gRPC plugin - 2.1.6