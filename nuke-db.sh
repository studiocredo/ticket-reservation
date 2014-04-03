#!/bin/bash
dropdb credo -U vhs -h localhost
createdb credo -U vhs -W -h localhost
