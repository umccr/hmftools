#!/bin/bash
set -euo pipefail

TOOL="esvee"
VERSION="1.0.2"

# Parse arguments
jvm_mem_opts=""
jvm_gen_opts=""
declare -a app_args
for arg in "$@"; do
  case ${arg} in
    '-Xm'*)
      jvm_mem_opts="${jvm_mem_opts} ${arg}"
      ;;
    '-D'* | '-XX'*)
      jvm_gen_opts="${jvm_gen_opts} ${arg}"
      ;;
    *)
      app_args+=("${arg}")
      ;;
  esac
done

# Form and execute command
if [[ ${app_args[0]:=} == com.hartwig.* ]]; then
  java ${jvm_mem_opts} ${jvm_gen_opts} -cp /usr/share/java/${TOOL}_v${VERSION}.jar ${app_args[@]}
else
  java ${jvm_mem_opts} ${jvm_gen_opts} -jar /usr/share/java/${TOOL}_v${VERSION}.jar ${app_args[@]}
fi

