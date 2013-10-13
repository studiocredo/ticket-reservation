#!/bin/bash
dropdb credo -U postgres -h localhost
createdb credo -U postgres -W -h localhost
