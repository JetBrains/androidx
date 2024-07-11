#!/bin/bash

#
# Copyright 2024 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Directory to go through
crowding_res_path=$1
target_dir='res'

# Check if directory path is not empty
if [ -z "$crowding_res_path" ]; then
    echo "Error: No source directory path provided."
    echo "Usage: $0 <directory path>"
    exit 1
fi

alias_name() {
    local name=$1
    case "$name" in
        "pt-rPT") echo "pt" ;;
        "es-rES") echo "es" ;;
        "hy-rAM") echo "hy" ;;
        "id") echo "in" ;;
        "he") echo "iw" ;;
        "sv-rSE") echo "sv" ;;
        "gu-rIN") echo "gu" ;;
        "ml-rIN") echo "ml" ;;
        "ne-rNP") echo "ne" ;;
        "pa-rIN") echo "pa" ;;
        "si-rLK") echo "si" ;;
        "ur-rPK") echo "ur" ;;
        *) echo "" ;;
    esac
}

rm -rf $target_dir
mkdir -p $target_dir

mkdir -p "$target_dir/values"
cp "$crowding_res_path/en/general/strings.xml" "$target_dir/values/strings.xml"

for locale_dir in "$crowding_res_path"/*; do
    # Check if it is a directory
    echo locale $locale_dir

    if [ -d "$locale_dir" ]; then
        locale=$(basename "$locale_dir") # Get the locale name from the directory name
        locale="${locale/-/-r}"

        if [ -f "$locale_dir/general/strings.xml" ]; then
            localAlias=$(alias_name $locale)
            if [ "$localAlias" != "" ]; then
                mkdir -p "$target_dir/values-$localAlias"
                cp "$locale_dir/general/strings.xml" "$target_dir/values-$localAlias/strings.xml"
            else
                mkdir -p "$target_dir/values-$locale"
                cp "$locale_dir/general/strings.xml" "$target_dir/values-$locale/strings.xml"
            fi
        fi
    fi
done
