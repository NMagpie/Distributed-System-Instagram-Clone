#!/bin/bash

docker build ./post -t post

docker build ./discovery -t discovery

docker build ./discovery -t discovery

cd Auth

sbt docker:publishLocal

cd ../Cache

sbt docker:publishLocal

cd ../Gateway

sbt docker:publishLocal