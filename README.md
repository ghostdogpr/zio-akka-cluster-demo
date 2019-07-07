# Demo for zio-akka-cluster

This repository is an example usage of [zio-akka-cluster](https://github.com/zio/zio-akka-cluster).

To run 2 different nodes, execute:
```
sbt run -J-Dconfig.resource=application1.conf
sbt run -J-Dconfig.resource=application2.conf
```