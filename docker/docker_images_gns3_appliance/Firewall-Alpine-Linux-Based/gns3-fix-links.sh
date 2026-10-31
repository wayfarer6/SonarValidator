#!/bin/sh
set -eu

find /etc -type l -print | while IFS= read -r link; do
    target=$(readlink "$link")
    case "$target" in
        /*)
            relative=$(awk -v link="$link" -v target="$target" '
                BEGIN {
                    sub("/[^/]*$", "", link)
                    from_count = split(link, from, "/")
                    to_count = split(target, to, "/")
                    i = 1
                    j = 1
                    while (i <= from_count && j <= to_count && from[i] == to[j]) {
                        i++
                        j++
                    }
                    result = ""
                    for (; i <= from_count; i++) {
                        if (from[i] != "") result = result "../"
                    }
                    for (; j <= to_count; j++) {
                        if (to[j] != "") {
                            if (result != "" && substr(result, length(result), 1) != "/") result = result "/"
                            result = result to[j]
                        }
                    }
                    if (result == "") result = "."
                    print result
                }')
            ln -snf "$relative" "$link"
            ;;
    esac
done

if [ "$(readlink /etc/mtab 2>/dev/null || true)" = /proc/mounts ]; then
    ln -snf ../proc/mounts /etc/mtab
fi
