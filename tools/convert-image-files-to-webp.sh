#!/bin/bash
set -euo pipefail

find . -type f \( -iname "*.png" -o -iname "*.jpg" -o -iname "*.jpeg" \) | while IFS= read -r file; do
    output="${file%.*}.webp"
    echo "Converting: $file -> $output"
    if cwebp -q 90 "$file" -o "$output"; then
        rm "$file"
    else
        echo "ERROR: Failed to convert $file" >&2
    fi
done
