#!/bin/bash

sbt stage

sbt docker:stage

sbt docker:publishLocal