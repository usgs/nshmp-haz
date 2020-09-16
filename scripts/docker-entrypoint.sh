#!/bin/bash
# shellcheck disable=SC1090

INFO="INFO";

source "$(dirname "${0}")/docker-config.inc.sh";
exit_status=${?};
[ "${exit_status}" -eq 0 ] || exit "${exit_status}";

# Get nshmp program to call
nshmp_program=$(get_nshmp_program "${PROGRAM}");
exit_status=${?};
check_exit_status "${exit_status}";

# Get model path to use
if [ "${MOUNT_MODEL}" = true ]; then
  nshm_path="model";
else
  printf "\n%s Downloading model %s \n" "${INFO}" "${MODEL}";
  nshm_path=$(get_model_path "${MODEL}" "${NSHM_VERSION}");
  exit_status=${?};
  check_exit_status "${exit_status}";
fi

# Check site file and get site file path
site_file=$(check_sites_file);
exit_status=${?};
check_exit_status "${exit_status}";

# Check config file
[ -f "${CONFIG_FILE}" ] || echo "{}" > "${CONFIG_FILE}";
jq empty < "${CONFIG_FILE}";
exit_status=${?};
check_exit_status "${exit_status}";

# Run nshmp-haz
printf "\n\n   %s Starting %s\n" "${INFO}" "${PROGRAM}";
java -"Xmx${JAVA_XMX}" \
    -cp "/app/${PROJECT}.jar" \
    "gov.usgs.earthquake.nshmp.${nshmp_program}" \
    "${nshm_path}" \
    "${site_file}" \
    ${RETURN_PERIOD:+ "${RETURN_PERIOD}"} \
    ${IML:+ "${IML}"} \
    "${CONFIG_FILE}";
exit_status=${?};
check_exit_status "${exit_status}";

# Move results to container volume
move_to_output_volume "${CONFIG_FILE}";
exit_status=${?};
check_exit_status "${exit_status}";

printf "\n\n   %s %s complete\n" "${INFO}" "${PROGRAM}";

exit ${exit_status};
