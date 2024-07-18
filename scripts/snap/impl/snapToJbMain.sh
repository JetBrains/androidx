#!/bin/bash

# This script is rarely needed to be used explicitly.
# Use parent scripts and see the parent Readme.md

set -e

if [ -z "$1" ]; then
echo "Specify folders to snap to jb-main. For example: ./snapToJbMain.sh compose ':(exclude)compose/material3'"
exit 1
fi

DIR=$(dirname "$0")
ALL_FOLDERS=${@:1}
CURRENT_COMMIT=$(git rev-parse --short @)
BRANCH_TO_RESTORE_IN_THE_END=$(git branch --show-current)

JB_MAIN_BRANCH=$(git config branch.jb-main.remote)/jb-main
INTEGRATION_BRANCH=$(git config branch.integration.remote)/integration

TO_JB_MAIN_BRANCH=integration-snap/$CURRENT_COMMIT/to-jb-main
git checkout --quiet $(git merge-base $CURRENT_COMMIT $JB_MAIN_BRANCH) -B $TO_JB_MAIN_BRANCH
$DIR/snapSubfolder.sh $CURRENT_COMMIT $ALL_FOLDERS
echo "Created $TO_JB_MAIN_BRANCH"

TO_INTEGRATION_BRANCH=integration-snap/$CURRENT_COMMIT/to-integration
git checkout --quiet $(git merge-base $CURRENT_COMMIT $INTEGRATION_BRANCH) -B $TO_INTEGRATION_BRANCH
$DIR/mergeEmpty.sh $TO_JB_MAIN_BRANCH
echo "Created $TO_INTEGRATION_BRANCH"


git checkout --quiet $BRANCH_TO_RESTORE_IN_THE_END