#!/bin/bash

# Grab root directory to help with creating an absolute path for changed files.
root_dir="$(git rev-parse --show-toplevel)"
[ -d "${root_dir}" ] || exit 1

# Path to jar
jar_base_dir="git_hooks/"
formatter_jar="${root_dir}/${jar_base_dir}/google-java-format-1.6-SNAPSHOT-all-deps.jar"
formatter_cmd="java -jar ${formatter_jar}"

# Format file in-place and use 4-space style (AOSP).
formatter_args="--replace --aosp"

changed_java_files=($(git diff --name-only --diff-filter=ACMR src/main/java src/test/java \
    | grep ".*java$" \
    | sed "s:^:${root_dir}/:"))

# If we have changed java files, format them!
if [ ${#changed_java_files[@]} -gt 0 ]; then
    eval ${formatter_cmd} ${formatter_args} "${changed_java_files[@]}"
fi